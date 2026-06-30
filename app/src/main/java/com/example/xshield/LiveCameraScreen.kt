package com.example.xshield

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.filled.Save
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

data class UnifiedCapture(
    val id: String,
    val thumbnailUrl: String,
    val isVideo: Boolean,
    val timestamp: Long,
    val videoUrl: String? = null,
    val originalPhotoUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCameraScreen(onBack: () -> Unit, onNavigateToGallery: () -> Unit = {}, onNavigateToVideoPreview: (RecordedVideo) -> Unit = {}, onNavigateToScreenShare: () -> Unit = {}) {
    val context = LocalContext.current
    val status by XshieldRepository.liveCameraStatus
    val activeTrack by XshieldRepository.activeVideoTrack
    val capturedPhotos = XshieldRepository.capturedPhotosList
    val recordedVideos = XshieldRepository.videosList
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var selectedPhotoId by remember { mutableStateOf<String?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }
    var activeCameraState by remember { mutableStateOf(XshieldRepository.webRtcManager?.currentType ?: "rear") }

    // Full screen image dialog
    if (selectedPhotoId != null) {
        val selectedPhoto = capturedPhotos.find { it.id == selectedPhotoId }
        if (selectedPhoto != null) {
            Dialog(onDismissRequest = { selectedPhotoId = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = selectedPhoto.url,
                        contentDescription = "Full Screen Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    // Top row for Close and Delete
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { selectedPhotoId = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                        val context = LocalContext.current
                        IconButton(onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val connection = java.net.URL(selectedPhoto.url).openConnection() as java.net.HttpURLConnection
                                    connection.doInput = true
                                    connection.connect()
                                    val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                                    if (bitmap != null) {
                                        saveBitmapToGallery(context, bitmap)
                                        CoroutineScope(Dispatchers.Main).launch {
                                            android.widget.Toast.makeText(context, "Saved to Gallery", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.Green)
                        }
                        IconButton(onClick = {
                            XshieldRepository.deleteCapturedPhoto(selectedPhoto.id)
                            selectedPhotoId = null
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }

    // Cleanup when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            XshieldRepository.stopCameraStream()
        }
    }

    val cameraControlsOverlay: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().run {
                if (isFullScreen) this.padding(horizontal = 16.dp, vertical = 32.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(12.dp) else this
            }
        ) {
            val isFrontCamera = activeCameraState == "front"
            
            val onToggleCamera: (Boolean) -> Unit = { toFront ->
                if (!XshieldRepository.isVideoRecording.value) {
                    val newCam = if (toFront) "front" else "rear"
                    activeCameraState = newCam
                    XshieldRepository.switchCamera(newCam)
                    XshieldRepository.webRtcManager?.currentType = newCam
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status == "idle" || status == "offline" || status == "error") {
                    
                    // Toggle Component
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rear", color = if (!isFrontCamera) Color(0xFF00A8B5) else Color.Gray, fontWeight = if (!isFrontCamera) FontWeight.Bold else FontWeight.Normal)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isFrontCamera,
                            onCheckedChange = onToggleCamera,
                            enabled = !XshieldRepository.isVideoRecording.value,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00A8B5),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Front", color = if (isFrontCamera) Color(0xFF00A8B5) else Color.Gray, fontWeight = if (isFrontCamera) FontWeight.Bold else FontWeight.Normal)
                    }

                    
                    if (XshieldRepository.isVideoRecording.value) {
                        Button(
                            onClick = {
                                XshieldRepository.stopVideoRecording()
                                XshieldRepository.isVideoRecording.value = false
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    XshieldRepository.startCameraStream(activeCameraState)
                                }, 2000)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop Rec")
                        }
                    } else {
                        // Re-connect stream
                        Button(
                            onClick = { XshieldRepository.startCameraStream(activeCameraState) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            enabled = !XshieldRepository.isVideoRecording.value
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Connect")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect")
                        }
                    }
                    
                    // Screen Share Button
                    Button(
                        onClick = onNavigateToScreenShare,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A8B5))
                    ) {
                        Icon(Icons.Default.ScreenShare, contentDescription = "Screen Share", tint = Color.White)
                    }
                } else {
                    // Toggle Component
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rear", color = if (!isFrontCamera) Color(0xFF00A8B5) else Color.Gray, fontWeight = if (!isFrontCamera) FontWeight.Bold else FontWeight.Normal)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isFrontCamera,
                            onCheckedChange = onToggleCamera,
                            enabled = !XshieldRepository.isVideoRecording.value,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00A8B5),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Front", color = if (isFrontCamera) Color(0xFF00A8B5) else Color.Gray, fontWeight = if (isFrontCamera) FontWeight.Bold else FontWeight.Normal)
                    }

                    // Record Video Button
                    if (XshieldRepository.isVideoRecording.value) {
                        Button(
                            onClick = {
                                XshieldRepository.stopVideoRecording()
                                XshieldRepository.isVideoRecording.value = false
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    XshieldRepository.startCameraStream(activeCameraState)
                                }, 2000)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop Rec")
                        }
                    } else {
                        Button(
                            onClick = {
                                XshieldRepository.startVideoRecording(activeCameraState)
                                XshieldRepository.isVideoRecording.value = true
                                XshieldRepository.stopCameraStream()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A8B5))
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Record", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Record")
                        }
                    }

                    // Capture Photo Button
                    Button(
                        onClick = {
                            rendererRef?.addFrameListener({ bitmap ->
                                val bmpCopy = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    android.widget.Toast.makeText(context, "Uploading capture...", android.widget.Toast.LENGTH_SHORT).show()
                                    XshieldRepository.uploadCapturedFrame(context, bmpCopy)
                                }
                            }, 1.0f)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                        enabled = !XshieldRepository.isVideoRecording.value
                    ) {
                        Icon(Icons.Default.Camera, contentDescription = "Capture Photo")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Photo")
                    }

                    // Screen Share Button
                    Button(
                        onClick = onNavigateToScreenShare,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A8B5)),
                        enabled = !XshieldRepository.isVideoRecording.value
                    ) {
                        Icon(Icons.Default.ScreenShare, contentDescription = "Screen Share", tint = Color.White)
                    }
                }
            }

            if (status == "live" || status == "connecting") {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { XshieldRepository.stopCameraStream() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !XshieldRepository.isVideoRecording.value
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = "Stop Stream", tint = Color.Red)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Live Stream", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = { Text("Live Camera Stream", fontWeight = FontWeight.SemiBold) },
                    windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToGallery) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Video Gallery")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }},
        containerColor = Color(0xFF12121D)
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Status Indicator
            if (!isFullScreen) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Connection Status:", color = Color.White)
                        val statusColor = when (status) {
                            "live" -> Color(0xFF4CAF50)
                            "connecting" -> Color(0xFFFFC107)
                            "error" -> Color(0xFFF44336)
                            else -> Color.Gray
                        }
                        Text(
                            text = status.uppercase(),
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Camera Video View (WebRTC)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (isFullScreen) it.fillMaxHeight() else it.aspectRatio(4f/3f) }
                    .clip(if (isFullScreen) RoundedCornerShape(0.dp) else MaterialTheme.shapes.large)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {

                if (XshieldRepository.isVideoRecording.value) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = "Recording",
                            tint = Color.Red,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Recording Remote Video...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Live stream paused",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    if (activeTrack != null) {
                        AndroidView(
                            factory = { ctx ->
                                SurfaceViewRenderer(ctx).apply {
                                    XshieldRepository.webRtcManager?.eglBase?.eglBaseContext?.let { eglContext ->
                                        init(eglContext, null)
                                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                                        setEnableHardwareScaler(true)
                                        rendererRef = this
                                    }
                                    activeTrack?.addSink(this)
                                }
                            },
                            update = { renderer ->
                                activeTrack?.addSink(renderer)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (status == "connecting" || status == "live") {
                        CircularProgressIndicator(color = Color(0xFF00A8B5))
                    } else {
                        Icon(
                            Icons.Default.Cameraswitch,
                            contentDescription = "No Camera",
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                    }
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
                
                if (isFullScreen) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        cameraControlsOverlay()
                    }
                }
            }

            if (!isFullScreen) {
                Spacer(modifier = Modifier.height(16.dp))
                cameraControlsOverlay()

            val unifiedCaptures = (capturedPhotos.map { photo ->
                UnifiedCapture(photo.id, photo.url, false, photo.timestamp, originalPhotoUrl = photo.url)
            } + recordedVideos.map { video ->
                UnifiedCapture(video.id, video.thumbnailUrl, true, video.timestamp, videoUrl = video.videoUrl)
            }).sortedByDescending { it.timestamp }

            if (unifiedCaptures.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3C)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Captures",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onNavigateToGallery() }
                            ) {
                                Text(
                                    text = "View All",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF00A8B5)
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "View All",
                                    tint = Color(0xFF00A8B5),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(unifiedCaptures) { index, capture ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(Color.DarkGray)
                                            .clickable { 
                                                if (capture.isVideo) {
                                                    val videoObj = recordedVideos.find { it.id == capture.id }
                                                    if (videoObj != null) {
                                                        onNavigateToVideoPreview(videoObj)
                                                    }
                                                } else {
                                                    selectedPhotoId = capture.id 
                                                }
                                            }
                                    ) {
                                        AsyncImage(
                                            model = capture.thumbnailUrl,
                                            contentDescription = "Capture",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        
                                        if (capture.isVideo) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Play Video",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .size(32.dp)
                                                    .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape)
                                                    .padding(4.dp)
                                            )
                                        }
                                        
                                        if (index == 0) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF00A8B5), shape = RoundedCornerShape(bottomEnd = 8.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    .align(Alignment.TopStart)
                                            ) {
                                                Text("NEW", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                    Text(
                                        text = sdf.format(Date(capture.timestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- Instant Remote Commands ---
            Spacer(modifier = Modifier.height(16.dp))
            Text("Instant Remote Commands", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isSirenPlaying = XshieldRepository.isSirenPlaying.value
                    val sirenStatusText = if (isSirenPlaying) "Output: Siren playing" else "Output: Siren stopped"
                    CommandItem(
                        icon = Icons.Default.VolumeUp,
                        title = "Sound Alarm Siren",
                        desc = "Plays loud siren sound on target device",
                        status = sirenStatusText,
                        onClick = {
                            XshieldRepository.toggleSiren(!isSirenPlaying)
                        }
                    )
                    HorizontalDivider(color = Color.DarkGray)
                    CommandItem(
                        icon = Icons.Default.Lock,
                        title = "Lock Screen Instantly",
                        desc = "Wipes active view and locks target window",
                        onClick = { 
                            XshieldRepository.lockScreen()
                            feedbackMessage = "Lock Command Sent! Screen will turn off instantly."
                        }
                    )
                    HorizontalDivider(color = Color.DarkGray)
                    CommandItem(
                        icon = Icons.Default.CameraAlt,
                        title = "Remote Photo Capture",
                        desc = "Takes secret snapshot using front camera",
                        onClick = { 
                            XshieldRepository.captureSecretPhoto()
                            XshieldRepository.stopCameraStream()
                            feedbackMessage = "Photo Capture triggered! Saving to Recent Captures soon." 
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                XshieldRepository.startCameraStream(activeCameraState)
                            }, 3500)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            } // End of if (!isFullScreen)
        }
    }

    feedbackMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { feedbackMessage = null },
            confirmButton = {
                TextButton(onClick = { feedbackMessage = null }) {
                    Text("OK", color = Color(0xFF00A8B5))
                }
            },
            title = { Text("Command Dashboard Status", color = Color.White) },
            text = { Text(msg, color = Color.LightGray) },
            containerColor = Color(0xFF1E1E2E)
        )
    }
}
fun saveBitmapToGallery(context: android.content.Context, bitmap: android.graphics.Bitmap) {
    val filename = "Xshield_${System.currentTimeMillis()}.jpg"
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Xshield")
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    uri?.let {
        context.contentResolver.openOutputStream(it).use { out ->
            if (out != null) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
    }
}
