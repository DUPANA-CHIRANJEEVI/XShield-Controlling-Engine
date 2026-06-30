package com.example.xshield

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.clickable

data class ChatGroup(val app: String, val sender: String)

fun getAppName(appPackage: String): String {
    return when(appPackage) {
        "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
        "com.instagram.android" -> "Instagram"
        "org.telegram.messenger" -> "Telegram"
        "com.snapchat.android" -> "Snapchat"
        "com.facebook.katana", "com.facebook.orca" -> "Facebook"
        "com.twitter.android" -> "X (Twitter)"
        "com.google.android.youtube" -> "YouTube"
        else -> appPackage
    }
}

fun getAppIconColor(appName: String): Color {
    return when(appName) {
        "WhatsApp" -> Color(0xFF25D366)
        "Instagram" -> Color(0xFFE1306C)
        "Telegram" -> Color(0xFF0088CC)
        "Snapchat" -> Color(0xFFFFFC00)
        "Facebook" -> Color(0xFF1877F2)
        "X (Twitter)" -> Color.Black
        "YouTube" -> Color(0xFFFF0000)
        else -> Color.Gray
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstantMessagingScreen(onBack: () -> Unit) {
    val messages = XshieldRepository.instantMessages.collectAsState().value
    val config = XshieldRepository.imConfig.collectAsState().value
    val deviceStatus = XshieldRepository.deviceOnlineStatus.value

    var currentChat by remember { mutableStateOf<ChatGroup?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = { 
                    val titleText = if (currentChat == null) "Instant messaging" else "${getAppName(currentChat!!.app)} - ${currentChat!!.sender}"
                    Text(titleText, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentChat != null) {
                            currentChat = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (currentChat == null) {
                        Text(
                            text = if (deviceStatus == "online") "Live Sync Active" else "Synced: ${XshieldRepository.lastSyncTime.value}",
                            color = Color(0xFF00A8B5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        IconButton(onClick = { XshieldRepository.clearInstantMessages() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear all", tint = Color(0xFFFF5252))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2C313C)
                )
            )
        },
        containerColor = if (currentChat == null) Color(0xFF1E2124) else Color(0xFFE5DDD5) // WhatsApp background color for chat thread
    ) { paddingValues ->
        var isRefreshing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    delay(1000)
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(if (currentChat == null) Color(0xFFF0F2F5) else Color(0xFFE5DDD5))
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentChat == null) {
                    item { MessagingHeaderCard() }
                    item { AppSelectionPanel(config) }

                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No messages captured yet.", color = Color.Gray, fontSize = 16.sp)
                            }
                        }
                    } else {
                        // Group messages by App and Sender
                        val grouped = messages.groupBy { 
                            ChatGroup(it.app, if (it.sender == "Me" || it.sender.isBlank()) "Unknown Contact" else it.sender) 
                        }
                        val sortedGroups = grouped.mapValues { it.value.maxByOrNull { msg -> msg.timestamp }!! }
                            .toList()
                            .sortedByDescending { it.second.timestamp }

                        items(items = sortedGroups, key = { "${it.first.app}_${it.first.sender}" }) { (group, lastMsg) ->
                            ChatListCard(group, lastMsg) {
                                currentChat = group
                            }
                        }
                    }
                } else {
                    // Chat Thread View
                    val threadMessages = messages.filter { 
                        it.app == currentChat!!.app && 
                        (if (it.sender == "Me" || it.sender.isBlank()) "Unknown Contact" else it.sender) == currentChat!!.sender 
                    }.sortedBy { it.timestamp }

                    items(items = threadMessages, key = { it.id }) { msg ->
                        ChatBubble(msg)
                    }
                }
            }
        }
    }
}

@Composable
fun MessagingHeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Social Messaging Chats",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1E2124)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tracks target application notifications logs for Whatsapp, Facebook, Discord etc.",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun AppSelectionPanel(config: Map<String, Boolean>) {
    val apps = listOf(
        "whatsapp" to "WhatsApp",
        "instagram" to "Instagram",
        "telegram" to "Telegram",
        "snapchat" to "Snapchat",
        "facebook" to "Facebook",
        "x" to "X (Twitter)",
        "youtube" to "YouTube"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monitored Applications",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFF1E2124)
            )
            Spacer(modifier = Modifier.height(8.dp))

            apps.forEach { (key, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = name, fontSize = 15.sp, color = Color.DarkGray)
                    Switch(
                        checked = config[key] ?: false,
                        onCheckedChange = { isChecked ->
                            XshieldRepository.updateImConfig(key, isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00A8B5),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.LightGray
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ChatListCard(group: ChatGroup, lastMsg: InstantMessage, onClick: () -> Unit) {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = if (lastMsg.timestamp > 0) sdf.format(Date(lastMsg.timestamp)) else ""
    val appName = getAppName(group.app)
    val iconColor = getAppIconColor(appName)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(iconColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = appName.take(1),
                    color = if (appName == "Snapchat") Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.sender,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = dateStr, color = Color.Gray, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                val prefix = if (lastMsg.direction == "OUTGOING") "You: " else ""
                Text(
                    text = "$prefix${lastMsg.message}",
                    color = Color.DarkGray,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ChatBubble(msg: InstantMessage) {
    val isOutgoing = msg.direction == "OUTGOING"
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = if (msg.timestamp > 0) sdf.format(Date(msg.timestamp)) else ""
    
    val bubbleColor = if (isOutgoing) Color(0xFFDCF8C6) else Color.White
    val textColor = Color.Black
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOutgoing) 16.dp else 4.dp,
                bottomEnd = if (isOutgoing) 4.dp else 16.dp
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(text = msg.message, color = textColor, fontSize = 15.sp)
                Text(
                    text = timeStr,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                )
            }
        }
    }
}
