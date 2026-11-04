package com.waste.link.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waste.link.domain.model.UserRole
import com.waste.link.ui.theme.DarkBackground
import com.waste.link.ui.theme.DarkBorder
import com.waste.link.ui.theme.DarkCard
import com.waste.link.ui.theme.DarkDestructive
import com.waste.link.ui.theme.DarkForeground
import com.waste.link.ui.theme.DarkMutedForeground
import com.waste.link.ui.theme.DarkPrimary
import com.waste.link.ui.theme.DarkPrimaryForeground
import com.waste.link.ui.theme.DarkSecondary
import com.waste.link.ui.theme.Dimens
import com.waste.link.ui.theme.EmeraldGreen

/**
 * Canonical UI test tags and interaction registry IDs for Module 1 Stage 4.
 */
object AuthTestTags {
    // Login Screen
    const val LOGIN_SCREEN = "AUTH_LOGIN_SCREEN"
    const val LOGIN_INPUT_EMAIL = "AUTH_LOGIN_INPUT_EMAIL"
    const val LOGIN_INPUT_PASSWORD = "AUTH_LOGIN_INPUT_PASSWORD"
    const val LOGIN_BTN_TOGGLE_PW = "AUTH_LOGIN_BTN_TOGGLE_PW"
    const val LOGIN_BTN_FORGOT_PW = "AUTH_LOGIN_BTN_FORGOT_PW"
    const val LOGIN_BTN_SUBMIT = "AUTH_LOGIN_BTN_SUBMIT"
    const val LOGIN_BTN_GO_REGISTER = "AUTH_LOGIN_BTN_GO_REGISTER"
    const val LOGIN_BTN_RETRY_LOAD = "AUTH_LOGIN_BTN_RETRY_LOAD"
    const val LOGIN_BTN_SIGN_OUT = "AUTH_LOGIN_BTN_SIGN_OUT"
    const val LOGIN_BANNER = "AUTH_LOGIN_BANNER"

    // Forgot Password Dialog
    const val PW_RESET_DIALOG = "AUTH_PW_RESET_DIALOG"
    const val PW_RESET_INPUT_EMAIL = "AUTH_PW_RESET_INPUT_EMAIL"
    const val PW_RESET_BTN_SEND = "AUTH_PW_RESET_BTN_SEND"
    const val PW_RESET_BTN_CLOSE = "AUTH_PW_RESET_BTN_CLOSE"
    const val PW_RESET_CONFIRMATION = "AUTH_PW_RESET_CONFIRMATION"

    // Register Screen
    const val REGISTER_SCREEN = "AUTH_REGISTER_SCREEN"
    const val REG_ROLE_RESIDENT = "AUTH_REG_ROLE_RESIDENT"
    const val REG_ROLE_COLLECTOR = "AUTH_REG_ROLE_COLLECTOR"
    const val REG_INPUT_NAME = "AUTH_REG_INPUT_NAME"
    const val REG_INPUT_EMAIL = "AUTH_REG_INPUT_EMAIL"
    const val REG_INPUT_PHONE = "AUTH_REG_INPUT_PHONE"
    const val REG_INPUT_PASSWORD = "AUTH_REG_INPUT_PASSWORD"
    const val REG_INPUT_CONFIRM_PASSWORD = "AUTH_REG_INPUT_CONFIRM_PASSWORD"
    const val REG_BTN_TOGGLE_PW = "AUTH_REG_BTN_TOGGLE_PW"
    const val REG_BTN_TOGGLE_CONFIRM_PW = "AUTH_REG_BTN_TOGGLE_CONFIRM_PW"
    const val REG_PASSWORD_STRENGTH = "AUTH_REG_PASSWORD_STRENGTH"
    const val REG_BTN_SUBMIT = "AUTH_REG_BTN_SUBMIT"
    const val REG_BTN_RETRY_PROFILE = "AUTH_REG_BTN_RETRY_PROFILE"
    const val REG_BTN_GO_LOGIN = "AUTH_REG_BTN_GO_LOGIN"
    const val REG_BTN_DUP_EMAIL_LOGIN = "AUTH_REG_BTN_DUP_EMAIL_LOGIN"
    const val REG_BTN_ABORT_RECOVERY = "AUTH_REG_BTN_ABORT_RECOVERY"
    const val REG_BANNER = "AUTH_REG_BANNER"

    // Verify Email Screen
    const val VERIFY_EMAIL_SCREEN = "AUTH_VERIFY_EMAIL_SCREEN"
    const val VERIFY_BTN_CHECK = "AUTH_VERIFY_BTN_CHECK"
    const val VERIFY_BTN_RESEND = "AUTH_VERIFY_BTN_RESEND"
    const val VERIFY_BTN_CONTINUE = "AUTH_VERIFY_BTN_CONTINUE"
    const val VERIFY_BTN_LOGOUT = "AUTH_VERIFY_BTN_LOGOUT"
    const val VERIFY_BANNER = "AUTH_VERIFY_BANNER"

