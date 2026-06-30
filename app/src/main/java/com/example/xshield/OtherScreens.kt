package com.example.xshield

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xshield.ui.theme.*
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

// ==========================================
// 1. LOCATIONS SCREEN (MAP LOGS)
// ==========================================

@Composable
fun LocationsScreen() {
    val context = LocalContext.current
    
    // Required configuration for osmdroid
    Configuration.getInstance().userAgentValue = context.packageName

    val lat = XshieldRepository.liveLatitude.value
    val lng = XshieldRepository.liveLongitude.value

    val displayLat = if (lat != 0.0) lat else 12.9716
    val displayLng = if (lng != 0.0) lng else 77.5946

    val hasRealLocation = lat != 0.0 || lng != 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.5f),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                            
                            val geoPoint = GeoPoint(displayLat, displayLng)
                            controller.setCenter(geoPoint)

                            val marker = Marker(this)
                            marker.position = geoPoint
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            overlays.add(marker)
                        }
                    },
                    update = { mapView ->
                        val geoPoint = GeoPoint(displayLat, displayLng)
                        mapView.controller.setCenter(geoPoint)
                        
                        mapView.overlays.clear()
                        val marker = Marker(mapView)
                        marker.position = geoPoint
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        mapView.overlays.add(marker)
                        mapView.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )

                FloatingActionButton(
                    onClick = {
                        val uriStr = "https://maps.google.com/?q=$displayLat,$displayLng"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = AccentTeal,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open Google Maps")
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (hasRealLocation) Color.Green else Color.Yellow, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasRealLocation) String.format(Locale.US, "GPS: %.4f, %.4f", displayLat, displayLng) else "Waiting for GPS...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Text("Location Status", fontWeight = FontWeight.ExtraBold, color = DarkText, fontSize = 18.sp)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(AccentTeal.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(28.dp))
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Last Known Location",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (hasRealLocation) {
                        Text(
                            text = "Coordinates: ${String.format(Locale.US, "%.5f", displayLat)}, ${String.format(Locale.US, "%.5f", displayLng)}",
                            fontSize = 13.sp,
                            color = LightText
                        )
                        Text(
                            text = "Source: Target Device GPS",
                            fontSize = 12.sp,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Text(
                            text = "No GPS lock from target yet.\nShowing fallback placeholder.",
                            fontSize = 13.sp,
                            color = RedAlert
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. APPS SCREEN (BLOCK APP LISTS)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen() {
    val appsList = XshieldRepository.appsList
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val monitoringEnabled by remember { derivedStateOf { XshieldRepository.monitoringEnabled.value } }
    val agentHidden by remember { derivedStateOf { XshieldRepository.agentHidden.value } }
    val blockedAppsCount = remember(appsList) { appsList.count { it.isBlocked } }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                val deviceId = XshieldRepository.selectedDevice.value
                if (deviceId.isNotBlank()) {
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("commands/$deviceId/syncApps")
                        .setValue(mapOf("timestamp" to System.currentTimeMillis()))
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device Controls Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Device Controls",
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        fontSize = 16.sp
                    )

                    // Monitoring Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Monitoring Control",
                                fontWeight = FontWeight.Bold,
                                color = DarkText,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (monitoringEnabled) "Status: Active Monitoring" else "Status: Monitoring Paused",
                                color = if (monitoringEnabled) AccentTeal else RedAlert,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Switch(
                            checked = monitoringEnabled,
                            onCheckedChange = { checked ->
                                XshieldRepository.setMonitoringEnabled(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentTeal
                            )
                        )
                    }

                    HorizontalDivider(color = BorderColor)

                    // Visibility Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Launcher Icon Visibility",
                                fontWeight = FontWeight.Bold,
                                color = DarkText,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (agentHidden) "Status: Hiding (Hidden from Launcher)" else "Status: Visible (Shown in Launcher)",
                                color = if (agentHidden) RedAlert else AccentTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Switch(
                            checked = agentHidden,
                            onCheckedChange = { checked ->
                                XshieldRepository.setAgentHidden(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RedAlert
                            )
                        )
                    }

                    HorizontalDivider(color = BorderColor)

                    // Parent Phone Number Setting (Offline Remote SMS Control)
                    val parentPhone = XshieldRepository.parentPhoneNumber.value
                    var phoneInput by remember(parentPhone) { mutableStateOf(parentPhone) }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Authorized Parent Phone Number",
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Configured phone number used to verify offline SMS remote commands (e.g. lock/unlock, locate).",
                            color = LightText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                placeholder = { Text("e.g. +15550199", fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentTeal,
                                    unfocusedBorderColor = BorderColor
                                ),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = LightText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                            )
                            Button(
                                onClick = {
                                    XshieldRepository.setParentPhoneNumber(phoneInput)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("SAVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = BorderColor)

                    // Blocked Apps Count
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Blocked Applications",
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "$blockedAppsCount Apps Blocked",
                            color = PrimaryBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text("Manage Applications", fontWeight = FontWeight.Bold, color = DarkText, fontSize = 16.sp)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (appsList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No applications synced yet", color = LightText, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(appsList) { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (app.isBlocked) RedAlert.copy(alpha = 0.15f) else AccentTeal.copy(alpha = 0.15f),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (app.isBlocked) Icons.Default.Block else Icons.Default.Android,
                                        contentDescription = null,
                                        tint = if (app.isBlocked) RedAlert else AccentTeal,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = app.name, fontWeight = FontWeight.Bold, color = DarkText, fontSize = 15.sp)
                                    Text(text = "${app.category} • Today's usage: ${app.usageTime}", color = LightText, fontSize = 12.sp)
                                }

                                // Block Switch Control bound directly to repository
                                Switch(
                                    checked = app.isBlocked,
                                    onCheckedChange = { checked ->
                                        XshieldRepository.toggleAppBlock(app.packageName, checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = RedAlert,
                                        uncheckedThumbColor = LightText,
                                        uncheckedTrackColor = BorderColor
                                    )
                                )
                            }
                            HorizontalDivider(color = BorderColor)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. LIVE REMOTE SCREEN
// ==========================================
@Composable
fun LiveRemoteScreen(
    onNavigateToLiveViewing: () -> Unit = {}
) {
    var activeRemoteMode by remember { mutableStateOf(0) } // 0 = Screen Stream, 1 = Audio Stream
    var isStreaming by remember { mutableStateOf(false) }

    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            delay(15000) // Auto close stream after 15s to save simulated battery
            isStreaming = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stream Monitor View
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isStreaming) {
                    // Streaming Graphics
                    val infiniteTransition = rememberInfiniteTransition(label = "scan")
                    val alphaState by infiniteTransition.animateFloat(
                        initialValue = 0.2f,
                        targetValue = 0.8f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                            )
                        )
                        // Scan line drawing
                        drawLine(
                            color = Color(0xFF00E5FF).copy(alpha = alphaState),
                            start = Offset(0f, size.height * 0.5f),
                            end = Offset(size.width, size.height * 0.5f),
                            strokeWidth = 4f
                        )
                    }

                    // Stream overlays
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color.Red, CircleShape)
                        )
                        Text(
                            text = if (activeRemoteMode == 0) "LIVE SCREENSTREAM" else "LIVE CAMERA FEED",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Simulating real-time transmission...\nCodec: H.264 • Delay: 0.2s",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Stopped state
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.VideocamOff, contentDescription = null, tint = LightText, modifier = Modifier.size(48.dp))
                        Text("Remote stream is offline", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Select mode and click Start Stream below", color = LightText, fontSize = 12.sp)
                    }
                }
            }
        }
        
        // Add a padding/spacer to balance the screen or place a description
        Text(
            "Use the Live Viewing menu to control streams and instant commands.", 
            color = LightText, 
            textAlign = TextAlign.Center, 
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
    }
}

