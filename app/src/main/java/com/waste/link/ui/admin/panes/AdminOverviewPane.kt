package com.waste.link.ui.admin.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waste.link.ui.admin.ActivityLogItem
import com.waste.link.ui.admin.AdminDashboardViewModel
import com.waste.link.ui.admin.components.DashboardMetricCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminOverviewPane(viewModel: AdminDashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        
        // Anomaly Banner
        if (uiState.hasAnomaly) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("System Anomaly Detected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("High number of active disputes requiring immediate attention.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // Date Range Filter & Export
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val options = listOf("TODAY", "WEEK", "MONTH", "LIFETIME")
            val selectedIndex = options.indexOf(uiState.selectedDateRange).takeIf { it >= 0 } ?: 3
            
            SingleChoiceSegmentedButtonRow {
                options.forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        onClick = { viewModel.setDateRange(label) },
                        selected = index == selectedIndex
                    ) {
                        Text(label, fontSize = 10.sp)
                    }
                }
            }

            OutlinedButton(
                onClick = { /* Export Report Stub */ },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export", fontSize = 12.sp)
            }
        }

        // High-level Scannable Grid
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DashboardMetricCard(
                title = "Total Active Clients",
                value = uiState.activeClients.toString(),
                icon = Icons.Default.People,
                tintColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            DashboardMetricCard(
                title = "Pending Fleet",
                value = uiState.pendingFleets.toString(),
                icon = Icons.Default.Campaign,
                tintColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DashboardMetricCard(
                title = "Active Disputes",
                value = uiState.activeDisputes.toString(),
                icon = Icons.Default.Warning,
                tintColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            DashboardMetricCard(
                title = "System Health",
                value = uiState.systemHealth,
                icon = Icons.Default.CheckCircle,
                tintColor = Color(0xFF22C55E),
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Recent Action Log", 
            fontWeight = FontWeight.SemiBold, 
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        // Activity Log List
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (uiState.recentLogs.isEmpty()) {
                item {
                    Text(
                        text = "No recent activity.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(uiState.recentLogs) { logItem ->
                    ActivityLogItemCard(item = logItem)
                }
            }
        }
    }
}

@Composable
fun ActivityLogItemCard(item: ActivityLogItem) {
    val timeFormatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val formattedTime = timeFormatter.format(Date(item.timestampMs))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (item.description.isNotEmpty()) {
                    Text(text = item.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(text = formattedTime, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}
