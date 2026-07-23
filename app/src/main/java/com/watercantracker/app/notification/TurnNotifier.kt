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
 * Fires immediate push notifications — either when the rotation naturally
 * advances to a new person, or when someone manually taps "Nudge" to remind
 * the current next-payer on demand.
 */
@Singleton
class TurnNotifier @Inject constructor() {

    fun notifyTurnChanged(context: Context, newPayerName: String) {
        show(
            context = context,
            notifId = NOTIF_ID_TURN_CHANGED,
            title   = context.getString(R.string.notification_reminder_title),
            body    = context.getString(R.string.notification_reminder_body, newPayerName)
        )
    }

    /**
     * Sends an on-demand nudge to the current next payer. Unlike the automatic
     * turn-change notification, this can be triggered any time by anyone in the
     * group tapping the "Nudge" button — useful when someone's turn has been
     * pending a while and a gentle reminder is needed sooner than the daily check.
     */
    fun notifyNudge(context: Context, payerName: String) {
        show(
            context = context,
            notifId = NOTIF_ID_NUDGE,
            title   = "💧 Friendly nudge",
            body    = "Hey $payerName, it's still your turn to pay for the water can!"
        )
    }

    private fun show(context: Context, notifId: Int, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, WaterCanTrackerApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip
        }
    }

    companion object {
        private const val NOTIF_ID_TURN_CHANGED = 2001
        private const val NOTIF_ID_NUDGE = 2002
    }
}