@Composable
fun CommandItem(
    icon: ImageVector,
    title: String,
    desc: String,
    status: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(AccentTeal.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AccentTeal)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = Color(0xFFE0E0E0), fontSize = 15.sp)
            Text(text = desc, color = Color.Gray, fontSize = 12.sp)
            if (status != null) {
                Text(
                    text = status, 
                    color = AccentTeal, 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = LightText)
    }
}

// ==========================================
// 4. SCHEDULE RESTRICTION SCREEN
// ==========================================
@Composable
fun ScheduleRestrictionScreen() {
    val schedules by XshieldRepository.schedules.collectAsState()
    val appsList = XshieldRepository.appsList
    
    var showDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<ScheduleRestriction?>(null) }

    // Dialog state variables
    var name by remember { mutableStateOf("") }
    var startHour by remember { mutableStateOf("21") }
    var startMin by remember { mutableStateOf("00") }
    var endHour by remember { mutableStateOf("07") }
    var endMin by remember { mutableStateOf("00") }
    var selectedDays by remember { mutableStateOf(setOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")) }
    var blockAll by remember { mutableStateOf(true) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var appSearchQuery by remember { mutableStateOf("") }

    fun openAddDialog() {
        editingSchedule = null
        name = ""
        startHour = "21"
        startMin = "00"
        endHour = "07"
        endMin = "00"
        selectedDays = setOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        blockAll = true
        selectedApps = emptySet()
        appSearchQuery = ""
        showDialog = true
    }

    fun openEditDialog(schedule: ScheduleRestriction) {
        editingSchedule = schedule
        name = schedule.name
        val startParts = schedule.startTime.split(":")
        startHour = startParts.getOrNull(0) ?: "21"
        startMin = startParts.getOrNull(1) ?: "00"
        val endParts = schedule.endTime.split(":")
        endHour = endParts.getOrNull(0) ?: "07"
        endMin = endParts.getOrNull(1) ?: "00"
        selectedDays = schedule.days.toSet()
        blockAll = schedule.blockAll
        selectedApps = schedule.blockedApps.toSet()
        appSearchQuery = ""
        showDialog = true
    }

    val daysOfWeek = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Premium Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AccentTeal.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Schedule Restrictions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Lock the child's device or restrict specific applications during study hours, school time, or bedtime.",
                            color = LightText,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Action Button
        item {
            Button(
                onClick = { openAddDialog() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ADD RESTRICTION SCHEDULE", fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.5.sp)
            }
        }

        // Schedules List
        if (schedules.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = BorderColor,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No Schedules Configured",
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Configured restrictions will display here. Tap the button above to create a school or bedtime lock schedule.",
                            color = LightText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        } else {
            items(schedules) { schedule ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openEditDialog(schedule) },
                    colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Colored accent bar indicating lock type
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight()
                                .height(110.dp)
                                .background(if (schedule.blockAll) RedAlert else AccentTeal)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = schedule.name,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkText,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (schedule.blockAll) Icons.Default.Lock else Icons.Default.AppBlocking,
                                            contentDescription = null,
                                            tint = if (schedule.blockAll) RedAlert else AccentTeal,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (schedule.blockAll) "Device Lockout" else "App Blocking (${schedule.blockedApps.size} apps)",
                                            fontSize = 11.sp,
                                            color = LightText,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Switch(
                                    checked = schedule.isEnabled,
                                    onCheckedChange = {
                                        XshieldRepository.saveSchedule(schedule.copy(isEnabled = it))
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = AccentTeal,
                                        checkedThumbColor = Color.White
                                    )
                                )
                            }

                            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Active hours pill chip
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AccentTeal.copy(alpha = 0.1f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = AccentTeal,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${schedule.startTime} - ${schedule.endTime}",
                                            fontWeight = FontWeight.Bold,
                                            color = AccentTeal,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                // Days indicator
                                val activeDaysText = when {
                                    schedule.days.size == 7 -> "Everyday"
                                    schedule.days.size == 5 && schedule.days.containsAll(listOf("MON", "TUE", "WED", "THU", "FRI")) -> "Weekdays"
                                    schedule.days.size == 2 && schedule.days.containsAll(listOf("SAT", "SUN")) -> "Weekends"
                                    else -> schedule.days.joinToString(", ")
                                }
                                Text(
                                    text = activeDaysText,
                                    fontSize = 11.sp,
                                    color = LightText,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Dynamic blocked apps list preview
                            if (!schedule.blockAll && schedule.blockedApps.isNotEmpty()) {
                                val appsText = schedule.blockedApps.map { pkg ->
                                    appsList.find { it.packageName == pkg }?.name ?: pkg.substringAfterLast(".")
                                }.joinToString(", ")
                                Text(
                                    text = "Restricted: $appsText",
                                    fontSize = 10.sp,
                                    color = LightText.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = if (editingSchedule == null) "Create Restriction Schedule" else "Edit Restriction Schedule",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkText
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Schedule Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Schedule Name") },
                        placeholder = { Text("e.g. Bedtime Lock, School Study") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentTeal,
                            unfocusedBorderColor = BorderColor
                        ),
                        singleLine = true
                    )

                    // Time Inputs (Clickable Native TimePicker Trigger)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Active Time Range", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Start Time Card
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val h = startHour.toIntOrNull() ?: 21
                                        val m = startMin.toIntOrNull() ?: 0
                                        android.app.TimePickerDialog(context, { _, hour, minute ->
                                            startHour = hour.toString().padStart(2, '0')
                                            startMin = minute.toString().padStart(2, '0')
                                        }, h, m, true).show()
                                    },
                                colors = CardDefaults.cardColors(containerColor = BackgroundGrey.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, BorderColor),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("START TIME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = LightText)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = AccentTeal,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "$startHour:$startMin",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = DarkText
                                        )
                                    }
                                }
                            }

                            // End Time Card
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val h = endHour.toIntOrNull() ?: 7
                                        val m = endMin.toIntOrNull() ?: 0
                                        android.app.TimePickerDialog(context, { _, hour, minute ->
                                            endHour = hour.toString().padStart(2, '0')
                                            endMin = minute.toString().padStart(2, '0')
                                        }, h, m, true).show()
                                    },
                                colors = CardDefaults.cardColors(containerColor = BackgroundGrey.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, BorderColor),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("END TIME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = LightText)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = AccentTeal,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "$endHour:$endMin",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = DarkText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Days of week
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Repeat Days", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, dayLetter ->
                                val fullDay = daysOfWeek[index]
                                val isSelected = selectedDays.contains(fullDay)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) AccentTeal else BorderColor)
                                        .clickable {
                                            selectedDays = if (isSelected) {
                                                selectedDays - fullDay
                                            } else {
                                                selectedDays + fullDay
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayLetter,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Lock Mode Cards
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lock Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Lock All Card
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { blockAll = true },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (blockAll) RedAlert.copy(alpha = 0.08f) else WhiteSurface
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (blockAll) RedAlert else BorderColor
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock, 
                                        contentDescription = null, 
                                        tint = if (blockAll) RedAlert else LightText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Lock Device", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkText)
                                    Text("Locks screen completely during hours", color = LightText, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
                                }
                            }

                            // Block Specific Card
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { blockAll = false },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!blockAll) AccentTeal.copy(alpha = 0.08f) else WhiteSurface
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (!blockAll) AccentTeal else BorderColor
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AppBlocking, 
                                        contentDescription = null, 
                                        tint = if (!blockAll) AccentTeal else LightText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Block Apps", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkText)
                                    Text("Block selected games or social apps", color = LightText, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
                                }
                            }
                        }
                    }

                    // Block Apps list if blockAll = false
                    if (!blockAll) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Select Apps to Block", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                                Text(
                                    text = "${selectedApps.size} selected",
                                    fontSize = 11.sp,
                                    color = AccentTeal,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Search bar with icon
                            OutlinedTextField(
                                value = appSearchQuery,
                                onValueChange = { appSearchQuery = it },
                                placeholder = { Text("Search installed apps...", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = LightText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (appSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { appSearchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                tint = LightText,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentTeal,
                                    unfocusedBorderColor = BorderColor
                                ),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                            )

                            // Quick helper action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        selectedApps = appsList.map { it.packageName }.toSet()
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Select All", fontSize = 11.sp, color = AccentTeal)
                                }
                                TextButton(
                                    onClick = {
                                        selectedApps = emptySet()
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Clear All", fontSize = 11.sp, color = RedAlert)
                                }
                            }

                            val filteredApps = appsList.filter {
                                it.name.contains(appSearchQuery, ignoreCase = true) ||
                                it.packageName.contains(appSearchQuery, ignoreCase = true)
                            }

                            if (filteredApps.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .background(BackgroundGrey.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No apps match search criteria", fontSize = 11.sp, color = LightText)
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth().height(180.dp),
                                    colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                                    border = BorderStroke(1.dp, BorderColor),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.padding(4.dp)) {
                                        items(filteredApps) { app ->
                                            val isChecked = selectedApps.contains(app.packageName)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedApps = if (isChecked) {
                                                            selectedApps - app.packageName
                                                        } else {
                                                            selectedApps + app.packageName
                                                        }
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = {
                                                        selectedApps = if (it == true) {
                                                            selectedApps + app.packageName
                                                        } else {
                                                            selectedApps - app.packageName
                                                        }
                                                    },
                                                    colors = CheckboxDefaults.colors(checkedColor = AccentTeal)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(app.name, fontSize = 12.sp, color = DarkText, fontWeight = FontWeight.SemiBold)
                                                    Text(app.packageName, fontSize = 9.sp, color = LightText)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val startHourStr = startHour.trim().padStart(2, '0')
                        val startMinStr = startMin.trim().padStart(2, '0')
                        val endHourStr = endHour.trim().padStart(2, '0')
                        val endMinStr = endMin.trim().padStart(2, '0')
                        
                        val newSchedule = ScheduleRestriction(
                            id = editingSchedule?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.ifBlank { if (blockAll) "Device Lockout" else "Study Time" },
                            startTime = "$startHourStr:$startMinStr",
                            endTime = "$endHourStr:$endMinStr",
                            days = selectedDays.toList(),
                            isEnabled = editingSchedule?.isEnabled ?: true,
                            blockAll = blockAll,
                            blockedApps = if (blockAll) emptyList() else selectedApps.toList()
                        )
                        XshieldRepository.saveSchedule(newSchedule)
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingSchedule != null) {
                        TextButton(
                            onClick = {
                                XshieldRepository.deleteSchedule(editingSchedule!!.id)
                                showDialog = false
                            }
                        ) {
                            Text("Delete", color = RedAlert)
                        }
                    }
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel", color = LightText)
                    }
                }
            }
        )
    }
}

