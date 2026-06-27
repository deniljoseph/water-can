package com.watercantracker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WaterCanTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Enable offline persistence so writes are queued locally and retried
        // automatically when the database becomes reachable.
        // Must be called before any other Firebase Database usage.
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Already initialized — safe to ignore on hot reloads
        }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    getString(R.string.notification_channel_reminders_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = getString(R.string.notification_channel_reminders_desc) },
                NotificationChannel(
                    CHANNEL_OVERDUE,
                    getString(R.string.notification_channel_overdue_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = getString(R.string.notification_channel_overdue_desc) }
            )
        )
    }

    companion object {
        const val CHANNEL_REMINDERS = "water_can_reminders"
        const val CHANNEL_OVERDUE   = "water_can_overdue"
    }
}
