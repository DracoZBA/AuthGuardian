package com.example.authguardian.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.authguardian.R
import com.example.authguardian.data.remote.MqttClientManager
import com.example.authguardian.data.repository.DataRepository
import com.example.authguardian.ui.guardian.GuardianMainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage
import javax.inject.Inject

@AndroidEntryPoint
class MqttDataService : Service() {

    @Inject
    lateinit var mqttClientManager: MqttClientManager

    @Inject
    lateinit var dataRepository: DataRepository

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val NOTIFICATION_CHANNEL_ID = "MqttDataServiceChannel"
    private val NOTIFICATION_ID = 101

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        mqttClientManager.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.e("MqttDataService", "Connection lost: ${cause?.message}")
                // Implementar lógica de reconexión
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                message?.let {
                    val payload = String(it.payload)
                    Log.d("MqttDataService", "Message arrived on topic $topic: $payload")
                    serviceScope.launch {
                        dataRepository.processMqttData(topic, payload)
                        // Trigger meltdown detection here or in DataRepository
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Not used for subscription
            }
        })

        // Conectar y suscribirse al iniciar el servicio
        serviceScope.launch {
            if (!mqttClientManager.isConnected()) {
                mqttClientManager.connect()
            }
            mqttClientManager.subscribe("aura_guardian/child_bracelet/data") // Ejemplo de tópico
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MqttDataService", "Service started")
        // Si el servicio es "sticky", Android intentará recrearlo si se cierra.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // No se permite binding
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        mqttClientManager.disconnect()
        Log.d("MqttDataService", "Service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Aura Guardian Data Service",
                NotificationManager.IMPORTANCE_LOW // Importancia baja para no molestar al usuario
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification { // <--- Change return type to Notification
        val notificationIntent = Intent(this, GuardianMainActivity::class.java).apply {
            // Consider flags for how the activity should be launched
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0, // requestCode
            notificationIntent,
            pendingIntentFlag
        )

        // TODO: Replace ic_launcher_foreground with a proper notification icon (monochromatic)
        // TODO: Use string resources for title and text
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name)) // Example: "Aura Guardian"
            .setContentText(getString(R.string.notification_monitoring_data)) // Example: "Monitoring child's data..."
            .setSmallIcon(R.drawable.ic_notification_icon) // Use a proper notification icon
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Good practice for foreground service notifications
            .setPriority(NotificationCompat.PRIORITY_LOW) // Consistent with NotificationManager.IMPORTANCE_LOW
            .build() // <--- ADD .build() HERE
    }
}