    // Complete Profile (Ghost Account Recovery) Screen
    const val COMPLETE_PROFILE_SCREEN = "AUTH_COMPLETE_PROFILE_SCREEN"
    const val RECOVER_ROLE_RESIDENT = "AUTH_RECOVER_ROLE_RESIDENT"
    const val RECOVER_ROLE_COLLECTOR = "AUTH_RECOVER_ROLE_COLLECTOR"
    const val RECOVER_INPUT_NAME = "AUTH_RECOVER_INPUT_NAME"
    const val RECOVER_INPUT_PHONE = "AUTH_RECOVER_INPUT_PHONE"
    const val RECOVER_BTN_SUBMIT = "AUTH_RECOVER_BTN_SUBMIT"
    const val RECOVER_BTN_LOGOUT = "AUTH_RECOVER_BTN_LOGOUT"
    const val RECOVER_BANNER = "AUTH_RECOVER_BANNER"

    // Onboarding Screen (Stage 5 Role-Specific Onboarding)
    const val ONBOARDING_SCREEN = "AUTH_ONBOARDING_SCREEN"
    const val ONBOARD_ROLE_BADGE = "AUTH_ONBOARD_ROLE_BADGE"
    const val ONBOARD_BTN_CONTINUE = "AUTH_ONBOARD_BTN_CONTINUE"
    const val ONBOARD_BTN_LOGOUT = "AUTH_ONBOARD_BTN_LOGOUT"

    // Resident Onboarding Form
    const val ONBOARD_RES_ZONE_DROPDOWN = "AUTH_ONBOARD_RES_ZONE_DROPDOWN"
    const val ONBOARD_RES_INPUT_ADDRESS = "AUTH_ONBOARD_RES_INPUT_ADDRESS"
    const val ONBOARD_RES_NOTIF_TOGGLE = "AUTH_ONBOARD_RES_NOTIF_TOGGLE"
    const val ONBOARD_RES_BTN_FINISH = "AUTH_ONBOARD_RES_BTN_FINISH"
    const val ONBOARD_RES_BANNER = "AUTH_ONBOARD_RES_BANNER"

    // Collector Onboarding Form
    const val ONBOARD_COL_VEHICLE_TYPE = "AUTH_ONBOARD_COL_VEHICLE_TYPE"
    const val ONBOARD_COL_INPUT_PLATE = "AUTH_ONBOARD_COL_INPUT_PLATE"
    const val ONBOARD_COL_INPUT_LICENSE = "AUTH_ONBOARD_COL_INPUT_LICENSE"
    const val ONBOARD_COL_ZONE_DROPDOWN = "AUTH_ONBOARD_COL_ZONE_DROPDOWN"
    const val ONBOARD_COL_BTN_REVIEW = "AUTH_ONBOARD_COL_BTN_REVIEW"
    const val ONBOARD_COL_BTN_BACK_EDIT = "AUTH_ONBOARD_COL_BTN_BACK_EDIT"
    const val ONBOARD_COL_BTN_SUBMIT = "AUTH_ONBOARD_COL_BTN_SUBMIT"
    const val ONBOARD_COL_BANNER = "AUTH_ONBOARD_COL_BANNER"

    // Collector Verification Status Screen
    const val COL_STATUS_SCREEN = "AUTH_COL_STATUS_SCREEN"
    const val COLLECTOR_STATUS_SCREEN = COL_STATUS_SCREEN
    const val ONBOARD_COL_VEHICLE_DROPDOWN = ONBOARD_COL_VEHICLE_TYPE
    const val COL_STATUS_BADGE = "AUTH_COL_STATUS_BADGE"
    const val COL_STATUS_REJECTION_REASON = "AUTH_COL_STATUS_REJECTION_REASON"
    const val COL_STATUS_BTN_REFRESH = "AUTH_COL_STATUS_BTN_REFRESH"
    const val COL_STATUS_BTN_RESUBMIT = "AUTH_COL_STATUS_BTN_RESUBMIT"
    const val COL_STATUS_BTN_DASHBOARD = "AUTH_COL_STATUS_BTN_DASHBOARD"
    const val COL_STATUS_BTN_LOGOUT = "AUTH_COL_STATUS_BTN_LOGOUT"
    const val COL_STATUS_BANNER = "AUTH_COL_STATUS_BANNER"
}

