package com.waste.link.ui.collector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waste.link.ui.components.GlassCard

@Composable
fun CollectorProfileScreen(
    onNavigateToSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Profile",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Profile Header
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = Color.White.copy(alpha = 0.05f),
            borderColor = Color.White.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text("Alex Driver", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("alex.driver@wastelink.com", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFF59E0B).copy(alpha = 0.2f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("4.98 Rating", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("Account", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Menu Options
        ProfileMenuItem(icon = Icons.Default.Settings, title = "Settings & Preferences", onClick = onNavigateToSettings)
    }
}

@Composable
private fun ProfileMenuItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .background(Color.White.copy(alpha = 0.03f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
    }
}
