package com.waste.link.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waste.link.domain.model.ProfileIncompleteReason
import com.waste.link.domain.model.UserRole
import com.waste.link.domain.validation.AuthValidator
import com.waste.link.ui.theme.DarkBorder
import com.waste.link.ui.theme.DarkCard
import com.waste.link.ui.theme.DarkForeground
import com.waste.link.ui.theme.DarkMutedForeground

/**
 * Incomplete Profile Recovery Screen (`CompleteProfileScreen` — Module 1, Stage 4).
 *
 * Displayed when a user authenticates with a valid Firebase Authentication account whose
 * canonical `/users/{uid}` Firestore profile is missing or incomplete (Ghost Account).
 *
 * Never creates a second Firebase Auth account and never fabricates placeholder values.
 */
@Composable
fun CompleteProfileScreen(
    uiState: AuthUiState,
    onRoleSelected: (UserRole) -> Unit,
    onFullNameChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onSubmitRecovery: (fullName: String, phone: String, role: UserRole?) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isFormEnabled = !uiState.isSubmitting

    val normalizedPhonePreview = remember(uiState.recoveryPhone) {
        if (uiState.recoveryPhone.isNotBlank()) {
            AuthValidator.validateAndNormalizePhone(uiState.recoveryPhone).getOrNull()
        } else null
    }

    val reasonDescription = when (uiState.recoveryReason) {
        ProfileIncompleteReason.DOCUMENT_MISSING ->
            "Your login succeeded, but your WasteLink profile was not finished when your account was first created."
        ProfileIncompleteReason.PROFILE_WRITE_FAILED ->
            "Your authentication account was created, and only your WasteLink profile setup remains."
        ProfileIncompleteReason.LEGACY_UNASSIGNED_ROLE ->
            "Please select your account type and confirm your contact details to upgrade your profile."
        ProfileIncompleteReason.LEGACY_FABRICATED_PLACEHOLDER ->
            "Please provide your real full name, phone number, and account type to complete account setup."
    }

    AuthScreenContainer(
        testTag = AuthTestTags.COMPLETE_PROFILE_SCREEN,
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        AuthBrandHeader(
            title = "Complete Account Setup",
            subtitle = reasonDescription,
            badgeText = "PROFILE RECOVERY"
        )

        Spacer(modifier = Modifier.height(20.dp))

        val banner = uiState.recoveryBanner
        if (banner != null) {
            AuthBannerCard(
                banner = banner,
                testTag = AuthTestTags.RECOVER_BANNER
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
                // Authenticated Identity Summary (Read-Only)
                Surface(
                    color = AuthInfoContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AuthPrimaryGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Authenticated Firebase Account",
                            color = AuthPrimaryGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.recoveryEmail.ifBlank { "Signed-in account" },
                            color = DarkForeground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (uiState.recoveryUid.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "UID: ${uiState.recoveryUid}",
                                color = DarkMutedForeground,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                AuthRoleSelectorSection(
                    selectedRole = uiState.recoveryRole,
                    onRoleSelected = onRoleSelected,
                    enabled = isFormEnabled,
                    roleError = uiState.recoveryRoleError,
                    residentTestTag = AuthTestTags.RECOVER_ROLE_RESIDENT,
                    collectorTestTag = AuthTestTags.RECOVER_ROLE_COLLECTOR
                )

                Spacer(modifier = Modifier.height(18.dp))

                AuthOutlinedTextField(
                    value = uiState.recoveryEmail.ifBlank { "Authenticated user" },
                    onValueChange = {},
                    label = "Verified Sign-In Email",
                    enabled = false,
                    readOnly = true,
                    leadingIcon = Icons.Default.Email,
                    testTag = "AUTH_RECOVER_READONLY_EMAIL"
                )

                Spacer(modifier = Modifier.height(14.dp))

                AuthOutlinedTextField(
                    value = uiState.recoveryFullName,
                    onValueChange = onFullNameChanged,
                    label = "Full Name",
                    placeholder = "Enter your full legal name",
                    errorMessage = uiState.recoveryFullNameError,
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
                    testTag = AuthTestTags.RECOVER_INPUT_NAME
                )

                Spacer(modifier = Modifier.height(14.dp))

                AuthOutlinedTextField(
                    value = uiState.recoveryPhone,
                    onValueChange = onPhoneChanged,
                    label = "Phone Number",
                    placeholder = "+255 712 345 678 or 0712 345 678",
                    supportingHint = normalizedPhonePreview?.let { "Normalized E.164 format: $it" }
                        ?: "Include country code (e.g., +255) or local trunk prefix (07...)",
                    errorMessage = uiState.recoveryPhoneError,
                    enabled = isFormEnabled,
                    leadingIcon = Icons.Default.Phone,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (isFormEnabled) {
                                onSubmitRecovery(
                                    uiState.recoveryFullName,
                                    uiState.recoveryPhone,
                                    uiState.recoveryRole
                                )
                            }
                        }
                    ),
                    testTag = AuthTestTags.RECOVER_INPUT_PHONE
                )

                Spacer(modifier = Modifier.height(22.dp))

                AuthPrimaryButton(
                    text = "Complete Account Setup",
                    onClick = {
                        focusManager.clearFocus()
                        onSubmitRecovery(
                            uiState.recoveryFullName,
                            uiState.recoveryPhone,
                            uiState.recoveryRole
                        )
                    },
                    enabled = isFormEnabled,
                    isLoading = uiState.isSubmitting,
                    testTag = AuthTestTags.RECOVER_BTN_SUBMIT
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        AuthSecondaryOutlinedButton(
            text = "Sign Out",
            onClick = {
                focusManager.clearFocus()
                onLogoutClick()
            },
            enabled = isFormEnabled,
            isDestructive = true,
            testTag = AuthTestTags.RECOVER_BTN_LOGOUT
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