// Refined Dark Forest & Emerald Palette Tokens derived from Color.kt
val AuthSurfaceElevated = Color(0xFF171D17)
val AuthFieldBackground = Color(0xFF141A14)
val AuthFieldBorderUnfocused = Color(0xFF2B3A2C)
val AuthEmeraldAccent = EmeraldGreen
val AuthPrimaryGreen = DarkPrimary
val AuthWarningAmber = Color(0xFFF59E0B)
val AuthWarningContainer = Color(0xFF2B2111)
val AuthErrorRed = Color(0xFFF87171)
val AuthErrorContainer = Color(0xFF2D1618)
val AuthSuccessContainer = Color(0xFF132A1E)
val AuthInfoContainer = Color(0xFF162420)

/**
 * Scrollable, edge-to-edge dark authentication container with disciplined spacing and contrast.
 */
@Composable
fun AuthScreenContainer(
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0E140F),
            DarkBackground,
            Color(0xFF0D120E)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpacingLarge, vertical = Dimens.SpacingLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/**
 * WasteLink brand header with forest-emerald icon mark and clear visual hierarchy.
 */
@Composable
fun AuthBrandHeader(
    title: String,
    subtitle: String,
    badgeText: String? = "WASTELINK",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSecondary)
                .border(1.dp, AuthEmeraldAccent.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Eco,
                contentDescription = "WasteLink Emblem",
                tint = AuthPrimaryGreen,
                modifier = Modifier.size(28.dp)
            )
        }

        if (!badgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = DarkSecondary.copy(alpha = 0.75f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, AuthEmeraldAccent.copy(alpha = 0.3f))
            ) {
                Text(
                    text = badgeText,
                    color = AuthPrimaryGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = title,
            color = DarkForeground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitle,
            color = DarkMutedForeground,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Disciplined dark-mode text field with inline validation error support and accessibility tags.
 */
@Composable
fun AuthOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingHint: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val isError = !errorMessage.isNullOrBlank()

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(
                    text = label,
                    fontSize = 13.sp
                )
            },
            placeholder = placeholder?.let {
                {
                    Text(
                        text = it,
                        color = DarkMutedForeground.copy(alpha = 0.65f),
                        fontSize = 14.sp
                    )
                }
            },
            leadingIcon = leadingIcon?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isError) AuthErrorRed else AuthPrimaryGreen
                    )
                }
            },
            trailingIcon = trailingIcon,
            isError = isError,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DarkForeground,
                unfocusedTextColor = DarkForeground,
                disabledTextColor = DarkForeground.copy(alpha = 0.6f),
                errorTextColor = DarkForeground,
                focusedContainerColor = AuthFieldBackground,
                unfocusedContainerColor = AuthFieldBackground,
                disabledContainerColor = AuthFieldBackground.copy(alpha = 0.6f),
                errorContainerColor = AuthFieldBackground,
                cursorColor = AuthPrimaryGreen,
                errorCursorColor = AuthErrorRed,
                focusedBorderColor = AuthEmeraldAccent,
                unfocusedBorderColor = AuthFieldBorderUnfocused,
                disabledBorderColor = DarkBorder,
                errorBorderColor = AuthErrorRed,
                focusedLabelColor = AuthPrimaryGreen,
                unfocusedLabelColor = DarkMutedForeground,
                disabledLabelColor = DarkMutedForeground.copy(alpha = 0.6f),
                errorLabelColor = AuthErrorRed
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )

        AnimatedVisibility(visible = isError) {
            Text(
                text = errorMessage.orEmpty(),
                color = AuthErrorRed,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 4.dp)
                    .testTag("${testTag}_ERROR")
            )
        }

        if (!isError && !supportingHint.isNullOrBlank()) {
            Text(
                text = supportingHint,
                color = DarkMutedForeground.copy(alpha = 0.8f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Primary action button with emerald accent, minimum 54.dp touch target, loading indicator, and cooldown support.
 */
@Composable
fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthEmeraldAccent,
            contentColor = DarkPrimaryForeground,
            disabledContainerColor = DarkSecondary.copy(alpha = 0.6f),
            disabledContentColor = DarkMutedForeground.copy(alpha = 0.7f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .testTag(testTag)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = DarkPrimaryForeground,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Processing...",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Secondary outlined button with subtle forest border and minimum 50.dp touch target.
 */
@Composable
fun AuthSecondaryOutlinedButton(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false
) {
    val borderColor = if (isDestructive) {
        DarkDestructive.copy(alpha = 0.45f)
    } else {
        AuthFieldBorderUnfocused
    }
    val textColor = if (isDestructive) DarkDestructive else DarkForeground

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DarkCard,
            contentColor = textColor,
            disabledContentColor = DarkMutedForeground.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .testTag(testTag)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Structured banner card for displaying specific authentication, network, duplicate-email,
 * or account-restriction feedback with optional recovery actions.
 */
@Composable
fun AuthBannerCard(
    banner: AuthBannerMessage,
    testTag: String,
    modifier: Modifier = Modifier,
    primaryActionText: String? = null,
    primaryActionTag: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    secondaryActionTag: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val (containerColor, borderColor, accentColor, icon) = when (banner.type) {
        AuthBannerType.ERROR,
        AuthBannerType.PROFILE_LOAD_FAILED -> BannerVisuals(
            container = AuthErrorContainer,
            border = AuthErrorRed.copy(alpha = 0.45f),
            accent = AuthErrorRed,
            icon = Icons.Default.ErrorOutline
        )
        AuthBannerType.ACCOUNT_LOCKED -> BannerVisuals(
            container = AuthErrorContainer,
            border = AuthErrorRed.copy(alpha = 0.6f),
            accent = AuthErrorRed,
            icon = Icons.Default.Lock
        )
        AuthBannerType.WARNING,
        AuthBannerType.DUPLICATE_EMAIL -> BannerVisuals(
            container = AuthWarningContainer,
            border = AuthWarningAmber.copy(alpha = 0.5f),
            accent = AuthWarningAmber,
            icon = Icons.Default.WarningAmber
        )
        AuthBannerType.SUCCESS -> BannerVisuals(
            container = AuthSuccessContainer,
            border = AuthEmeraldAccent.copy(alpha = 0.5f),
            accent = AuthPrimaryGreen,
            icon = Icons.Default.CheckCircle
        )
        AuthBannerType.INFO -> BannerVisuals(
            container = AuthInfoContainer,
            border = AuthPrimaryGreen.copy(alpha = 0.35f),
            accent = AuthPrimaryGreen,
            icon = Icons.Default.Info
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = banner.title,
                    color = DarkForeground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = banner.message,
                color = DarkForeground.copy(alpha = 0.9f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            if (primaryActionText != null && onPrimaryAction != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPrimaryAction,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color(0xFF0B120C)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .let { mod ->
                                if (primaryActionTag != null) mod.testTag(primaryActionTag) else mod
                            }
                    ) {
                        Text(
                            text = primaryActionText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (secondaryActionText != null && onSecondaryAction != null) {
                        OutlinedButton(
                            onClick = onSecondaryAction,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = DarkForeground
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .let { mod ->
                                    if (secondaryActionTag != null) mod.testTag(secondaryActionTag) else mod
                                }
                        ) {
                            Text(
                                text = secondaryActionText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class BannerVisuals(
    val container: Color,
    val border: Color,
    val accent: Color,
    val icon: ImageVector
)

/**
 * Two-option role selector for Resident vs. Waste Collector.
 * Never exposes `UserRole.ADMIN`.
 */
@Composable
fun AuthRoleSelectorSection(
    selectedRole: UserRole?,
    onRoleSelected: (UserRole) -> Unit,
    enabled: Boolean,
    roleError: String?,
    residentTestTag: String,
    collectorTestTag: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Select Account Type",
            color = DarkForeground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Choose how you will use WasteLink. Your account type is saved permanently.",
            color = DarkMutedForeground,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccountRoleOptionCard(
                title = "Resident",
                subtitle = "Schedule pickups & track collections",
                icon = Icons.Default.Home,
                isSelected = selectedRole == UserRole.RESIDENT,
                enabled = enabled,
                testTag = residentTestTag,
                onClick = { onRoleSelected(UserRole.RESIDENT) },
                modifier = Modifier.weight(1f)
            )

            AccountRoleOptionCard(
                title = "Waste Collector",
                subtitle = "Collect waste & manage assigned routes",
                icon = Icons.Default.DirectionsCar,
                isSelected = selectedRole == UserRole.COLLECTOR,
                enabled = enabled,
                testTag = collectorTestTag,
                onClick = { onRoleSelected(UserRole.COLLECTOR) },
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedVisibility(visible = !roleError.isNullOrBlank()) {
            Text(
                text = roleError.orEmpty(),
                color = AuthErrorRed,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 6.dp)
                    .testTag("${residentTestTag}_ERROR")
            )
        }
    }
}

@Composable
private fun AccountRoleOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        DarkSecondary.copy(alpha = 0.9f)
    } else {
        AuthFieldBackground
    }
    val borderColor = if (isSelected) {
        AuthEmeraldAccent
    } else {
        AuthFieldBorderUnfocused
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .semantics {
                this.selected = isSelected
                this.contentDescription = "$title role option"
            }
            .clickable(
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(14.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) AuthEmeraldAccent.copy(alpha = 0.2f)
                        else DarkCard
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) AuthPrimaryGreen else DarkMutedForeground,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = AuthEmeraldAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            color = DarkForeground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            color = DarkMutedForeground,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}
