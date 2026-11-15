package com.utility.smsgateway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.utility.smsgateway.R
import com.utility.smsgateway.data.preferences.GatewayPreferences
import com.utility.smsgateway.data.repository.GatewayRepository
import com.utility.smsgateway.data.repository.ServerState
import com.utility.smsgateway.server.GatewayHttpServer
import com.utility.smsgateway.sms.SmsQueueManager
import com.utility.smsgateway.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class GatewayService : Service() {

    companion object {
        const val ACTION_START = "com.utility.smsgateway.ACTION_START"
        const val ACTION_STOP = "com.utility.smsgateway.ACTION_STOP"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sms_gateway_channel"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: GatewayPreferences
    private lateinit var queueManager: SmsQueueManager
    private var httpServer: GatewayHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        prefs = GatewayPreferences(this)
        queueManager = SmsQueueManager(this, serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopGateway()
                stopSelf()
            }
            else -> {
                startGateway()
            }
        }
        return START_STICKY
    }

    private fun startGateway() {
        createNotificationChannel()
        GatewayRepository.updateLocalIp()
        val ip = GatewayRepository.localIp.value
        val port = prefs.port

        startForeground(NOTIFICATION_ID, buildNotification("Gateway active on http://$ip:$port"))

        acquireLocks()
        queueManager.start()

        GatewayRepository.setServerState(ServerState.STARTING)

        val server = GatewayHttpServer(this, queueManager)
        val result = server.start(port)

        result.onSuccess {
            httpServer = server
            prefs.isServerActive = true
            GatewayRepository.setServerState(ServerState.RUNNING)
            val notification = buildNotification("Running at http://$ip:$port (Twilio Compatible)")
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }.onFailure { error ->
            GatewayRepository.setServerState(ServerState.ERROR, error.message)
            stopGateway()
            stopSelf()
        }
    }

    private fun stopGateway() {
        try {
            httpServer?.stop()
            httpServer = null
            queueManager.stop()
            releaseLocks()
            prefs.isServerActive = false
            GatewayRepository.setServerState(ServerState.STOPPED)
        } catch (_: Exception) {
        }
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmsGateway::ServiceWakeLock"
            ).apply {
                acquire(24 * 60 * 60 * 1000L) // 24 hour max safety timeout
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager.createWifiLock(
                wifiMode,
                "SmsGateway::ServiceWifiLock"
            ).apply {
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null

            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val desc = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = desc
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GatewayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Gateway Running")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "Stop Gateway", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopGateway()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

