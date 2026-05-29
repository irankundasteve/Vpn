package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class NetworkWatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == ConnectivityManager.CONNECTIVITY_ACTION || action == "android.net.wifi.STATE_CHANGE") {
            Log.d("NetworkWatchdogReceiver", "Network connectivity state transition observed.")
            evaluateNetworkAutoAction(context)
        }
    }
}

fun evaluateNetworkAutoAction(context: Context, simulatedSsid: String? = null) {
    val prefs = context.getSharedPreferences("secure_shield_prefs", Context.MODE_PRIVATE)
    val isTunnelEnabled = prefs.getBoolean("vpn_tunnel_active", false) // Mock state lookup
    val trustedSet = prefs.getStringSet("trusted_wifis", emptySet()) ?: emptySet()
    
    // Determine target SSID
    var resolvedSsid = ""
    if (simulatedSsid != null) {
        resolvedSsid = simulatedSsid
    } else {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info: WifiInfo? = wifiManager?.connectionInfo
            val raw = info?.ssid?.replace("\"", "") ?: ""
            if (raw.isNotEmpty() && raw != "<unknown ssid>") {
                resolvedSsid = raw
            }
        } catch (e: Exception) {
            Log.e("NetworkWatchdog", "Could not fetch Wi-Fi connection info due to missing permissions or service issues", e)
        }
    }

    if (resolvedSsid.isEmpty()) {
        Log.d("NetworkWatchdog", "No valid Wi-Fi SSID detected; skipping auto routing evaluation.")
        return
    }

    Log.d("NetworkWatchdog", "Evaluating safety matrix for SSID: $resolvedSsid (Trusted list: $trustedSet)")

    if (trustedSet.contains(resolvedSsid)) {
        // Trusted WiFi Target -> Terminate the tunnel automatically to reclaim full band speed
        Log.d("NetworkWatchdog", "Matches Trusted Wi-Fi [$resolvedSsid]. Terminating active tunnel protection.")
        
        val stopIntent = Intent(context, ShieldVpnService::class.java).apply {
            action = ShieldVpnService.ACTION_DISCONNECT
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(stopIntent)
            } else {
                context.startService(stopIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        showWatchdogNotification(
            context,
            "Autopilot: Home Safe",
            "Connected to trusted Wi-Fi [$resolvedSsid]. Disconnecting tunnel to maximize bandwidth."
        )
    } else {
        // Untrusted Public Target -> Silently spin up WireGuard configuration handshake
        Log.d("NetworkWatchdog", "Untrusted network [$resolvedSsid] encountered. Deploying defensive routing.")
        
        val startIntent = Intent(context, ShieldVpnService::class.java).apply {
            action = ShieldVpnService.ACTION_CONNECT
            putExtra(ShieldVpnService.EXTRA_SERVER_NAME, "Optimal Node (Auto)")
            putExtra(ShieldVpnService.EXTRA_SERVER_IP, "104.28.16.92")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        showWatchdogNotification(
            context,
            "Autopilot: Threat Secured",
            "Untrusted Wi-Fi [$resolvedSsid] detected. Silently binding WireGuard security tunnel."
        )
    }
}

private fun showWatchdogNotification(context: Context, title: String, content: String) {
    val channelId = "secure_shield_watchdog_channel"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Secure Shield Automation Rules",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Provides real-time updates when automated watchdog rules connect or disconnect the tunnel."
        }
        notificationManager.createNotificationChannel(channel)
    }

    val launchIntent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context,
        99,
        launchIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_lock_lock)
        .setContentTitle(title)
        .setContentText(content)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(7859, notification)
}
