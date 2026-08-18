package com.example.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.TrackingMetrics
import com.example.domain.model.TrackingState
import com.example.ui.components.AppTopBar
import com.example.ui.components.ErrorNotice
import com.example.ui.components.MetricCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.RoseError
import com.example.ui.theme.TealAccent
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToMap: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAiInsights: () -> Unit,
    onNavigateToGeofence: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasLocationPermission by remember { mutableStateOf(false) }
    var isGpsHardwareEnabled by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        hasLocationPermission = permissionsMap[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissionsMap[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(metrics.state) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        isGpsHardwareEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Rastreamento GPS",
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("home_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurações"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hardware GPS Warning Banner if disabled
            if (!isGpsHardwareEnabled) {
                Surface(
                    color = RoseError.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoseError.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "GPS Desativado",
                            tint = RoseError
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "A localização está desativada",
                                fontWeight = FontWeight.Bold,
                                color = RoseError,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Ative o GPS nas configurações para iniciar o rastreamento.",
                                color = RoseError.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text("Ativar", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (metrics.errorMessage != null) {
                ErrorNotice(message = metrics.errorMessage ?: "", modifier = Modifier.padding(bottom = 12.dp))
            }

            // Main Status & Hero Speed Card
            HeroTrackingCard(
                metrics = metrics,
                onStart = {
                    if (hasLocationPermission) {
                        viewModel.startTracking()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                onPause = { viewModel.pauseTracking() },
                onResume = { viewModel.resumeTracking() },
                onStop = { viewModel.stopTracking() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Real-time Metrics Grid
            Text(
                text = "MÉTRICAS DA SESSÃO",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val distKm = metrics.distanceMeters / 1000.0
                MetricCard(
                    title = "Distância",
                    value = String.format(Locale.getDefault(), "%.2f", distKm),
                    unit = "km",
                    icon = Icons.Default.Route,
                    accentColor = CyanPrimary,
                    modifier = Modifier.weight(1f),
                    testTag = "metric_distance"
                )

                MetricCard(
                    title = "Tempo",
                    value = formatDuration(metrics.elapsedTimeMs),
                    icon = Icons.Default.AvTimer,
                    accentColor = TealAccent,
                    modifier = Modifier.weight(1f),
                    testTag = "metric_time"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Vel. Média",
                    value = String.format(Locale.getDefault(), "%.1f", metrics.averageSpeedKmh),
                    unit = "km/h",
                    icon = Icons.Default.Speed,
                    accentColor = EmeraldActive,
                    modifier = Modifier.weight(1f),
                    testTag = "metric_avg_speed"
                )

                MetricCard(
                    title = "Vel. Máxima",
                    value = String.format(Locale.getDefault(), "%.1f", metrics.maxSpeedKmh),
                    unit = "km/h",
                    icon = Icons.Default.Speed,
                    accentColor = RoseError,
                    modifier = Modifier.weight(1f),
                    testTag = "metric_max_speed"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Pontos Gravados",
                    value = metrics.pointCount.toString(),
                    unit = "pts",
                    icon = Icons.Default.LocationOn,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    testTag = "metric_points"
                )

                val accText = metrics.currentAccuracy?.let { String.format(Locale.getDefault(), "±%.0fm", it) } ?: "--"
                MetricCard(
                    title = "Precisão GPS",
                    value = accText,
                    icon = Icons.Default.GpsFixed,
                    accentColor = EmeraldActive,
                    modifier = Modifier.weight(1f),
                    testTag = "metric_accuracy"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation Hub Buttons
            Text(
                text = "FERRAMENTAS E HISTÓRICO",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 8.dp)
            )

            // Prominent Multi-Device Observation Card
            QuickNavCard(
                title = "Observar Outros Celulares",
                subtitle = "Monitore múltiplos aparelhos em tempo real com código",
                icon = Icons.Default.Devices,
                accentColor = CyanPrimary,
                onClick = onNavigateToDevices,
                modifier = Modifier.fillMaxWidth(),
                testTag = "nav_devices_btn"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickNavCard(
                    title = "Ver Mapa",
                    subtitle = "Trajeto ao vivo & frota",
                    icon = Icons.Default.Map,
                    accentColor = CyanPrimary,
                    onClick = onNavigateToMap,
                    modifier = Modifier.weight(1f),
                    testTag = "nav_map_btn"
                )

                QuickNavCard(
                    title = "Histórico",
                    subtitle = "Sessões e exportação",
                    icon = Icons.Default.History,
                    accentColor = TealAccent,
                    onClick = onNavigateToHistory,
                    modifier = Modifier.weight(1f),
                    testTag = "nav_history_btn"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickNavCard(
                    title = "Análise IA",
                    subtitle = "Padrões e anomalias",
                    icon = Icons.Default.AutoGraph,
                    accentColor = EmeraldActive,
                    onClick = onNavigateToAiInsights,
                    modifier = Modifier.weight(1f),
                    testTag = "nav_ai_btn"
                )

                QuickNavCard(
                    title = "Geofences",
                    subtitle = "Áreas e alertas",
                    icon = Icons.Default.Policy,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = onNavigateToGeofence,
                    modifier = Modifier.weight(1f),
                    testTag = "nav_geofence_btn"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun HeroTrackingCard(
    metrics: TrackingMetrics,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        CyanPrimary.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .testTag("hero_tracking_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ESTADO DO GPS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusBadge(state = metrics.state)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Speed Display Dial / Circle
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                CyanPrimary.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        color = if (metrics.state == TrackingState.TRACKING) CyanPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(Locale.getDefault(), "%.0f", metrics.currentSpeedKmh),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "KM/H",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            when (metrics.state) {
                TrackingState.IDLE, TrackingState.ERROR -> {
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_tracking_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = Color(0xFF003648)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Iniciar")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "INICIAR RASTREAMENTO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
                TrackingState.TRACKING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPause,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("pause_tracking_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = "Pausar")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PAUSAR", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onStop,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(52.dp)
                                .testTag("stop_tracking_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoseError,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = "Parar")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PARAR", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                TrackingState.PAUSED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onResume,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("resume_tracking_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldActive,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Retomar")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RETOMAR", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onStop,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("stop_tracking_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoseError,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = "Parar")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PARAR", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                TrackingState.STOPPING -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("FINALIZANDO SESSÃO...")
                    }
                }
            }
        }
    }
}

@Composable
fun QuickNavCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    val hours = sec / 3600
    val minutes = (sec % 3600) / 60
    val seconds = sec % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
