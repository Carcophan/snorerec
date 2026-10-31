package de.carcophan.snorerec

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import de.carcophan.snorerec.data.db.SnoreDatabase
import de.carcophan.snorerec.data.repository.SnoreRepository

class SnoreRecApp : Application() {

    lateinit var database: SnoreDatabase
        private set

    lateinit var repository: SnoreRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = SnoreDatabase(this)
        repository = SnoreRepository(database)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_MONITORING,
                getString(R.string.notification_channel_monitoring_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_monitoring_desc)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_MONITORING = "snore_monitoring_channel"

        lateinit var instance: SnoreRecApp
            private set
    }
}
