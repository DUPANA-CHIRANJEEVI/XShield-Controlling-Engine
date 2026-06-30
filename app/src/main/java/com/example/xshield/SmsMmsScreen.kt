package com.example.xshield

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xshield.ui.theme.*

@Composable
fun SmsMmsScreen(initialTabIsMms: Boolean = false) {
    var activeTab by remember { mutableStateOf(if (initialTabIsMms) 1 else 0) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Bind to shared repository state
    val smsList = XshieldRepository.smsList
    val mmsList = XshieldRepository.mmsList
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundGrey)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tab Selector (SMS vs MMS)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WhiteSurface),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = WhiteSurface,
                    contentColor = AccentTeal
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0; searchQuery = "" },
                        text = { Text("SMS Logs", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1; searchQuery = "" },
                        text = { Text("MMS Logs", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Controls bar: Search & Filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search logs...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LightText) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .background(WhiteSurface, RoundedCornerShape(8.dp)),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = BorderColor
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )

                IconButton(
                    onClick = {
                        val deviceId = XshieldRepository.selectedDevice.value
                        if (deviceId.isNotEmpty()) {
                            XshieldRepository.triggerSmsSync(deviceId)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Refresh command sent to device...")
                            }
                        }
                    },
                    modifier = Modifier
                        .size(50.dp)
                        .background(WhiteSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AccentTeal)
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
                    
                    // Data lists calculated outside LazyListScope
                    val filteredSms = smsList.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                        it.number.contains(searchQuery, ignoreCase = true) ||
                        it.message.contains(searchQuery, ignoreCase = true)
                    }

                    val filteredMms = mmsList.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                        it.number.contains(searchQuery, ignoreCase = true) ||
                        it.message.contains(searchQuery, ignoreCase = true) ||
                        it.subject.contains(searchQuery, ignoreCase = true)
                    }

                    // Horizontal scrolling container representing table columns
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        LazyColumn(modifier = Modifier.width(750.dp)) {
                            item {
                                // Table Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BackgroundGrey)
                                        .border(1.dp, BorderColor)
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "", modifier = Modifier.width(40.dp)) // Action col
                                    Text(text = "Type", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                                    Text(text = "Name", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp))
                                    if (activeTab == 0) {
                                        Text(text = "Message", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(220.dp))
                                    } else {
                                        Text(text = "Contents", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                                        Text(text = "Message/Subject", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp))
                                    }
                                    Text(text = "Number", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp))
                                    Text(text = "Date", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp))
                                    Text(text = "Address", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                                }
                            }

                            if (activeTab == 0) {
                                if (filteredSms.isEmpty()) {
                                    item { EmptyTableMessage("No SMS data found.") }
                                } else {
                                    items(filteredSms, key = { it.id }) { sms ->
                                        SmsRow(
                                            sms = sms,
                                            onDelete = {
                                                XshieldRepository.deleteSms(XshieldRepository.selectedDevice.value, sms.id)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("SMS deleted.")
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                                if (filteredMms.isEmpty()) {
                                    item { EmptyTableMessage("No MMS data found.") }
                                } else {
                                    items(filteredMms, key = { it.id }) { mms ->
                                        MmsRow(
                                            mms = mms,
                                            onDelete = {
                                                // mmsList.remove(mms) 
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } // Close the outer Column

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    } // Close the Box
}

@Composable
fun EmptyTableMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = LightText, fontSize = 14.sp)
    }
}

@Composable
fun SmsRow(
    sms: SmsData,
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
                .width(40.dp)
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
                    contentDescription = "Delete",
                    tint = RedAlert,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(4.dp)
                )
            }
        }

        // Type
        Box(
            modifier = Modifier.width(80.dp),
            contentAlignment = Alignment.Center
        ) {
            val bg = if (sms.type == "Incoming") Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
            val fg = if (sms.type == "Incoming") Color(0xFF2E7D32) else Color(0xFF1565C0)
            Card(
                colors = CardDefaults.cardColors(containerColor = bg),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = sms.type,
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Name
        Text(text = sms.name, color = DarkText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp))
        // Message
        Text(text = sms.message, color = DarkText, fontSize = 13.sp, modifier = Modifier.width(220.dp))
        // Number
        Text(text = sms.number, color = DarkText, fontSize = 13.sp, modifier = Modifier.width(120.dp))
        // Date
        Text(text = sms.date, color = DarkText, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        // Address
        Text(
            text = sms.address,
            color = PrimaryBlue,
            fontSize = 13.sp,
            modifier = Modifier.width(80.dp)
        )
    }
}

@Composable
fun MmsRow(
    mms: MmsData,
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
                .width(40.dp)
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
                    contentDescription = "Delete",
                    tint = RedAlert,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(4.dp)
                )
            }
        }

        // Type
        Box(
            modifier = Modifier.width(80.dp),
            contentAlignment = Alignment.Center
        ) {
            val bg = if (mms.type == "Incoming") Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
            val fg = if (mms.type == "Incoming") Color(0xFF2E7D32) else Color(0xFF1565C0)
            Card(
                colors = CardDefaults.cardColors(containerColor = bg),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = mms.type,
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Contents
        Text(text = mms.contents, color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
        // Name
        Text(text = mms.name, color = DarkText, fontSize = 13.sp, modifier = Modifier.width(130.dp))
        // Message / Subject
        Text(text = "${mms.subject}\n${mms.message}", color = DarkText, fontSize = 12.sp, modifier = Modifier.width(120.dp), maxLines = 2)
        // Number
        Text(text = mms.number, color = DarkText, fontSize = 13.sp, modifier = Modifier.width(120.dp))
        // Date
        Text(text = mms.date, color = DarkText, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        // Address
        Text(
            text = mms.address,
            color = PrimaryBlue,
            fontSize = 13.sp,
            modifier = Modifier.width(80.dp)
        )
    }
}
