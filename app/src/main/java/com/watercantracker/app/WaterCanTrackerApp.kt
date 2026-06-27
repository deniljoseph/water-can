package com.watercantracker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.watercantracker.app.data.repository.SettingsRepository
import com.watercantracker.app.sync.FirebaseSyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WaterCanTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncManager: FirebaseSyncManager
    @Inject lateinit var settingsRepository: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        try {
            FirebaseDatabase.getInstance(
                "https://water-can-tracker-a5033-default-rtdb.asia-southeast1.firebasedatabase.app"
            ).setPersistenceEnabled(true)
        } catch (_: Exception) {}

        // Start background sync listener as soon as the app process starts.
        // This means secondary devices receive live updates even without
        // opening the Sync screen.
        appScope.launch {
            val settings = settingsRepository.getSettings()
            settings.firebaseRoomId?.let { roomId ->
                syncManager.startListening(roomId, settings.isMasterDevice)
            }
        }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_REMINDERS,
                getString(R.string.notification_channel_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = getString(R.string.notification_channel_reminders_desc)
            },
            NotificationChannel(CHANNEL_OVERDUE,
                getString(R.string.notification_channel_overdue_name),
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.notification_channel_overdue_desc)
            }
        ))
    }

    companion object {
        const val CHANNEL_REMINDERS = "water_can_reminders"
        const val CHANNEL_OVERDUE   = "water_can_overdue"
    }
}
