package com.waste.link.ui.auth

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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waste.link.domain.model.AccountStatus
import com.waste.link.ui.theme.DarkBorder
import com.waste.link.ui.theme.DarkCard
import com.waste.link.ui.theme.DarkForeground
import com.waste.link.ui.theme.DarkMutedForeground
import com.waste.link.ui.theme.DarkSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Account Status Lock Screen (Module 1, Stage 6).
 *
 * Implements non-destructive presentation for [AccountStatus.SUSPENDED] and [AccountStatus.BANNED] accounts.
 *
 * Key Requirements:
 * - Suspended/Banned users remain authenticated but restricted to this screen.
 * - Displays the official municipal status reason and formatted expiration timestamp.
 * - Allows checking if suspension has expired or been lifted without re-entering credentials.
 * - Provides an action to contact compliance support or submit an appeal.
 * - Allows the user to deliberately sign out if desired.
 */
@Composable
fun AccountStatusLockScreen(
    uiState: AuthUiState,
    onRefreshStatus: () -> Unit,
    onContactSupport: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = uiState.currentUser?.accountStatus
        ?: uiState.lockedAccountStatus
        ?: AccountStatus.SUSPENDED

    val isBanned = status == AccountStatus.BANNED

    val reason = uiState.currentUser?.statusReason?.takeIf { it.isNotBlank() }
        ?: uiState.lockedStatusReason?.takeIf { it.isNotBlank() }
        ?: if (isBanned) {
            "Account permanently deactivated by municipal compliance decision."
        } else {
            "Account temporarily restricted following municipal moderation review."
        }

    val suspendedUntilEpochMs = uiState.currentUser?.suspendedUntilEpochMs
        ?: uiState.lockedSuspendedUntilEpochMs

    val formattedExpiry = when {
        isBanned -> "Permanent — This account cannot be restored automatically."
        suspendedUntilEpochMs != null && suspendedUntilEpochMs > 0 -> {
            try {
                val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                sdf.format(Date(suspendedUntilEpochMs))
            } catch (_: Exception) {
                "Until municipal review is completed"
            }
        }
        else -> "Until municipal moderation review is completed"
    }

    AuthScreenContainer(
        testTag = AuthTestTags.ACCOUNT_LOCK_SCREEN,
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        AuthBrandHeader(
            title = if (isBanned) "Account Deactivated" else "Account Suspended",
            subtitle = if (isBanned) {
                "This WasteLink account has been banned due to compliance or terms violations."
            } else {
                "Your WasteLink account has been temporarily suspended by municipal moderation."
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(
                1.dp,
                if (isBanned) AuthErrorRed.copy(alpha = 0.5f) else AuthWarningAmber.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isBanned) AuthErrorContainer else AuthWarningContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBanned) Icons.Default.Block else Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = if (isBanned) AuthErrorRed else AuthWarningAmber,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Account Standing",
                            fontSize = 12.sp,
                            color = DarkMutedForeground,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isBanned) AuthErrorContainer else AuthWarningContainer,
                            border = BorderStroke(
                                1.dp,
                                if (isBanned) AuthErrorRed.copy(alpha = 0.6f) else AuthWarningAmber.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.testTag(AuthTestTags.ACCOUNT_LOCK_STATUS_BADGE)
                        ) {
                            Text(
                                text = if (isBanned) "BANNED" else "SUSPENDED",
                                color = if (isBanned) AuthErrorRed else AuthWarningAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Official Administrative Reason
                Text(
                    text = "Official Reason",
                    fontSize = 12.sp,
                    color = DarkMutedForeground,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = reason,
                    fontSize = 14.sp,
                    color = DarkForeground,
                    lineHeight = 20.sp,
                    modifier = Modifier.testTag(AuthTestTags.ACCOUNT_LOCK_REASON)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Expiry / Duration
                Text(
                    text = if (isBanned) "Duration" else "Suspension Elapses",
                    fontSize = 12.sp,
                    color = DarkMutedForeground,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedExpiry,
                    fontSize = 13.sp,
                    color = if (isBanned) DarkMutedForeground else AuthWarningAmber,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag(AuthTestTags.ACCOUNT_LOCK_EXPIRY)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Policy Notice
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSecondary.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = DarkMutedForeground,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isBanned) {
                        "While banned, this account cannot perform collections or schedule waste pickups. You may submit a formal appeal through municipal compliance."
                    } else {
                        "While suspended, dispatch access and bookings are temporarily paused. If your suspension period has expired, tap 'Check Status' to restore full access."
                    },
                    fontSize = 12.sp,
                    color = DarkMutedForeground,
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Actions
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Check / Refresh Status CTA
            AuthPrimaryButton(
                text = "Check Status",
                onClick = onRefreshStatus,
                isLoading = uiState.isSubmitting || uiState.isInitialSessionLoading,
                testTag = AuthTestTags.ACCOUNT_LOCK_BTN_REFRESH,
                modifier = Modifier.fillMaxWidth()
            )

            // Contact Support / Appeal CTA
            AuthSecondaryOutlinedButton(
                text = "Contact Support / Appeal",
                onClick = onContactSupport,
                testTag = AuthTestTags.ACCOUNT_LOCK_BTN_APPEAL,
                modifier = Modifier.fillMaxWidth()
            )

            // Sign Out CTA
            AuthSecondaryOutlinedButton(
                text = "Sign Out",
                onClick = onLogoutClick,
                testTag = AuthTestTags.ACCOUNT_LOCK_BTN_LOGOUT,
                isDestructive = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
