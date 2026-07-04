package com.example.playback

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.example.MainActivity

class MusicPlaybackService : Service() {

    private lateinit var playerManager: MusicPlayerManager
    private var mediaSession: MediaSession? = null

    companion object {
        const val CHANNEL_ID = "nirvana_playback_channel"
        const val NOTIFICATION_ID = 808
        const val ACTION_PLAY = "com.example.nirvana.PLAY"
        const val ACTION_PAUSE = "com.example.nirvana.PAUSE"
        const val ACTION_NEXT = "com.example.nirvana.NEXT"
        const val ACTION_PREV = "com.example.nirvana.PREV"
        const val ACTION_STOP = "com.example.nirvana.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        playerManager = MusicPlayerManager.getInstance(this)
        
        createNotificationChannel()
        setupMediaSession()

        playerManager.onStateChangedListener = {
            updateNotificationAndState()
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "NirvanaMediaSession").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    playerManager.playOrPause()
                }

                override fun onPause() {
                    playerManager.playOrPause()
                }

                override fun onSkipToNext() {
                    playerManager.next()
                }

                override fun onSkipToPrevious() {
                    playerManager.prev()
                }

                override fun onStop() {
                    stopPlayback()
                }
            })
            
            isActive = true
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nirvana Playback Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground playback controls and dynamic notifications for Nirvana"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playerManager.playOrPause()
            ACTION_PAUSE -> playerManager.playOrPause()
            ACTION_NEXT -> playerManager.next()
            ACTION_PREV -> playerManager.prev()
            ACTION_STOP -> stopPlayback()
        }
        
        updateNotificationAndState()
        return START_STICKY
    }

    private fun updateNotificationAndState() {
        val song = playerManager.currentSong.value
        val isPlaying = playerManager.isPlaying.value

        if (song == null) {
            stopSelf()
            return
        }

        // Update MediaSession Playback State
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_STOP
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                playerManager.progress.value,
                1.0f
            )
        mediaSession?.setPlaybackState(stateBuilder.build())

        // Create notification
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Native Android Notifications
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY

        val notification = builder
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSubText("Nirvana Streaming - ${song.source}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "Previous",
                    getPendingIntentAction(ACTION_PREV)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    playPauseIcon, playPauseTitle,
                    getPendingIntentAction(playPauseAction)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next, "Next",
                    getPendingIntentAction(ACTION_NEXT)
                ).build()
            )
            .setStyle(
                Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession?.sessionToken)
            )
            .setColorized(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun getPendingIntentAction(action: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun stopPlayback() {
        if (playerManager.isPlaying.value) {
            playerManager.playOrPause()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
