package com.example.xshield

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreenShareScreen(allowInteraction: Boolean = true, onBack: () -> Unit) {
    val status by XshieldRepository.screenShareStatus
    val activeTrack by XshieldRepository.activeVideoTrack
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }

    var showTextInputDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        XshieldRepository.observeScreenShareStatus()
        onDispose {
            XshieldRepository.stopScreenShare()
        }
    }

    if (showTextInputDialog) {
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            title = { Text("Remote Text Input") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Text to type") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val sanitized = textInput.replace("\"", "\\\"").replace("\n", "\\n")
                    XshieldRepository.sendRemoteCommand("{\"action\":\"type_text\",\"text\":\"$sanitized\"}")
                    showTextInputDialog = false
                    textInput = ""
                }) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val remoteControlsOverlay: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { XshieldRepository.sendRemoteCommand("{\"action\":\"global_action\",\"actionType\":\"HOME\"}") }) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White)
            }
            IconButton(onClick = { XshieldRepository.sendRemoteCommand("{\"action\":\"global_action\",\"actionType\":\"BACK\"}") }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            IconButton(onClick = { XshieldRepository.sendRemoteCommand("{\"action\":\"global_action\",\"actionType\":\"RECENTS\"}") }) {
                Icon(Icons.Default.Menu, contentDescription = "Recents", tint = Color.White)
            }
            IconButton(onClick = { XshieldRepository.sendRemoteCommand("{\"action\":\"global_action\",\"actionType\":\"NOTIFICATIONS\"}") }) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
            }
            IconButton(onClick = { XshieldRepository.sendRemoteCommand("{\"action\":\"global_action\",\"actionType\":\"QUICK_SETTINGS\"}") }) {
                Icon(Icons.Default.Settings, contentDescription = "Quick Settings", tint = Color.White)
            }
            IconButton(onClick = { showTextInputDialog = true }) {
                Icon(Icons.Default.Keyboard, contentDescription = "Type Text", tint = Color.White)
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    windowInsets = WindowInsets(0.dp),
                    title = { Text("Live Screen Share", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            var boxWidth by remember { mutableStateOf(0f) }
            var boxHeight by remember { mutableStateOf(0f) }

            // Video Renderer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        boxWidth = it.size.width.toFloat()
                        boxHeight = it.size.height.toFloat()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (status == "LIVE" && activeTrack != null) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                XshieldRepository.webRtcManager?.eglBase?.eglBaseContext?.let { eglContext ->
                                    init(eglContext, null)
                                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                                    setEnableHardwareScaler(true)
                                }
                                rendererRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    LaunchedEffect(activeTrack, rendererRef) {
                        val track = activeTrack
                        val renderer = rendererRef
                        if (track != null && renderer != null) {
                            track.addSink(renderer)
                        }
                    }

                    // Touch Capture Overlay
                    if (allowInteraction) {
                        Box(modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    if (boxWidth > 0 && boxHeight > 0) {
                                        val xRatio = offset.x / boxWidth
                                        val yRatio = offset.y / boxHeight
                                        XshieldRepository.sendRemoteCommand("{\"action\":\"tap\",\"xRatio\":$xRatio,\"yRatio\":$yRatio}")
                                    }
                                },
                                onDoubleTap = { offset ->
                                    if (boxWidth > 0 && boxHeight > 0) {
                                        val xRatio = offset.x / boxWidth
                                        val yRatio = offset.y / boxHeight
                                        XshieldRepository.sendRemoteCommand("{\"action\":\"double_tap\",\"xRatio\":$xRatio,\"yRatio\":$yRatio}")
                                    }
                                },
                                onLongPress = { offset ->
                                    if (boxWidth > 0 && boxHeight > 0) {
                                        val xRatio = offset.x / boxWidth
                                        val yRatio = offset.y / boxHeight
                                        XshieldRepository.sendRemoteCommand("{\"action\":\"long_press\",\"xRatio\":$xRatio,\"yRatio\":$yRatio}")
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            var startOffset = Offset.Zero
                            var currentOffset = Offset.Zero
                            detectDragGestures(
                                onDragStart = { offset ->
                                    startOffset = offset
                                    currentOffset = offset
                                },
                                onDragEnd = {
                                    if (boxWidth > 0 && boxHeight > 0) {
                                        val startXRatio = startOffset.x / boxWidth
                                        val startYRatio = startOffset.y / boxHeight
                                        val endXRatio = currentOffset.x / boxWidth
                                        val endYRatio = currentOffset.y / boxHeight
                                        XshieldRepository.sendRemoteCommand("{\"action\":\"swipe\",\"startXRatio\":$startXRatio,\"startYRatio\":$startYRatio,\"endXRatio\":$endXRatio,\"endYRatio\":$endYRatio}")
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentOffset += dragAmount
                                }
                            )
                        }
                        ) // closing parenthesis for Box
                    }
                } else {
                    val statusText = when (status) {
                        "WAITING_PERMISSION" -> "Waiting for child approval..."
                        "CONNECTING" -> "Connecting to screen..."
                        "ERROR" -> "Failed to connect"
                        "DENIED" -> "Child denied permission"
                        else -> "Screen Share is Offline"
                    }
                    Text(
                        text = statusText,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Add the Fullscreen toggle at the top end
                IconButton(
                    onClick = { isFullScreen = !isFullScreen },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Toggle Fullscreen",
                        tint = Color.White
                    )
                }
                
                if (isFullScreen && status == "LIVE") {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        remoteControlsOverlay()
                    }
                }
            }

            // Controls
            if (!isFullScreen) {
                if (status == "LIVE") {
                    remoteControlsOverlay()
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { XshieldRepository.startScreenShare() },
                            enabled = status == "IDLE" || status == "STOPPED" || status == "ERROR" || status == "DENIED" || status == "OFFLINE",
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                            Spacer(Modifier.width(8.dp))
                            Text("Start Share")
                        }

                        Button(
                            onClick = { XshieldRepository.stopScreenShare() },
                            enabled = status == "LIVE" || status == "CONNECTING" || status == "WAITING_PERMISSION",
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                            Spacer(Modifier.width(8.dp))
                            Text("Stop Share")
                        }
                    }
                } // End of Surface
            }
        }
    }
}