// ==========================================
// 5. STATISTICS SCREEN (CANVAS CHARTS)
// ==========================================
@Composable
fun StatisticsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Device Activity Reports", fontWeight = FontWeight.Bold, color = DarkText, fontSize = 16.sp)

        // Custom Bar Chart Card (Top Callers)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Most Contacted Callers (Minutes)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LightText)
                Spacer(modifier = Modifier.height(16.dp))

                // Draw Bar Graph
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    
                    val barWidth = 40.dp.toPx()
                    val gap = (w - (barWidth * 4)) / 5
                    
                    val values = listOf(0.85f, 0.60f, 0.45f, 0.30f)
                    val labels = listOf("Mom", "Dad", "David", "Jack")
                    val colors = listOf(AccentTeal, PrimaryBlue, Color(0xFF9C27B0), Color(0xFFFF9800))

                    for (i in 0..3) {
                        val barHeight = values[i] * (h - 30.dp.toPx())
                        val x = gap + i * (barWidth + gap)
                        val y = h - barHeight - 20.dp.toPx()

                        // Bar
                        drawRect(
                            color = colors[i],
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight)
                        )

                        // Label (simulated placeholder text block drawing or line)
                        drawLine(
                            color = colors[i],
                            start = Offset(x, h - 8.dp.toPx()),
                            end = Offset(x + barWidth, h - 8.dp.toPx()),
                            strokeWidth = 3f
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Text("Mom (85m)", fontSize = 11.sp, color = DarkText, fontWeight = FontWeight.Bold)
                    Text("Dad (60m)", fontSize = 11.sp, color = DarkText, fontWeight = FontWeight.Bold)
                    Text("David (45m)", fontSize = 11.sp, color = DarkText, fontWeight = FontWeight.Bold)
                    Text("Jack (30m)", fontSize = 11.sp, color = DarkText, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Custom Donut Chart Card (App Categories Usage)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("App Category Distribution", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LightText)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Donut drawing
                    Canvas(
                        modifier = Modifier
                            .size(140.dp)
                    ) {
                        val radius = size.minDimension / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val strokeWidth = 24.dp.toPx()

                        // Social Media (50% = 180 degrees)
                        drawArc(
                            color = AccentTeal,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth)
                        )
                        // Gaming (30% = 108 degrees)
                        drawArc(
                            color = RedAlert,
                            startAngle = 180f,
                            sweepAngle = 108f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth)
                        )
                        // Others (20% = 72 degrees)
                        drawArc(
                            color = Color(0xFFF0AD4E),
                            startAngle = 288f,
                            sweepAngle = 72f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth)
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegendItem(color = AccentTeal, text = "Social Media (50%)")
                        LegendItem(color = RedAlert, text = "Gaming (30%)")
                        LegendItem(color = Color(0xFFF0AD4E), text = "Others (20%)")
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Text(text = text, fontSize = 12.sp, color = DarkText, fontWeight = FontWeight.Bold)
    }
}

