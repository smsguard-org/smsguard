package com.example.smsguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** Creates notification channels and builds the ongoing siren notification with a Stop action. */
object NotificationHelper {
    const val CHANNEL_SIREN = "siren"
    const val NOTIF_SIREN = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val siren = NotificationChannel(
            CHANNEL_SIREN,
            context.getString(R.string.siren_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(siren)
    }

    fun sirenNotification(context: Context): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, SirenService::class.java).setAction(SirenServiceActions.STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_SIREN)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle(context.getString(R.string.siren_notification))
            .setContentText(context.getString(R.string.app_name))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.siren_stop),
                    stopIntent
                ).build()
            )
            .build()
    }
}