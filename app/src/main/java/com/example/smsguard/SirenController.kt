package com.example.smsguard

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Single entry point for turning the siren on and off.
 *
 * Both the SMS "#ALARM#PIN"/"#STOP#PIN" commands and the in-app control go through here so the
 * siren state stays consistent no matter which one the user reaches for.
 */
object SirenController {

    fun start(context: Context) {
        val app = context.applicationContext
        ContextCompat.startForegroundService(
            app,
            Intent(app, SirenService::class.java).setAction(SirenServiceActions.START)
        )
    }

    /**
     * Stops the siren if it is running. Safe to call when nothing is playing, which is what lets
     * the dashboard keep a plain, always-tappable stop button.
     */
    fun stop(context: Context) {
        val app = context.applicationContext
        app.stopService(Intent(app, SirenService::class.java))
        Prefs.setSirenActive(app, false)
    }
}
