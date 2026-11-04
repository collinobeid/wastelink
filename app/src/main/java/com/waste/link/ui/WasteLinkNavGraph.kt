package com.waste.link.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.OnboardingStatus
import com.waste.link.domain.model.UserRole
import com.waste.link.ui.auth.*
import com.waste.link.ui.navigation.Destinations
import com.waste.link.ui.placeholders.AdminShellPlaceholder
import com.waste.link.ui.placeholders.CollectorDashboardPlaceholder
import com.waste.link.ui.placeholders.ResidentHomePlaceholder
import com.waste.link.ui.theme.DarkBackground
import com.waste.link.ui.theme.DarkMutedForeground
import com.waste.link.ui.theme.EmeraldGreen

@Composable
fun WasteLinkNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.restoreSessionOnLaunch()
    }

    LaunchedEffect(navController, authViewModel) {
        authViewModel.navigationEvents.collect { event ->
            when (event) {
                is AuthNavigationEvent.NavigateToLogin -> {
                    val prefill = event.prefillEmail
                    if (!prefill.isNullOrBlank()) {
                        authViewModel.onLoginEmailChanged(prefill)
                    }
                    if (event.clearBackStack) {
                        navController.navigate(Destinations.LOGIN) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        val popped = navController.popBackStack(Destinations.LOGIN, inclusive = false)
                        if (!popped) {
                            navController.navigate(Destinations.LOGIN) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
                is AuthNavigationEvent.NavigateToRegister -> {
                    if (navController.currentDestination?.route != Destinations.REGISTER) {
                        navController.navigate(Destinations.REGISTER) {
                            launchSingleTop = true
                        }
                    }
                }
                is AuthNavigationEvent.NavigateToVerifyEmail -> {
                    navController.navigate(Destinations.VERIFY_EMAIL) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is AuthNavigationEvent.NavigateToCompleteProfile -> {
                    navController.navigate(Destinations.COMPLETE_PROFILE) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is AuthNavigationEvent.NavigateToOnboarding -> {
                    navController.navigate(Destinations.ONBOARDING) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is AuthNavigationEvent.NavigateToCollectorVerificationStatus -> {
                    navController.navigate(Destinations.COLLECTOR_VERIFICATION_STATUS) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is AuthNavigationEvent.NavigateToDestination -> {
                    navController.navigate(event.route) {
                        if (event.clearBackStack) {
                            popUpTo(0) { inclusive = true }
                        }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destinations.CHECK_AUTH,
        modifier = modifier.background(DarkBackground),
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(320)
            ) + fadeIn(animationSpec = tween(320))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(320)
            ) + fadeOut(animationSpec = tween(320))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(320)
            ) + fadeIn(animationSpec = tween(320))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(320)
            ) + fadeOut(animationSpec = tween(320))
        }
    ) {
        composable(Destinations.CHECK_AUTH) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = EmeraldGreen,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Verifying WasteLink session...",
                        color = DarkMutedForeground,
                        fontSize = 13.sp
                    )
                }
            }
        }

        composable(Destinations.LOGIN) {
            LoginScreen(
                uiState = uiState,
                onEmailChanged = authViewModel::onLoginEmailChanged,
                onPasswordEdited = authViewModel::onLoginPasswordEdited,
                onSignInClick = authViewModel::submitLogin,
                onForgotPasswordClick = authViewModel::openForgotPasswordDialog,
                onDismissForgotPassword = authViewModel::dismissForgotPasswordDialog,
                onPasswordResetEmailChanged = authViewModel::onPasswordResetEmailChanged,
                onSubmitPasswordReset = authViewModel::submitPasswordReset,
                onNavigateToRegister = {
                    if (navController.currentDestination?.route != Destinations.REGISTER) {
                        navController.navigate(Destinations.REGISTER) {
                            launchSingleTop = true
                        }
                    }
                },
                onRetryProfileLoad = authViewModel::retryProfileLoadAfterLogin,
                onSignOutLockedAccount = authViewModel::logout
            )
        }

        composable(Destinations.REGISTER) {
            RegisterScreen(
                uiState = uiState,
                onRoleSelected = authViewModel::onRoleSelected,
                onFullNameChanged = authViewModel::onRegFullNameChanged,
                onEmailChanged = authViewModel::onRegEmailChanged,
                onPhoneChanged = authViewModel::onRegPhoneChanged,
                onPasswordChanged = authViewModel::onRegPasswordChanged,
                onConfirmPasswordChanged = authViewModel::onRegConfirmPasswordChanged,
                onRegisterSubmit = { fullName, email, phone, password, confirmPassword ->
                    authViewModel.submitRegistration(
                        rawFullName = fullName,
                        rawEmail = email,
                        rawPhone = phone,
                        rawPassword = password,
                        rawConfirmPassword = confirmPassword
                    )
                },
                onRetryProfileSetup = authViewModel::retrySameSessionProfileInitialization,
                onAbortRecoverySignOut = authViewModel::logout,
                onNavigateToLogin = {
                    val popped = navController.popBackStack(Destinations.LOGIN, inclusive = false)
                    if (!popped) {
                        navController.navigate(Destinations.LOGIN) {
                            launchSingleTop = true
                        }
                    }
                },
                onNavigateToLoginWithDuplicateEmail = authViewModel::navigateToLoginFromDuplicateEmail
            )
        }

        composable(Destinations.VERIFY_EMAIL) {
            VerifyEmailScreen(
                uiState = uiState,
                onRefreshVerificationStatus = authViewModel::refreshEmailVerificationStatus,
                onResendVerificationEmail = authViewModel::resendVerificationEmail,
                onContinueAfterVerification = authViewModel::continueFromEmailVerification,
                onLogoutClick = authViewModel::logout
            )
        }

        composable(Destinations.COMPLETE_PROFILE) {
            CompleteProfileScreen(
                uiState = uiState,
                onRoleSelected = authViewModel::onRecoveryRoleSelected,
                onFullNameChanged = authViewModel::onRecoveryFullNameChanged,
                onPhoneChanged = authViewModel::onRecoveryPhoneChanged,
                onSubmitRecovery = { fullName, phone, role ->
                    authViewModel.submitMissingProfileRecovery(
                        rawFullName = fullName,
                        rawPhone = phone,
                        role = role
                    )
                },
                onLogoutClick = authViewModel::logout
            )
        }

        composable(Destinations.ONBOARDING) {
            val user = uiState.currentUser
            if (user == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else if (user.accountStatus == AccountStatus.SUSPENDED || user.accountStatus == AccountStatus.BANNED) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.ACCOUNT_STATUS_LOCK) {
                        popUpTo(Destinations.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else if (user.role == UserRole.RESIDENT && user.onboardingStatus == OnboardingStatus.COMPLETED) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.RESIDENT_HOME) {
                        popUpTo(Destinations.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else if (user.role == UserRole.COLLECTOR && user.collectorVerificationStatus == CollectorVerificationStatus.APPROVED) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.COLLECTOR_DASHBOARD) {
                        popUpTo(Destinations.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else {
                OnboardingScreen(
                    uiState = uiState,
                    onResidentZoneChanged = authViewModel::onResidentZoneChanged,
                    onResidentAddressChanged = authViewModel::onResidentAddressChanged,
                    onCompleteResidentOnboarding = { authViewModel.completeResidentOnboarding() },
                    onCollectorVehicleTypeChanged = authViewModel::onCollectorVehicleTypeChanged,
                    onCollectorPlateNumberChanged = authViewModel::onCollectorPlateNumberChanged,
                    onCollectorLicenseNumberChanged = authViewModel::onCollectorLicenseNumberChanged,
                    onCollectorServiceZoneChanged = authViewModel::onCollectorServiceZoneChanged,
                    onCollectorReviewToggled = authViewModel::onCollectorReviewToggled,
                    onSubmitCollectorOnboarding = authViewModel::submitCollectorOnboarding,
                    onResubmitCollectorOnboarding = authViewModel::resubmitCollectorOnboarding,
                    onCompleteOnboarding = { authViewModel.completeStage4Onboarding() },
                    onLogoutClick = authViewModel::logout
                )
            }
        }

        composable(Destinations.COLLECTOR_VERIFICATION_STATUS) {
            val user = uiState.currentUser
            if (user == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else if (user.accountStatus == AccountStatus.SUSPENDED || user.accountStatus == AccountStatus.BANNED) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.ACCOUNT_STATUS_LOCK) {
                        popUpTo(Destinations.COLLECTOR_VERIFICATION_STATUS) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else if (user.role == UserRole.RESIDENT) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.RESIDENT_HOME) {
                        popUpTo(Destinations.COLLECTOR_VERIFICATION_STATUS) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else {
                CollectorVerificationStatusScreen(
                    uiState = uiState,
                    onRefreshStatus = authViewModel::refreshCollectorVerificationStatus,
                    onEditAndResubmit = authViewModel::startCollectorEditAndResubmit,
                    onNavigateToDashboard = {
                        navController.navigate(Destinations.COLLECTOR_DASHBOARD) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToOnboarding = {
                        navController.navigate(Destinations.ONBOARDING) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onLogoutClick = authViewModel::logout
                )
            }
        }

        composable(Destinations.ACCOUNT_STATUS_LOCK) {
            AccountStatusLockScreen(
                uiState = uiState,
                onRefreshStatus = authViewModel::refreshAccountStatus,
                onContactSupport = {
                    // Formal support / compliance inquiry handled via system intent or support dialog
                },
                onLogoutClick = authViewModel::logout
            )
        }

        composable(Destinations.RESIDENT_HOME) {
            val user = uiState.currentUser
            if (user == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else if (user.accountStatus == AccountStatus.SUSPENDED || user.accountStatus == AccountStatus.BANNED) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.ACCOUNT_STATUS_LOCK) {
                        popUpTo(Destinations.RESIDENT_HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else if (user.role == UserRole.COLLECTOR) {
                LaunchedEffect(Unit) {
                    val fallback = if (user.collectorVerificationStatus == CollectorVerificationStatus.APPROVED) {
                        Destinations.COLLECTOR_DASHBOARD
                    } else {
                        Destinations.COLLECTOR_VERIFICATION_STATUS
                    }
                    navController.navigate(fallback) {
                        popUpTo(Destinations.RESIDENT_HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else if (user.role == UserRole.RESIDENT && user.onboardingStatus != OnboardingStatus.COMPLETED) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.ONBOARDING) {
                        popUpTo(Destinations.RESIDENT_HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else {
                ResidentHomePlaceholder(
                    user = user,
                    onLogout = authViewModel::logout
                )
            }
        }

        composable(Destinations.COLLECTOR_DASHBOARD) {
            val user = uiState.currentUser
            if (user == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else if (user.accountStatus == AccountStatus.SUSPENDED || user.accountStatus == AccountStatus.BANNED) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.ACCOUNT_STATUS_LOCK) {
                        popUpTo(Destinations.COLLECTOR_DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else if (user.role == UserRole.RESIDENT) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.RESIDENT_HOME) {
                        popUpTo(Destinations.COLLECTOR_DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else if (user.role == UserRole.COLLECTOR &&
                user.collectorVerificationStatus != CollectorVerificationStatus.APPROVED
            ) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destinations.COLLECTOR_VERIFICATION_STATUS) {
                        popUpTo(Destinations.COLLECTOR_DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else {
                CollectorDashboardPlaceholder(
                    user = user,
                    onLogout = authViewModel::logout
                )
            }
        }

        composable(Destinations.ADMIN_SHELL) {
            if (!uiState.hasAdminClaim || uiState.currentUser?.role != UserRole.ADMIN) {
                LaunchedEffect(Unit) {
                    val fallback = when (uiState.currentUser?.role) {
                        UserRole.COLLECTOR -> Destinations.COLLECTOR_DASHBOARD
                        UserRole.RESIDENT -> Destinations.RESIDENT_HOME
                        else -> Destinations.LOGIN
                    }
                    navController.navigate(fallback) {
                        popUpTo(Destinations.ADMIN_SHELL) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else {
                AdminShellPlaceholder(
                    user = uiState.currentUser,
                    onLogout = authViewModel::logout
                )
            }
        }
    }
}
