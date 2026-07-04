package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.database.MusicDatabase
import com.example.data.model.PlaylistEntity
import com.example.data.model.Song
import com.example.data.repository.MusicRepository
import com.example.playback.MusicPlaybackService
import com.example.playback.MusicPlayerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val db: MusicDatabase = Room.databaseBuilder(
        application,
        MusicDatabase::class.java,
        "nirvana_music_db"
    ).fallbackToDestructiveMigration().build()

    private val songDao = db.songDao()
    val repository = MusicRepository(application, songDao)
    val playerManager = MusicPlayerManager.getInstance(application)

    init {
        playerManager.repository = repository
    }

    // Playback state bindings
    val currentSong: StateFlow<Song?> = playerManager.currentSong
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val progress: StateFlow<Long> = playerManager.progress
    val duration: StateFlow<Long> = playerManager.duration
    val playbackQueue: StateFlow<List<Song>> = playerManager.queue
    val isShuffle: StateFlow<Boolean> = playerManager.isShuffle
    val isRepeat: StateFlow<Boolean> = playerManager.isRepeat
    val sleepTimerRemaining: StateFlow<Long> = playerManager.sleepTimerRemaining
    val eqBands: StateFlow<Map<Int, Int>> = playerManager.eqBands

    // Playlists & Offline list
    val cachedSongs: StateFlow<List<Song>> = repository.cachedSongsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Jam Session State (Spotify-style real-time group listening)
    private val _isJoinedJam = MutableStateFlow(false)
    val isJoinedJam: StateFlow<Boolean> = _isJoinedJam.asStateFlow()

    private val _jamRoomCode = MutableStateFlow<String?>(null)
    val jamRoomCode: StateFlow<String?> = _jamRoomCode.asStateFlow()

    private val _jamLogs = MutableStateFlow<List<String>>(emptyList())
    val jamLogs: StateFlow<List<String>> = _jamLogs.asStateFlow()

    data class JamParticipant(val name: String, val avatar: String, val isHost: Boolean = false, val isMuted: Boolean = false)
    private val _jammers = MutableStateFlow<List<JamParticipant>>(emptyList())
    val jammers: StateFlow<List<JamParticipant>> = _jammers.asStateFlow()

    private var jamSimulationJob: Job? = null

    init {
        // Run initial search with empty query to pre-populate home recommendation feeds
        executeSearch("")
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        executeSearch(newQuery)
    }

    private fun executeSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            // Simulate brief network latency for premium look
            if (query.isNotEmpty()) {
                delay(300)
            }
            val results = repository.searchSongs(query)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    // Mobile Data Warning Dialog state
    private val _showMobileDataWarningForSong = MutableStateFlow<Song?>(null)
    val showMobileDataWarningForSong: StateFlow<Song?> = _showMobileDataWarningForSong.asStateFlow()

    fun dismissMobileDataWarning() {
        _showMobileDataWarningForSong.value = null
    }

    var useYoutubeOnMobileData: Boolean
        get() = playerManager.useYoutubeOnMobileData
        set(value) {
            playerManager.useYoutubeOnMobileData = value
        }

    // Playback control wrappers
    fun playSong(song: Song, contextQueue: List<Song> = emptyList()) {
        val q = if (contextQueue.isNotEmpty()) contextQueue else _searchResults.value
        
        // Intercept YouTube streams on mobile data
        if (song.source == "YouTube" && !playerManager.isWifiConnected() && !playerManager.useYoutubeOnMobileData) {
            _showMobileDataWarningForSong.value = song
            return
        }

        executePlaySong(song, q)
    }

    fun playSongAnyway(song: Song, contextQueue: List<Song> = emptyList()) {
        val q = if (contextQueue.isNotEmpty()) contextQueue else _searchResults.value
        executePlaySong(song, q)
    }

    private fun executePlaySong(song: Song, q: List<Song>) {
        playerManager.playSong(song, q)
        
        // Trigger background service
        val intent = Intent(getApplication(), MusicPlaybackService::class.java)
        getApplication<Application>().startService(intent)

        // Log action to Jam if joined
        if (_isJoinedJam.value) {
            logJamAction("You changed song to: ${song.title}")
        }
    }

    fun playOrPause() {
        playerManager.playOrPause()
        
        // Start service if we are playing
        if (playerManager.isPlaying.value) {
            val intent = Intent(getApplication(), MusicPlaybackService::class.java)
            getApplication<Application>().startService(intent)
        }

        if (_isJoinedJam.value) {
            val actionText = if (isPlaying.value) "You paused the playback" else "You resumed playback"
            logJamAction(actionText)
        }
    }

    fun next() {
        playerManager.next()
        if (_isJoinedJam.value) {
            logJamAction("You skipped to the next song")
        }
    }

    fun prev() {
        playerManager.prev()
        if (_isJoinedJam.value) {
            logJamAction("You skipped back")
        }
    }

    fun seekTo(position: Long) {
        playerManager.seekTo(position)
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        playerManager.reorderQueue(fromIndex, toIndex)
    }

    fun removeFromQueue(songId: String) {
        playerManager.removeFromQueue(songId)
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playerManager.toggleRepeat()
    }

    fun setSleepTimer(minutes: Int) {
        playerManager.setSleepTimer(minutes)
    }

    fun setEqualizerBand(band: Int, value: Int) {
        playerManager.setEqualizerBand(band, value)
    }

    // Playlists & offline cache management
    fun toggleOfflineCache(song: Song) {
        viewModelScope.launch {
            val alreadyCached = repository.isSongCached(song.id)
            if (alreadyCached) {
                repository.removeSongCache(song.id)
                Toast.makeText(getApplication(), "Removed ${song.title} from offline cache", Toast.LENGTH_SHORT).show()
            } else {
                repository.cacheSongOffline(song)
                Toast.makeText(getApplication(), "Saved ${song.title} for offline listening", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun isSongCachedState(songId: String): Flow<Boolean> = flow {
        emit(repository.isSongCached(songId))
    }

    fun createPlaylist(name: String, description: String) {
        viewModelScope.launch {
            repository.createPlaylist(
                name = name,
                description = description,
                coverArtwork = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500"
            )
            Toast.makeText(getApplication(), "Playlist '$name' created", Toast.LENGTH_SHORT).show()
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            Toast.makeText(getApplication(), "Playlist deleted", Toast.LENGTH_SHORT).show()
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, song)
            Toast.makeText(getApplication(), "Added to playlist", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
            Toast.makeText(getApplication(), "Removed from playlist", Toast.LENGTH_SHORT).show()
        }
    }

    fun getSongsForPlaylistFlow(playlistId: Long): Flow<List<Song>> {
        return repository.getSongsForPlaylist(playlistId)
    }

    // Jam Sync (Spotify Jam simulated multi-user environment)
    fun createJam() {
        val code = "NIRVANA-${(100..999).random()}"
        _jamRoomCode.value = code
        _isJoinedJam.value = true
        _jammers.value = listOf(
            JamParticipant("You (Host)", "🎸", isHost = true)
        )
        _jamLogs.value = listOf("Jam session started! Send code $code to friends to listen live.")
        startJamSimulation()
    }

    fun joinJam(code: String) {
        if (code.isBlank()) return
        _jamRoomCode.value = code.uppercase()
        _isJoinedJam.value = true
        _jammers.value = listOf(
            JamParticipant("You", "🎧"),
            JamParticipant("Kurt Cobain (Host)", "🎸", isHost = true),
            JamParticipant("Krist Novoselic", "🎸"),
            JamParticipant("Dave Grohl", "🥁")
        )
        _jamLogs.value = listOf(
            "Joined Jam room $code successfully!",
            "Dave Grohl is tapping to the beat.",
            "Host Kurt is playing: ${currentSong.value?.title ?: "Smells Like Teen Spirit Acoustic"}"
        )
        startJamSimulation()
    }

    fun leaveJam() {
        jamSimulationJob?.cancel()
        _isJoinedJam.value = false
        _jamRoomCode.value = null
        _jammers.value = emptyList()
        _jamLogs.value = emptyList()
        Toast.makeText(getApplication(), "Left the Jam Session", Toast.LENGTH_SHORT).show()
    }

    private fun logJamAction(message: String) {
        val currentLogs = _jamLogs.value.toMutableList()
        currentLogs.add(0, message)
        _jamLogs.value = currentLogs.take(30) // keep last 30 logs
    }

    private fun startJamSimulation() {
        jamSimulationJob?.cancel()
        jamSimulationJob = viewModelScope.launch {
            val mockPeers = listOf("Dave Grohl", "Krist Novoselic", "Lars Ulrich", "Taylor Swift", "Kurt Cobain")
            val avatars = listOf("🥁", "🎸", "🎤", "🎶", "📀")
            val reactions = listOf(
                "is feeling the groove!",
                "added a track to the shared queue",
                "voted to skip current track",
                "synchronized playback volume to 80%",
                "sent a double-tap emoji reaction 👍",
                "is headbanging to this guitar solo!"
            )

            while (_isJoinedJam.value) {
                delay((6000..15000).random().toLong()) // periodic event
                val peer = mockPeers.random()
                val avatar = avatars.random()
                val reaction = reactions.random()

                // Randomly add a peer if there are fewer than 5
                val currentJammers = _jammers.value.toMutableList()
                if (currentJammers.size < 5 && (0..10).random() > 6) {
                    val newPeer = JamParticipant(peer, avatar)
                    if (!currentJammers.any { it.name == peer }) {
                        currentJammers.add(newPeer)
                        _jammers.value = currentJammers
                        logJamAction("👋 $peer joined the Jam session!")
                        continue
                    }
                }

                // Randomly perform a sync action
                logJamAction("⚡ $peer $reaction")

                // Random skip or play sync simulation (without overriding user's track unless desired, let's keep it harmless!)
                if (reaction.contains("voted to skip") && isPlaying.value) {
                    delay(1500)
                    logJamAction("⏭️ Queue automatically skipped by majority group vote.")
                    next()
                }
            }
        }
    }
}
