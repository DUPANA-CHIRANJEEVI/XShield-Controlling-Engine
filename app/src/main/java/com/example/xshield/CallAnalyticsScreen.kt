package com.example.xshield

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xshield.ui.theme.*

data class ContactCallStat(
    val name: String,
    val number: String,
    val count: Int,
    val totalDuration: Int
)

@Composable
fun CallAnalyticsView(callsList: List<CallData>) {
    val scrollState = rememberScrollState()

    // Aggregation & Statistics Calculations
    val totalCalls = callsList.size
    val incomingCalls = callsList.count { it.type.equals("Incoming", ignoreCase = true) }
    val outgoingCalls = callsList.count { it.type.equals("Outgoing", ignoreCase = true) }
    val missedCalls = callsList.count { it.type.equals("Missed", ignoreCase = true) || it.type.equals("Rejected", ignoreCase = true) }

    val totalDurationSeconds = callsList.sumOf { it.duration.toIntOrNull() ?: 0 }
    val avgDurationSeconds = if (totalCalls > 0) totalDurationSeconds / totalCalls else 0
    val missedRate = if (totalCalls > 0) (missedCalls.toFloat() / totalCalls * 100).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Grid of KPI Cards
        AnalyticsKpiGrid(totalCalls, totalDurationSeconds, avgDurationSeconds, missedRate)

        // 2. Donut / Pie Chart Card
        CallTypeDistributionCard(incomingCalls, outgoingCalls, missedCalls)

        // 3. Most Contacted Card with Name, Number, and Call Volume Graph
        MostContactedCard(callsList)
    }
}

@Composable
fun AnalyticsKpiGrid(
    totalCalls: Int,
    totalDuration: Int,
    avgDuration: Int,
    missedRate: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiCard(
                title = "Total Calls",
                value = totalCalls.toString(),
                subtitle = "Logged calls",
                icon = Icons.Default.Phone,
                iconColor = Color(0xFF1976D2),
                iconBg = Color(0xFFE3F2FD),
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Talk Time",
                value = formatTalkTime(totalDuration),
                subtitle = "Total duration",
                icon = Icons.Default.HourglassEmpty,
                iconColor = Color(0xFF388E3C),
                iconBg = Color(0xFFE8F5E9),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiCard(
                title = "Avg Call",
                value = formatCallDuration(avgDuration),
                subtitle = "Per connection",
                icon = Icons.Default.AccessTime,
                iconColor = Color(0xFFE65100),
                iconBg = Color(0xFFFFF3E0),
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Missed Rate",
                value = "$missedRate%",
                subtitle = "Rejected / missed",
                icon = Icons.Default.CallMissed,
                iconColor = Color(0xFFD32F2F),
                iconBg = Color(0xFFFFEBEE),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(iconBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                }
            }
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(subtitle, fontSize = 10.sp, color = Color.LightGray)
        }
    }
}

@Composable
fun CallTypeDistributionCard(
    incoming: Int,
    outgoing: Int,
    missed: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Call Distribution", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(16.dp))

            val total = incoming + outgoing + missed
            if (total == 0) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No calls recorded yet.", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Custom Donut Chart on Canvas
                    Box(
                        modifier = Modifier.size(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 32f
                            
                            val incomingAngle = (incoming.toFloat() / total) * 360f
                            val outgoingAngle = (outgoing.toFloat() / total) * 360f
                            val missedAngle = (missed.toFloat() / total) * 360f

                            var startAngle = -90f

                            // Incoming (Teal)
                            if (incomingAngle > 0f) {
                                drawArc(
                                    color = Color(0xFF00A8B5),
                                    startAngle = startAngle,
                                    sweepAngle = incomingAngle,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                startAngle += incomingAngle
                            }

                            // Outgoing (Blue)
                            if (outgoingAngle > 0f) {
                                drawArc(
                                    color = Color(0xFF1976D2),
                                    startAngle = startAngle,
                                    sweepAngle = outgoingAngle,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                startAngle += outgoingAngle
                            }

                            // Missed (Red)
                            if (missedAngle > 0f) {
                                drawArc(
                                    color = Color(0xFFD32F2F),
                                    startAngle = startAngle,
                                    sweepAngle = missedAngle,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(total.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            Text("Calls", fontSize = 10.sp, color = Color.Gray)
                        }
                    }

                    // Legend Panel
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LegendRow(color = Color(0xFF00A8B5), label = "Incoming", count = incoming, percent = (incoming * 100 / total))
                        LegendRow(color = Color(0xFF1976D2), label = "Outgoing", count = outgoing, percent = (outgoing * 100 / total))
                        LegendRow(color = Color(0xFFD32F2F), label = "Missed", count = missed, percent = (missed * 100 / total))
                    }
                }
            }
        }
    }
}

@Composable
fun LegendRow(color: Color, label: String, count: Int, percent: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Column {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text("$count calls ($percent%)", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
fun MostContactedCard(callsList: List<CallData>) {
    val contactStats = remember(callsList) {
        callsList.groupBy { it.number }
            .map { (number, list) ->
                val name = list.firstOrNull { it.name.isNotBlank() }?.name ?: "Unknown"
                val count = list.size
                val totalDuration = list.sumOf { it.duration.toIntOrNull() ?: 0 }
                ContactCallStat(name, number, count, totalDuration)
            }
            .sortedByDescending { it.count }
            .take(5)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Top Contacted Leaderboard", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(12.dp))

            val maxCount = contactStats.firstOrNull()?.count ?: 1

            if (contactStats.isEmpty()) {
                Text("No calls recorded yet.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            } else {
                contactStats.forEach { stat ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(stat.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                                Text(stat.number, fontSize = 11.sp, color = Color.Gray)
                            }
                            Text("${stat.count} calls (${stat.totalDuration / 60}m)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF00A8B5))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Horizontal progress bar graph indicating relative call volume
                        val progress = stat.count.toFloat() / maxCount
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF00A8B5),
                            trackColor = Color(0xFFF0F2F5)
                        )
                    }
                }
            }
        }
    }
}

// Helper methods to format durations nicely
private fun formatTalkTime(totalSeconds: Int): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    return when {
        hrs > 0 -> "${hrs}h ${mins}m"
        mins > 0 -> "${mins}m"
        else -> "${totalSeconds}s"
    }
}

private fun formatCallDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}
