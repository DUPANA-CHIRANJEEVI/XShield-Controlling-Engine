package com.example.xshield

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.example.xshield.ui.theme.*


import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicturesScreen() {
    // Use shared repository list
    val picturesList = XshieldRepository.picturesList

    var selectedPictureForView by remember { mutableStateOf<PictureData?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)

    val filteredPictures = remember(picturesList.toList(), searchQuery, selectedDateMillis) {
        picturesList.filter { pic ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                pic.address.contains(searchQuery, ignoreCase = true) ||
                pic.path.contains(searchQuery, ignoreCase = true) ||
                pic.info.contains(searchQuery, ignoreCase = true)
            }
            val matchesDate = if (selectedDateMillis == null) true else {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val selectedDateStr = sdf.format(Date(selectedDateMillis!!))
                pic.date.startsWith(selectedDateStr)
            }
            matchesSearch && matchesDate
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search & Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search pictures...", color = LightText) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = PrimaryBlue) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = LightText)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = WhiteSurface,
                    unfocusedContainerColor = WhiteSurface
                ),
                shape = RoundedCornerShape(8.dp)
            )

            val isDateFiltered = selectedDateMillis != null
            Button(
                onClick = { showDatePicker = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDateFiltered) PrimaryBlue else WhiteSurface,
                    contentColor = if (isDateFiltered) Color.White else DarkText
                ),
                shape = RoundedCornerShape(8.dp),
                border = if (!isDateFiltered) BorderStroke(1.dp, BorderColor) else null,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = "Calendar")
                Spacer(modifier = Modifier.width(8.dp))
                if (isDateFiltered) {
                    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                    Text(sdf.format(Date(selectedDateMillis!!)))
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { 
                            selectedDateMillis = null
                            datePickerState.selectedDateMillis = null
                        },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Date", modifier = Modifier.size(14.dp))
                    }
                } else {
                    Text("Filter Date")
                }
            }
        }

        // Table Panel Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = WhiteSurface),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val horizontalScrollState = rememberScrollState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                ) {
                    LazyColumn(modifier = Modifier.width(620.dp)) {
                        item {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BackgroundGrey)
                                    .border(1.dp, BorderColor)
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "", modifier = Modifier.width(50.dp)) // Action col
                                Text(text = "Picture", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp), textAlign = TextAlign.Center)
                                Text(text = "Date", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(160.dp))
                                Text(text = "Information", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(170.dp))
                                Text(text = "Address", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp))
                            }
                        }

                        // Data list
                        if (filteredPictures.isEmpty()) {
                            item {
                                EmptyTableMessage("No pictures found.")
                            }
                        } else {
                            items(filteredPictures, key = { it.id }) { pic ->
                                PictureRow(
                                    pic = pic,
                                    onView = { selectedPictureForView = pic },
                                    onDelete = {
                                        picturesList.remove(pic)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Full Screen Zoom Dialog
    selectedPictureForView?.let { initialPic ->
        val currentPic = picturesList.find { it.id == initialPic.id } ?: initialPic

        Dialog(onDismissRequest = { selectedPictureForView = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WhiteSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Picture Telemetry Viewer",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        IconButton(onClick = { selectedPictureForView = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = LightText)
                        }
                    }

                    var isAwaitingDownload by remember { mutableStateOf(false) }
                    val context = androidx.compose.ui.platform.LocalContext.current

                    LaunchedEffect(currentPic.downloadUrl) {
                        if (isAwaitingDownload && currentPic.downloadUrl != null) {
                            isAwaitingDownload = false
                            val request = android.app.DownloadManager.Request(currentPic.downloadUrl.toUri())
                                .setTitle(java.io.File(currentPic.path).name)
                                .setDescription("Downloading full HD image")
                                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "Xshield/" + java.io.File(currentPic.path).name)
                            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            dm.enqueue(request)
                            android.widget.Toast.makeText(context, "HD Image saved to Downloads/Xshield!", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }

                    // Simulated Picture Drawing Canvas OR Real Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(BackgroundGrey, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentPic.previewUrl != null) {
                            AsyncImage(
                                model = currentPic.previewUrl,
                                contentDescription = "Preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // If no preview yet, show a loader and request it
                            LaunchedEffect(currentPic.id) {
                                XshieldRepository.requestPicturePreview(currentPic.id, currentPic.path)
                            }
                            CircularProgressIndicator(color = PrimaryBlue)
                        }
                    }

                    // Details
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailItemRow("Capture Date", currentPic.date)
                        DetailItemRow("Resolution / Size", currentPic.info)
                        DetailItemRow("GPS Location Address", currentPic.address)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(color = BorderColor)

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                if (currentPic.downloadUrl != null) {
                                    // Download locally to parent phone
                                    val request = android.app.DownloadManager.Request(currentPic.downloadUrl.toUri())
                                        .setTitle(java.io.File(currentPic.path).name)
                                        .setDescription("Downloading full HD image")
                                        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "Xshield/" + java.io.File(currentPic.path).name)
                                    val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                    dm.enqueue(request)
                                    android.widget.Toast.makeText(context, "Downloading to Downloads/Xshield...", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    // Request child to upload it
                                    isAwaitingDownload = true
                                    XshieldRepository.requestFullPicture(currentPic.id, currentPic.path)
                                    android.widget.Toast.makeText(context, "Requesting HD upload from target...", android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isAwaitingDownload,
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            if (isAwaitingDownload) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PrimaryBlue, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetching HD...", color = LightText)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, tint = LightText)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", color = LightText)
                            }
                        }

                        Button(
                            onClick = {
                                picturesList.remove(currentPic)
                                selectedPictureForView = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = RedAlert),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Log", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PictureRow(
    pic: PictureData,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, BorderColor)
            .background(WhiteSurface)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Delete button
        Box(
            modifier = Modifier
                .width(50.dp)
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = LightRedBg),
                border = BorderStroke(0.5.dp, RedAlert.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Picture Log",
                    tint = RedAlert,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(4.dp)
                )
            }
        }

        // Picture Preview Thumbnail
        Card(
            modifier = Modifier
                .width(100.dp)
                .height(50.dp)
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = pic.seedColor),
            onClick = { onView() }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (pic.previewUrl != null) {
                    AsyncImage(
                        model = pic.previewUrl,
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f),
                            radius = 12.dp.toPx(),
                            center = Offset(size.width * 0.5f, size.height * 0.5f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "Placeholder",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }


        // Date
        Text(text = pic.date, color = DarkText, fontSize = 13.sp, modifier = Modifier.width(160.dp))
        // Informations
        Text(text = pic.info, color = DarkText, fontSize = 13.sp, modifier = Modifier.width(170.dp))
        // Address
        Text(
            text = pic.address,
            color = PrimaryBlue,
            fontSize = 13.sp,
            modifier = Modifier.width(120.dp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DetailItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = LightText, fontSize = 13.sp)
        Text(text = value, color = DarkText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
