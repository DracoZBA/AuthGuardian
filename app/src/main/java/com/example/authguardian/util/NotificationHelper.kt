package com.example.authguardian.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.authguardian.R
import com.example.authguardian.ui.guardian.GuardianMainActivity // O la actividad principal del padre

object NotificationHelper {

    private const val GEOFENCE_CHANNEL_ID = "geofence_alerts_channel"
    private const val GEOFENCE_CHANNEL_NAME = "Geofence Alerts"
    private const val GEOFENCE_NOTIFICATION_ID = 1002

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                GEOFENCE_CHANNEL_ID,
                GEOFENCE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for geofence transition alerts"
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendGeofencePushNotification(context: Context, title: String, message: String) {
        createNotificationChannel(context) // Asegurarse que el canal exista

        val intent = Intent(context, GuardianMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Asegúrate de tener un icono
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // La notificación desaparece al hacer clic

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(GEOFENCE_NOTIFICATION_ID, notificationBuilder.build())
    }

    // También puedes tener una función general para otras notificaciones
    fun showNotification(context: Context, title: String, message: String, notificationId: Int = 1001) {
        createNotificationChannel(context) // Reusar el mismo canal o crear uno específico
        val notificationBuilder = NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}