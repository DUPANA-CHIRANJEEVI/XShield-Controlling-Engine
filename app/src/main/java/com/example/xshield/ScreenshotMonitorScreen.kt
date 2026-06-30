package com.example.xshield

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotMonitorScreen(onBack: () -> Unit) {
    var selectedPhotoId by remember { mutableStateOf<String?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val capturedPhotos = XshieldRepository.capturedPhotosList
    val screenshotPhotos = capturedPhotos.filter { it.type == "screenshot" }.sortedByDescending { it.timestamp }

    val context = LocalContext.current

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
                        contentDescription = "Full Screen Screenshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    
                    // Top Bar for Dialog
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x80000000))
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedPhotoId = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                        
                        Row {
                            IconButton(onClick = {
                                try {
                                    val request = DownloadManager.Request(Uri.parse(selectedPhoto.url))
                                        .setTitle("Screenshot_${selectedPhoto.timestamp}.jpg")
                                        .setDescription("Downloading screenshot")
                                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Screenshot_${selectedPhoto.timestamp}.jpg")
                                    
                                    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                    downloadManager.enqueue(request)
                                    feedbackMessage = "Download started. Check notifications."
                                } catch (e: Exception) {
                                    feedbackMessage = "Failed to download screenshot."
                                }
                            }) {
                                Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.White)
                            }
                            
                            IconButton(onClick = {
                                XshieldRepository.deleteCapturedPhoto(selectedPhoto.id)
                                selectedPhotoId = null
                                feedbackMessage = "Screenshot deleted."
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = { Text("Screenshot", fontWeight = FontWeight.SemiBold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Text(
                        text = "Synced: ${XshieldRepository.lastSyncTime.value}",
                        color = Color(0xFF00A8B5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2C313C)
                )
            )
        },
        containerColor = Color(0xFFF5F6F8)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Remote Screenshot Monitor", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Captures target screen viewport and displays chronological screenshot files.", color = Color.Gray, fontSize = 12.sp)
                }
            }

            val isAccessibilityActive = XshieldRepository.isAccessibilityActive.value
            if (!isAccessibilityActive) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = "Accessibility Service Inactive",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "System Health Monitor is disabled in the child's device settings. Remote screenshot capture cannot operate until it is re-enabled.",
                                color = Color(0xFFB91C1C),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    XshieldRepository.captureRemoteScreenshot()
                    feedbackMessage = "Screenshot capture triggered."
                },
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF337AB7))
            ) {
                Icon(Icons.Default.Smartphone, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("TRIGGER REMOTE SCREENSHOT CAPTURE", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Captured Screens Log", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(16.dp))

            if (screenshotPhotos.isEmpty()) {
                Text("No screenshots captured yet.", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(screenshotPhotos) { photo ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { selectedPhotoId = photo.id }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    AsyncImage(
                                        model = photo.url,
                                        contentDescription = "Screenshot",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                Text(
                                    text = sdf.format(Date(photo.timestamp)),
                                    fontSize = 10.sp,
                                    color = Color.DarkGray,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
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
            title = { Text("Command Status", color = Color.Black) },
            text = { Text(msg, color = Color.DarkGray) },
            containerColor = Color.White
        )
    }
}
