package com.example.xshield

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xshield.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteWebScreen(onBack: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val webHistoryList = XshieldRepository.webHistoryList
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    val filteredHistory = webHistoryList.filter { item ->
        item.title.contains(searchQuery, ignoreCase = true) || 
        item.url.contains(searchQuery, ignoreCase = true) ||
        item.browser.contains(searchQuery, ignoreCase = true)
    }

    // Chronological grouping
    val groupedHistory = remember(filteredHistory) {
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdfDate.format(Date(now))
        val yesterdayStr = sdfDate.format(Date(now - oneDayMs))

        filteredHistory.groupBy { item ->
            val itemDateStr = sdfDate.format(Date(item.timestamp))
            when (itemDateStr) {
                todayStr -> "Today"
                yesterdayStr -> "Yesterday"
                else -> {
                    val sdfDisplay = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                    sdfDisplay.format(Date(item.timestamp))
                }
            }
        }
    }

    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5))
    ) {
        // Toolbar
        TopAppBar(
            windowInsets = WindowInsets(0.dp),
            title = {
                Text(
                    text = "Site Web logs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            actions = {
                if (webHistoryList.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs", tint = Color.White)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2C313C))
        )

        // Confirm Dialog
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Clear Web History", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to clear all visited site web logs? This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            XshieldRepository.clearWebHistory()
                            showClearConfirm = false
                        }
                    ) {
                        Text("CLEAR", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("CANCEL", color = Color.Gray)
                    }
                }
            )
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    val deviceId = XshieldRepository.selectedDevice.value
                    if (deviceId.isNotBlank()) {
                        com.google.firebase.database.FirebaseDatabase.getInstance()
                            .getReference("commands/$deviceId/syncSms")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stats Panel
            if (webHistoryList.isNotEmpty()) {
                val uniqueDomainsCount = webHistoryList.map { it.title.substringBefore("/") }.distinct().size
                val mostUsedBrowser = webHistoryList.groupBy { it.browser }
                    .maxByOrNull { it.value.size }?.key ?: "Chrome"
                val cleanBrowserName = mostUsedBrowser.substringAfterLast(".").uppercase()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Visited", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Text("${webHistoryList.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentTeal)
                        }
                        Divider(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFFE0E0E0)))
                        Column {
                            Text("Unique Domains", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Text("$uniqueDomainsCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                        Divider(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFFE0E0E0)))
                        Column {
                            Text("Active Browser", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (cleanBrowserName.length > 8) cleanBrowserName.take(8) + ".." else cleanBrowserName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search browsing history...", fontSize = 14.sp, color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentTeal) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Color.White, RoundedCornerShape(8.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            // History List
            if (filteredHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matches found." else "No web logs captured yet.",
                            color = Color.Gray,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedHistory.forEach { (dateGroup, items) ->
                        item {
                            Text(
                                text = dateGroup.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                            )
                        }

                        items(items, key = { it.timestamp.toString() + "_" + it.url }) { item ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    if (dismissValue == SwipeToDismissBoxValue.StartToEnd || dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                        XshieldRepository.deleteWebHistoryItem(item)
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val direction = dismissState.dismissDirection
                                    val color = if (direction != null) Color(0xFFD32F2F) else Color.Transparent
                                    val alignment = when (direction) {
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                        else -> Alignment.Center
                                    }
                                    val iconPadding = when (direction) {
                                        SwipeToDismissBoxValue.StartToEnd -> Modifier.padding(start = 16.dp)
                                        SwipeToDismissBoxValue.EndToStart -> Modifier.padding(end = 16.dp)
                                        else -> Modifier
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(color),
                                        contentAlignment = alignment
                                    ) {
                                        if (direction != null) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.White,
                                                modifier = iconPadding.size(24.dp)
                                            )
                                        }
                                    }
                                },
                                content = {
                                    WebLogItemRow(
                                        item = item,
                                        onUrlClick = {
                                            try {
                                                val fullUrl = if (!item.url.startsWith("http://") && !item.url.startsWith("https://")) {
                                                    "https://" + item.url
                                                } else {
                                                    item.url
                                                }
                                                uriHandler.openUri(fullUrl)
                                            } catch (e: Exception) {
                                                // Ignore malformed URIs
                                            }
                                        },
                                        onCopyClick = {
                                            clipboardManager.setText(AnnotatedString(item.url))
                                        },
                                        onDeleteClick = {
                                            XshieldRepository.deleteWebHistoryItem(item)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun WebLogItemRow(
    item: WebHistoryData,
    onUrlClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val cleanBrowser = item.browser.substringAfterLast(".").lowercase()
    val (iconVector, iconBgColor, iconColor) = when {
        cleanBrowser.contains("chrome") -> Triple(Icons.Default.Language, Color(0xFFFFF9C4), Color(0xFFFBC02D))
        cleanBrowser.contains("firefox") -> Triple(Icons.Default.Explore, Color(0xFFFFE0B2), Color(0xFFF57C00))
        cleanBrowser.contains("opera") -> Triple(Icons.Default.Explore, Color(0xFFFFEBEE), Color(0xFFD32F2F))
        cleanBrowser.contains("sbrowser") || cleanBrowser.contains("samsung") -> Triple(Icons.Default.Cloud, Color(0xFFE8EAF6), Color(0xFF3F51B5))
        cleanBrowser.contains("edge") -> Triple(Icons.Default.Web, Color(0xFFE1F5FE), Color(0xFF0288D1))
        cleanBrowser.contains("brave") -> Triple(Icons.Default.Shield, Color(0xFFFBE9E7), Color(0xFFD84315))
        else -> Triple(Icons.Default.Public, Color(0xFFE0F2F1), Color(0xFF00796B))
    }

    val timeStr = remember(item.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUrlClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = item.browser,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.url,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cleanBrowser.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = iconColor,
                        modifier = Modifier
                            .background(iconBgColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCopyClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Link",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Log",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
