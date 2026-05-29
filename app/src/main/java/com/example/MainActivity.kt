package com.example

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.CardBackground
import com.example.ui.theme.BorderInactive
import com.example.ui.theme.CyberGreyLight
import com.example.ui.theme.CyberGreyMedium
import com.example.ui.theme.CyberGreyMuted
import com.example.ui.theme.UnprotectedCrimson
import com.example.ui.theme.UnprotectedCrimsonDim
import com.example.ui.theme.ProtectedGreen
import com.example.ui.theme.ProtectedGreenDim
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalAmberDim

class MainActivity : ComponentActivity() {

    private lateinit var model: ShieldViewModel

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            model.onVpnPermissionGranted()
            startVpnService()
        } else {
            model.onVpnPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            model = viewModel()
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AmoledBlack),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    SecureShieldApp(
                        viewModel = model,
                        onToggleVpn = { handleVpnToggle() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun handleVpnToggle() {
        val uiState = model.uiState.value
        if (uiState.connectionState == ConnectionState.DISCONNECTED) {
            model.triggerConnectionToggle()
            try {
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    vpnPermissionLauncher.launch(intent)
                } else {
                    model.onVpnPermissionGranted()
                    startVpnService()
                }
            } catch (e: Exception) {
                model.onVpnPermissionGranted()
            }
        } else {
            model.disconnectTunnel()
            stopVpnService()
        }
    }

    private fun startVpnService() {
        try {
            val server = model.uiState.value.selectedServer
            val startIntent = Intent(this, ShieldVpnService::class.java).apply {
                action = ShieldVpnService.ACTION_CONNECT
                putExtra(ShieldVpnService.EXTRA_SERVER_NAME, server.name)
                putExtra(ShieldVpnService.EXTRA_SERVER_IP, server.maskedIp)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVpnService() {
        try {
            val stopIntent = Intent(this, ShieldVpnService::class.java).apply {
                action = ShieldVpnService.ACTION_DISCONNECT
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(stopIntent)
            } else {
                startService(stopIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun SecureShieldApp(
    viewModel: ShieldViewModel,
    onToggleVpn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearToast()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        if (uiState.isSplashActive) {
            SplashScreen(
                logs = uiState.splashLogs,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DashboardScreen(
                uiState = uiState,
                viewModel = viewModel,
                onToggleConnection = onToggleVpn,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun SplashScreen(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(
                    color = CyberGreyMuted.copy(alpha = 0.12f * (2f - pulseScale)),
                    radius = 70.dp.toPx() * pulseScale,
                    center = center
                )
                drawCircle(
                    color = CyberGreyMedium.copy(alpha = 0.06f),
                    radius = 90.dp.toPx() * pulseScale,
                    center = center
                )
            }

            Canvas(modifier = Modifier.size(96.dp)) {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2, h / 2)

                val shieldPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.5f, h * 0.1f)
                    lineTo(w * 0.85f, h * 0.2f)
                    quadraticTo(w * 0.85f, h * 0.65f, w * 0.5f, h * 0.9f)
                    quadraticTo(w * 0.15f, h * 0.65f, w * 0.15f, h * 0.2f)
                    close()
                }

                drawPath(
                    path = shieldPath,
                    color = CyberGreyMedium.copy(alpha = pulseAlpha),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                val n1 = Offset(w * 0.5f, h * 0.3f)
                val n2 = Offset(w * 0.32f, h * 0.48f)
                val n3 = Offset(w * 0.68f, h * 0.48f)
                val n4 = Offset(w * 0.36f, h * 0.7f)
                val n5 = Offset(w * 0.64f, h * 0.7f)

                drawLine(color = CyberGreyMuted, start = n1, end = n2, strokeWidth = 1.dp.toPx())
                drawLine(color = CyberGreyMuted, start = n1, end = n3, strokeWidth = 1.dp.toPx())
                drawLine(color = CyberGreyMuted, start = n2, end = center, strokeWidth = 1.dp.toPx())
                drawLine(color = CyberGreyMuted, start = n3, end = center, strokeWidth = 1.dp.toPx())
                drawLine(color = CyberGreyMuted, start = center, end = n4, strokeWidth = 1.dp.toPx())
                drawLine(color = CyberGreyMuted, start = center, end = n5, strokeWidth = 1.dp.toPx())
                drawLine(color = CyberGreyMuted, start = n4, end = n5, strokeWidth = 1.dp.toPx())

                drawCircle(color = CyberGreyLight.copy(alpha = pulseAlpha), radius = 4.dp.toPx(), center = n1)
                drawCircle(color = CyberGreyMedium, radius = 3.dp.toPx(), center = n2)
                drawCircle(color = CyberGreyMedium, radius = 3.dp.toPx(), center = n3)
                drawCircle(color = CyberGreyMedium, radius = 3.dp.toPx(), center = n4)
                drawCircle(color = CyberGreyMedium, radius = 3.dp.toPx(), center = n5)

                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = center)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .background(CardBackground, shape = RoundedCornerShape(16.dp))
                .border(1.dp, BorderInactive, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INITIALIZATION SEQUENCER",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberGreyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(SignalAmber, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val listState = rememberLazyListState()
            LaunchedEffect(logs.size) {
                if (logs.isNotEmpty()) {
                    listState.animateScrollToItem(logs.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .height(140.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    Text(
                        text = "> $log",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (log.contains("DETECTOR_OK") || log.contains("LOADED")) ProtectedGreen else CyberGreyLight,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "SECURE SHIELD SYSTEM v1.0.0",
            style = MaterialTheme.typography.labelSmall,
            color = CyberGreyMuted,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}

@Composable
fun DashboardScreen(
    uiState: ShieldUiState,
    viewModel: ShieldViewModel,
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showServerDialog by remember { mutableStateOf(false) }

    // Micro-parallax animations: active receding dashboard effect when bottom sheet overlay is visible
    val dashboardScale by animateFloatAsState(
        targetValue = if (showServerDialog) 0.95f else 1.0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "dashboard_scale"
    )
    val dashboardAlpha by animateFloatAsState(
        targetValue = if (showServerDialog) 0.4f else 1.0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "dashboard_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Receding Dashboard container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = dashboardScale,
                    scaleY = dashboardScale,
                    alpha = dashboardAlpha
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (uiState.connectionState == ConnectionState.BLOCKED) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF1744)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("traffic_blocked_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "TRAFFIC BLOCKED — RESTORING TUNNEL...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                DashboardTopBar(
                    connectionState = uiState.connectionState,
                    onSettingsClick = { viewModel.toggleSettingsScreen(true) }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CentralConnectionNode(
                        connectionState = uiState.connectionState,
                        connectionDuration = uiState.connectionDuration,
                        onConnectClick = onToggleConnection
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    LocationPill(
                        selectedServer = uiState.selectedServer,
                        enabled = uiState.connectionState != ConnectionState.CONNECTING,
                        onClick = { showServerDialog = true }
                    )
                }
            }

            TelemetryDocker(
                uiState = uiState,
                onRefreshIp = { viewModel.fetchPublicIp() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // Sliding Modal Bottom Sheet - Occupies 90% space smoothly from bottom viewport
        AnimatedVisibility(
            visible = showServerDialog,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 280, easing = FastOutLinearInEasing)
            ) + fadeOut()
        ) {
            NodeVaultSheet(
                uiState = uiState,
                viewModel = viewModel,
                onDismissRequest = { showServerDialog = false },
                onServerSelected = { node ->
                    viewModel.selectServer(node)
                    showServerDialog = false
                }
            )
        }

        // Sliding Settings Screen - Full viewport custom setup
        AnimatedVisibility(
            visible = uiState.inSettingsScreen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 280, easing = FastOutLinearInEasing)
            ) + fadeOut()
        ) {
            ShieldSettingsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onDismissRequest = { viewModel.toggleSettingsScreen(false) }
            )
        }
    }
}

@Composable
fun DashboardTopBar(
    connectionState: ConnectionState,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "SECURE SHIELD",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = Color.White
            )
            Text(
                text = "ACTIVE PROTOCOL: WIREGUARD v2",
                style = MaterialTheme.typography.labelSmall,
                color = CyberGreyMedium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }

        val statusText = when (connectionState) {
            ConnectionState.DISCONNECTED -> "UNPROTECTED"
            ConnectionState.CONNECTING -> "RESOLVING..."
            ConnectionState.CONNECTED -> "SECURED"
            ConnectionState.BLOCKED -> "TRAFFIC BLOCKED"
        }
        val statusColor = when (connectionState) {
            ConnectionState.DISCONNECTED -> UnprotectedCrimson
            ConnectionState.CONNECTING -> SignalAmber
            ConnectionState.CONNECTED -> ProtectedGreen
            ConnectionState.BLOCKED -> Color(0xFFFF1744)
        }
        val statusBg = when (connectionState) {
            ConnectionState.DISCONNECTED -> UnprotectedCrimsonDim
            ConnectionState.CONNECTING -> SignalAmberDim
            ConnectionState.CONNECTED -> ProtectedGreenDim
            ConnectionState.BLOCKED -> UnprotectedCrimsonDim
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(statusBg, RoundedCornerShape(8.dp))
                    .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse_dot")
                    val alphaDot by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha_dot"
                    )

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(if (connectionState == ConnectionState.CONNECTING) alphaDot else 1.0f)
                            .background(statusColor, CircleShape)
                    )

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(34.dp)
                    .background(CardBackground, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderInactive, RoundedCornerShape(8.dp))
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Open Settings",
                    tint = CyberGreyLight,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun CentralConnectionNode(
    connectionState: ConnectionState,
    connectionDuration: String,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.DISCONNECTED -> BorderInactive
            ConnectionState.CONNECTING -> SignalAmber
            ConnectionState.CONNECTED -> ProtectedGreen
            ConnectionState.BLOCKED -> Color(0xFFFF1744)
        },
        animationSpec = tween(600),
        label = "color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "active_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .testTag("connection_node_button")
            .size(240.dp)
            .clip(CircleShape)
            .clickable { onConnectClick() },
        contentAlignment = Alignment.Center
    ) {
        if (connectionState != ConnectionState.DISCONNECTED) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = ringColor.copy(alpha = 0.04f),
                    radius = (112.dp.toPx() * if (connectionState == ConnectionState.CONNECTED) 1.05f else pulseScale)
                )
                drawCircle(
                    color = ringColor.copy(alpha = 0.02f),
                    radius = (128.dp.toPx() * if (connectionState == ConnectionState.CONNECTED) 1.10f else pulseScale)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .size(210.dp)
        ) {
            drawCircle(
                color = CardBackground,
                radius = 100.dp.toPx()
            )

            drawCircle(
                color = ringColor,
                radius = 100.dp.toPx(),
                style = Stroke(width = 6.dp.toPx())
            )

            if (connectionState == ConnectionState.CONNECTING) {
                drawArc(
                    color = Color.White,
                    startAngle = rotationAngle,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(center.x - 100.dp.toPx(), center.y - 100.dp.toPx()),
                    size = size,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            val centralText = when (connectionState) {
                ConnectionState.DISCONNECTED -> "TAP TO CONNECT"
                ConnectionState.CONNECTING -> "SECURING TUNNEL..."
                ConnectionState.CONNECTED -> "SECURE BINDED"
                ConnectionState.BLOCKED -> "RESTORE TUNNEL"
            }
            Text(
                text = centralText,
                style = MaterialTheme.typography.labelLarge,
                color = if (connectionState == ConnectionState.DISCONNECTED) CyberGreyLight else Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center
            )

            if (connectionState == ConnectionState.CONNECTING) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "HANDSHAKE PROBE...",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberGreyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            } else if (connectionState == ConnectionState.CONNECTED) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = connectionDuration,
                    style = MaterialTheme.typography.titleMedium,
                    color = ProtectedGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LocationPill(
    selectedServer: ServerNode,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .testTag("location_pill")
            .clip(RoundedCornerShape(32.dp))
            .background(CardBackground)
            .border(1.dp, BorderInactive, RoundedCornerShape(32.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = selectedServer.flag,
                fontSize = 18.sp
            )

            Text(
                text = selectedServer.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Box(
                modifier = Modifier
                    .background(Color(0xFF262629), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${selectedServer.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalAmber,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select Server",
                tint = CyberGreyMedium,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun TelemetryDocker(
    uiState: ShieldUiState,
    onRefreshIp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TelemetryMetricCard(
                label = "DOWNLOAD",
                value = String.format("%.2f Mbps", uiState.downloadSpeed),
                isActive = uiState.connectionState == ConnectionState.CONNECTED,
                modifier = Modifier.weight(1f)
            )
            TelemetryMetricCard(
                label = "UPLOAD",
                value = String.format("%.2f Mbps", uiState.uploadSpeed),
                isActive = uiState.connectionState == ConnectionState.CONNECTED,
                modifier = Modifier.weight(1f)
            )
        }

        val unprotectedState = uiState.connectionState == ConnectionState.DISCONNECTED
        val borderHighlight = if (unprotectedState) UnprotectedCrimson.copy(alpha = 0.4f) else ProtectedGreen.copy(alpha = 0.4f)
        val containerHighlight = if (unprotectedState) UnprotectedCrimsonDim else ProtectedGreenDim

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(16.dp))
                .border(1.dp, BorderInactive, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "IP ADDRESS",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberGreyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (unprotectedState) Icons.Default.Warning else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (unprotectedState) UnprotectedCrimson else ProtectedGreen,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (unprotectedState) uiState.realPublicIp else uiState.currentConnectedIp,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (unprotectedState) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E20))
                                .clickable { onRefreshIp() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload IP",
                                tint = CyberGreyLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(containerHighlight, RoundedCornerShape(8.dp))
                            .border(1.dp, borderHighlight, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (unprotectedState) "⚠️ EXPOSED" else "🛡️ TUNNEL ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unprotectedState) UnprotectedCrimson else ProtectedGreen,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryMetricCard(
    label: String,
    value: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(CardBackground, RoundedCornerShape(16.dp))
            .border(1.dp, BorderInactive, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = CyberGreyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (isActive) Color.White else CyberGreyMuted
            )
        }
    }
}

// --- PART 3 DEDICATED SLIDING MODAL SHEET OVERLAY (90% SCREEN VIEWPORT HEIGHT) ---

@Composable
fun NodeVaultSheet(
    uiState: ShieldUiState,
    viewModel: ShieldViewModel,
    onDismissRequest: () -> Unit,
    onServerSelected: (ServerNode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismissRequest() }
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clickable(enabled = false) {} // Prevent click-through back to scrim
                .testTag("node_vault_sheet"),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0C0C0E)
            ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                BorderInactive
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Drag and drop handle illustration at top
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 44.dp, height = 4.dp)
                        .background(CyberGreyMedium.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Vault header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SECURE SHELTER INDEX",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberGreyMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "The Node Vault",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .background(Color(0xFF161619), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Node Vault",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sticky search filter bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text(
                            text = "Search Server...",
                            color = CyberGreyMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_server_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyberGreyLight,
                        unfocusedBorderColor = BorderInactive,
                        focusedContainerColor = Color(0xFF141416),
                        unfocusedContainerColor = Color(0xFF101012),
                        focusedPlaceholderColor = CyberGreyMuted,
                        unfocusedPlaceholderColor = CyberGreyMuted
                    ),
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = CyberGreyMedium,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = CyberGreyLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Favorites Filter chips, sorting logic switches, metrics scanner pulsing alerts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterOptionChip(
                        selected = uiState.favoritesOnly,
                        onClick = { viewModel.toggleFavoritesOnly() },
                        label = "Favorites Only",
                        activeColor = UnprotectedCrimson,
                        activeBgColor = UnprotectedCrimsonDim,
                        icon = Icons.Default.Favorite
                    )

                    FilterOptionChip(
                        selected = uiState.smartSorting,
                        onClick = { viewModel.toggleSmartSorting() },
                        label = "Smart Routing",
                        activeColor = ProtectedGreen,
                        activeBgColor = ProtectedGreenDim,
                        icon = Icons.Default.Refresh
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (uiState.isScanningLatency) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val infiniteTr = rememberInfiniteTransition(label = "pulse_radar")
                            val opPulse by infiniteTr.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "radar_opacity"
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .alpha(opPulse)
                                    .background(SignalAmber, CircleShape)
                            )
                            Text(
                                text = "Probing...",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = SignalAmber
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = BorderInactive, thickness = 1.dp)

                Spacer(modifier = Modifier.height(12.dp))

                val scrollState = rememberLazyListState()

                if (uiState.serverList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No server nodes active",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = CyberGreyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Adjust filters or query metrics index.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberGreyMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = uiState.serverList,
                            key = { it.id }
                        ) { server ->
                            val isSelected = server.id == uiState.selectedServer.id
                            val isFavorite = uiState.favoriteServerIds.contains(server.id)

                            ServerRowItem(
                                server = server,
                                isSelected = isSelected,
                                isFavorite = isFavorite,
                                onSelect = { onServerSelected(server) },
                                onToggleFavorite = { viewModel.toggleFavorite(server.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterOptionChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    activeColor: Color,
    activeBgColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) activeBgColor else Color(0xFF141416))
            .border(
                1.dp,
                if (selected) activeColor.copy(alpha = 0.4f) else BorderInactive,
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) activeColor else CyberGreyMuted,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (selected) activeColor else CyberGreyLight
            )
        }
    }
}

@Composable
fun ServerRowItem(
    server: ServerNode,
    isSelected: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF16161A) else Color(0xFF101012)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) ProtectedGreen.copy(alpha = 0.5f) else BorderInactive
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("favorite_button_${server.id}")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (isFavorite) UnprotectedCrimson else CyberGreyMedium,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF16161A), CircleShape)
                    .border(1.dp, BorderInactive, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = server.flag,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else CyberGreyLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Protocol: ${server.protocol}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberGreyMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val dotColor = when {
                        server.latencyMs < 50 -> ProtectedGreen
                        server.latencyMs <= 120 -> SignalAmber
                        else -> UnprotectedCrimson
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(dotColor, CircleShape)
                    )
                    Text(
                        text = "${server.latencyMs} ms",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (server.latencyMs < 50) ProtectedGreen else CyberGreyLight
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${server.capacityPercent}% LOAD",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (server.capacityPercent > 75) SignalAmber else CyberGreyMedium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = server.capacityPercent / 100f,
                        modifier = Modifier
                            .width(55.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = when {
                            server.capacityPercent > 75 -> UnprotectedCrimson
                            server.capacityPercent > 50 -> SignalAmber
                            else -> ProtectedGreen
                        },
                        trackColor = Color(0xFF1E1E22)
                    )
                }
            }
        }
    }
}

@Composable
fun ShieldSettingsScreen(
    uiState: ShieldUiState,
    viewModel: ShieldViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var appSearchQuery by remember { mutableStateOf("") }
    var newSsidInput by remember { mutableStateOf("") }

    val filteredApps = remember(uiState.splitTunnelApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) {
            uiState.splitTunnelApps
        } else {
            uiState.splitTunnelApps.filter {
                it.appName.contains(appSearchQuery, ignoreCase = true) ||
                it.packageName.contains(appSearchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(top = 16.dp)
            .testTag("settings_screen_viewport")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Go Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SYSTEM SECURITY GUARD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Autopilot policies & custom route bindings",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberGreyMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main single-scrolling page
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Native Kill Switch
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderInactive)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (uiState.isKillSwitchEnabled) UnprotectedCrimson else CyberGreyMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "System Kill Switch",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Enforce allowBypass(false)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = CyberGreyMuted
                                        )
                                    }
                                }
                                Switch(
                                    checked = uiState.isKillSwitchEnabled,
                                    onCheckedChange = { viewModel.setKillSwitchEnabled(it) },
                                    modifier = Modifier.testTag("kill_switch_toggle")
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Instantly blocks all internet communication if the defensive tunnel drops handshake or network roaming occurs (e.g., swapping Wi-Fi to Cellular). No packets can leak to local networks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberGreyMedium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Section 2: Automation Watchdog SSIDs
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderInactive)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = ProtectedGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "SSID Automation Watchdog",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "SSID Autopilot Action Rules",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyberGreyMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "When connecting to open public access routers, Shield instantly provisions isochronous connections. When joining marked Trusted networks, the tunnel automatically disconnects.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberGreyMedium,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Input row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newSsidInput,
                                    onValueChange = { newSsidInput = it },
                                    placeholder = {
                                        Text(
                                            "Home_Network_WiFi",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CyberGreyMuted
                                        )
                                    },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = BorderInactive,
                                        focusedBorderColor = ProtectedGreen,
                                        cursorColor = ProtectedGreen
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("trusted_wifi_input")
                                )

                                Button(
                                    onClick = {
                                        if (newSsidInput.isNotBlank()) {
                                            viewModel.addTrustedWifi(newSsidInput.trim())
                                            newSsidInput = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ProtectedGreen),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.testTag("add_trusted_wifi_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add SSID",
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (uiState.trustedWifiSsids.isEmpty()) {
                                Text(
                                    text = "No SSIDs marked trusted. All connections treated as unverified.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyberGreyMuted,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                )
                            } else {
                                uiState.trustedWifiSsids.forEach { ssid ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(AmoledBlack, RoundedCornerShape(8.dp))
                                            .border(1.dp, BorderInactive, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = ProtectedGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = ssid,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeTrustedWifi(ssid) },
                                            modifier = Modifier.size(24.dp).testTag("delete_ssid_$ssid")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove trusted",
                                                tint = UnprotectedCrimson,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: App Split Tunneling App Matrix
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderInactive)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = if (uiState.isSplitTunnelingEnabled) ProtectedGreen else CyberGreyMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Split Tunneling Matrix",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "App Routing Checklist",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = CyberGreyMuted
                                        )
                                    }
                                }
                                Switch(
                                    checked = uiState.isSplitTunnelingEnabled,
                                    onCheckedChange = { viewModel.setSplitTunnelingEnabled(it) },
                                    modifier = Modifier.testTag("split_tunnel_toggle")
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Checked applications are routed directly inside the secure WireGuard virtual adapter (tun0). Unchecked bypass programs bypass encryption, linking straight to open local networks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberGreyMedium,
                                fontSize = 11.sp
                            )

                            if (uiState.isSplitTunnelingEnabled) {
                                Spacer(modifier = Modifier.height(14.dp))

                                // App Filter field
                                OutlinedTextField(
                                    value = appSearchQuery,
                                    onValueChange = { appSearchQuery = it },
                                    placeholder = {
                                        Text(
                                            "Search installed packages...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CyberGreyMuted
                                        )
                                    },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = BorderInactive,
                                        focusedBorderColor = ProtectedGreen,
                                        cursorColor = ProtectedGreen
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("app_search_input")
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Application matrix box
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = AmoledBlack),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderInactive)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 280.dp)
                                            .padding(8.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        if (filteredApps.isEmpty()) {
                                            Text(
                                                text = "No matching packets. Double check system labels.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = CyberGreyMuted,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 24.dp)
                                            )
                                        } else {
                                            filteredApps.forEach { app ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .clickable { viewModel.toggleSplitTunnelApp(app.packageName) }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .background(CardBackground, CircleShape)
                                                                .border(1.dp, BorderInactive, CircleShape),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = app.appName.firstOrNull()?.toString() ?: "A",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = ProtectedGreen
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                            Text(
                                                                text = app.appName,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                            Text(
                                                                text = app.packageName,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontSize = 9.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                color = CyberGreyMuted
                                                            )
                                                        }
                                                    }
                                                    Checkbox(
                                                        checked = app.isChecked,
                                                        onCheckedChange = { viewModel.toggleSplitTunnelApp(app.packageName) },
                                                        modifier = Modifier.testTag("checklist_checkbox_${app.packageName}")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Sandbox Diagnostics SIMULATORS
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderInactive)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = SignalAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Autopilot Sandbox Diagnostics",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Environment Interruption Simulation",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyberGreyMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Fulfill deep-security tests instantly using sandboxed state injection triggers below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberGreyMedium,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Trigger 1: Abrupt Drop
                            Button(
                                onClick = {
                                    if (uiState.connectionState == ConnectionState.BLOCKED) {
                                        viewModel.recoverFromSignalDrop()
                                    } else {
                                        viewModel.simulateAbruptSignalDrop()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.connectionState == ConnectionState.BLOCKED) ProtectedGreen else UnprotectedCrimson
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("simulate_drop_button")
                            ) {
                                Text(
                                    text = if (uiState.connectionState == ConnectionState.BLOCKED) "RESTORE TUNNEL DEFENSER" else "SIMULATE SUDDEN SIGNAL DROP (BLOCKED)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Simulated SSID triggers
                            Text(
                                text = "Simulate WiFi SSID Broadcasts",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = CyberGreyLight
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.setSimulatedSsid("Airport_Public_NoPassword") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E22)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderInactive),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("simulate_ssid_airport")
                                ) {
                                    Text(
                                        "Airport Wi-Fi",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = CyberGreyLight
                                    )
                                }

                                Button(
                                    onClick = { 
                                        viewModel.addTrustedWifi("Office_Secure_WiFi")
                                        viewModel.setSimulatedSsid("Office_Secure_WiFi")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E22)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderInactive),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("simulate_ssid_office")
                                ) {
                                    Text(
                                        "Trusted Office WIFI",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = CyberGreyLight
                                    )
                                }
                            }

                            if (uiState.simulatedSsid.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF101012), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Current Pretended Network SSID: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 10.sp,
                                        color = CyberGreyMuted
                                    )
                                    Text(
                                        text = uiState.simulatedSsid,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = ProtectedGreen
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
