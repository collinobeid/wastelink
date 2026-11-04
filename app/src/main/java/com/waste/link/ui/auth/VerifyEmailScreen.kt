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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waste.link.ui.theme.DarkBorder
import com.waste.link.ui.theme.DarkCard
import com.waste.link.ui.theme.DarkForeground
import com.waste.link.ui.theme.DarkMutedForeground
import com.waste.link.ui.theme.DarkSecondary

/**
 * Email Verification Gate Screen (Module 1, Stage 4).
 *
 * Connected to canonical [com.waste.link.data.repository.AuthRepository.refreshEmailVerificationStatus],
 * [com.waste.link.data.repository.AuthRepository.resendEmailVerification], and
 * [com.waste.link.data.repository.AuthRepository.logout].
 */
@Composable
fun VerifyEmailScreen(
    uiState: AuthUiState,
    onRefreshVerificationStatus: () -> Unit,
    onResendVerificationEmail: () -> Unit,
    onContinueAfterVerification: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedEmail = uiState.verificationEmailAddress
        .ifBlank { uiState.currentUser?.email.orEmpty() }
        .ifBlank { "your registered email address" }

    val isBusy = uiState.isRefreshingVerification || uiState.isResendingVerification

    AuthScreenContainer(
        testTag = AuthTestTags.VERIFY_EMAIL_SCREEN,
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        AuthBrandHeader(
            title = "Verify Your Email",
            subtitle = "Email verification protects your WasteLink account and unlocks onboarding and pickup services."
        )

        Spacer(modifier = Modifier.height(24.dp))

        val banner = uiState.verificationBanner
        if (banner != null) {
            AuthBannerCard(
                banner = banner,
                testTag = AuthTestTags.VERIFY_BANNER
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
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Badge
                val statusContainerColor = if (uiState.isEmailVerified) {
                    AuthSuccessContainer
                } else {
                    AuthWarningContainer
                }
                val statusAccentColor = if (uiState.isEmailVerified) {
                    AuthPrimaryGreen
                } else {
                    AuthWarningAmber
                }

                Surface(
                    color = statusContainerColor,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, statusAccentColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isEmailVerified) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Schedule
                            },
                            contentDescription = null,
                            tint = statusAccentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isEmailVerified) {
                                "STATUS: VERIFIED"
                            } else {
                                "STATUS: PENDING VERIFICATION"
                            },
                            color = statusAccentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DarkSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isEmailVerified) {
                            Icons.Default.MarkEmailRead
                        } else {
                            Icons.Default.MarkEmailUnread
                        },
                        contentDescription = null,
                        tint = AuthPrimaryGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Registered Email Address",
                    color = DarkMutedForeground,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = displayedEmail,
                    color = DarkForeground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = DarkBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // Instructions
                VerificationStepRow(
                    stepNumber = "1",
                    text = "Open your email inbox for $displayedEmail (and check your Spam/Promotions folder)."
                )
                Spacer(modifier = Modifier.height(10.dp))
                VerificationStepRow(
                    stepNumber = "2",
                    text = "Click the verification link sent by WasteLink / Firebase Authentication."
                )
                Spacer(modifier = Modifier.height(10.dp))
                VerificationStepRow(
                    stepNumber = "3",
                    text = "Return here and tap 'Refresh Verification Status' below to update your security token."
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Primary Continue Action (Enabled only when email is verified)
                AuthPrimaryButton(
                    text = if (uiState.isEmailVerified) {
                        "Continue to Onboarding"
                    } else {
                        "Continue (Verify Email First)"
                    },
                    onClick = onContinueAfterVerification,
                    enabled = uiState.isEmailVerified && !isBusy,
                    testTag = AuthTestTags.VERIFY_BTN_CONTINUE
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Refresh Verification Status Button
                AuthPrimaryButton(
                    text = "Refresh Verification Status",
                    onClick = onRefreshVerificationStatus,
                    enabled = !isBusy,
                    isLoading = uiState.isRefreshingVerification,
                    leadingIcon = Icons.Default.Refresh,
                    testTag = AuthTestTags.VERIFY_BTN_CHECK
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Resend Verification Email Button with Cooldown
                val resendLabel = when {
                    uiState.isResendingVerification -> "Sending Verification Email..."
                    uiState.resendCooldownSeconds > 0 -> "Resend Verification Email (${uiState.resendCooldownSeconds}s)"
                    else -> "Resend Verification Email"
                }

                AuthSecondaryOutlinedButton(
                    text = resendLabel,
                    onClick = onResendVerificationEmail,
                    enabled = !isBusy && uiState.resendCooldownSeconds == 0 && !uiState.isEmailVerified,
                    testTag = AuthTestTags.VERIFY_BTN_RESEND
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        AuthSecondaryOutlinedButton(
            text = "Sign Out & Return to Login",
            onClick = onLogoutClick,
            enabled = !isBusy,
            isDestructive = true,
            testTag = AuthTestTags.VERIFY_BTN_LOGOUT
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun VerificationStepRow(
    stepNumber: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(DarkSecondary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = AuthPrimaryGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = DarkForeground.copy(alpha = 0.9f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
