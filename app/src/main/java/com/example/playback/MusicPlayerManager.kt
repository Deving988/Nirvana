package com.example.playback

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.data.model.Song
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

class MusicPlayerManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State flows for Compose UI
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0L)
    val progress: StateFlow<Long> = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isRepeat = MutableStateFlow(false)
    val isRepeat: StateFlow<Boolean> = _isRepeat.asStateFlow()

    // Sleep Timer
    private val _sleepTimerRemaining = MutableStateFlow<Long>(0L) // milliseconds
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()
    private var sleepTimerJob: Job? = null

    // Equalizer State
    private var physicalEqualizer: Equalizer? = null
    private val _eqBands = MutableStateFlow<Map<Int, Int>>(mapOf(0 to 50, 1 to 50, 2 to 50, 3 to 50, 4 to 50)) // Percentages
    val eqBands: StateFlow<Map<Int, Int>> = _eqBands.asStateFlow()

    // Persistent Settings
    private val prefs = appContext.getSharedPreferences("nirvana_settings", Context.MODE_PRIVATE)
    var useYoutubeOnMobileData: Boolean
        get() = prefs.getBoolean("use_youtube_on_mobile_data", false)
        set(value) {
            prefs.edit().putBoolean("use_youtube_on_mobile_data", value).apply()
        }

    // Network connection utility
    fun isWifiConnected(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        return if (cm != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                val activeNetworkInfo = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                activeNetworkInfo != null && activeNetworkInfo.type == android.net.ConnectivityManager.TYPE_WIFI
            }
        } else {
            false
        }
    }

    private var youtubeWebView: android.webkit.WebView? = null
    private var isYoutubeReady = false

    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            val song = _currentSong.value
            if (song != null) {
                if (song.source == "YouTube") {
                    youtubeWebView?.let { webView ->
                        if (isYoutubeReady && _isPlaying.value) {
                            webView.evaluateJavascript("getCurrentTime()") { timeStr ->
                                try {
                                    val timeSec = timeStr?.replace("\"", "")?.toFloatOrNull() ?: 0f
                                    _progress.value = (timeSec * 1000).toLong()
                                } catch (e: Exception) {}
                            }
                            webView.evaluateJavascript("getDuration()") { durationStr ->
                                try {
                                    val durationSec = durationStr?.replace("\"", "")?.toFloatOrNull() ?: 0f
                                    if (durationSec > 0) {
                                        _duration.value = (durationSec * 1000).toLong()
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                    }
                } else {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            _progress.value = player.currentPosition.toLong()
                            _duration.value = player.duration.toLong()
                        }
                    }
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    var repository: MusicRepository? = null
    var onStateChangedListener: (() -> Unit)? = null

    init {
        initMediaPlayer()
        initYoutubeWebView()
        handler.post(progressUpdater)
    }

    private fun initMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setOnCompletionListener {
                handleTrackCompletion()
            }
            setOnErrorListener { _, what, extra ->
                Log.e("MusicPlayerManager", "MediaPlayer error: what=$what, extra=$extra")
                true
            }
        }
    }

    // Serves app assets (our local youtube_player.html) over a real HTTPS-style origin
    // (https://appassets.androidplatform.net/...) instead of loadDataWithBaseURL, which
    // was the root cause of "Error 152": the WebView never sent a valid HTTP Referer to
    // YouTube because the page wasn't actually being served from anywhere.
    private var assetLoader: androidx.webkit.WebViewAssetLoader? = null

    private fun initYoutubeWebView() {
        handler.post {
            try {
                assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(appContext))
                    .build()

                val webView = android.webkit.WebView(appContext)
                webView.settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                }

                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: android.webkit.WebView,
                        request: android.webkit.WebResourceRequest
                    ): android.webkit.WebResourceResponse? {
                        return assetLoader?.shouldInterceptRequest(request.url)
                    }
                }

                webView.addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onPlayerReady() {
                        Log.d("MusicPlayerManager", "YouTube HTML Player is fully ready")
                        isYoutubeReady = true
                    }

                    @android.webkit.JavascriptInterface
                    fun onPlayerStateChange(state: Int) {
                        Log.d("MusicPlayerManager", "YouTube Player state change callback: $state")
                        // YT.PlayerState: ENDED = 0, PLAYING = 1, PAUSED = 2, BUFFERING = 3
                        mainScope.launch {
                            when (state) {
                                0 -> { // ENDED
                                    _isPlaying.value = false
                                    handleTrackCompletion()
                                }
                                1 -> { // PLAYING
                                    _isPlaying.value = true
                                    notifyService()
                                }
                                2 -> { // PAUSED
                                    _isPlaying.value = false
                                    notifyService()
                                }
                            }
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onPlayerError(error: Int) {
                        Log.e("MusicPlayerManager", "YouTube Player JavaScript error: $error")
                        mainScope.launch {
                            // Common codes: 2 = invalid video id, 5 = HTML5 player error,
                            // 100 = video removed/private, 101/150 = embedding disabled by owner.
                            val message = when (error) {
                                100, 101, 150 -> "This song can't be played here (embedding disabled). Skipping..."
                                else -> "YouTube playback issue (Error $error). Skipping..."
                            }
                            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                            _isPlaying.value = false
                            // Auto-skip to the next track instead of getting stuck.
                            next()
                        }
                    }
                }, "Android")

                webView.loadUrl("https://appassets.androidplatform.net/assets/youtube_player.html")
                youtubeWebView = webView
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Failed to construct hidden YouTube webview: ${e.message}")
            }
        }
    }

    fun playSong(song: Song, newQueue: List<Song> = emptyList()) {
        mainScope.launch {
            try {
                if (newQueue.isNotEmpty()) {
                    _queue.value = newQueue
                } else if (!_queue.value.any { it.id == song.id }) {
                    _queue.value = _queue.value + song
                }

                _currentSong.value = song
                _progress.value = 0L

                if (song.source == "YouTube") {
                    // Release/stop native MediaPlayer
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            it.pause()
                        }
                    }
                    _isPlaying.value = true
                    _duration.value = song.duration

                    // Trigger video load inside YouTube WebView
                    handler.post {
                        youtubeWebView?.evaluateJavascript("playVideo('${song.streamUrl}')", null)
                    }
                    notifyService()
                } else {
                    // Pause YouTube player if running
                    handler.post {
                        youtubeWebView?.evaluateJavascript("pauseVideo()", null)
                    }

                    initMediaPlayer()

                    mediaPlayer?.apply {
                        setDataSource(song.streamUrl)
                        prepareAsync()
                        setOnPreparedListener { player ->
                            player.start()
                            _isPlaying.value = true
                            _duration.value = player.duration.toLong()
                            setupEqualizer(player.audioSessionId)
                            notifyService()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Error playing song: ${e.message}")
                Toast.makeText(appContext, "Error loading audio stream", Toast.LENGTH_SHORT).show()
                _isPlaying.value = false
            }
        }
    }

    fun playOrPause() {
        val song = _currentSong.value
        if (song != null && song.source == "YouTube") {
            if (_isPlaying.value) {
                handler.post {
                    youtubeWebView?.evaluateJavascript("pauseVideo()", null)
                }
                _isPlaying.value = false
            } else {
                handler.post {
                    youtubeWebView?.evaluateJavascript("playVideo()", null)
                }
                _isPlaying.value = true
            }
            notifyService()
            return
        }

        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else {
                if (_currentSong.value != null) {
                    player.start()
                    _isPlaying.value = true
                } else if (_queue.value.isNotEmpty()) {
                    playSong(_queue.value.first())
                }
            }
            notifyService()
        }
    }

    fun next() {
        val current = _currentSong.value ?: return
        val currentList = _queue.value
        val index = currentList.indexOfFirst { it.id == current.id }

        if (index != -1 && index < currentList.size - 1) {
            val nextSong = currentList[index + 1]
            playSong(nextSong)
        } else {
            // End of queue -> Same-genre auto-play feature!
            triggerAutoPlay(current)
        }
    }

    private fun triggerAutoPlay(currentSong: Song) {
        mainScope.launch {
            val repo = repository
            if (repo != null) {
                val results = repo.searchSongs("")
                // Filter songs with same genre, excluding current one
                val candidates = results.filter { 
                    it.genre.equals(currentSong.genre, ignoreCase = true) && it.id != currentSong.id 
                }
                
                val nextSong = if (candidates.isNotEmpty()) {
                    candidates.random()
                } else {
                    results.filter { it.id != currentSong.id }.randomOrNull() ?: currentSong
                }

                Toast.makeText(appContext, "Auto-playing similar: ${nextSong.title}", Toast.LENGTH_LONG).show()
                _queue.value = _queue.value + nextSong
                playSong(nextSong)
            } else {
                // Circular queue fallback
                if (_queue.value.isNotEmpty()) {
                    playSong(_queue.value.first())
                }
            }
        }
    }

    fun prev() {
        val current = _currentSong.value ?: return
        val currentList = _queue.value
        val index = currentList.indexOfFirst { it.id == current.id }

        if (index > 0) {
            playSong(currentList[index - 1])
        } else {
            seekTo(0L)
        }
    }

    fun seekTo(position: Long) {
        val song = _currentSong.value
        if (song != null && song.source == "YouTube") {
            handler.post {
                youtubeWebView?.evaluateJavascript("seekTo(${position / 1000f})", null)
            }
            _progress.value = position
            notifyService()
            return
        }

        mediaPlayer?.seekTo(position.toInt())
        _progress.value = position
        notifyService()
    }

    private fun handleTrackCompletion() {
        if (_isRepeat.value) {
            _progress.value = 0L
            mediaPlayer?.seekTo(0)
            mediaPlayer?.start()
            _isPlaying.value = true
            notifyService()
        } else {
            next()
        }
    }

    // Dynamic queue drag-to-reorder logic
    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val list = _queue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            Collections.swap(list, fromIndex, toIndex)
            _queue.value = list
            notifyService()
        }
    }

    fun removeFromQueue(songId: String) {
        _queue.value = _queue.value.filter { it.id != songId }
        if (_currentSong.value?.id == songId) {
            next()
        }
        notifyService()
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        if (_isShuffle.value) {
            val current = _currentSong.value
            val shuffled = _queue.value.filter { it.id != current?.id }.shuffled().toMutableList()
            if (current != null) {
                shuffled.add(0, current)
            }
            _queue.value = shuffled
        }
    }

    fun toggleRepeat() {
        _isRepeat.value = !_isRepeat.value
    }

    // Sleep Timer engine
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes == 0) {
            _sleepTimerRemaining.value = 0L
            return
        }

        val totalMillis = minutes * 60 * 1000L
        _sleepTimerRemaining.value = totalMillis

        sleepTimerJob = mainScope.launch {
            var remaining = totalMillis
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _sleepTimerRemaining.value = remaining
            }
            // Timer expired!
            if (_isPlaying.value) {
                playOrPause()
                Toast.makeText(appContext, "Sleep timer finished. Playback paused.", Toast.LENGTH_LONG).show()
            }
            _sleepTimerRemaining.value = 0L
        }
    }

    // Equalizer engine
    private fun setupEqualizer(audioSessionId: Int) {
        try {
            physicalEqualizer?.release()
            physicalEqualizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                val bands = numberOfBands
                Log.d("MusicPlayerManager", "Physical Equalizer configured with $bands bands")
            }
            // Apply current levels
            _eqBands.value.forEach { (band, levelPercentage) ->
                setPhysicalBandLevel(band, levelPercentage)
            }
        } catch (e: Exception) {
            Log.w("MusicPlayerManager", "Hardware equalizer not supported on this platform: ${e.message}")
        }
    }

    fun setEqualizerBand(band: Int, levelPercentage: Int) {
        val updated = _eqBands.value.toMutableMap()
        updated[band] = levelPercentage
        _eqBands.value = updated
        setPhysicalBandLevel(band, levelPercentage)
    }

    private fun setPhysicalBandLevel(band: Int, levelPercentage: Int) {
        try {
            physicalEqualizer?.let { eq ->
                val range = eq.bandLevelRange
                val minLevel = range[0]
                val maxLevel = range[1]
                val targetLevel = minLevel + ((maxLevel - minLevel) * (levelPercentage / 100f)).toInt()
                eq.setBandLevel(band.toShort(), targetLevel.toShort())
            }
        } catch (e: Exception) {
            Log.w("MusicPlayerManager", "Cannot set physical equalizer band: ${e.message}")
        }
    }

    private fun notifyService() {
        onStateChangedListener?.invoke()
    }

    companion object {
        @Volatile
        private var instance: MusicPlayerManager? = null

        fun getInstance(context: Context): MusicPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayerManager(context).also { instance = it }
            }
        }
    }
}
