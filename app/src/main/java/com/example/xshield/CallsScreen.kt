package com.example.xshield

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xshield.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.random.Random
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

data class CallTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun CallsScreen(initialViewIsBlockNumber: Boolean = false) {
    var activeSubView by remember { mutableStateOf(if (initialViewIsBlockNumber) 2 else 0) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Bind to shared repository state
    val callsList = XshieldRepository.callsList
    val blockedList = XshieldRepository.blockedList

    val tabs = listOf(
        CallTab("History", Icons.Default.History),
        CallTab("Contacts", Icons.Default.ContactPhone),
        CallTab("Blocker", Icons.Default.Lock),
        CallTab("Analytics", Icons.Default.BarChart)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5)) // Light grayish background matching screenshot
    ) {
        // Sub View Switcher (Dark Top Bar)
        TabRow(
            selectedTabIndex = activeSubView,
            containerColor = Color(0xFF2C313C), // Dark slate matching the top app bar
            contentColor = AccentTeal,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[activeSubView]),
                    color = AccentTeal,
                    height = 3.dp
                )
            }
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = activeSubView == index
                val color = if (isSelected) Color.White else Color.Gray
                Tab(
                    selected = isSelected,
                    onClick = { activeSubView = index; searchQuery = "" },
                    text = { 
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = tab.icon, contentDescription = tab.title, tint = color, modifier = Modifier.size(20.dp))
                            Text(tab.title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = color)
                        }
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            when (activeSubView) {
                0 -> {
                    // HISTORY VIEW
                    CallsDataView(
                        callsList = callsList,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it }
                    )
                }
                1 -> {
                    // CONTACTS VIEW
                    ContactsScreen()
                }
                2 -> {
                    // BLOCKER VIEW
                    BlockNumberView(
                        blockedList = blockedList,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it }
                    )
                }
                3 -> {
                    // ANALYTICS VIEW
                    CallAnalyticsView(callsList = callsList)
                }
            }
        }
    }
}

@Composable
fun CallsDataView(
    callsList: MutableList<CallData>,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    var selectedFilterChip by remember { mutableStateOf("All") }
    val filters = listOf("All", "Incoming", "Outgoing", "Missed", "Recordings")

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search logs...", fontSize = 14.sp, color = Color.Gray) },
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

        // Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filters) { filter ->
                val isSelected = selectedFilterChip == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) AccentTeal else Color.White)
                        .border(1.dp, if (isSelected) AccentTeal else Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
                        .clickable { selectedFilterChip = filter }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) Color.White else Color.DarkGray,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Data Row List
        val filteredCalls = callsList.filter { call ->
            val matchesSearch = call.name.contains(searchQuery, ignoreCase = true) || call.number.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilterChip) {
                "Incoming" -> call.type.equals("Incoming", ignoreCase = true)
                "Outgoing" -> call.type.equals("Outgoing", ignoreCase = true)
                "Missed" -> call.type.equals("Missed", ignoreCase = true) || call.type.equals("Rejected", ignoreCase = true)
                "Recordings" -> call.hasRecording && call.audioUrl != null
                else -> true // "All"
            }
            matchesSearch && matchesFilter
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filteredCalls.isEmpty()) {
                item { EmptyTableMessage("No call logs found.") }
            } else {
                items(items = filteredCalls, key = { it.id }) { call ->
                    key(call.id) {
                        var isVisible by remember { mutableStateOf(true) }
                        AnimatedVisibility(
                            visible = isVisible,
                            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
                        ) {
                            CallRowCard(
                                call = call,
                                onDelete = {
                                    isVisible = false
                                    callsList.remove(call)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallRowCard(
    call: CallData,
    onDelete: () -> Unit
) {
    var expandedPlayer by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val blockedItem = XshieldRepository.blockedList.find { android.telephony.PhoneNumberUtils.compare(it.number, call.number) || it.number == call.number }
    val isBlocked = blockedItem != null

    val isIncoming = call.type.equals("Incoming", ignoreCase = true)
    val isOutgoing = call.type.equals("Outgoing", ignoreCase = true)
    val isMissed = call.type.equals("Missed", ignoreCase = true) || call.type.equals("Rejected", ignoreCase = true)

    val iconBgColor = when {
        isIncoming -> Color(0xFFE8F5E9)
        isOutgoing -> Color(0xFFE3F2FD)
        isMissed -> Color(0xFFFFEBEE)
        else -> Color(0xFFF5F5F5)
    }
    
    val iconColor = when {
        isIncoming -> Color(0xFF388E3C)
        isOutgoing -> Color(0xFF1976D2)
        isMissed -> Color(0xFFD32F2F)
        else -> Color(0xFF757575)
    }
    
    val iconVector = when {
        isIncoming -> Icons.Default.CallReceived
        isOutgoing -> Icons.Default.CallMade
        isMissed -> Icons.Default.CallMissed
        else -> Icons.Default.Phone
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar Icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(iconBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = iconVector, contentDescription = call.type, tint = iconColor, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Center Info Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = call.name.ifBlank { call.number },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black
                    )
                    if (call.name.isNotBlank() && call.name != call.number) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = call.number,
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                    } else if (call.name.isBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Unknown",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = call.date, fontSize = 11.sp, color = Color.Gray)
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "GPS Tracked", fontSize = 11.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Medium)
                    }
                }

                // Right Info Column
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.height(60.dp)
                ) {
                    // Top: Duration or Missed
                    val durationText = if (isMissed) "Missed" else {
                        val sec = call.duration.toIntOrNull() ?: 0
                        val min = sec / 60
                        val left = sec % 60
                        String.format("%02d:%02d", min, left)
                    }
                    val durationColor = if (isMissed) Color(0xFFD32F2F) else Color.Black
                    Text(
                        text = durationText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = durationColor
                    )

                    // Bottom: Action Icons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (call.hasRecording && call.audioUrl != null) {
                            Icon(
                                imageVector = if (expandedPlayer) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color(0xFF388E3C),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { expandedPlayer = !expandedPlayer }
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { clipboardManager.setText(AnnotatedString(call.number)) }
                        )
                        if (isBlocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Unlock",
                                tint = AccentTeal,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { XshieldRepository.removeBlockedNumber(blockedItem!!.id) }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Block",
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { XshieldRepository.addBlockedNumber(call.number, "both") }
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onDelete() }
                        )
                    }
                }
            }

            // Expandable Recording Player
            AnimatedVisibility(
                visible = expandedPlayer,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                if (call.audioUrl != null) {
                    Divider(color = Color(0xFFF0F2F5))
                    CallRecordingPlayer(audioUrl = call.audioUrl)
                }
            }
        }
    }
}

