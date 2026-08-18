package com.example.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CloudDevice
import com.example.data.model.DeviceOnlineStatus
import com.example.ui.components.AppTopBar
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.TealAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel,
    onBackClick: () -> Unit,
    onDeviceClick: (String) -> Unit,
    onNavigateToSharing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localDeviceName by viewModel.localDeviceName.collectAsStateWithLifecycle()
    val localDeviceId by viewModel.localDeviceId.collectAsStateWithLifecycle()
    val isSharingEnabled by viewModel.isSharingEnabled.collectAsStateWithLifecycle()
    val allDevices by viewModel.allAuthorizedDevices.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var editingName by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(localDeviceName) }

    LaunchedEffect(localDeviceName) {
        nameInput = localDeviceName
    }

    LaunchedEffect(uiState.userMessage, uiState.errorMessage) {
        val msg = uiState.userMessage ?: uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Meus Dispositivos",
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        onClick = { viewModel.openAddDeviceDialog(true) },
                        modifier = Modifier.testTag("add_device_appbar_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar Dispositivo", tint = CyanPrimary)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Este Dispositivo
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Este dispositivo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Surface(
                                color = if (isSharingEnabled) EmeraldActive.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isSharingEnabled) EmeraldActive else Color.Gray)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isSharingEnabled) "Compartilhando" else "Local",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSharingEnabled) EmeraldActive else Color.Gray
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (editingName) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it },
                                    label = { Text("Nome do Aparelho") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        viewModel.saveDeviceName(nameInput)
                                        editingName = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Salvar")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = localDeviceName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanPrimary
                                    )
                                    Text(
                                        text = "ID: $localDeviceId",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(onClick = { editingName = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Renomear", tint = CyanPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick presets chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Meu celular", "Minha Moto", "Tablet", "Rastreador").forEach { preset ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.saveDeviceName(preset)
                                            nameInput = preset
                                            editingName = false
                                        }
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Header "MEUS DISPOSITIVOS" + Action Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MEUS DISPOSITIVOS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row {
                        IconButton(
                            onClick = {
                                viewModel.addDemoDevice("Minha Moto", "MOTO")
                                viewModel.addDemoDevice("Tablet", "TABLET")
                            },
                            modifier = Modifier.testTag("add_demo_preset_btn")
                        ) {
                            Icon(Icons.Default.Science, contentDescription = "Adicionar Exemplos", tint = TealAccent)
                        }

                        Button(
                            onClick = { viewModel.openAddDeviceDialog(true) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            modifier = Modifier.testTag("add_device_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Adicionar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Device List Items with Battery & Wi-Fi telemetry
            if (allDevices.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Nenhum outro dispositivo vinculado",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Adicione outros celulares ou veículos vinculando pelo código de convite de 10 minutos.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    viewModel.addDemoDevice("Minha Moto", "MOTO")
                                    viewModel.addDemoDevice("Tablet", "TABLET")
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                            ) {
                                Text("Gerar Dispositivos de Teste")
                            }
                        }
                    }
                }
            } else {
                items(allDevices, key = { it.deviceId }) { device ->
                    DeviceCardItem(
                        device = device,
                        isLocal = device.deviceId == localDeviceId,
                        onClick = { viewModel.selectDeviceForDetails(device) },
                        onMapClick = { onDeviceClick(device.deviceId) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Add Device Dialog
    if (uiState.isAddDeviceDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.openAddDeviceDialog(false) },
            title = { Text("Adicionar Dispositivo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Insira o código de convite temporário (8 dígitos) gerado no outro celular:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = uiState.inviteCodeInput,
                        onValueChange = { viewModel.setInviteCodeInput(it.uppercase()) },
                        label = { Text("Código de Convite") },
                        placeholder = { Text("Ex: X7K4-92PL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.redeemInviteCode() },
                    enabled = !uiState.isRedeeming && uiState.inviteCodeInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    if (uiState.isRedeeming) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Vincular")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.openAddDeviceDialog(false) }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Device Status Details Dialog (Section 8: Status do Dispositivo)
    uiState.selectedDeviceForDetails?.let { device ->
        DeviceStatusDetailsDialog(
            device = device,
            isLocal = device.deviceId == localDeviceId,
            onDismiss = { viewModel.selectDeviceForDetails(null) },
            onViewOnMap = {
                viewModel.selectDeviceForDetails(null)
                onDeviceClick(device.deviceId)
            }
        )
    }
}

/**
 * Rich Device Card Item formatted according to user spec:
 * 🏍️ Minha Moto
 * 🟢 Online
 * 🔋 78%
 * ⚡ Não carregando
 * 📶 Wi-Fi: MinhaRede
 * 📍 Última localização: agora
 */
@Composable
fun DeviceCardItem(
    device: CloudDevice,
    isLocal: Boolean,
    onClick: () -> Unit,
    onMapClick: () -> Unit
) {
    val status = device.dynamicStatus
    val statusColor = when (status) {
        DeviceOnlineStatus.ONLINE -> EmeraldActive
        DeviceOnlineStatus.RECENT -> AmberWarning
        DeviceOnlineStatus.OFFLINE -> Color.Gray
    }

    val lastSeenText = remember(device.lastSeen) {
        val diffMs = System.currentTimeMillis() - device.lastSeen
        val diffMin = diffMs / (60 * 1000)
        when {
            diffMin < 1 -> "agora"
            diffMin < 60 -> "$diffMin min atrás"
            diffMin < 1440 -> "${diffMin / 60}h atrás"
            diffMin < 2880 -> "ontem"
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(device.lastSeen))
        }
    }

    val icon = when (device.iconType) {
        "MOTO" -> Icons.Default.DirectionsBike
        "CAR" -> Icons.Default.DirectionsCar
        "TABLET" -> Icons.Default.Tablet
        else -> Icons.Default.PhoneAndroid
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("device_card_${device.deviceId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Icon + Name + Online Status Badge + Map Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyanPrimary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isLocal) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = CyanPrimary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Este Celular",
                                        fontSize = 9.5.sp,
                                        color = CyanPrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Status Badge: 🟢 Online / 🟡 Atualização recente / ⚪ Offline
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = status.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                    }
                }

                // Map Action Button
                IconButton(
                    onClick = onMapClick,
                    modifier = Modifier.testTag("view_map_${device.deviceId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Ver no Mapa",
                        tint = CyanPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))

            // Telemetry Grid (Battery, Charging, Wi-Fi, Location)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Battery & Power
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🔋 ${device.batteryPercent}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (device.isCharging) "⚡ Carregando" else "⚡ Não carregando",
                        fontSize = 11.5.sp,
                        color = if (device.isCharging) EmeraldActive else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Wi-Fi Connection
                Column(modifier = Modifier.weight(1.2f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "📶 Wi-Fi: ${device.displayWifi}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "📍 Localização: $lastSeenText",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Section 8: Status do Dispositivo Dialog
 */
@Composable
fun DeviceStatusDetailsDialog(
    device: CloudDevice,
    isLocal: Boolean,
    onDismiss: () -> Unit,
    onViewOnMap: () -> Unit
) {
    val status = device.dynamicStatus
    val statusColor = when (status) {
        DeviceOnlineStatus.ONLINE -> EmeraldActive
        DeviceOnlineStatus.RECENT -> AmberWarning
        DeviceOnlineStatus.OFFLINE -> Color.Gray
    }

    val lastLocationTime = remember(device.lastSeen) {
        val diffMs = System.currentTimeMillis() - device.lastSeen
        val diffMin = diffMs / (60 * 1000)
        when {
            diffMin < 1 -> "Atualizada agora"
            diffMin < 60 -> "Atualizada há $diffMin min"
            else -> "Atualizada em ${SimpleDateFormat("HH:mm • dd/MM", Locale.getDefault()).format(Date(device.lastSeen))}"
        }
    }

    val syncTimeFormatted = remember(device.lastUpdated) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(device.lastUpdated))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
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
                    Text(
                        text = device.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "STATUS DO DISPOSITIVO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary,
                    letterSpacing = 1.sp
                )

                // 1. Localização
                StatusDetailRow(
                    icon = Icons.Default.LocationOn,
                    title = "Localização",
                    value = lastLocationTime,
                    subtitle = if (device.lastLatitude != 0.0) {
                        String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", device.lastLatitude, device.lastLongitude)
                    } else "Coordenadas não registradas"
                )

                // 2. Bateria
                Column {
                    StatusDetailRow(
                        icon = if (device.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        title = "Bateria",
                        value = "${device.batteryPercent}%",
                        subtitle = device.batteryStatus
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = (device.batteryPercent / 100f).coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (device.batteryPercent > 20) EmeraldActive else Color.Red,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // 3. Energia / Carregando
                StatusDetailRow(
                    icon = Icons.Default.Bolt,
                    title = "Energia",
                    value = if (device.isCharging) "Carregando (Sim)" else "Não carregando (Não)",
                    subtitle = if (device.isCharging) "Conectado à tomada/USB" else "Operando em bateria"
                )

                // 4. Rede Wi-Fi
                StatusDetailRow(
                    icon = if (device.wifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    title = "Wi-Fi",
                    value = device.displayWifi,
                    subtitle = if (device.wifiConnected) "Conectado via rede sem fio" else "Não conectado ao Wi-Fi"
                )

                // 5. Última Sincronização
                StatusDetailRow(
                    icon = Icons.Default.Sync,
                    title = "Última sincronização",
                    value = syncTimeFormatted,
                    subtitle = "Sincronizado com segurança via nuvem"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onViewOnMap,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ver no Mapa", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun StatusDetailRow(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CyanPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
