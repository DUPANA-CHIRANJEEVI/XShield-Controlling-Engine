package com.example.xshield

import android.app.DownloadManager
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xshield.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Helper function to format time in mm:ss
fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

@Composable
fun AudioStreamingScreen() {
    val liveStatus = XshieldRepository.liveAudioStatus.value
    val isLive = liveStatus == "live"
    val isConnecting = liveStatus == "connecting"
    val isRecording = XshieldRepository.isRemoteAudioRecording.value
    val context = LocalContext.current

    // Audio Playback State
    var currentlyPlayingUrl by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentPlaybackPos by remember { mutableStateOf(0) }
    var totalPlaybackDuration by remember { mutableStateOf(0) }

    // Timers
    var streamDurationSecs by remember { mutableStateOf(0L) }
    var recordingDurationSecs by remember { mutableStateOf(0L) }

    // Live Stream Timer Loop
    LaunchedEffect(isLive) {
        if (isLive) {
            while (true) {
                delay(1000)
                streamDurationSecs++
            }
        } else {
            streamDurationSecs = 0L
        }
    }

    // Recording Timer Loop
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                delay(1000)
                recordingDurationSecs++
            }
        } else {
            recordingDurationSecs = 0L
        }
    }

    // MediaPlayer progress loop
    LaunchedEffect(currentlyPlayingUrl, mediaPlayer) {
        if (currentlyPlayingUrl != null && mediaPlayer != null) {
            while (true) {
                try {
                    if (mediaPlayer?.isPlaying == true) {
                        currentPlaybackPos = mediaPlayer?.currentPosition ?: 0
                    }
                } catch (e: Exception) {}
                delay(100)
            }
        }
    }

    // Cleanup MediaPlayer when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Live Audio Player Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(1.dp) // For border effect
                .background(Color.Transparent, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLive || isConnecting) {
                // Background Waves
                val infiniteTransition = rememberInfiniteTransition(label = "waves")
                val wave1 by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart), label = "wave1"
                )
                val wave2 by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing, delayMillis = 600), RepeatMode.Restart), label = "wave2"
                )
                val wave3 by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing, delayMillis = 1200), RepeatMode.Restart), label = "wave3"
                )

                if (isLive) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val maxRadius = size.minDimension / 2f
                        listOf(wave1, wave2, wave3).forEach { progress ->
                            val currentRadius = progress * maxRadius
                            val alpha = 1f - progress
                            drawCircle(color = AccentTeal.copy(alpha = alpha * 0.5f), radius = currentRadius, center = center, style = Stroke(width = 8f))
                        }
                    }
                }

                // Glowing Headphone Icon
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(AccentTeal.copy(alpha = 0.15f), CircleShape)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Headphones, contentDescription = "Listening", modifier = Modifier.size(60.dp), tint = AccentTeal)
                    }
                    if (isLive) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = formatDuration(streamDurationSecs),
                            color = AccentTeal,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }

                // Status Indicator
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(20.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(10.dp).background(if (isLive) Color.Red else Color.Yellow, CircleShape))
                    Text(
                        text = if (isLive) "LIVE STREAMING" else "CONNECTING...",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.size(100.dp).background(Color.White.copy(alpha = 0.05f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MicOff, contentDescription = "Offline", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                    }
                    Text("Stream is Offline", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tap below to start capturing ambient audio", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
            
            // Toggle Record Audio floating button & Timer
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isRecording) {
                    Text(
                        text = formatDuration(recordingDurationSecs),
                        color = Color.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                val fabInfiniteTransition = rememberInfiniteTransition()
                val scale by fabInfiniteTransition.animateFloat(
                    initialValue = 1f, targetValue = if (isRecording) 1.15f else 1f,
                    animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "fab_pulse"
                )

                FloatingActionButton(
                    onClick = {
                        if (isRecording) {
                            XshieldRepository.stopRemoteAudioRecording()
                            Toast.makeText(context, "Recording Stopped. Audio will be saved shortly.", Toast.LENGTH_SHORT).show()
                        } else {
                            XshieldRepository.startRemoteAudioRecording()
                            Toast.makeText(context, "Recording Started on target device.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.scale(scale),
                    containerColor = if (isRecording) Color.Red else RedAlert,
                    contentColor = Color.White
                ) {
                    Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = if (isRecording) "Stop Recording" else "Record Audio")
                }
            }
        }

        // Action Button
        Button(
            onClick = {
                if (isLive || isConnecting) XshieldRepository.stopAudioFeed() else XshieldRepository.requestAudioFeed()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isLive || isConnecting) RedAlert else AccentTeal),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(if (isLive || isConnecting) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(if (isLive || isConnecting) "Stop Stream" else "Start Stream", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Text("Saved Recordings", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))

        // Audio Recordings List
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            val recordings = XshieldRepository.audioRecordingsList
            if (recordings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AudioFile, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(48.dp).padding(bottom = 8.dp))
                        Text("No recordings available", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recordings) { rec ->
                        val isPlayingThis = currentlyPlayingUrl == rec.url
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Play/Pause Action
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(PrimaryBlue.copy(alpha = 0.2f), CircleShape)
                                        .clickable {
                                            if (isPlayingThis) {
                                                mediaPlayer?.stop()
                                                mediaPlayer?.release()
                                                mediaPlayer = null
                                                currentlyPlayingUrl = null
                                            } else {
                                                mediaPlayer?.release()
                                                currentlyPlayingUrl = rec.url
                                                mediaPlayer = MediaPlayer().apply {
                                                    setDataSource(rec.url)
                                                    prepareAsync()
                                                    setOnPreparedListener { 
                                                        totalPlaybackDuration = it.duration
                                                        start() 
                                                    }
                                                    setOnCompletionListener {
                                                        currentlyPlayingUrl = null
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = "Play/Stop Audio", tint = PrimaryBlue)
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                                    Text("Remote Audio Capture", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                                    Text(sdf.format(Date(rec.timestamp)), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                }
                                
                                // Download Action
                                IconButton(onClick = {
                                    val filename = Uri.parse(rec.url).lastPathSegment ?: "${rec.id}.3gp"
                                    val request = DownloadManager.Request(Uri.parse(rec.url))
                                        .setTitle("Remote Audio Capture")
                                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                                    Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Download, contentDescription = "Save", tint = AccentTeal)
                                }

                                // Delete Action
                                IconButton(onClick = {
                                    val filename = Uri.parse(rec.url).lastPathSegment ?: "${rec.id}.3gp"
                                    XshieldRepository.deleteAudioRecording(rec.id, filename)
                                    Toast.makeText(context, "Deleted Recording", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedAlert)
                                }
                            }

                            // Expanded Progress Slider
                            if (isPlayingThis) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(formatDuration((currentPlaybackPos / 1000).toLong()), color = Color.White, fontSize = 12.sp)
                                    Slider(
                                        value = currentPlaybackPos.toFloat(),
                                        onValueChange = { 
                                            currentPlaybackPos = it.toInt()
                                            mediaPlayer?.seekTo(it.toInt())
                                        },
                                        valueRange = 0f..(if (totalPlaybackDuration > 0) totalPlaybackDuration.toFloat() else 100f),
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = AccentTeal,
                                            activeTrackColor = AccentTeal,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    )
                                    Text(formatDuration((totalPlaybackDuration / 1000).toLong()), color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
