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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

class ShieldVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetProcessor: VpnPacketProcessor? = null
    private var proxyClient: ProxyClient? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

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
        
        // Build active notification state and promote service to foreground immediately
        // to satisfy startForegroundService Contract on Android 8.0+
        val notification = buildStatusNotification("Secure Shield status transition...")
        startForeground(NOTIFICATION_ID, notification)

        if (action == ACTION_DISCONNECT) {
            disconnectVpn()
            stopForeground(true)
            stopSelf()
        } else if (action == ACTION_CONNECT) {
            val serverName = intent?.getStringExtra(EXTRA_SERVER_NAME) ?: "Optimal Node"
            val serverIp = intent?.getStringExtra(EXTRA_SERVER_IP) ?: "10.8.0.1"

            val updateNotification = buildStatusNotification("Requesting tunnel handshake...")
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, updateNotification)

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
            
            val prefs = getSharedPreferences("secure_shield_prefs", Context.MODE_PRIVATE)
            val vpnMode = prefs.getString("vpn_mode", "sandbox") ?: "sandbox"
            val proxyHost = prefs.getString("proxy_host", "8.8.8.8") ?: "8.8.8.8"
            val proxyPort = prefs.getInt("proxy_port", 8080)

            // Build the local virtual network interface parameters
            val builder = Builder()
                .setSession("Secure Shield Connection")
                .setMtu(1420)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0) // Route all traffic through VPN

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

            // Establish the VPN interface
            vpnInterface = builder.establish()
            Log.d("ShieldVpnService", "TUN interface established successfully")
            
            // Initialize proxy client
            proxyClient = ProxyClient(proxyHost, proxyPort)
            
            // Start packet processing in background
            serviceScope.launch {
                try {
                    // Connect to proxy server
                    if (proxyClient?.connect() == true) {
                        Log.d("ShieldVpnService", "Connected to proxy: $proxyHost:$proxyPort")
                        
                        // Create and start packet processor
                        vpnInterface?.let { iface ->
                            packetProcessor = VpnPacketProcessor(iface, proxyHost, proxyPort)
                            packetProcessor?.startProcessing()
                        }
                    } else {
                        Log.e("ShieldVpnService", "Failed to connect to proxy server")
                        updateNotification("Failed to connect to proxy. Unsecured.")
                        disconnectVpn()
                    }
                } catch (e: Exception) {
                    Log.e("ShieldVpnService", "Error in packet processing: ${e.message}", e)
                    disconnectVpn()
                }
            }
            
            // Update notification
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildStatusNotification("Secured with $serverName - Routing through $proxyHost"))
            
        } catch (e: Exception) {
            Log.e("ShieldVpnService", "Failed to establish VPN TUN interface", e)
            disconnectVpn()
            updateNotification("Failed to establish tunnel. Unsecured.")
        }
    }
    
    private fun updateNotification(statusText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildStatusNotification(statusText))
    }

    private fun disconnectVpn() {
        try {
            packetProcessor?.let { processor ->
                serviceScope.launch {
                    processor.shutdown()
                }
            }
            proxyClient?.let { client ->
                serviceScope.launch {
                    client.disconnect()
                }
            }
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e("ShieldVpnService", "Error closing VPN interface", e)
        } finally {
            vpnInterface = null
            packetProcessor = null
            proxyClient = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectVpn()
        serviceScope.cancel()
    }
}
