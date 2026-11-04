package com.waste.link.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waste.link.domain.model.UserRole
import com.waste.link.domain.validation.AuthValidator
import com.waste.link.domain.validation.PasswordStrengthReport
import com.waste.link.ui.theme.DarkBorder
import com.waste.link.ui.theme.DarkCard
import com.waste.link.ui.theme.DarkForeground
import com.waste.link.ui.theme.DarkMutedForeground

/**
 * Premium dark-themed Registration Screen (Module 1, Stage 4).
 *
 * Implements:
 * - Step 1: Single-choice Resident vs. Waste Collector role selection (`UserRole.RESIDENT` / `UserRole.COLLECTOR`).
 * - Step 2: Full Name, Email, International E.164 Phone Number (backed by Google `libphonenumber`),
 *   Password with strength checklist, and Confirm Password.
 * - Step 3: Canonical registration submission, duplicate-email sign-in redirect, and same-session
 *   partial registration profile recovery (`retryProfileInitialization`).
 */
@Composable
fun RegisterScreen(
    uiState: AuthUiState,
    onRoleSelected: (UserRole) -> Unit,
    onFullNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onPasswordChanged: (password: String, confirmPassword: String) -> Unit,
    onConfirmPasswordChanged: (password: String, confirmPassword: String) -> Unit,
    onRegisterSubmit: (
        fullName: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String
    ) -> Unit,
    onRetryProfileSetup: () -> Unit,
    onAbortRecoverySignOut: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToLoginWithDuplicateEmail: () -> Unit,
    modifier: Modifier = Modifier
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isConfirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val isFormEnabled = !uiState.isSubmitting
    val canSubmit = !uiState.isSubmitting && uiState.regRateLimitSeconds == 0

    val passwordStrength = remember(password) {
        AuthValidator.evaluatePasswordStrength(password)
    }

    val normalizedPhonePreview = remember(uiState.regPhone) {
        if (uiState.regPhone.isNotBlank()) {
            AuthValidator.validateAndNormalizePhone(uiState.regPhone).getOrNull()
        } else null
    }

    AuthScreenContainer(
        testTag = AuthTestTags.REGISTER_SCREEN,
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        AuthBrandHeader(
            title = if (uiState.isPartialRegistrationPending) {
                "Finish Setting Up Profile"
            } else {
                "Create Your Account"
            },
            subtitle = if (uiState.isPartialRegistrationPending) {
                "Your authentication account is ready. Complete your profile below to continue."
            } else {
                "Select your account type and enter your details to join WasteLink."
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Banner Feedback Area (Duplicate Email, Partial Registration Interruption, Network Error, Rate Limit)
        val banner = uiState.regBanner
        if (banner != null) {
            when (banner.type) {
                AuthBannerType.DUPLICATE_EMAIL -> {
                    AuthBannerCard(
                        banner = banner,
                        testTag = AuthTestTags.REG_BANNER,
                        primaryActionText = "Sign In with This Email",
                        primaryActionTag = AuthTestTags.REG_BTN_DUP_EMAIL_LOGIN,
                        onPrimaryAction = onNavigateToLoginWithDuplicateEmail
                    )
                }
                else -> {
                    AuthBannerCard(
                        banner = banner,
                        testTag = AuthTestTags.REG_BANNER
                    )
                }
            }
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
                // Step 1: Role Selection (Resident vs. Waste Collector)
                AuthRoleSelectorSection(
                    selectedRole = uiState.selectedRole,
                    onRoleSelected = onRoleSelected,
                    enabled = isFormEnabled,
                    roleError = uiState.regRoleError,
                    residentTestTag = AuthTestTags.REG_ROLE_RESIDENT,
                    collectorTestTag = AuthTestTags.REG_ROLE_COLLECTOR
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Step 2: Identity & Contact Fields
                AuthOutlinedTextField(
                    value = uiState.regFullName,
                    onValueChange = onFullNameChanged,
                    label = "Full Name",
                    placeholder = "e.g., Amina Nyerere",
                    errorMessage = uiState.regFullNameError,
                    enabled = isFormEnabled,
                    leadingIcon = Icons.Default.Person,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    testTag = AuthTestTags.REG_INPUT_NAME
                )

                Spacer(modifier = Modifier.height(14.dp))

                AuthOutlinedTextField(
                    value = uiState.regEmail,
                    onValueChange = onEmailChanged,
                    label = "Email Address",
                    placeholder = "amina@wastelink.co.tz",
                    supportingHint = if (uiState.isPartialRegistrationPending) {
                        "Authenticated account email (locked during profile recovery)"
                    } else null,
                    errorMessage = uiState.regEmailError,
                    enabled = isFormEnabled && !uiState.isPartialRegistrationPending,
                    readOnly = uiState.isPartialRegistrationPending,
                    leadingIcon = Icons.Default.Email,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    testTag = AuthTestTags.REG_INPUT_EMAIL
                )

                Spacer(modifier = Modifier.height(14.dp))

                AuthOutlinedTextField(
                    value = uiState.regPhone,
                    onValueChange = onPhoneChanged,
                    label = "Phone Number",
                    placeholder = "+255 712 345 678 or 0712 345 678",
                    supportingHint = normalizedPhonePreview?.let { "Normalized E.164 format: $it" }
                        ?: "Include country code (e.g., +255) or local trunk prefix (07...)",
                    errorMessage = uiState.regPhoneError,
                    enabled = isFormEnabled,
                    leadingIcon = Icons.Default.Phone,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Phone,
                        imeAction = if (uiState.isPartialRegistrationPending) ImeAction.Done else ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        onDone = {
                            focusManager.clearFocus()
                            if (uiState.isPartialRegistrationPending && canSubmit) {
                                onRetryProfileSetup()
                            }
                        }
                    ),
                    testTag = AuthTestTags.REG_INPUT_PHONE
                )

                if (!uiState.isPartialRegistrationPending) {
                    Spacer(modifier = Modifier.height(14.dp))

                    AuthOutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            onPasswordChanged(it, confirmPassword)
                        },
                        label = "Password",
                        placeholder = "At least 8 characters",
                        errorMessage = uiState.regPasswordError,
                        enabled = isFormEnabled,
                        leadingIcon = Icons.Default.Lock,
                        trailingIcon = {
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                enabled = isFormEnabled,
                                modifier = Modifier.testTag(AuthTestTags.REG_BTN_TOGGLE_PW)
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (isPasswordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    },
                                    tint = DarkMutedForeground
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        testTag = AuthTestTags.REG_INPUT_PASSWORD
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PasswordStrengthSection(
                        report = passwordStrength,
                        isVisible = password.isNotEmpty()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    AuthOutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            onConfirmPasswordChanged(password, it)
                        },
                        label = "Confirm Password",
                        placeholder = "Re-enter your password",
                        errorMessage = uiState.regConfirmPasswordError,
                        enabled = isFormEnabled,
                        leadingIcon = Icons.Default.Lock,
                        trailingIcon = {
                            IconButton(
                                onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
                                enabled = isFormEnabled,
                                modifier = Modifier.testTag(AuthTestTags.REG_BTN_TOGGLE_CONFIRM_PW)
                            ) {
                                Icon(
                                    imageVector = if (isConfirmPasswordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (isConfirmPasswordVisible) {
                                        "Hide confirm password"
                                    } else {
                                        "Show confirm password"
                                    },
                                    tint = DarkMutedForeground
                                )
                            }
                        },
                        visualTransformation = if (isConfirmPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (canSubmit) {
                                    onRegisterSubmit(
                                        uiState.regFullName,
                                        uiState.regEmail,
                                        uiState.regPhone,
                                        password,
                                        confirmPassword
                                    )
                                }
                            }
                        ),
                        testTag = AuthTestTags.REG_INPUT_CONFIRM_PASSWORD
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Step 3: Account Creation or Same-Session Profile Recovery CTA
                if (uiState.isPartialRegistrationPending) {
                    AuthPrimaryButton(
                        text = "Retry Profile Setup",
                        onClick = {
                            focusManager.clearFocus()
                            onRetryProfileSetup()
                        },
                        enabled = canSubmit,
                        isLoading = uiState.isSubmitting,
                        testTag = AuthTestTags.REG_BTN_RETRY_PROFILE
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    AuthSecondaryOutlinedButton(
                        text = "Sign Out & Start Over",
                        onClick = {
                            focusManager.clearFocus()
                            onAbortRecoverySignOut()
                        },
                        enabled = isFormEnabled,
                        isDestructive = true,
                        testTag = AuthTestTags.REG_BTN_ABORT_RECOVERY
                    )
                } else {
                    val submitText = when {
                        uiState.regRateLimitSeconds > 0 -> "Try Again in ${uiState.regRateLimitSeconds}s"
                        uiState.selectedRole == UserRole.COLLECTOR -> "Create Collector Account"
                        uiState.selectedRole == UserRole.RESIDENT -> "Create Resident Account"
                        else -> "Create Account"
                    }

                    AuthPrimaryButton(
                        text = submitText,
                        onClick = {
                            focusManager.clearFocus()
                            onRegisterSubmit(
                                uiState.regFullName,
                                uiState.regEmail,
                                uiState.regPhone,
                                password,
                                confirmPassword
                            )
                        },
                        enabled = canSubmit,
                        isLoading = uiState.isSubmitting,
                        testTag = AuthTestTags.REG_BTN_SUBMIT
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (!uiState.isPartialRegistrationPending) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Already have an account? ",
                    color = DarkMutedForeground,
                    fontSize = 14.sp
                )
                Text(
                    text = "Sign In",
                    color = AuthPrimaryGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = isFormEnabled) {
                            focusManager.clearFocus()
                            onNavigateToLogin()
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .testTag(AuthTestTags.REG_BTN_GO_LOGIN)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PasswordStrengthSection(
    report: PasswordStrengthReport,
    isVisible: Boolean
) {
    if (!isVisible) return

    val barColor = when (report.score) {
        0, 1 -> AuthErrorRed
        2 -> AuthWarningAmber
        3 -> Color(0xFF34D399)
        else -> AuthEmeraldAccent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AuthTestTags.REG_PASSWORD_STRENGTH)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Password Strength",
                color = DarkMutedForeground,
                fontSize = 12.sp
            )
            Text(
                text = report.label,
                color = barColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (index in 1..4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            color = if (index <= report.score) barColor else DarkBorder,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PasswordRequirementChip(label = "8+ chars", met = report.hasMinLength)
            PasswordRequirementChip(label = "Uppercase", met = report.hasUppercase)
            PasswordRequirementChip(label = "Lowercase", met = report.hasLowercase)
            PasswordRequirementChip(label = "Number", met = report.hasDigit)
        }
    }
}

@Composable
private fun PasswordRequirementChip(
    label: String,
    met: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (met) AuthPrimaryGreen else DarkMutedForeground.copy(alpha = 0.6f),
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = if (met) DarkForeground else DarkMutedForeground.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
    }
}
