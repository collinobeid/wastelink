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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.ui.theme.DarkBorder
import com.waste.link.ui.theme.DarkCard
import com.waste.link.ui.theme.DarkForeground
import com.waste.link.ui.theme.DarkMutedForeground
import com.waste.link.ui.theme.DarkSecondary

/**
 * Dedicated Collector Verification Status Screen (Module 1, Stage 5).
 *
 * Implements the lifecycle presentation for waste collector KYC verification:
 * - [CollectorVerificationStatus.NOT_SUBMITTED] -> Route back to Onboarding.
 * - [CollectorVerificationStatus.PENDING_REVIEW] -> Review badge, explanation, refresh & logout.
 * - [CollectorVerificationStatus.APPROVED] -> Unlocked badge, continue to Collector Dashboard.
 * - [CollectorVerificationStatus.REJECTED] -> Rejection callout with reason & "Edit & Resubmit" CTA.
 *
 * Security Invariant:
 * - Internal moderator notes and raw system claims are NEVER exposed in this UI.
 */
@Composable
fun CollectorVerificationStatusScreen(
    uiState: AuthUiState,
    onRefreshStatus: () -> Unit,
    onEditAndResubmit: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = uiState.currentUser?.collectorVerificationStatus
        ?: uiState.collectorSubmission?.status
        ?: CollectorVerificationStatus.PENDING_REVIEW

    val submission = uiState.collectorSubmission

    AuthScreenContainer(
        testTag = AuthTestTags.COLLECTOR_STATUS_SCREEN,
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        AuthBrandHeader(
            title = when (status) {
                CollectorVerificationStatus.APPROVED -> "Verification Approved"
                CollectorVerificationStatus.REJECTED -> "Action Required"
                CollectorVerificationStatus.PENDING_REVIEW -> "Application In Review"
                CollectorVerificationStatus.NOT_SUBMITTED,
                CollectorVerificationStatus.NOT_APPLICABLE -> "Application Not Submitted"
            },
            subtitle = when (status) {
                CollectorVerificationStatus.APPROVED ->
                    "Your commercial collector credentials have been verified by municipal oversight."
                CollectorVerificationStatus.REJECTED ->
                    "Your verification could not be completed with the details provided. Review the feedback below to resubmit."
                CollectorVerificationStatus.PENDING_REVIEW ->
                    "Our municipal dispatch team is verifying your vehicle and driver credentials."
                CollectorVerificationStatus.NOT_SUBMITTED,
                CollectorVerificationStatus.NOT_APPLICABLE ->
                    "Please submit your vehicle and driver details to complete onboarding."
            },
            badgeText = "KYC VERIFICATION"
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Main Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(
                width = 1.dp,
                color = when (status) {
                    CollectorVerificationStatus.APPROVED -> AuthEmeraldAccent.copy(alpha = 0.5f)
                    CollectorVerificationStatus.REJECTED -> Color(0xFFEF4444).copy(alpha = 0.5f)
                    CollectorVerificationStatus.PENDING_REVIEW -> Color(0xFFF59E0B).copy(alpha = 0.5f)
                    CollectorVerificationStatus.NOT_SUBMITTED,
                    CollectorVerificationStatus.NOT_APPLICABLE -> DarkBorder
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Icon Avatar
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            when (status) {
                                CollectorVerificationStatus.APPROVED -> AuthEmeraldAccent.copy(alpha = 0.15f)
                                CollectorVerificationStatus.REJECTED -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                CollectorVerificationStatus.PENDING_REVIEW -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                CollectorVerificationStatus.NOT_SUBMITTED,
                                CollectorVerificationStatus.NOT_APPLICABLE -> DarkSecondary
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (status) {
                            CollectorVerificationStatus.APPROVED -> Icons.Default.VerifiedUser
                            CollectorVerificationStatus.REJECTED -> Icons.Default.Cancel
                            CollectorVerificationStatus.PENDING_REVIEW -> Icons.Default.HourglassEmpty
                            CollectorVerificationStatus.NOT_SUBMITTED,
                            CollectorVerificationStatus.NOT_APPLICABLE -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = when (status) {
                            CollectorVerificationStatus.APPROVED -> AuthPrimaryGreen
                            CollectorVerificationStatus.REJECTED -> Color(0xFFEF4444)
                            CollectorVerificationStatus.PENDING_REVIEW -> Color(0xFFF59E0B)
                            CollectorVerificationStatus.NOT_SUBMITTED,
                            CollectorVerificationStatus.NOT_APPLICABLE -> DarkMutedForeground
                        },
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Badge Pill
                Surface(
                    color = DarkSecondary,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        1.dp,
                        when (status) {
                            CollectorVerificationStatus.APPROVED -> AuthEmeraldAccent.copy(alpha = 0.4f)
                            CollectorVerificationStatus.REJECTED -> Color(0xFFEF4444).copy(alpha = 0.4f)
                            CollectorVerificationStatus.PENDING_REVIEW -> Color(0xFFF59E0B).copy(alpha = 0.4f)
                            CollectorVerificationStatus.NOT_SUBMITTED,
                            CollectorVerificationStatus.NOT_APPLICABLE -> DarkBorder
                        }
                    ),
                    modifier = Modifier.testTag(AuthTestTags.COL_STATUS_BADGE)
                ) {
                    Text(
                        text = when (status) {
                            CollectorVerificationStatus.APPROVED -> "STATUS: APPROVED"
                            CollectorVerificationStatus.REJECTED -> "STATUS: REJECTED"
                            CollectorVerificationStatus.PENDING_REVIEW -> "STATUS: UNDER REVIEW"
                            CollectorVerificationStatus.NOT_SUBMITTED,
                            CollectorVerificationStatus.NOT_APPLICABLE -> "STATUS: NOT SUBMITTED"
                        },
                        color = when (status) {
                            CollectorVerificationStatus.APPROVED -> AuthPrimaryGreen
                            CollectorVerificationStatus.REJECTED -> Color(0xFFEF4444)
                            CollectorVerificationStatus.PENDING_REVIEW -> Color(0xFFF59E0B)
                            CollectorVerificationStatus.NOT_SUBMITTED,
                            CollectorVerificationStatus.NOT_APPLICABLE -> DarkMutedForeground
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Rejection Reason Callout (if REJECTED)
                if (status == CollectorVerificationStatus.REJECTED) {
                    val rejectionReasonText = submission?.rejectionReason
                        ?: uiState.currentUser?.collectorRejectionReason
                        ?: "Your vehicle or driver license details could not be validated by administrative oversight."

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(AuthTestTags.COL_STATUS_REJECTION_REASON),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF261313)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Feedback from Municipal Reviewer:",
                                color = Color(0xFFF87171),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = rejectionReasonText,
                                color = DarkForeground,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Submission summary details
                if (submission != null || uiState.currentUser?.vehiclePlateNumber != null) {
                    val vType = submission?.vehicleType ?: uiState.currentUser?.vehicleType ?: "Commercial Vehicle"
                    val plate = submission?.vehiclePlateNumber ?: uiState.currentUser?.vehiclePlateNumber ?: "N/A"
                    val zone = submission?.serviceZoneId ?: uiState.currentUser?.defaultZoneId ?: "Kinondoni"
                    val attempt = submission?.submissionAttempt ?: 1

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DarkSecondary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Vehicle Type:", color = DarkMutedForeground, fontSize = 12.sp)
                                Text(text = vType, color = DarkForeground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Plate Number:", color = DarkMutedForeground, fontSize = 12.sp)
                                Text(text = plate, color = DarkForeground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Service Zone:", color = DarkMutedForeground, fontSize = 12.sp)
                                Text(text = zone, color = DarkForeground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            if (attempt > 1) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Submission Attempt:", color = DarkMutedForeground, fontSize = 12.sp)
                                    Text(text = "$attempt", color = DarkForeground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Action Buttons based on status
                when (status) {
                    CollectorVerificationStatus.APPROVED -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            color = AuthPrimaryGreen.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AuthEmeraldAccent.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Ready for Municipal Dispatch",
                                    color = AuthPrimaryGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Your vehicle and driver credentials have been verified by municipal oversight. Live dispatch and route operations will activate upon regional deployment (Module 2).",
                                    color = DarkForeground,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                        AuthPrimaryButton(
                            text = "Open Collector Dashboard",
                            onClick = onNavigateToDashboard,
                            testTag = AuthTestTags.COL_STATUS_BTN_DASHBOARD
                        )
                    }
                    CollectorVerificationStatus.REJECTED -> {
                        AuthPrimaryButton(
                            text = "Edit & Resubmit Application",
                            onClick = onEditAndResubmit,
                            testTag = AuthTestTags.COL_STATUS_BTN_RESUBMIT
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AuthSecondaryOutlinedButton(
                            text = if (uiState.isLoadingCollectorSubmission) "Checking..." else "Refresh Status",
                            onClick = onRefreshStatus,
                            enabled = !uiState.isLoadingCollectorSubmission,
                            testTag = AuthTestTags.COL_STATUS_BTN_REFRESH
                        )
                    }
                    CollectorVerificationStatus.PENDING_REVIEW -> {
                        AuthPrimaryButton(
                            text = if (uiState.isLoadingCollectorSubmission) "Checking Status..." else "Refresh Status",
                            onClick = onRefreshStatus,
                            enabled = !uiState.isLoadingCollectorSubmission,
                            isLoading = uiState.isLoadingCollectorSubmission,
                            testTag = AuthTestTags.COL_STATUS_BTN_REFRESH
                        )
                    }
                    CollectorVerificationStatus.NOT_SUBMITTED,
                    CollectorVerificationStatus.NOT_APPLICABLE -> {
                        AuthPrimaryButton(
                            text = "Submit KYC Application",
                            onClick = onNavigateToOnboarding,
                            testTag = AuthTestTags.ONBOARD_BTN_CONTINUE
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AuthSecondaryOutlinedButton(
            text = "Sign Out",
            onClick = onLogoutClick,
            enabled = !uiState.isLoadingCollectorSubmission,
            isDestructive = true,
            testTag = AuthTestTags.COL_STATUS_BTN_LOGOUT
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