// ==========================================
// 6. CONTACTS SCREEN
// ==========================================
@Composable
fun ContactsScreen() {
    val contacts = XshieldRepository.contactsList
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Target Address Book", fontWeight = FontWeight.Bold, color = DarkText, fontSize = 16.sp)

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search contacts...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LightText) },
            modifier = Modifier
                .fillMaxWidth()
                .background(WhiteSurface, RoundedCornerShape(8.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentTeal,
                unfocusedBorderColor = BorderColor
            ),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )

        val filteredContacts = if (searchQuery.isBlank()) contacts else contacts.filter {
            it.first.contains(searchQuery, ignoreCase = true) || it.second.contains(searchQuery, ignoreCase = true)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (filteredContacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No contacts found", color = LightText, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredContacts) { contact ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(AccentTeal.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val firstChar = if (contact.first.isNotEmpty()) contact.first.first().toString() else "?"
                                Text(
                                    text = firstChar,
                                    color = AccentTeal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = contact.first, fontWeight = FontWeight.Bold, color = DarkText, fontSize = 14.sp)
                                Text(text = contact.second, color = LightText, fontSize = 12.sp)
                            }
                            val matchedBlock = XshieldRepository.blockedList.find { 
                                android.telephony.PhoneNumberUtils.compare(it.number, contact.second) 
                            }
                            if (matchedBlock != null) {
                                Box(
                                    modifier = Modifier
                                        .clickable { XshieldRepository.removeBlockedNumber(matchedBlock.id) }
                                        .background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Unblock", color = RedAlert, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clickable { XshieldRepository.addBlockedNumber(contact.second, "both") }
                                        .background(AccentTeal.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Block", color = AccentTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Divider(color = BorderColor, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. FILE EXPLORER SCREEN
// ==========================================
@Composable
fun FileExplorerScreen() {
    val currentPath = XshieldRepository.currentExplorerPath.value
    val filesList = XshieldRepository.explorerFilesList
    val isLoading = XshieldRepository.isExplorerLoading.value
    val errorMsg = XshieldRepository.explorerError.value
    
    val isPreviewLoading = XshieldRepository.isPreviewLoading.value
    val previewData by XshieldRepository.currentPreview.collectAsState(initial = null)
    val downloadUrl by XshieldRepository.currentDownloadUrl.collectAsState(initial = "")
    var selectedFileForDownload by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var activeDownloadId by remember { mutableStateOf<Long?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Trigger local download when URL is received
    LaunchedEffect(downloadUrl) {
        if (downloadUrl.isNotBlank() && isDownloading) {
            try {
                val request = android.app.DownloadManager.Request(android.net.Uri.parse(downloadUrl))
                request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val fileName = android.net.Uri.parse(downloadUrl).lastPathSegment ?: "downloaded_file"
                request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                
                val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                val id = downloadManager.enqueue(request)
                activeDownloadId = id
                
                android.widget.Toast.makeText(context, "Download started...", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                isDownloading = false
            }
            XshieldRepository.currentDownloadUrl.value = "" // Reset
        }
    }

    // Poll DownloadManager for progress
    LaunchedEffect(activeDownloadId) {
        activeDownloadId?.let { id ->
            val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            var isFinished = false
            while (!isFinished) {
                val query = android.app.DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIdx)
                    
                    val bytesDownloadedIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    if (bytesDownloadedIdx >= 0 && bytesTotalIdx >= 0) {
                        val downloaded = cursor.getInt(bytesDownloadedIdx)
                        val total = cursor.getInt(bytesTotalIdx)
                        if (total > 0) {
                            downloadProgress = downloaded.toFloat() / total.toFloat()
                        }
                    }
                    
                    if (status == android.app.DownloadManager.STATUS_SUCCESSFUL || status == android.app.DownloadManager.STATUS_FAILED) {
                        isFinished = true
                        activeDownloadId = null
                        isDownloading = false
                        downloadProgress = 0f
                    }
                } else {
                    isFinished = true
                    activeDownloadId = null
                    isDownloading = false
                    downloadProgress = 0f
                }
                cursor?.close()
                if (!isFinished) kotlinx.coroutines.delay(500)
            }
        }
    }

    // When the screen first opens, request the default path if we haven't already
    LaunchedEffect(Unit) {
        if (filesList.isEmpty() && errorMsg == null && !isLoading) {
            XshieldRepository.requestDirectory("/storage/emulated/0")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
            .padding(16.dp)
    ) {
        // Breadcrumb/Path UI
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val parent = java.io.File(currentPath).parent
                    if (parent != null) {
                        XshieldRepository.requestDirectory(parent)
                    }
                }) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Go Up", tint = PrimaryBlue)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Current Directory", fontSize = 10.sp, color = LightText, fontWeight = FontWeight.Bold)
                    Text(
                        text = currentPath,
                        color = DarkText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = {
                    XshieldRepository.requestDirectory("/storage/emulated/0")
                }) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = PrimaryBlue)
                }
            }
        }

        // List
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else if (errorMsg != null) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = RedAlert, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = errorMsg, color = RedAlert, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { XshieldRepository.requestDirectory(currentPath) }) {
                            Text("Retry")
                        }
                    }
                }
            } else if (filesList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Folder is empty", color = LightText)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filesList) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (file.isDirectory) {
                                        XshieldRepository.requestDirectory(file.path)
                                    } else {
                                        selectedFileForDownload = file.path
                                        XshieldRepository.requestPreview(file.path)
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (file.isDirectory) Color(0xFFF0AD4E) else LightText,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    fontWeight = if (file.isDirectory) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    color = DarkText,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (!file.isDirectory) {
                                    // Format size
                                    val sizeKb = file.size / 1024
                                    val sizeDisplay = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB"
                                    
                                    val dateStr = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified))
                                    Text(text = "$sizeDisplay • $dateStr", fontSize = 12.sp, color = LightText)
                                }
                            }
                            if (file.isDirectory) {
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BorderColor)
                            }
                        }
                        Divider(color = BorderColor)
                    }
                }
            }
        }
    }

    if (isPreviewLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Preparing Preview") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(24.dp))
                    Text("Fetching from device...")
                }
            },
            confirmButton = {}
        )
    } else if (previewData != null) {
        AlertDialog(
            onDismissRequest = { XshieldRepository.currentPreview.value = null },
            title = { Text("File Preview") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val type = previewData?.get("type")
                    when (type) {
                        "image", "video", "pdf" -> {
                            val url = previewData?.get("url")
                            if (!url.isNullOrBlank()) {
                                coil.compose.AsyncImage(
                                    model = url,
                                    contentDescription = "Preview",
                                    modifier = Modifier.fillMaxWidth().height(200.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                        }
                        "audio" -> {
                            Text("Title: ${previewData?.get("title")}", fontWeight = FontWeight.Bold)
                            Text("Duration: ${previewData?.get("duration")} ms")
                        }
                        "text" -> {
                            Text("Snippet:", fontWeight = FontWeight.Bold)
                            Card(colors = CardDefaults.cardColors(containerColor = BackgroundGrey)) {
                                Text(
                                    text = previewData?.get("content") ?: "",
                                    modifier = Modifier.padding(8.dp),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        else -> {
                            Text("No preview available for this file type.")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    isDownloading = true
                    selectedFileForDownload?.let { XshieldRepository.requestDownload(it) }
                    XshieldRepository.currentPreview.value = null
                }) {
                    Text("Download Original File")
                }
            },
            dismissButton = {
                TextButton(onClick = { XshieldRepository.currentPreview.value = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isDownloading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Download Progress") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (activeDownloadId == null) {
                        CircularProgressIndicator(color = PrimaryBlue)
                        Text("Extracting file from remote device...")
                    } else {
                        LinearProgressIndicator(
                            progress = downloadProgress,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = PrimaryBlue
                        )
                        Text("Downloading to your phone... ${(downloadProgress * 100).toInt()}%")
                    }
                }
            },
            confirmButton = {}
        )
    }
}
