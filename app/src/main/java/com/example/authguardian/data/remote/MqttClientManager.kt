package com.example.authguardian.data.remote // Or your actual package

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.eclipse.paho.android.service.MqttAndroidClient // Use this for Android
// Alternatively, if not using MqttAndroidClient:
// import org.eclipse.paho.client.mqttv3.MqttClient
// import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended // Good for handling connectComplete and auto-reconnect
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MqttClientManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private lateinit var mqttAndroidClient: MqttAndroidClient
    private val serverUri = "tcp://YOUR_MQTT_BROKER_ADDRESS:1883" // Replace with your broker URI
    private val clientId = "AuraGuardianClient_${System.currentTimeMillis()}" // Ensure unique client ID

    companion object {
        private const val TAG = "MqttClientManager"
    }

    init {
        // It's often better to initialize the client here or in a dedicated init method
        // rather than relying on connect() to do it for the first time.
        mqttAndroidClient = MqttAndroidClient(context, serverUri, clientId)
        // Set a default callback or a callback that handles reconnections internally
        mqttAndroidClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d(TAG, "Connection complete. Reconnect: $reconnect, ServerURI: $serverURI")
                if (reconnect) {
                    // Resubscribe to topics if it's a reconnect
                    // subscribeToTopics() // You'd need a method to store and resubscribe
                }
            }

            override fun connectionLost(cause: Throwable?) {
                Log.e(TAG, "Connection lost internally in MqttClientManager: ${cause?.message}", cause)
                // This callback might be overridden by the one set from MqttDataService
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d(TAG, "Message arrived internally in MqttClientManager on topic $topic: ${message?.toString()}")
                // This callback will likely be overridden by the one set from MqttDataService
            }

            override fun deliveryComplete(token: org.eclipse.paho.client.mqttv3.IMqttDeliveryToken?) {
                Log.d(TAG, "Delivery complete for token: ${token?.messageId}")
            }
        })
    }

    // THIS IS THE METHOD YOU NEED TO ADD/ENSURE EXISTS
    fun setCallback(callback: org.eclipse.paho.client.mqttv3.MqttCallback) {
        try {
            if (::mqttAndroidClient.isInitialized) {
                mqttAndroidClient.setCallback(callback) // Delegate to the actual Paho client
                Log.d(TAG, "External MqttCallback successfully set.")
            } else {
                Log.w(TAG, "mqttAndroidClient not initialized when trying to set callback.")
                // You might want to store the callback and set it upon client initialization/connection
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting MqttCallback", e)
        }
    }

    fun connect() {
        if (!::mqttAndroidClient.isInitialized) {
            mqttAndroidClient = MqttAndroidClient(context, serverUri, clientId)
            // Consider re-applying default callback if initialized here and not in init {}
        }

        if (mqttAndroidClient.isConnected) {
            Log.d(TAG, "Already connected.")
            return
        }
        try {
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                // setUserName("YOUR_USERNAME")
                // setPassword("YOUR_PASSWORD".toCharArray())
            }
            mqttAndroidClient.connect(options, null, object : org.eclipse.paho.client.mqttv3.IMqttActionListener {
                override fun onSuccess(asyncActionToken: org.eclipse.paho.client.mqttv3.IMqttToken?) { // Use IMqttToken?
                    Log.d(TAG, "Connection successful to $serverUri")
                    // Example: subscribe("aura_guardian/child_bracelet/data")
                }

                override fun onFailure(asyncActionToken: org.eclipse.paho.client.mqttv3.IMqttToken?, exception: Throwable?) { // Use IMqttToken?
                    Log.e(TAG, "Connection failed to $serverUri: ${exception?.message}", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "Error during connect: ${e.message}", e)
        } catch (e: Exception) { // Catching generic exception as a fallback
            Log.e(TAG, "Unexpected error during connect: ${e.message}", e)
        }
    }

    fun subscribe(topic: String, qos: Int = 1) {
        if (!mqttAndroidClient.isConnected) {
            Log.w(TAG, "Cannot subscribe, MQTT client not connected.")
            // Optionally, try to connect first: connect()
            return
        }
        try {
            mqttAndroidClient.subscribe(topic, qos, null, object : org.eclipse.paho.client.mqttv3.IMqttActionListener {
                override fun onSuccess(asyncActionToken: org.eclipse.paho.client.mqttv3.IMqttToken?) { // Use IMqttToken?
                    Log.d(TAG, "Subscribed to topic: $topic")
                }

                override fun onFailure(asyncActionToken: org.eclipse.paho.client.mqttv3.IMqttToken?, exception: Throwable?) { // Use IMqttToken?
                    Log.e(TAG, "Failed to subscribe to topic: $topic", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "Error subscribing to topic $topic: ${e.message}", e)
        }
    }

    // In MqttClientManager.kt

    // ... in MqttClientManager.kt
    fun publish(topic: String, message: String, qos: Int = 1, retained: Boolean = false) {
        if (!::mqttAndroidClient.isInitialized || !mqttAndroidClient.isConnected) {
            Log.w(TAG, "Cannot publish, MQTT client not connected or not initialized.")
            return
        }
        try {
            val mqttMessage = MqttMessage(message.toByteArray())
            mqttMessage.qos = qos
            mqttMessage.isRetained = retained

            mqttAndroidClient.publish(topic, mqttMessage, null, object : org.eclipse.paho.client.mqttv3.IMqttActionListener {
                override fun onSuccess(asyncActionToken: org.eclipse.paho.client.mqttv3.IMqttToken?) { // Override with IMqttToken?
                    val deliveryToken = asyncActionToken as? org.eclipse.paho.client.mqttv3.IMqttDeliveryToken // Safe cast
                    Log.d(TAG, "Message published to topic: $topic. Message ID: ${deliveryToken?.messageId}")
                    // val sentMessage = deliveryToken?.message // You can get the message if needed
                }

                override fun onFailure(asyncActionToken: org.eclipse.paho.client.mqttv3.IMqttToken?, exception: Throwable?) { // Override with IMqttToken?
                    val deliveryToken = asyncActionToken as? org.eclipse.paho.client.mqttv3.IMqttDeliveryToken // Safe cast
                    Log.e(TAG, "Failed to publish message to topic: $topic. Message ID: ${deliveryToken?.messageId}", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "Error publishing to topic $topic: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error publishing to topic $topic: ${e.message}", e)
        }
    }

    fun disconnect() {
        if (!::mqttAndroidClient.isInitialized || !mqttAndroidClient.isConnected) {
            Log.d(TAG, "Already disconnected or not initialized.")
            return
        }
        try {
            mqttAndroidClient.disconnect(null, object : org.eclipse.paho.client.mqttv3.IMqttActionListener {
                override fun onSuccess(asyncActionToken: org.eclipse.paho.client.mqttv3.IMqttToken?) { // CORRECTED: Use IMqttToken?
                    Log.d(TAG, "Disconnected successfully.")
                }

                override fun onFailure(asyncActionToken: org.eclipse.paho.client.mqttv3.IMqttToken?, exception: Throwable?) { // CORRECTED: Use IMqttToken?
                    Log.e(TAG, "Disconnection failed: ${exception?.message}", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "Error during disconnect: ${e.message}", e)
        }
    }

    fun isConnected(): Boolean {
        return if (::mqttAndroidClient.isInitialized) mqttAndroidClient.isConnected else false
    }
}