package com.watercantracker.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.watercantracker.app.MainActivity
import com.watercantracker.app.R
import com.watercantracker.app.WaterCanTrackerApp
import com.watercantracker.app.data.repository.MemberRepository
import com.watercantracker.app.data.repository.PaymentRepository
import com.watercantracker.app.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val memberRepository: MemberRepository,
    private val paymentRepository: PaymentRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.getSettings()
        if (!settings.remindersEnabled) return Result.success()

        val lastPayment = paymentRepository.getLastPayment()
        val nextPayer = memberRepository.resolveNextPayer(lastPayment?.paidByMemberId)

        nextPayer.member?.let { member ->
            // Check overdue
            if (settings.overdueRemindersEnabled && lastPayment != null) {
                val daysSince = ((System.currentTimeMillis() - lastPayment.purchaseDate) /
                        (1000 * 60 * 60 * 24)).toInt()
                if (daysSince >= settings.overdueThresholdDays) {
                    showOverdueNotification(member.name, daysSince)
                    return Result.success()
                }
            }
            if (settings.remindersEnabled) {
                showReminderNotification(member.name)
            }
        }
        return Result.success()
    }

    private fun showReminderNotification(memberName: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(
            applicationContext, WaterCanTrackerApp.CHANNEL_REMINDERS
        )
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle(applicationContext.getString(R.string.notification_reminder_title))
            .setContentText(
                applicationContext.getString(R.string.notification_reminder_body, memberName)
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(NOTIF_ID_REMINDER, notification)
    }

    private fun showOverdueNotification(memberName: String, days: Int) {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(
            applicationContext, WaterCanTrackerApp.CHANNEL_OVERDUE
        )
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle(applicationContext.getString(R.string.notification_overdue_title))
            .setContentText(
                applicationContext.getString(R.string.notification_overdue_body, memberName, days)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(NOTIF_ID_OVERDUE, notification)
    }

    companion object {
        private const val NOTIF_ID_REMINDER = 1001
        private const val NOTIF_ID_OVERDUE = 1002
        const val WORK_NAME = "water_can_reminder_work"

        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(
                24, TimeUnit.HOURS
            ).build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}
