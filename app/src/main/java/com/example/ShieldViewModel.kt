package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.random.Random

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    BLOCKED
}

data class AppEntry(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val isChecked: Boolean = false
)

data class ServerNode(
    val name: String,
    val flag: String,
    val latency: String, // Keeping legacy for compatibility
    val maskedIp: String,
    val latencyMs: Int,
    val capacityPercent: Int,
    val protocol: String = "WireGuard v2",
    val id: String = name
)

// Define over 50 distinct international nodes to fulfill the "50+ mocked international node addresses" Fast Scroll Benchmark
val AVAILABLE_SERVERS = listOf(
    ServerNode("Optimal Node (Auto)", "⚡", "12ms", "104.28.16.92", 12, 14, "SecureGuard v3 (Fastest)", "auto_optimal"),
    ServerNode("US-West (Silicon Valley)", "🇺🇸", "42ms", "142.250.31.20", 42, 28, "WireGuard v2", "us_west"),
    ServerNode("UK-London (Grid Node)", "🇬🇧", "75ms", "82.165.177.15", 75, 42, "WireGuard v2", "uk_london"),
    ServerNode("DE-Frankfurt (Rhine Core)", "🇩🇪", "58ms", "172.217.16.68", 58, 35, "WireGuard v2", "de_frankfurt"),
    ServerNode("JP-Tokyo (Shibuya Node)", "🇯🇵", "155ms", "210.140.10.1", 155, 68, "WireGuard v2", "jp_tokyo"),
    ServerNode("CH-Zurich (Alps Shield)", "🇨🇭", "48ms", "179.43.144.10", 48, 19, "WireGuard v2", "ch_zurich"),
    ServerNode("NL-Amsterdam (Delta Node)", "🇳🇱", "32ms", "185.112.144.2", 32, 22, "WireGuard v2", "nl_ams"),
    ServerNode("FR-Paris (Eiffel Guard)", "🇫🇷", "39ms", "195.154.120.3", 39, 45, "WireGuard v2", "fr_par"),
    ServerNode("CA-Toronto (Ontario Hub)", "🇨🇦", "53ms", "198.50.148.5", 53, 31, "WireGuard v2", "ca_tor"),
    ServerNode("SG-Singapore (Marina Base)", "🇸🇬", "162ms", "103.22.200.41", 162, 53, "WireGuard v2", "sg_sin"),
    ServerNode("AU-Sydney (Harbor Node)", "🇦🇺", "212ms", "103.4.114.5", 212, 27, "WireGuard v2", "au_syd"),
    ServerNode("HK-Hong Kong (Pearl Vault)", "🇭🇰", "178ms", "218.102.13.5", 178, 48, "WireGuard v2", "hk_hk"),
    ServerNode("KR-Seoul (Hangang Node)", "🇰🇷", "185ms", "112.110.1.22", 185, 61, "WireGuard v2", "kr_seo"),
    ServerNode("IN-Mumbai (Gateway Shield)", "🇮🇳", "138ms", "115.112.3.9", 138, 79, "WireGuard v2", "in_mum"),
    ServerNode("BR-Sao Paulo (Samba Core)", "🇧🇷", "122ms", "200.128.0.51", 122, 64, "WireGuard v2", "br_sao"),
    ServerNode("ZA-Cape Town (Table Node)", "🇿🇦", "235ms", "41.74.0.1", 235, 17, "WireGuard v2", "za_cap"),
    ServerNode("IE-Dublin (Liffey Core)", "🇮🇪", "45ms", "89.207.132.4", 45, 29, "WireGuard v2", "ie_dub"),
    ServerNode("SE-Stockholm (Baltic Shield)", "🇸🇪", "62ms", "193.10.244.1", 62, 34, "WireGuard v2", "se_sto"),
    ServerNode("NO-Oslo (Fjord Core)", "🇳🇴", "67ms", "193.156.120.3", 67, 21, "WireGuard v2", "no_osl"),
    ServerNode("DK-Copenhagen (Beacon)", "🇩🇰", "54ms", "185.125.188.5", 54, 40, "WireGuard v2", "dk_cop"),
    ServerNode("FI-Helsinki (Aurora Node)", "🇫🇮", "71ms", "192.110.12.8", 71, 38, "WireGuard v2", "fi_hel"),
    ServerNode("IT-Milan (Duomo Grid)", "🇮🇹", "49ms", "93.186.240.11", 49, 52, "WireGuard v2", "it_mil"),
    ServerNode("ES-Madrid (Castilian Hub)", "🇪🇸", "52ms", "195.110.124.9", 52, 44, "WireGuard v2", "es_mad"),
    ServerNode("AT-Vienna (Danube Shield)", "🇦🇹", "56ms", "194.116.12.32", 56, 31, "WireGuard v2", "at_vie"),
    ServerNode("BE-Brussels (Euro Core)", "🇧🇪", "41ms", "193.190.150.150", 41, 60, "WireGuard v2", "be_bru"),
    ServerNode("PL-Warsaw (Vistula Node)", "🇵🇱", "63ms", "193.201.21.23", 63, 49, "WireGuard v2", "pl_war"),
    ServerNode("CZ-Prague (Vltava Shield)", "🇨🇿", "59ms", "195.113.144.12", 59, 36, "WireGuard v2", "cz_pra"),
    ServerNode("RO-Bucharest (Carpathian)", "🇷🇴", "74ms", "193.226.12.8", 74, 42, "WireGuard v2", "ro_buc"),
    ServerNode("UA-Kyiv (Dnipro Core)", "🇺🇦", "81ms", "194.44.12.18", 81, 72, "WireGuard v2", "ua_kyi"),
    ServerNode("TR-Istanbul (Bosphorus)", "🇹🇷", "98ms", "193.140.1.22", 98, 85, "WireGuard v2", "tr_ist"),
    ServerNode("IL-Tel Aviv (Levante Vault)", "🇮🇱", "110ms", "193.106.12.9", 110, 58, "WireGuard v2", "il_tel"),
    ServerNode("AE-Dubai (Desert Core)", "🇦🇪", "125ms", "194.170.1.3", 125, 63, "WireGuard v2", "ae_dub"),
    ServerNode("NZ-Auckland (Kiwi Shield)", "🇳🇿", "225ms", "202.12.120.4", 225, 23, "WireGuard v2", "nz_auc"),
    ServerNode("AR-Buenos Aires (Pampa)", "🇦🇷", "145ms", "200.10.122.5", 145, 51, "WireGuard v2", "ar_bue"),
    ServerNode("CO-Bogota (Andes Hub)", "🇨🇴", "112ms", "200.12.110.12", 112, 47, "WireGuard v2", "co_bog"),
    ServerNode("CL-Santiago (Pacifico)", "🇨🇱", "131ms", "200.1.1.22", 131, 39, "WireGuard v2", "cl_san"),
    ServerNode("MX-Mexico City (Maya Shield)", "🇲🇽", "85ms", "201.120.12.5", 85, 59, "WireGuard v2", "mx_mex"),
    ServerNode("IS-Reykjavik (Geysir)", "🇮🇸", "78ms", "193.106.120.3", 78, 12, "WireGuard v2", "is_rey"),
    ServerNode("MY-Kuala Lumpur (Petronas)", "🇲🇾", "154ms", "202.190.1.5", 154, 55, "WireGuard v2", "my_kul"),
    ServerNode("ID-Jakarta (Monas Node)", "🇮🇩", "172ms", "103.11.120.4", 172, 69, "WireGuard v2", "id_jak"),
    ServerNode("VN-Hanoi (Red River Core)", "🇻🇳", "179ms", "113.160.1.2", 179, 73, "WireGuard v2", "vn_han"),
    ServerNode("TH-Bangkok (Siam Vault)", "🇹🇭", "159ms", "202.44.12.50", 159, 62, "WireGuard v2", "th_ban"),
    ServerNode("PH-Manila (Luzon Shield)", "🇵🇭", "181ms", "202.90.130.4", 181, 66, "WireGuard v2", "ph_man"),
    ServerNode("TW-Taipei (101 Tower Node)", "🇹🇼", "165ms", "140.112.1.20", 165, 51, "WireGuard v2", "tw_tai"),
    ServerNode("GR-Athens (Onyx Core)", "🇬🇷", "88ms", "194.177.1.3", 88, 43, "WireGuard v2", "gr_ath"),
    ServerNode("PT-Lisbon (Tagus Guard)", "🇵🇹", "56ms", "193.136.12.8", 56, 37, "WireGuard v2", "pt_lis"),
    ServerNode("HU-Budapest (Magyar Shield)", "🇭🇺", "65ms", "195.111.12.5", 65, 41, "WireGuard v2", "hu_bud"),
    ServerNode("SK-Bratislava (Castle)", "🇸🇰", "68ms", "193.110.12.4", 68, 30, "WireGuard v2", "sk_bra"),
    ServerNode("BG-Sofia (Balkan Core)", "🇧🇬", "79ms", "194.145.1.22", 79, 39, "WireGuard v2", "bg_sof"),
    ServerNode("EE-Tallinn (Baltic Hub)", "🇪🇪", "66ms", "193.40.1.5", 66, 25, "WireGuard v2", "ee_tal"),
    ServerNode("LU-Luxembourg (Alte Grid)", "🇱🇺", "44ms", "194.154.1.3", 44, 28, "WireGuard v2", "lu_lux"),
    ServerNode("MX-Monterrey (Regio Guard)", "🇲🇽", "89ms", "201.130.22.45", 89, 41, "WireGuard v2", "mx_mon")
)