@Composable
fun CallRecordingPlayer(audioUrl: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0f) }
    var durationSec by remember { mutableStateOf(0) }
    
    // Waveform simulation
    val waves = remember { List(30) { (Random.nextFloat() * 24).dp + 4.dp } }

    val mediaPlayer = remember { android.media.MediaPlayer() }
    val isPrepared = remember { mutableStateOf(false) }

    DisposableEffect(audioUrl) {
        try {
            mediaPlayer.setDataSource(audioUrl)
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener {
                durationSec = it.duration / 1000
                isPrepared.value = true
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                currentProgress = 0f
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
            if (mediaPlayer.isPlaying) mediaPlayer.stop()
            mediaPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying && isPrepared.value) {
            mediaPlayer.start()
            while (isPlaying && mediaPlayer.isPlaying) {
                delay(100)
                val dur = mediaPlayer.duration
                if (dur > 0) {
                    currentProgress = mediaPlayer.currentPosition.toFloat() / dur.toFloat()
                }
            }
        } else if (!isPlaying && isPrepared.value && mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundGrey.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier
                    .size(36.dp)
                    .background(PlayWaveColor, RoundedCornerShape(18.dp))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
            }

            // Dynamic wave visualization
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                waves.forEachIndexed { index, height ->
                    val isPast = (index.toFloat() / waves.size) <= currentProgress
                    val color = if (isPast) PlayWaveColor else LightText.copy(alpha = 0.3f)
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(height)
                            .background(color, RoundedCornerShape(1.5.dp))
                    )
                }
            }

            // Playback progress display
            val elapsed = if (durationSec > 0) (currentProgress * durationSec).toInt() else 0
            val formatElapsed = String.format("%02d:%02d", elapsed / 60, elapsed % 60)
            val formatTotal = String.format("%02d:%02d", durationSec / 60, durationSec % 60)
            
            if (isPrepared.value) {
                Text(
                    text = "$formatElapsed / $formatTotal",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PlayWaveColor, strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
fun BlockNumberView(
    blockedList: MutableList<BlockedNumber>,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    var inputNumber by remember { mutableStateOf("") }
    var selectedBlockType by remember { mutableStateOf("incoming") }
    var expandedDropdown by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Global Block Toggles
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteSurface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "GLOBAL BLOCK SETTINGS",
                color = AccentTeal,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Block All Incoming Calls", color = DarkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                androidx.compose.material3.Switch(
                    checked = XshieldRepository.blockAllIncoming.value,
                    onCheckedChange = { 
                        XshieldRepository.toggleGlobalCallBlocking(it, XshieldRepository.blockAllOutgoing.value) 
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = AccentTeal)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Block All Outgoing Calls", color = DarkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                androidx.compose.material3.Switch(
                    checked = XshieldRepository.blockAllOutgoing.value,
                    onCheckedChange = { 
                        XshieldRepository.toggleGlobalCallBlocking(XshieldRepository.blockAllIncoming.value, it) 
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = AccentTeal)
                )
            }
        }
    }

    // Input area
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WhiteSurface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "BLOCK A NUMBER",
                color = AccentTeal,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputNumber,
                    onValueChange = { inputNumber = it },
                    placeholder = { Text("Example: 631 913 748") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = BorderColor
                    ),
                    shape = RoundedCornerShape(4.dp),
                    singleLine = true
                )

                // Select block type dropdown
                Box(modifier = Modifier.width(130.dp)) {
                    OutlinedButton(
                        onClick = { expandedDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedBlockType, fontSize = 13.sp, color = DarkText)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = LightText)
                        }
                    }
                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("incoming") },
                            onClick = { selectedBlockType = "incoming"; expandedDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("outgoing") },
                            onClick = { selectedBlockType = "outgoing"; expandedDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("both") },
                            onClick = { selectedBlockType = "both"; expandedDropdown = false }
                        )
                    }
                }

                // Add button
                Button(
                    onClick = {
                        if (inputNumber.isNotBlank()) {
                            XshieldRepository.addBlockedNumber(inputNumber, selectedBlockType)
                            inputNumber = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("+ ADD", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    // Search bar for blocked list
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Search blocked numbers...", fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LightText) },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(WhiteSurface, RoundedCornerShape(8.dp)),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentTeal,
            unfocusedBorderColor = BorderColor
        ),
        shape = RoundedCornerShape(8.dp),
        singleLine = true
    )

    // Blocked numbers list panel
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
                Column(modifier = Modifier.width(600.dp)) {
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
                        Text(text = "Number", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(180.dp))
                        Text(text = "Type", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center)
                        Text(text = "Date", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(140.dp))
                        Text(text = "Blocked", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp), textAlign = TextAlign.Center)
                    }

                    // Blocked numbers data
                    val filteredBlocked = blockedList.filter {
                        it.number.contains(searchQuery)
                    }

                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (filteredBlocked.isEmpty()) {
                            item { EmptyTableMessage("No blocked numbers.") }
                        } else {
                            items(items = filteredBlocked, key = { it.id.takeIf { id -> id.isNotEmpty() } ?: it.number }) { item ->
                                key(item.id) {
                                    var isVisible by remember { mutableStateOf(true) }
                                    AnimatedVisibility(
                                        visible = isVisible,
                                        exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
                                    ) {
                                        BlockedRow(
                                            item = item,
                                            onDelete = {
                                                isVisible = false
                                                XshieldRepository.removeBlockedNumber(item.id)
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
    }
}
}

@Composable
fun BlockedRow(
    item: BlockedNumber,
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
        // Unlock button
        Box(
            modifier = Modifier
                .width(50.dp)
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)), // Light Teal
                border = BorderStroke(0.5.dp, AccentTeal.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "Unlock",
                    tint = AccentTeal,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(4.dp)
                )
            }
        }

        // Number and Name mapping
        val contactMatch = XshieldRepository.contactsList.find { 
            android.telephony.PhoneNumberUtils.compare(it.second, item.number) 
        }
        val displayStr = if (contactMatch != null) {
            "${contactMatch.first} (${item.number})"
        } else {
            item.number
        }
        Text(text = displayStr, color = DarkText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(180.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        // Type
        Text(text = item.type, color = DarkText, fontSize = 13.sp, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center)
        // Date
        Text(text = item.date, color = DarkText, fontSize = 13.sp, modifier = Modifier.width(140.dp))
        // Blocked status
        Box(
            modifier = Modifier.width(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "Blocked",
                tint = RedAlert,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
