package com.waste.link.ui.collector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waste.link.ui.components.GlassCard

@Composable
fun CollectorEarningsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Your Earnings",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Main Balance Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Balance", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                    Icon(Icons.Default.BarChart, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("$1,245.50", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("This Week", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text("+$324.00", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Completed Jobs", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text("42", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { /*TODO*/ },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.AttachMoney, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cash Out", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { /*TODO*/ },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("History", fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("Recent Transactions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Transactions List
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(5) { index ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color.White.copy(alpha = 0.05f),
                    borderColor = Color.White.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Pickup Completed", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Req #${1024 + index}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                        }
                        Text("+$24.50", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
