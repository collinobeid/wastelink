package com.waste.link.ui.admin.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    tintColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tintColor.copy(alpha = 0.1f))
            .border(1.dp, tintColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = tintColor, 
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = value, 
                color = MaterialTheme.colorScheme.onBackground, 
                fontSize = 28.sp, 
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title, 
                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                fontSize = 12.sp
            )
        }
    }
}
