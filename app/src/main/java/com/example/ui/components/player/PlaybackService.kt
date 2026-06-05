package com.example.player

import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerManager.getInstance(this).getPlayer()
        if (player != null) {
            val forwardingPlayer = object : ForwardingPlayer(player) {
                override fun getAvailableCommands(): androidx.media3.common.Player.Commands {
                    return super.getAvailableCommands().buildUpon()
                        .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                        .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
                        .build()
                }

                override fun seekToNext() {
                    PlayerManager.getInstance(this@PlaybackService).playNext()
                }

                override fun seekToPrevious() {
                    PlayerManager.getInstance(this@PlaybackService).playPrevious()
                }

                override fun seekForward() {
                    PlayerManager.getInstance(this@PlaybackService).skipForward()
                }

                override fun seekBack() {
                    PlayerManager.getInstance(this@PlaybackService).skipBackward()
                }
            }
            val intent = Intent(this, com.example.MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            mediaSession = MediaSession.Builder(this, forwardingPlayer)
                .setSessionActivity(pendingIntent)
                .build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