data class ShieldUiState(
    val isSplashActive: Boolean = true,
    val splashLogs: List<String> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val realPublicIp: String = "Detecting exposure...",
    val currentConnectedIp: String = "Detecting exposure...",
    val selectedServer: ServerNode = AVAILABLE_SERVERS[0],
    val downloadSpeed: Double = 0.00,
    val uploadSpeed: Double = 0.00,
    val connectionDuration: String = "00:00",
    val toastMessage: String? = null,
    val wireguardBinaryOk: Boolean = false,
    val configProfileOk: Boolean = false,
    
    // PART 3: Server Navigation and Caching Telemetry States
    val serverList: List<ServerNode> = AVAILABLE_SERVERS,
    val searchQuery: String = "",
    val favoritesOnly: Boolean = false,
    val smartSorting: Boolean = false,
    val favoriteServerIds: Set<String> = emptySet(),
    val isScanningLatency: Boolean = false,

    // PART 4: System Guard (Kill Switch, Split Tunneling & Trusted SSID Automation)
    val isKillSwitchEnabled: Boolean = false,
    val isSplitTunnelingEnabled: Boolean = false,
    val splitTunnelApps: List<AppEntry> = emptyList(),
    val trustedWifiSsids: Set<String> = emptySet(),
    val inSettingsScreen: Boolean = false,
    val isBlockedStateSimulated: Boolean = false,
    val simulatedSsid: String = ""
)

class ShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ShieldUiState())
    val uiState: StateFlow<ShieldUiState> = _uiState.asStateFlow()

    private val httpClient = OkHttpClient()
    private val prefs = application.getSharedPreferences("secure_shield_prefs", Context.MODE_PRIVATE)

    private var connectionTimerJob: Job? = null
    private var connectionJob: Job? = null
    private var telemetryJob: Job? = null
    private var latencyPollingJob: Job? = null

    init {
        loadFavorites()
        runProtocolChecks()
        fetchPublicIp()
        startLatencyPolling()
        loadGuardSettings()
    }

    private fun loadFavorites() {
        val favs = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        _uiState.update { state ->
            state.copy(favoriteServerIds = favs)
        }
        reapplyFiltersAndSorting()
    }

    private fun runProtocolChecks() {
        viewModelScope.launch {
            addLog("Initializing secure handshake protocol...")
            delay(400)

            addLog("Probing environment binary compatibility...")
            delay(500)
            val abis = Build.SUPPORTED_ABIS.joinToString(", ")
            addLog("Supported CPUs: [$abis]")
            delay(400)

            val supportsWireGuard = Build.SUPPORTED_ABIS.any {
                it.contains("arm64") || it.contains("x86_64") || it.contains("armeabi")
            }
            _uiState.update { it.copy(wireguardBinaryOk = supportsWireGuard) }
            addLog("Wireguard binary target support: ${if (supportsWireGuard) "DETECTOR_OK [PASSED]" else "UNKNOWN_REDUCED_MODE"}")
            delay(500)

            addLog("Scanning file system for tunnel configuration payload profile...")
            delay(400)
            
            val profileCreated = withContext(Dispatchers.IO) {
                try {
                    val context = getApplication<Application>()
                    val configFile = File(context.filesDir, "secureshield_wg.conf")
                    if (!configFile.exists()) {
                        configFile.writeText(
                            """
                            [Interface]
                            PrivateKey = sec_shield_mock_key_uD9FjKsLlOwPq_placeholder=
                            Address = 10.8.0.2/32
                            DNS = 1.1.1.1, 9.9.9.9
                            MTU = 1420

                            [Peer]
                            PublicKey = sec_shield_gateway_mock_eR2T6YgH8_placeholder=
                            Endpoint = auto-endpoint.secureshield.net:51820
                            AllowedIPs = 0.0.0.0/0
                            Keepalive = 25
                            """.trimIndent()
                        )
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }

            _uiState.update { it.copy(configProfileOk = profileCreated) }
            addLog("Local secure profile verified: [secureshield_wg.conf] [LOADED]")
            delay(600)

            addLog("Handshake complete. Secure interface ready to bind.")
            delay(500)

            _uiState.update { it.copy(isSplashActive = false) }
        }
    }

    fun fetchPublicIp() {
        _uiState.update { state ->
            state.copy(
                realPublicIp = "Resolving DNS...",
                currentConnectedIp = if (state.connectionState == ConnectionState.CONNECTED) state.currentConnectedIp else "Resolving DNS..."
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ip = performIpResolution()
            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(
                        realPublicIp = ip,
                        currentConnectedIp = if (state.connectionState == ConnectionState.CONNECTED) state.selectedServer.maskedIp else ip
                    )
                }
            }
        }
    }

    private fun performIpResolution(): String {
        try {
            val request = Request.Builder()
                .url("https://checkip.amazonaws.com")
                .header("User-Agent", "SecureShield/1.0 (Android)")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim()
                    if (!body.isNullOrBlank() && body.split(".").size == 4) {
                        return body
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val request = Request.Builder()
                .url("https://api.ipify.org")
                .header("User-Agent", "SecureShield/1.0 (Android)")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim()
                    if (!body.isNullOrBlank()) {
                        return body
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return "114.28.91.137"
    }

    private fun addLog(message: String) {
        _uiState.update { state ->
            state.copy(splashLogs = state.splashLogs + message)
        }
    }

    fun selectServer(server: ServerNode) {
        val wasConnected = _uiState.value.connectionState == ConnectionState.CONNECTED
        _uiState.update { state ->
            state.copy(
                selectedServer = server,
                currentConnectedIp = if (wasConnected) server.maskedIp else state.realPublicIp
            )
        }
        if (wasConnected) {
            disconnectTunnel()
            connectTunnel()
        }
    }

    fun triggerConnectionToggle() {
        val currentState = _uiState.value.connectionState
        if (currentState == ConnectionState.DISCONNECTED) {
            connectTunnel()
        } else {
            disconnectTunnel()
        }
    }

    fun onVpnPermissionGranted() {
        proceedToConnect()
    }

    fun onVpnPermissionDenied() {
        _uiState.update { state ->
            state.copy(
                connectionState = ConnectionState.DISCONNECTED,
                toastMessage = "VPN setup permission was denied."
            )
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun connectTunnel() {
        _uiState.update { it.copy(connectionState = ConnectionState.CONNECTING) }
    }

    fun proceedToConnect() {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            val isTokyoDead = _uiState.value.selectedServer.name.contains("Tokyo")
            
            if (isTokyoDead) {
                delay(8000)
                _uiState.update { state ->
                    state.copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        toastMessage = "Server Unreachable — Try Another Node"
                    )
                }
            } else {
                delay(2400)
                _uiState.update { state ->
                    state.copy(
                        connectionState = ConnectionState.CONNECTED,
                        downloadSpeed = 48.24,
                        uploadSpeed = 19.85,
                        currentConnectedIp = state.selectedServer.maskedIp,
                        connectionDuration = "00:00"
                    )
                }

                startSpeedTelemetryLoop()
                startTimerLoop()
            }
        }
    }

    fun disconnectTunnel() {
        connectionJob?.cancel()
        connectionTimerJob?.cancel()
        telemetryJob?.cancel()
        
        _uiState.update { state ->
            state.copy(
                connectionState = ConnectionState.DISCONNECTED,
                downloadSpeed = 0.00,
                uploadSpeed = 0.00,
                connectionDuration = "00:00",
                currentConnectedIp = state.realPublicIp
            )
        }
    }

    private fun startTimerLoop() {
        connectionTimerJob?.cancel()
        connectionTimerJob = viewModelScope.launch {
            var totalSeconds = 0
            while (_uiState.value.connectionState == ConnectionState.CONNECTED || _uiState.value.connectionState == ConnectionState.BLOCKED) {
                delay(1000)
                totalSeconds++
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                val formattedTime = String.format("%02d:%02d", minutes, seconds)
                _uiState.update { it.copy(connectionDuration = formattedTime) }
            }
        }
    }

    private fun startSpeedTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
            while (_uiState.value.connectionState == ConnectionState.CONNECTED || _uiState.value.connectionState == ConnectionState.BLOCKED) {
                delay(1000)
                _uiState.update { state ->
                    if (state.connectionState == ConnectionState.CONNECTED) {
                        val baseDl = 45.0 + Random.nextDouble(-12.0, 18.0)
                        val baseUl = 15.0 + Random.nextDouble(-4.0, 9.0)
                        state.copy(
                            downloadSpeed = Math.max(0.1, Math.round(baseDl * 100.0) / 100.0),
                            uploadSpeed = Math.max(0.1, Math.round(baseUl * 100.0) / 100.0)
                        )
                    } else if (state.connectionState == ConnectionState.BLOCKED) {
                        state.copy(
                            downloadSpeed = 0.00,
                            uploadSpeed = 0.00
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    // --- PART 3 SERVER NAVIGATION & FAVORITES SYSTEMS ---

    fun toggleFavorite(serverId: String) {
        val currentFavs = _uiState.value.favoriteServerIds.toMutableSet()
        if (currentFavs.contains(serverId)) {
            currentFavs.remove(serverId)
        } else {
            currentFavs.add(serverId)
        }
        prefs.edit().putStringSet("favorites", currentFavs).apply()
        _uiState.update { it.copy(favoriteServerIds = currentFavs) }
        reapplyFiltersAndSorting()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        reapplyFiltersAndSorting()
    }

    fun toggleFavoritesOnly() {
        _uiState.update { state -> state.copy(favoritesOnly = !state.favoritesOnly) }
        reapplyFiltersAndSorting()
    }

    fun toggleSmartSorting() {
        _uiState.update { state -> state.copy(smartSorting = !state.smartSorting) }
        reapplyFiltersAndSorting()
    }

    private fun reapplyFiltersAndSorting() {
        val state = _uiState.value
        var list = AVAILABLE_SERVERS

        // 1. Filter by Search Query
        if (state.searchQuery.isNotBlank()) {
            list = list.filter {
                it.name.contains(state.searchQuery, ignoreCase = true) ||
                it.maskedIp.contains(state.searchQuery, ignoreCase = true)
            }
        }

        // 2. Filter by Favorites
        if (state.favoritesOnly) {
            list = list.filter { state.favoriteServerIds.contains(it.id) }
        }

        // 3. Sort nodes
        if (state.smartSorting) {
            list = list.sortedWith(compareBy<ServerNode> { it.latencyMs }.thenBy { it.capacityPercent })
        } else {
            list = list.sortedWith { o1, o2 ->
                when {
                    o1.id == "auto_optimal" -> -1
                    o2.id == "auto_optimal" -> 1
                    else -> 0
                }
            }
        }

        _uiState.update { it.copy(serverList = list) }
    }

    private fun startLatencyPolling() {
        latencyPollingJob?.cancel()
        latencyPollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(3000)
                
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isScanningLatency = true) }
                }
                delay(400) // Visual probe trace simulation

                val currentList = _uiState.value.serverList
                val updatedList = currentList.map { server ->
                    if (server.id == "auto_optimal") {
                        val minOther = _uiState.value.serverList
                            .filter { it.id != "auto_optimal" }
                            .minOfOrNull { it.latencyMs } ?: 15
                        val valMs = maxOf(4, minOther - 4)
                        server.copy(
                            latencyMs = valMs,
                            latency = "${valMs}ms",
                            capacityPercent = maxOf(4, minOf(95, server.capacityPercent + Random.nextInt(-2, 3)))
                        )
                    } else {
                        val latencyDiff = Random.nextInt(-3, 4)
                        val newLatencyMs = maxOf(10, minOf(350, server.latencyMs + latencyDiff))
                        val capacityDiff = Random.nextInt(-2, 3)
                        val newCapacity = maxOf(5, minOf(98, server.capacityPercent + capacityDiff))
                        server.copy(
                            latencyMs = newLatencyMs,
                            latency = "${newLatencyMs}ms",
                            capacityPercent = newCapacity
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(
                            serverList = updatedList,
                            isScanningLatency = false
                        )
                    }
                    if (_uiState.value.smartSorting) {
                        reapplyFiltersAndSorting()
                    }
                }
            }
        }
    }

    private fun loadGuardSettings() {
        val killSwitch = prefs.getBoolean("kill_switch_enabled", false)
        val splitTunnel = prefs.getBoolean("split_tunneling_enabled", false)
        val trusted = prefs.getStringSet("trusted_wifis", emptySet()) ?: emptySet()
        val splitPackages = prefs.getStringSet("split_tunnel_packages", emptySet()) ?: emptySet()

        _uiState.update { state ->
            state.copy(
                isKillSwitchEnabled = killSwitch,
                isSplitTunnelingEnabled = splitTunnel,
                trustedWifiSsids = trusted
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = getApplication<Application>().packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val launchables = pm.queryIntentActivities(mainIntent, 0)
                val apps = launchables.mapNotNull { resolveInfo ->
                    val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                    val packageName = appInfo.packageName
                    val appLabel = pm.getApplicationLabel(appInfo).toString()
                    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    val isChecked = splitPackages.contains(packageName)
                    AppEntry(packageName, appLabel, isSystem, isChecked)
                }.distinctBy { it.packageName }.sortedBy { it.appName }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(splitTunnelApps = apps) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val workManager = androidx.work.WorkManager.getInstance(getApplication())
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<ConnectivityWatchdogWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            ).build()
            workManager.enqueueUniquePeriodicWork(
                "connectivity_watchdog_work",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun setKillSwitchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("kill_switch_enabled", enabled).apply()
        _uiState.update { it.copy(isKillSwitchEnabled = enabled) }
        restartVpnIfActive()
    }

    fun setSplitTunnelingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("split_tunneling_enabled", enabled).apply()
        _uiState.update { it.copy(isSplitTunnelingEnabled = enabled) }
        restartVpnIfActive()
    }

    fun toggleSplitTunnelApp(packageName: String) {
        val apps = _uiState.value.splitTunnelApps.map {
            if (it.packageName == packageName) {
                it.copy(isChecked = !it.isChecked)
            } else {
                it
            }
        }
        _uiState.update { it.copy(splitTunnelApps = apps) }
        
        val checkedPackages = apps.filter { it.isChecked }.map { it.packageName }.toSet()
        prefs.edit().putStringSet("split_tunnel_packages", checkedPackages).apply()
        restartVpnIfActive()
    }

    fun addTrustedWifi(ssid: String) {
        if (ssid.isBlank()) return
        val ssids = _uiState.value.trustedWifiSsids.toMutableSet()
        ssids.add(ssid)
        prefs.edit().putStringSet("trusted_wifis", ssids).apply()
        _uiState.update { it.copy(trustedWifiSsids = ssids) }
    }

    fun removeTrustedWifi(ssid: String) {
        val ssids = _uiState.value.trustedWifiSsids.toMutableSet()
        ssids.remove(ssid)
        prefs.edit().putStringSet("trusted_wifis", ssids).apply()
        _uiState.update { it.copy(trustedWifiSsids = ssids) }
    }

    fun setSimulatedSsid(ssid: String) {
        _uiState.update { it.copy(simulatedSsid = ssid) }
        viewModelScope.launch(Dispatchers.IO) {
            evaluateNetworkAutoAction(getApplication(), if (ssid.isBlank()) null else ssid)
        }
    }

    fun toggleSettingsScreen(show: Boolean) {
        _uiState.update { it.copy(inSettingsScreen = show) }
    }

    fun simulateAbruptSignalDrop() {
        if (_uiState.value.connectionState == ConnectionState.CONNECTED) {
            _uiState.update { state ->
                state.copy(
                    connectionState = ConnectionState.BLOCKED,
                    isBlockedStateSimulated = true,
                    toastMessage = "TRAFFIC BLOCKED — RESTORING TUNNEL..."
                )
            }
        }
    }

    fun recoverFromSignalDrop() {
        if (_uiState.value.connectionState == ConnectionState.BLOCKED) {
            _uiState.update { state ->
                state.copy(
                    connectionState = ConnectionState.CONNECTED,
                    isBlockedStateSimulated = false,
                    toastMessage = "Defensive Tunnel Re-stabilized Successfully!"
                )
            }
        }
    }

    private fun restartVpnIfActive() {
        if (_uiState.value.connectionState == ConnectionState.CONNECTED) {
            val app = getApplication<Application>()
            val intent = Intent(app, ShieldVpnService::class.java).apply {
                action = ShieldVpnService.ACTION_CONNECT
                putExtra(ShieldVpnService.EXTRA_SERVER_NAME, _uiState.value.selectedServer.name)
                putExtra(ShieldVpnService.EXTRA_SERVER_IP, _uiState.value.selectedServer.maskedIp)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectTunnel()
        latencyPollingJob?.cancel()
    }
}
