package com.example.smsguard

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder

class SirenService : Service() {

    private var player: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == SirenServiceActions.STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIF_SIREN,
                NotificationHelper.sirenNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NotificationHelper.NOTIF_SIREN, NotificationHelper.sirenNotification(this))
        }
        startSiren()
        return START_NOT_STICKY
    }

    private fun startSiren() {
        if (player?.isPlaying == true) return
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        runCatching {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        }
        val mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(SirenSound.file(this@SirenService).absolutePath)
            isLooping = true
            setVolume(1f, 1f)
            setOnPreparedListener { it.start() }
        }
        mediaPlayer.prepareAsync()
        player = mediaPlayer
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}