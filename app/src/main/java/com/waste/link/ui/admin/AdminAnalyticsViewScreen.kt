package com.waste.link.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun AdminAnalyticsViewScreen(
    modifier: Modifier = Modifier,
    viewModel: AdminMapViewModel = viewModel()
) {
    val requests by viewModel.requests.collectAsState()
    
    // Aggregations
    val totalRequests = requests.size.takeIf { it > 0 } ?: 1 // Avoid divide by zero
    val pendingCount = requests.count { it.status == "PENDING" }
    val assignedCount = requests.count { it.status == "ASSIGNED" }
    val inTransitCount = requests.count { it.status == "IN_TRANSIT" }
    val collectedCount = requests.count { it.status == "COMPLETED" || it.status == "COLLECTED" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Analytics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = { /*TODO*/ }) {
                Text("Export CSV", style = MaterialTheme.typography.labelSmall)
            }
        }
        
        HorizontalDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // KPI Cards
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard(label = "Today", value = "${requests.size}", delta = "+4", color = Color(0xFF1A6B38), modifier = Modifier.weight(1f))
                KpiCard(label = "This Week", value = "142", delta = "+18", color = Color(0xFF1D4ED8), modifier = Modifier.weight(1f))
                KpiCard(label = "Avg. Hrs", value = "3.2h", delta = "-0.4", color = Color(0xFF15803D), modifier = Modifier.weight(1f))
            }

            // Status Breakdown
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "STATUS BREAKDOWN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                StatusBar(label = "Pending", count = pendingCount, total = totalRequests, color = Color(0xFFF59E0B))
                StatusBar(label = "Assigned", count = assignedCount, total = totalRequests, color = Color(0xFF3B82F6))
                StatusBar(label = "In Transit", count = inTransitCount, total = totalRequests, color = Color(0xFFF97316))
                StatusBar(label = "Collected", count = collectedCount, total = totalRequests, color = Color(0xFF10B981))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, delta: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = delta, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StatusBar(label: String, count: Int, total: Int, color: Color) {
    val pct = (count.toFloat() / total)
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
            Box(modifier = Modifier.fillMaxWidth(pct).height(6.dp).background(color, RoundedCornerShape(3.dp)))
        }
    }
}
