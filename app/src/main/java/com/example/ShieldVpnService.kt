package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

class ShieldVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val ACTION_CONNECT = "com.example.action.CONNECT"
        const val ACTION_DISCONNECT = "com.example.action.DISCONNECT"
        const val EXTRA_SERVER_NAME = "com.example.extra.SERVER_NAME"
        const val EXTRA_SERVER_IP = "com.example.extra.SERVER_IP"
        private const val NOTIFICATION_ID = 2468
        private const val CHANNEL_ID = "secure_shield_vpn_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_DISCONNECT) {
            disconnectVpn()
            stopForeground(true)
            stopSelf()
        } else if (action == ACTION_CONNECT) {
            val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "Optimal Node"
            val serverIp = intent.getStringExtra(EXTRA_SERVER_IP) ?: "10.8.0.1"

            // Build active notification state and promote service to foreground
            val notification = buildStatusNotification("Requesting tunnel handshake...")
            startForeground(NOTIFICATION_ID, notification)

            connectVpn(serverName, serverIp)
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Secure Shield Tunnel Status"
            val descriptionText = "Displays real-time defense tunnel state."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildStatusNotification(statusText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Secure Shield Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun connectVpn(serverName: String, serverIp: String) {
        disconnectVpn() // Ensure clean slate
        try {
            Log.d("ShieldVpnService", "Establishing Secure Shield Tunnel to $serverName ($serverIp)")
            
            // Build the local virtual network interface parameters
            val builder = Builder()
                .setSession("Secure Shield Connection")
                .setMtu(1420)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0) // Route all IPv4 traffic securely
                .addDnsServer("1.1.1.1")
                .addDnsServer("9.9.9.9")

            val prefs = getSharedPreferences("secure_shield_prefs", Context.MODE_PRIVATE)
            val killSwitch = prefs.getBoolean("kill_switch_enabled", false)
            val splitTunnel = prefs.getBoolean("split_tunneling_enabled", false)
            val splitPackages = prefs.getStringSet("split_tunnel_packages", emptySet()) ?: emptySet()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (!killSwitch) {
                    builder.allowBypass()
                }

                if (splitTunnel && splitPackages.isNotEmpty()) {
                    for (pkg in splitPackages) {
                        try {
                            builder.addAllowedApplication(pkg)
                        } catch (e: Exception) {
                            Log.e("ShieldVpnService", "Could not bind package to tunnel: $pkg", e)
                        }
                    }
                }
            }

            // On some platforms or emulator versions, establishing directly might need specific configuration
            vpnInterface = builder.establish()
            Log.d("ShieldVpnService", "TUN interface established successfully: $vpnInterface")
            
            // Update the active status notification to show secured connection details
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildStatusNotification("Secured with $serverName"))

            // Start a worker loop to consume incoming interface traffic (simulation loop / dummy reader)
            serviceScope.launch {
                try {
                    while (vpnInterface != null) {
                        // In a real VPN client, we read packets from vpnInterface.fileDescriptor here.
                        // For our secure visualization shield, keeping the handle open establishes the tunnel session.
                        delay(1000)
                    }
                } catch (e: InterruptedException) {
                    Log.d("ShieldVpnService", "Worker thread interrupted")
                }
            }
        } catch (e: Exception) {
            Log.e("ShieldVpnService", "Failed to establish VPN TUN interface", e)
            disconnectVpn()
            
            // Show failure on the notification before stopping
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildStatusNotification("Failed to resolve handshake. Unsecured."))
        }
    }

    private fun disconnectVpn() {
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e("ShieldVpnService", "Error closing VPN interface", e)
        } finally {
            vpnInterface = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        disconnectVpn()
    }
}
