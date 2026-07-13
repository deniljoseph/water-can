package com.watercantracker.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.watercantracker.app.MainActivity
import com.watercantracker.app.R
import com.watercantracker.app.WaterCanTrackerApp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires an immediate "it's your turn" notification the moment the rotation
 * advances to a new member — as opposed to ReminderWorker, which only checks
 * once a day in the background. Called directly from MemberRepository right
 * after a payment causes the queue to rotate.
 */
@Singleton
class TurnNotifier @Inject constructor() {

    fun notifyTurnChanged(context: Context, newPayerName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, WaterCanTrackerApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle(context.getString(R.string.notification_reminder_title))
            .setContentText(context.getString(R.string.notification_reminder_body, newPayerName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Notification permission is checked by the OS at post-time on Android 13+;
        // if not granted, this call is a no-op rather than a crash.
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_TURN_CHANGED, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip
        }
    }

    companion object {
        private const val NOTIF_ID_TURN_CHANGED = 2001
    }
}
