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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.waste.link.data.models.User
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.tasks.await

@Composable
fun AdminUsersViewScreen(modifier: Modifier = Modifier) {
    var searchQuery by remember { mutableStateOf("") }
    
    val users = remember { mutableStateOf<List<User>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val snapshot = FirebaseFirestore.getInstance().collection("users").get().await()
            val userList = snapshot.toObjects(User::class.java)
            users.value = userList
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text(
                text = "User Management",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search users...") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF52634F))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                )
            )
        }
        
        HorizontalDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            
            val filteredUsers = users.value.filter { it.fullName.contains(searchQuery, ignoreCase = true) }
            
            filteredUsers.forEach { user ->
                UserCard(
                    user = user,
                    onToggleActive = {
                        val newStatus = if (user.status == "ACTIVE") "RESTRICTED" else "ACTIVE"
                        FirebaseFirestore.getInstance().collection("users").document(user.id)
                            .update("status", newStatus)
                        users.value = users.value.map {
                            if (it.id == user.id) it.copy(status = newStatus) else it
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UserCard(user: User, onToggleActive: () -> Unit) {
    val isActive = user.status == "ACTIVE"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isActive) Color(0xFF1A6B38) else Color(0xFF8DA88A), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.fullName.firstOrNull()?.toString() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = user.fullName.ifEmpty { "Unknown User" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = user.status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color(0xFF15803D) else Color(0xFF6B7280),
                        modifier = Modifier
                            .background(if (isActive) Color(0xFFDCFCE7) else Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(text = user.phoneNumber.ifEmpty { user.email }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(text = user.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = " · ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Joined recently", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { /*TODO*/ },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.textButtonColors(containerColor = Color(0xFFF4FAF4), contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("View Requests", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(
                onClick = onToggleActive,
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (isActive) Color(0xFFFEE2E2) else Color(0xFFE8F5E9),
                    contentColor = if (isActive) Color(0xFFBA1A1A) else Color(0xFF1A6B38)
                )
            ) {
                Text(if (isActive) "Deactivate" else "Activate", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
