package com.example.smsguard

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat

/** Foreground service keeping the ongoing "protection active" notification with a live timer. */
class ProtectionService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            if (!Prefs.serviceEnabled(this@ProtectionService)) {
                stopSelf()
                return
            }
            val elapsed = elapsedMillis()
            NotificationHelper.updateProtection(this@ProtectionService, elapsed)
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.serviceEnabled(this)) {
            if (intent != null) {
                startAsForeground()
                stopSelf()
            }
            return START_NOT_STICKY
        }
        startAsForeground()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    private fun elapsedMillis(): Long {
        val start = Prefs.serviceStartTime(this)
        return if (start > 0) (System.currentTimeMillis() - start).coerceAtLeast(0L) else 0L
    }

    private fun startAsForeground() {
        val notification = NotificationHelper.protectionNotification(this, elapsedMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIF_PROTECTION,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationHelper.NOTIF_PROTECTION, notification)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        NotificationHelper.cancelProtection(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TICK_INTERVAL_MS = 1000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, ProtectionService::class.java)
            )
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, ProtectionService::class.java)
            )
        }
    }
}
