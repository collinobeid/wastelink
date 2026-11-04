package com.waste.link.ui.auth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.UserRole
import com.waste.link.domain.validation.AuthValidator
import com.waste.link.ui.theme.DarkBorder
import com.waste.link.ui.theme.DarkCard
import com.waste.link.ui.theme.DarkForeground
import com.waste.link.ui.theme.DarkMutedForeground
import com.waste.link.ui.theme.DarkSecondary

/**
 * Role-Specific Onboarding & KYC Submission Screen (Module 1, Stage 5).
 *
 * Provides dedicated onboarding paths for each self-registered role:
 * - [UserRole.RESIDENT]: Municipal service zone selection, street/landmark, non-blocking permissions.
 * - [UserRole.COLLECTOR]: Multi-step commercial vehicle & driver license KYC form, review step, atomic submission.
 *
 * Invariant:
 * - Role is derived strictly from the canonical authenticated User profile and NEVER asked a second time.
 */
@Composable
fun OnboardingScreen(
    uiState: AuthUiState,
    onResidentZoneChanged: (String) -> Unit = {},
    onResidentAddressChanged: (String) -> Unit = {},
    onCompleteResidentOnboarding: () -> Unit = {},
    onCollectorVehicleTypeChanged: (String) -> Unit = {},
    onCollectorPlateNumberChanged: (String) -> Unit = {},
    onCollectorLicenseNumberChanged: (String) -> Unit = {},
    onCollectorServiceZoneChanged: (String) -> Unit = {},
    onCollectorReviewToggled: (Boolean) -> Unit = {},
    onSubmitCollectorOnboarding: () -> Unit = {},
    onResubmitCollectorOnboarding: () -> Unit = {},
    onCompleteOnboarding: () -> Unit = onCompleteResidentOnboarding,
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

    val isCollectorRejected = uiState.currentUser?.collectorVerificationStatus == CollectorVerificationStatus.REJECTED
        || uiState.collectorSubmission?.status == CollectorVerificationStatus.REJECTED

    AuthScreenContainer(
        testTag = AuthTestTags.ONBOARDING_SCREEN,
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        AuthBrandHeader(
            title = if (resolvedRole == UserRole.COLLECTOR) {
                if (isCollectorRejected) "Resubmit KYC Application" else "Collector Onboarding"
            } else {
                "Welcome to WasteLink"
            },
            subtitle = if (resolvedRole == UserRole.COLLECTOR) {
                "Complete your vehicle and commercial driver verification to join municipal dispatch."
            } else {
                "Set your municipal service zone and preferences for scheduled doorstep pickups."
            },
            badgeText = if (resolvedRole == UserRole.COLLECTOR) "COLLECTOR KYC" else "RESIDENT SETUP"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Canonical Role Badge
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
                    text = if (resolvedRole == UserRole.COLLECTOR) "WASTE COLLECTOR ACCOUNT" else "RESIDENT ACCOUNT",
                    color = AuthPrimaryGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Display Active Error Banner
        val errorBanner = uiState.collectorOnboardingBanner ?: uiState.residentOnboardingBanner
        if (errorBanner != null) {
            AuthBannerCard(
                banner = errorBanner,
                testTag = "${AuthTestTags.ONBOARDING_SCREEN}_BANNER"
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else if (!uiState.onboardingError.isNullOrBlank()) {
            AuthBannerCard(
                banner = AuthBannerMessage(
                    type = AuthBannerType.ERROR,
                    title = "Setup Failed",
                    message = uiState.onboardingError,
                    isRetryable = true
                ),
                testTag = "${AuthTestTags.ONBOARDING_SCREEN}_ERROR"
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (resolvedRole == UserRole.COLLECTOR) {
            // =================================================================
            // COLLECTOR ONBOARDING & KYC WORKFLOW
            // =================================================================
            if (!uiState.isCollectorReviewStep) {
                CollectorKycFormCard(
                    uiState = uiState,
                    onVehicleTypeChanged = onCollectorVehicleTypeChanged,
                    onPlateNumberChanged = onCollectorPlateNumberChanged,
                    onLicenseNumberChanged = onCollectorLicenseNumberChanged,
                    onServiceZoneChanged = onCollectorServiceZoneChanged,
                    onReviewClick = { onCollectorReviewToggled(true) }
                )
            } else {
                CollectorReviewCard(
                    uiState = uiState,
                    isRejected = isCollectorRejected,
                    onEditClick = { onCollectorReviewToggled(false) },
                    onSubmitClick = {
                        if (isCollectorRejected) {
                            onResubmitCollectorOnboarding()
                        } else {
                            onSubmitCollectorOnboarding()
                        }
                    }
                )
            }
        } else {
            // =================================================================
            // RESIDENT ONBOARDING WORKFLOW
            // =================================================================
            ResidentOnboardingCard(
                uiState = uiState,
                notificationsGranted = notificationsGranted,
                onZoneChanged = onResidentZoneChanged,
                onAddressChanged = onResidentAddressChanged,
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onCompleteClick = onCompleteResidentOnboarding
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        AuthSecondaryOutlinedButton(
            text = "Sign Out",
            onClick = onLogoutClick,
            enabled = !uiState.isCompletingOnboarding && !uiState.isSubmittingCollectorOnboarding,
            isDestructive = true,
            testTag = AuthTestTags.ONBOARD_BTN_LOGOUT
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// =============================================================================
// Resident Components
// =============================================================================

@Composable
private fun ResidentOnboardingCard(
    uiState: AuthUiState,
    notificationsGranted: Boolean,
    onZoneChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onCompleteClick: () -> Unit
) {
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
                text = "Municipal Service Preferences",
                color = DarkForeground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select your collection zone for doorstep waste and recycling schedules.",
                color = DarkMutedForeground,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Service Zone Selector
            ServiceZoneDropdownSelector(
                selectedZoneId = uiState.residentSelectedZone,
                onZoneSelected = onZoneChanged,
                errorMessage = uiState.residentZoneError,
                testTag = AuthTestTags.ONBOARD_RES_ZONE_DROPDOWN
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Optional Street Address / Landmark
            AuthOutlinedTextField(
                label = "Street Address / Landmark (Optional)",
                value = uiState.residentAddress,
                onValueChange = onAddressChanged,
                placeholder = "e.g., Plot 42, Ali Hassan Mwinyi Rd",
                testTag = AuthTestTags.ONBOARD_RES_INPUT_ADDRESS
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Optional Permissions Sub-section
            Text(
                text = "Recommended Permissions",
                color = DarkForeground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(DarkSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = AuthPrimaryGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Pickup Notifications",
                            color = DarkForeground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Real-time alerts when your collector arrives",
                            color = DarkMutedForeground,
                            fontSize = 11.sp
                        )
                    }
                }

                if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    TextButton(onClick = onRequestNotificationPermission) {
                        Text(text = "Allow", color = AuthPrimaryGreen, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Notifications Enabled",
                        tint = AuthPrimaryGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Complete Setup Button
            AuthPrimaryButton(
                text = "Complete Resident Setup",
                onClick = onCompleteClick,
                enabled = !uiState.isCompletingOnboarding,
                isLoading = uiState.isCompletingOnboarding,
                testTag = AuthTestTags.ONBOARD_RES_BTN_FINISH
            )
        }
    }
}

// =============================================================================
// Collector Components
// =============================================================================

@Composable
private fun CollectorKycFormCard(
    uiState: AuthUiState,
    onVehicleTypeChanged: (String) -> Unit,
    onPlateNumberChanged: (String) -> Unit,
    onLicenseNumberChanged: (String) -> Unit,
    onServiceZoneChanged: (String) -> Unit,
    onReviewClick: () -> Unit
) {
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
                text = "Vehicle & Driver Verification",
                color = DarkForeground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter your official commercial vehicle and operator license details.",
                color = DarkMutedForeground,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Vehicle Type Selector
            VehicleTypeDropdownSelector(
                selectedType = uiState.collectorVehicleType,
                onTypeSelected = onVehicleTypeChanged,
                errorMessage = uiState.collectorVehicleTypeError,
                testTag = AuthTestTags.ONBOARD_COL_VEHICLE_DROPDOWN
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Vehicle Registration Plate
            AuthOutlinedTextField(
                label = "Vehicle Registration Plate",
                value = uiState.collectorPlateNumber,
                onValueChange = onPlateNumberChanged,
                placeholder = "e.g., T 123 ABC",
                errorMessage = uiState.collectorPlateNumberError,
                testTag = AuthTestTags.ONBOARD_COL_INPUT_PLATE
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Commercial Driver License Number
            AuthOutlinedTextField(
                label = "Commercial Driver License Number",
                value = uiState.collectorLicenseNumber,
                onValueChange = onLicenseNumberChanged,
                placeholder = "e.g., DL-987654321",
                errorMessage = uiState.collectorLicenseNumberError,
                testTag = AuthTestTags.ONBOARD_COL_INPUT_LICENSE
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Service Zone Selector
            ServiceZoneDropdownSelector(
                selectedZoneId = uiState.collectorServiceZoneId,
                onZoneSelected = onServiceZoneChanged,
                errorMessage = uiState.collectorServiceZoneError,
                testTag = AuthTestTags.ONBOARD_COL_ZONE_DROPDOWN
            )

            Spacer(modifier = Modifier.height(22.dp))

            AuthPrimaryButton(
                text = "Review Application",
                onClick = onReviewClick,
                enabled = !uiState.isSubmittingCollectorOnboarding,
                testTag = AuthTestTags.ONBOARD_COL_BTN_REVIEW
            )
        }
    }
}

@Composable
private fun CollectorReviewCard(
    uiState: AuthUiState,
    isRejected: Boolean,
    onEditClick: () -> Unit,
    onSubmitClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, AuthEmeraldAccent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = AuthPrimaryGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Review Application Details",
                    color = DarkForeground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Confirm details match your commercial registration before final submission.",
                color = DarkMutedForeground,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Review Summary Rows
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSecondary.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ReviewRow(label = "Vehicle Type", value = uiState.collectorVehicleType)
                    Spacer(modifier = Modifier.height(10.dp))
                    ReviewRow(label = "Plate Number", value = uiState.collectorPlateNumber)
                    Spacer(modifier = Modifier.height(10.dp))
                    ReviewRow(label = "Driver License", value = uiState.collectorLicenseNumber)
                    Spacer(modifier = Modifier.height(10.dp))
                    ReviewRow(
                        label = "Service Zone",
                        value = formatZoneName(uiState.collectorServiceZoneId)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AuthPrimaryButton(
                text = if (isRejected) "Resubmit KYC Application" else "Submit KYC Application",
                onClick = onSubmitClick,
                enabled = !uiState.isSubmittingCollectorOnboarding,
                isLoading = uiState.isSubmittingCollectorOnboarding,
                testTag = AuthTestTags.ONBOARD_COL_BTN_SUBMIT
            )

            Spacer(modifier = Modifier.height(12.dp))

            AuthSecondaryOutlinedButton(
                text = "Edit Information",
                onClick = onEditClick,
                enabled = !uiState.isSubmittingCollectorOnboarding,
                testTag = AuthTestTags.ONBOARD_COL_BTN_BACK_EDIT
            )
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = DarkMutedForeground, fontSize = 13.sp)
        Text(
            text = value,
            color = DarkForeground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// =============================================================================
// Dropdown Selectors
// =============================================================================

@Composable
private fun ServiceZoneDropdownSelector(
    selectedZoneId: String,
    onZoneSelected: (String) -> Unit,
    errorMessage: String? = null,
    testTag: String
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Service Zone",
            color = DarkMutedForeground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
                    .clickable { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = DarkSecondary,
                border = BorderStroke(
                    1.dp,
                    if (errorMessage != null) androidx.compose.ui.graphics.Color(0xFFEF4444) else DarkBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatZoneName(selectedZoneId),
                        color = DarkForeground,
                        fontSize = 14.sp
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Zone",
                        tint = DarkMutedForeground
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkCard)
            ) {
                AuthValidator.MUNICIPAL_SERVICE_ZONES.forEach { zone ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = zone.displayName,
                                color = DarkForeground,
                                fontSize = 14.sp
                            )
                        },
                        onClick = {
                            onZoneSelected(zone.id)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                color = androidx.compose.ui.graphics.Color(0xFFEF4444),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun VehicleTypeDropdownSelector(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    errorMessage: String? = null,
    testTag: String
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Vehicle Type",
            color = DarkMutedForeground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
                    .clickable { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = DarkSecondary,
                border = BorderStroke(
                    1.dp,
                    if (errorMessage != null) androidx.compose.ui.graphics.Color(0xFFEF4444) else DarkBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedType.ifBlank { "Select Vehicle Type" },
                        color = DarkForeground,
                        fontSize = 14.sp
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Type",
                        tint = DarkMutedForeground
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkCard)
            ) {
                AuthValidator.STANDARD_VEHICLE_TYPES.forEach { vType ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = vType,
                                color = DarkForeground,
                                fontSize = 14.sp
                            )
                        },
                        onClick = {
                            onTypeSelected(vType)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                color = androidx.compose.ui.graphics.Color(0xFFEF4444),
                fontSize = 11.sp
            )
        }
    }
}

private fun formatZoneName(zoneId: String): String {
    return AuthValidator.MUNICIPAL_SERVICE_ZONES.firstOrNull { it.id == zoneId }?.displayName
        ?: zoneId.replace("zone_", "").replace("_", " ").replaceFirstChar { it.uppercase() }
}
