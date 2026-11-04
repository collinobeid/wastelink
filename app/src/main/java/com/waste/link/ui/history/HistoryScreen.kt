package com.waste.link.ui.history

import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.waste.link.R
import com.waste.link.ui.theme.NavActiveBlue

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel()
) {
    val requests by viewModel.requests.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F12)) // Dark premium background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.nav_history),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            var selectedFilter by remember { mutableStateOf("All") }
            val filters = listOf("All", "Pending", "Completed", "Cancelled")

            // Filtering Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(filters) { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f))
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) Color(0xFF16181C) else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            val filteredRequests = if (selectedFilter == "All") {
                requests
            } else {
                requests.filter { it.status.equals(selectedFilter, ignoreCase = true) }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NavActiveBlue)
                }
            } else if (filteredRequests.isEmpty()) {
                // Premium Empty State
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Empty History",
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (requests.isEmpty()) stringResource(R.string.historyEmpty) else "No $selectedFilter requests",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 16.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 100.dp), // offset for BNB
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredRequests) { request ->
                        HistoryCard(request = request)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryCard(request: com.waste.link.data.models.WasteRequest) {
    // Determine status color
    val statusColor = when (request.status.lowercase()) {
        "completed" -> Color(0xFF10B981) // Emerald Green
        "in transit" -> NavActiveBlue // Active Blue
        "assigned" -> Color(0xFFF59E0B) // Amber
        "cancelled" -> Color(0xFFEF4444) // Red
        else -> Color.White.copy(alpha = 0.5f) // Pending / Default
    }

    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF16181C).copy(alpha = 0.8f))
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.requestRef,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                
                // Premium Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = request.status.uppercase(),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Category", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    Text(request.category, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Volume", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    Text(request.volumeTier, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Address", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    Text(
                        text = request.pickupAddress, 
                        color = Color.White.copy(alpha = 0.9f), 
                        fontSize = 14.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 1
                    )
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text("Transaction Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Agreed Price", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        Text(if (request.agreedPrice != null) "${request.agreedPrice} TZS" else "Pending", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Collector", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        Text(request.assignedTruckId?.take(8) ?: "Pending", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
