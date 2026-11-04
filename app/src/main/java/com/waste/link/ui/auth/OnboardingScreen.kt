package com.waste.link.ui.auth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.waste.link.domain.model.UserRole
import com.waste.link.ui.theme.DarkBorder
import com.waste.link.ui.theme.DarkCard
import com.waste.link.ui.theme.DarkForeground
import com.waste.link.ui.theme.DarkMutedForeground
import com.waste.link.ui.theme.DarkSecondary

/**
 * Role-Aware Onboarding Compatibility Screen (Module 1, Stage 4).
 *
 * Derives the user's immutable [UserRole] from the authenticated canonical [com.waste.link.domain.model.User]
 * profile without asking the user to select Resident or Collector a second time.
 * Full role-specific setup and Collector KYC submission forms belong to Stage 5.
 */
@Composable
fun OnboardingScreen(
    uiState: AuthUiState,
    onCompleteOnboarding: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedRole = uiState.currentUser?.role ?: uiState.selectedRole ?: UserRole.RESIDENT

    var notificationsGranted by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    val roleLabel = when (resolvedRole) {
        UserRole.COLLECTOR -> "WASTE COLLECTOR ACCOUNT"
        UserRole.RESIDENT -> "RESIDENT ACCOUNT"
        UserRole.ADMIN -> "ADMINISTRATOR ACCOUNT"
    }

    val roleSubtitle = when (resolvedRole) {
        UserRole.COLLECTOR ->
            "Your collector account is verified. Review recommended permissions before opening your dashboard."
        UserRole.RESIDENT ->
            "Your resident account is verified. Review recommended permissions before opening your dashboard."
        UserRole.ADMIN ->
            "Your administrator account is active."
    }

    AuthScreenContainer(
        testTag = AuthTestTags.ONBOARDING_SCREEN,
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        AuthBrandHeader(
            title = "Welcome, ${uiState.currentUser?.fullName?.ifBlank { "Member" } ?: "Member"}",
            subtitle = roleSubtitle,
            badgeText = "ONBOARDING"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Derived Canonical Role Badge (No duplicate role selection)
        Surface(
            color = DarkSecondary,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, AuthEmeraldAccent.copy(alpha = 0.45f)),
            modifier = Modifier.testTag(AuthTestTags.ONBOARD_ROLE_BADGE)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (resolvedRole == UserRole.COLLECTOR) {
                        Icons.Default.DirectionsCar
                    } else {
                        Icons.Default.Home
                    },
                    contentDescription = null,
                    tint = AuthPrimaryGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = roleLabel,
                    color = AuthPrimaryGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (!uiState.onboardingError.isNullOrBlank()) {
            AuthBannerCard(
                banner = AuthBannerMessage(
                    type = AuthBannerType.ERROR,
                    title = "Onboarding Update Failed",
                    message = uiState.onboardingError,
                    isRetryable = true
                ),
                testTag = "${AuthTestTags.ONBOARDING_SCREEN}_ERROR"
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Recommended Device Permissions",
                    color = DarkForeground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Permissions are optional and never block account access.",
                    color = DarkMutedForeground,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Location Services Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DarkSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = AuthPrimaryGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Location Services",
                            color = DarkForeground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (resolvedRole == UserRole.COLLECTOR) {
                                "Used when navigating active collection routes"
                            } else {
                                "Used to pin pickup locations accurately"
                            },
                            color = DarkMutedForeground,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Push Notifications Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(DarkSecondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = AuthPrimaryGreen
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Push Notifications",
                                color = DarkForeground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (resolvedRole == UserRole.COLLECTOR) {
                                    "Receive real-time dispatch & mission alerts"
                                } else {
                                    "Receive updates when your collector arrives"
                                },
                                color = DarkMutedForeground,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        TextButton(
                            onClick = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        ) {
                            Text(
                                text = "Allow",
                                color = AuthPrimaryGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (notificationsGranted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Notifications Enabled",
                            tint = AuthPrimaryGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                AuthPrimaryButton(
                    text = "Get Started",
                    onClick = onCompleteOnboarding,
                    enabled = !uiState.isCompletingOnboarding,
                    isLoading = uiState.isCompletingOnboarding,
                    testTag = AuthTestTags.ONBOARD_BTN_CONTINUE
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        AuthSecondaryOutlinedButton(
            text = "Sign Out",
            onClick = onLogoutClick,
            enabled = !uiState.isCompletingOnboarding,
            isDestructive = true,
            testTag = AuthTestTags.ONBOARD_BTN_LOGOUT
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
