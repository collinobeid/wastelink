package com.waste.link.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.waste.link.ui.theme.DarkBorder
import com.waste.link.ui.theme.DarkCard
import com.waste.link.ui.theme.DarkForeground
import com.waste.link.ui.theme.DarkMutedForeground

/**
 * Premium dark-themed Login Screen connected to canonical [AuthViewModel] and [com.waste.link.data.repository.AuthRepository].
 */
@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordEdited: () -> Unit,
    onSignInClick: (email: String, password: String) -> Unit,
    onForgotPasswordClick: (prefillEmail: String) -> Unit,
    onDismissForgotPassword: () -> Unit,
    onPasswordResetEmailChanged: (String) -> Unit,
    onSubmitPasswordReset: (String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onRetryProfileLoad: () -> Unit = {},
    onSignOutLockedAccount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val isFormEnabled = !uiState.isSubmitting
    val canSubmitLogin = !uiState.isSubmitting && uiState.loginRateLimitSeconds == 0

    AuthScreenContainer(
        testTag = AuthTestTags.LOGIN_SCREEN,
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        AuthBrandHeader(
            title = "Welcome Back",
            subtitle = "Sign in to schedule pickups, manage routes, and track waste operations in real time."
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Banner Feedback Area (Invalid Credentials, Network Error, Rate Limit, Profile Load Failed, Account Locked)
        val banner = uiState.loginBanner
        if (banner != null) {
            when (banner.type) {
                AuthBannerType.PROFILE_LOAD_FAILED -> {
                    AuthBannerCard(
                        banner = banner,
                        testTag = AuthTestTags.LOGIN_BANNER,
                        primaryActionText = if (banner.isRetryable) "Retry Profile Load" else null,
                        primaryActionTag = AuthTestTags.LOGIN_BTN_RETRY_LOAD,
                        onPrimaryAction = if (banner.isRetryable) onRetryProfileLoad else null,
                        secondaryActionText = "Sign Out",
                        secondaryActionTag = AuthTestTags.LOGIN_BTN_SIGN_OUT,
                        onSecondaryAction = onSignOutLockedAccount
                    )
                }
                AuthBannerType.ACCOUNT_LOCKED -> {
                    AuthBannerCard(
                        banner = banner,
                        testTag = AuthTestTags.LOGIN_BANNER,
                        primaryActionText = if (banner.isRetryable) "Refresh Status" else null,
                        primaryActionTag = AuthTestTags.LOGIN_BTN_RETRY_LOAD,
                        onPrimaryAction = if (banner.isRetryable) onRetryProfileLoad else null,
                        secondaryActionText = "Sign Out",
                        secondaryActionTag = AuthTestTags.LOGIN_BTN_SIGN_OUT,
                        onSecondaryAction = onSignOutLockedAccount
                    )
                }
                else -> {
                    AuthBannerCard(
                        banner = banner,
                        testTag = AuthTestTags.LOGIN_BANNER
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Main Sign-In Form Card
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
                AuthOutlinedTextField(
                    value = uiState.loginEmail,
                    onValueChange = onEmailChanged,
                    label = "Email Address",
                    placeholder = "you@wastelink.co.tz",
                    errorMessage = uiState.loginEmailError,
                    enabled = isFormEnabled,
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
                    testTag = AuthTestTags.LOGIN_INPUT_EMAIL
                )

                Spacer(modifier = Modifier.height(16.dp))

                AuthOutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onPasswordEdited()
                    },
                    label = "Password",
                    placeholder = "Enter your password",
                    errorMessage = uiState.loginPasswordError,
                    enabled = isFormEnabled,
                    leadingIcon = Icons.Default.Lock,
                    trailingIcon = {
                        IconButton(
                            onClick = { isPasswordVisible = !isPasswordVisible },
                            enabled = isFormEnabled,
                            modifier = Modifier.testTag(AuthTestTags.LOGIN_BTN_TOGGLE_PW)
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
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (canSubmitLogin) {
                                onSignInClick(uiState.loginEmail, password)
                            }
                        }
                    ),
                    testTag = AuthTestTags.LOGIN_INPUT_PASSWORD
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            onForgotPasswordClick(uiState.loginEmail)
                        },
                        enabled = isFormEnabled,
                        modifier = Modifier.testTag(AuthTestTags.LOGIN_BTN_FORGOT_PW)
                    ) {
                        Text(
                            text = "Forgot Password?",
                            color = AuthPrimaryGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val signInButtonText = if (uiState.loginRateLimitSeconds > 0) {
                    "Try Again in ${uiState.loginRateLimitSeconds}s"
                } else {
                    "Sign In"
                }

                AuthPrimaryButton(
                    text = signInButtonText,
                    onClick = {
                        focusManager.clearFocus()
                        onSignInClick(uiState.loginEmail, password)
                    },
                    enabled = canSubmitLogin,
                    isLoading = uiState.isSubmitting,
                    testTag = AuthTestTags.LOGIN_BTN_SUBMIT
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Navigate to Create Account
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "New to WasteLink? ",
                color = DarkMutedForeground,
                fontSize = 14.sp
            )
            Text(
                text = "Create Account",
                color = AuthPrimaryGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(enabled = isFormEnabled) {
                        focusManager.clearFocus()
                        onNavigateToRegister()
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
                    .testTag(AuthTestTags.LOGIN_BTN_GO_REGISTER)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Forgot Password Modal Dialog
    if (uiState.passwordReset.isDialogVisible) {
        ForgotPasswordDialog(
            state = uiState.passwordReset,
            onEmailChanged = onPasswordResetEmailChanged,
            onSubmit = onSubmitPasswordReset,
            onDismiss = onDismissForgotPassword
        )
    }
}

@Composable
private fun ForgotPasswordDialog(
    state: PasswordResetUiState,
    onEmailChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Dialog(
        onDismissRequest = {
            if (!state.isLoading) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag(AuthTestTags.PW_RESET_DIALOG),
            shape = RoundedCornerShape(20.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Reset Your Password",
                    color = DarkForeground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (state.dispatchedToEmail != null) {
                    // Privacy-Preserving Success Confirmation
                    Surface(
                        color = AuthSuccessContainer,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AuthEmeraldAccent.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(AuthTestTags.PW_RESET_CONFIRMATION)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AuthPrimaryGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Check Your Email",
                                    color = DarkForeground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "If an account is registered for ${state.dispatchedToEmail}, a password reset link has been sent. Please check your inbox and spam folder.",
                                color = DarkForeground.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    AuthPrimaryButton(
                        text = "Back to Sign In",
                        onClick = onDismiss,
                        testTag = AuthTestTags.PW_RESET_BTN_CLOSE
                    )
                } else {
                    Text(
                        text = "Enter the email address associated with your WasteLink account and we'll send instructions to reset your password.",
                        color = DarkMutedForeground,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!state.errorBanner.isNullOrBlank()) {
                        AuthBannerCard(
                            banner = AuthBannerMessage(
                                type = if (state.rateLimitSecondsRemaining > 0) AuthBannerType.WARNING else AuthBannerType.ERROR,
                                title = if (state.rateLimitSecondsRemaining > 0) "Rate Limit Active" else "Could Not Send Reset Link",
                                message = state.errorBanner,
                                isRetryable = state.isRetryable
                            ),
                            testTag = "${AuthTestTags.PW_RESET_DIALOG}_BANNER"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    AuthOutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChanged,
                        label = "Email Address",
                        placeholder = "you@wastelink.co.tz",
                        errorMessage = state.emailError,
                        enabled = !state.isLoading,
                        leadingIcon = Icons.Default.Email,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (!state.isLoading && state.rateLimitSecondsRemaining == 0) {
                                    onSubmit(state.email)
                                }
                            }
                        ),
                        testTag = AuthTestTags.PW_RESET_INPUT_EMAIL
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val sendButtonLabel = when {
                        state.rateLimitSecondsRemaining > 0 -> "Try Again in ${state.rateLimitSecondsRemaining}s"
                        state.isRetryable -> "Retry Sending Link"
                        else -> "Send Reset Link"
                    }

                    AuthPrimaryButton(
                        text = sendButtonLabel,
                        onClick = {
                            focusManager.clearFocus()
                            onSubmit(state.email)
                        },
                        enabled = !state.isLoading && state.rateLimitSecondsRemaining == 0,
                        isLoading = state.isLoading,
                        testTag = AuthTestTags.PW_RESET_BTN_SEND
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    AuthSecondaryOutlinedButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        enabled = !state.isLoading,
                        testTag = AuthTestTags.PW_RESET_BTN_CLOSE
                    )
                }
            }
        }
    }
}
