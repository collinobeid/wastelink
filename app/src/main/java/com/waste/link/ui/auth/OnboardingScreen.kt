package com.waste.link.ui.auth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.waste.link.ui.theme.AppBgFrom
import com.waste.link.ui.theme.AppBgMid
import com.waste.link.ui.theme.AppBgTo
import com.waste.link.ui.theme.Dimens

@Composable
fun OnboardingScreen(
    onRoleSelected: (String) -> Unit,
    authState: AuthState
) {
    var step by remember { mutableIntStateOf(1) } // 1 = Role, 2 = Permissions
    var selectedRole by remember { mutableStateOf<String?>(null) }
    
    val bgBrush = Brush.verticalGradient(
        colors = listOf(AppBgFrom, AppBgMid, AppBgTo)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = bgBrush)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Progress Dots
            Row(
                modifier = Modifier.padding(bottom = Dimens.SpacingExtraLarge),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (step == 1) Color.White else Color.White.copy(alpha = 0.3f)))
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (step == 2) Color.White else Color.White.copy(alpha = 0.3f)))
            }

            AnimatedVisibility(visible = step == 1) {
                RoleSelectionStep(
                    selectedRole = selectedRole,
                    onRoleClick = { selectedRole = it },
                    onNext = { step = 2 }
                )
            }

            AnimatedVisibility(visible = step == 2) {
                PermissionPrimingStep(
                    onComplete = {
                        selectedRole?.let { onRoleSelected(it) }
                    },
                    isLoading = authState is AuthState.Loading
                )
            }
        }
    }
}

@Composable
fun RoleSelectionStep(
    selectedRole: String?,
    onRoleClick: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Welcome to WasteLink",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "How do you plan to use the app?",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 32.dp),
            textAlign = TextAlign.Center
        )

        RoleCard(
            title = "I'm a Resident",
            description = "Request pickups and track waste collection",
            icon = Icons.Default.Home,
            isSelected = selectedRole == "resident",
            onClick = { onRoleClick("resident") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        RoleCard(
            title = "I'm a Collector",
            description = "Manage routes and complete pickups",
            icon = Icons.Default.DirectionsCar,
            isSelected = selectedRole == "collector",
            onClick = { onRoleClick("collector") }
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            enabled = selectedRole != null
        ) {
            Text("Continue", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RoleCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color.White else Color.Transparent
    val bgColor = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusLarge))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(Dimens.RadiusLarge))
            .clickable { onClick() }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        if (isSelected) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
fun PermissionPrimingStep(
    onComplete: () -> Unit,
    isLoading: Boolean
) {
    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(true) }
    
    // Check initial state
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Stay Updated",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "WasteLink works best with a few permissions",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 32.dp),
            textAlign = TextAlign.Center
        )

        // Location Priming
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Location Services", color = Color.White, fontWeight = FontWeight.Bold)
                Text("To accurately pin your pickup spot", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }

        // Notification Priming
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Push Notifications", color = Color.White, fontWeight = FontWeight.Bold)
                Text("To track your driver's arrival", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
            if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Allow", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else if (notificationsGranted) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            } else {
                Text("Get Started", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
