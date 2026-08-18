package com.example.ui.map

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import com.example.data.model.DeviceOnlineStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CloudDevice
import com.example.ui.components.AppTopBar
import com.example.ui.components.StatusBadge
import com.example.ui.home.formatDuration
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldActive
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    initialFocusedDeviceId: String? = null,
    onBackClick: () -> Unit
) {
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val activePoints by viewModel.activePoints.collectAsStateWithLifecycle()
    val followLocation by viewModel.followUserLocation.collectAsStateWithLifecycle()
    val geofences by viewModel.geofences.collectAsStateWithLifecycle()
    val cloudDevices by viewModel.cloudDevices.collectAsStateWithLifecycle()
    val selectedDeviceInfo by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val focusedDeviceId by viewModel.focusedDeviceId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(initialFocusedDeviceId) {
        if (initialFocusedDeviceId != null) {
            viewModel.focusOnDevice(initialFocusedDeviceId)
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(-23.55052, -46.633308))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Update map overlays (Geofences, Polyline, Local Device, and Remote Cloud Devices)
    LaunchedEffect(activePoints, metrics.currentLatitude, metrics.currentLongitude, geofences, cloudDevices, focusedDeviceId, selectedDeviceInfo) {
        mapView.overlays.clear()

        // 1. Draw Geofence circles
        for (geo in geofences) {
            val circle = Polygon.pointsAsCircle(
                GeoPoint(geo.latitude, geo.longitude),
                geo.radiusMeters
            )
            val polygon = Polygon(mapView).apply {
                points = circle
                outlinePaint.color = if (geo.isEnabled) AndroidColor.argb(180, 0, 210, 255) else AndroidColor.argb(100, 150, 150, 150)
                outlinePaint.strokeWidth = 3f
                outlinePaint.style = Paint.Style.STROKE
                fillPaint.color = if (geo.isEnabled) AndroidColor.argb(40, 0, 210, 255) else AndroidColor.argb(20, 150, 150, 150)
                title = "Geofence: ${geo.name} (${geo.radiusMeters.toInt()}m)"
            }
            mapView.overlays.add(polygon)
        }

        // 2. Draw Polyline for local active session track
        if (activePoints.size >= 2) {
            val geoPoints = activePoints.map { GeoPoint(it.latitude, it.longitude) }
            val polyline = Polyline(mapView).apply {
                setPoints(geoPoints)
                outlinePaint.color = AndroidColor.argb(255, 0, 210, 255)
                outlinePaint.strokeWidth = 9f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                title = "Trajeto Deste Aparelho"
            }
            mapView.overlays.add(polyline)
        }

        // 2b. Draw Polyline for Remote Device History if requested
        val remotePoints = selectedDeviceInfo?.historyPoints
        if (remotePoints != null && remotePoints.size >= 2) {
            val geoHistoryPoints = remotePoints.map { GeoPoint(it.latitude, it.longitude) }
            val remotePolyline = Polyline(mapView).apply {
                setPoints(geoHistoryPoints)
                outlinePaint.color = AndroidColor.argb(255, 16, 185, 129) // Emerald
                outlinePaint.strokeWidth = 8f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                title = "Trajeto Remoto: ${selectedDeviceInfo?.device?.name}"
            }
            mapView.overlays.add(remotePolyline)
        }

        // 3. Remote Cloud Devices Markers
        for (dev in cloudDevices) {
            if (dev.lastLatitude != 0.0 && dev.lastLongitude != 0.0) {
                val devGeo = GeoPoint(dev.lastLatitude, dev.lastLongitude)
                val isSelected = selectedDeviceInfo?.device?.deviceId == dev.deviceId
                val devMarker = Marker(mapView).apply {
                    position = devGeo
                    title = dev.name
                    snippet = "Velocidade: ${String.format(Locale.getDefault(), "%.1f km/h", dev.lastSpeedKmh)} | 🔋 ${dev.batteryPercent}%"
                    icon = MapMarkerHelper.createDeviceMarkerDrawable(context, dev, isSelected)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ ->
                        viewModel.selectDevice(dev)
                        mapView.controller.animateTo(devGeo)
                        true
                    }
                }
                mapView.overlays.add(devMarker)

                // If this specific device is focused
                if (focusedDeviceId == dev.deviceId) {
                    mapView.controller.animateTo(devGeo)
                }
            }
        }

        // 4. Current Local Device Location Marker
        val curLat = metrics.currentLatitude
        val curLon = metrics.currentLongitude
        if (curLat != null && curLon != null) {
            val currentGeo = GeoPoint(curLat, curLon)
            val userMarker = Marker(mapView).apply {
                position = currentGeo
                title = "📍 Este Celular"
                snippet = "Velocidade: ${String.format(Locale.getDefault(), "%.1f km/h", metrics.currentSpeedKmh)}"
                icon = MapMarkerHelper.createLocalUserMarkerDrawable(context)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(userMarker)

            if (followLocation && focusedDeviceId == null && selectedDeviceInfo == null) {
                mapView.controller.animateTo(currentGeo)
            }
        }

        mapView.invalidate()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (focusedDeviceId != null) {
                    val focused = cloudDevices.find { it.deviceId == focusedDeviceId }
                    "Seguindo: ${focused?.name ?: "Dispositivo"}"
                } else "Mapa dos Dispositivos",
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("osm_map_view")
            )

            // Top Floating Panel: Device Carousel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Device Quick Selector Carousel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Local Device
                    DeviceSelectorChip(
                        name = "Este Celular",
                        icon = Icons.Default.MyLocation,
                        color = CyanPrimary,
                        isSelected = focusedDeviceId == null && selectedDeviceInfo == null,
                        onClick = {
                            viewModel.focusOnDevice(null)
                            viewModel.dismissSelectedDevice()
                            val lat = metrics.currentLatitude
                            val lon = metrics.currentLongitude
                            if (lat != null && lon != null) {
                                mapView.controller.animateTo(GeoPoint(lat, lon))
                                viewModel.setFollowUserLocation(true)
                            }
                        }
                    )

                    // Option 2+: Each remote cloud device
                    cloudDevices.forEach { dev ->
                        val devColor = try {
                            Color(android.graphics.Color.parseColor(dev.colorHex))
                        } catch (e: Exception) {
                            CyanPrimary
                        }
                        val icon = when (dev.iconType) {
                            "CAR" -> Icons.Default.DirectionsCar
                            "PERSON" -> Icons.Default.Person
                            "MOTO", "BIKE" -> Icons.Default.DirectionsBike
                            "TABLET" -> Icons.Default.Tablet
                            else -> Icons.Default.PhoneAndroid
                        }

                        DeviceSelectorChip(
                            name = "${dev.name} (${String.format(Locale.getDefault(), "%.0f km/h", dev.lastSpeedKmh)})",
                            icon = icon,
                            color = devColor,
                            isSelected = focusedDeviceId == dev.deviceId || selectedDeviceInfo?.device?.deviceId == dev.deviceId,
                            onClick = {
                                viewModel.focusOnDevice(dev.deviceId)
                                viewModel.selectDevice(dev)
                                if (dev.lastLatitude != 0.0 && dev.lastLongitude != 0.0) {
                                    mapView.controller.animateTo(GeoPoint(dev.lastLatitude, dev.lastLongitude))
                                    mapView.controller.setZoom(17.0)
                                }
                            }
                        )
                    }
                }
            }

            // Bottom Device Detail Floating Card (Item 9: Ao tocar no marcador -> Minha Moto / Última atualização / Velocidade / [ VER TRAJETO ])
            if (selectedDeviceInfo != null) {
                val devInfo = selectedDeviceInfo!!
                val dev = devInfo.device
                val isOnline = (System.currentTimeMillis() - dev.lastSeen) < 10 * 60 * 1000L && dev.isOnline

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .border(1.dp, CyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                        .testTag("device_marker_detail_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        val status = dev.dynamicStatus
                        val statusColor = when (status) {
                            DeviceOnlineStatus.ONLINE -> EmeraldActive
                            DeviceOnlineStatus.RECENT -> AmberWarning
                            DeviceOnlineStatus.OFFLINE -> Color.Gray
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = dev.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanPrimary
                                    )
                                    Text(
                                        text = status.label,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = statusColor
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.dismissSelectedDevice() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Telemetry Row 1: Last update & Speed
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "📍 Localização:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = SimpleDateFormat("HH:mm:ss • dd/MM", Locale.getDefault()).format(Date(dev.lastSeen)),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Velocidade:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f km/h", dev.lastSpeedKmh),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldActive
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Telemetry Row 2: Battery (Charging) & Wi-Fi SSID
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "🔋 Bateria:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${dev.batteryPercent}% (${if (dev.isCharging) "⚡ Carregando" else "⚡ Não"})",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (dev.isCharging) EmeraldActive else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "📶 Conexão:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Wi-Fi: ${dev.displayWifi}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (dev.wifiConnected) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Filter chips for Remote History (Hoje, Ontem, Últimos 7 dias)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("TODAY" to "Hoje", "YESTERDAY" to "Ontem", "WEEK" to "7 dias").forEach { (filterKey, filterLabel) ->
                                val isSelected = devInfo.selectedTimeFilter == filterKey
                                Surface(
                                    color = if (isSelected) CyanPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) CyanPrimary else Color.Transparent),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.loadDeviceHistory(dev.deviceId, filterKey) }
                                ) {
                                    Text(
                                        text = filterLabel,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurface,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Explicit [ VER TRAJETO ] button as requested in Item 9
                        Button(
                            onClick = { viewModel.loadDeviceHistory(dev.deviceId, devInfo.selectedTimeFilter) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("view_device_track_btn")
                        ) {
                            Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("VER TRAJETO", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }

                        if (devInfo.isShowingHistory) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Trajeto exibido no mapa (${devInfo.historyPoints.size} pontos sincronizados)",
                                fontSize = 11.sp,
                                color = EmeraldActive,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Map Floating Controls (Zoom In, Zoom Out, Recenter)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                SmallFloatingActionButton(
                    onClick = { mapView.controller.zoomIn() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("zoom_in_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Aproximar Zoom")
                }

                SmallFloatingActionButton(
                    onClick = { mapView.controller.zoomOut() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("zoom_out_button")
                ) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Afastar Zoom")
                }

                FloatingActionButton(
                    onClick = {
                        val lat = metrics.currentLatitude
                        val lon = metrics.currentLongitude
                        if (lat != null && lon != null) {
                            mapView.controller.animateTo(GeoPoint(lat, lon))
                            mapView.controller.setZoom(17.0)
                            viewModel.focusOnDevice(null)
                            viewModel.dismissSelectedDevice()
                            viewModel.setFollowUserLocation(true)
                        }
                    },
                    containerColor = CyanPrimary,
                    contentColor = Color(0xFF003648),
                    modifier = Modifier.testTag("recenter_gps_button")
                ) {
                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Centralizar no Dispositivo")
                }
            }
        }
    }
}

@Composable
fun DeviceSelectorChip(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
