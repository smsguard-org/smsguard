package com.example.smsguard

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/** Background service that watches for SIM changes while Protection Mode is running. */
class ProtectionService : Service() {

    private var simMonitor: SimChangeMonitor? = null

    override fun onCreate() {
        super.onCreate()
        // Older versions kept this service in the foreground with an ongoing status notification.
        // Clear any leftover notification now that protection runs silently in the background.
        NotificationHelper.cancelProtection(this)
        simMonitor = SimChangeMonitor(this).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.serviceEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A user may grant Phone and SIM status while this service is already running.
        // Retrying here makes the monitor start without requiring protection to be toggled off.
        simMonitor?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        simMonitor?.stop()
        simMonitor = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            // Protection runs as a plain background service with no notification. Android may
            // reject this while the app is in the background, so ignore that case; protection
            // starts again the next time the app comes to the foreground.
            runCatching {
                context.applicationContext.startService(
                    Intent(context.applicationContext, ProtectionService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, ProtectionService::class.java)
            )
        }
    }
}
