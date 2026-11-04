package com.waste.link.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.waste.link.ui.admin.AdminShellScreen
import com.waste.link.ui.auth.AuthState
import com.waste.link.ui.auth.AuthViewModel
import com.waste.link.ui.auth.LoginScreen
import com.waste.link.ui.auth.RegisterScreen

object Destinations {
    const val CHECK_AUTH = "check_auth"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val RESIDENT_HOME = "resident_home"
    const val COLLECTOR_DASHBOARD = "collector_dashboard"
    const val ADMIN_SHELL = "admin_shell"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
}

@Composable
fun WasteLinkNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.checkCurrentUser()
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Idle -> {
                if (!authViewModel.isUserSignedIn()) {
                    if (navController.currentDestination?.route != Destinations.LOGIN && 
                        navController.currentDestination?.route != Destinations.REGISTER) {
                        navController.navigate(Destinations.LOGIN) {
                            popUpTo(0) // clear backstack
                        }
                    }
                }
            }
            is AuthState.Authenticated -> {
                val route = when (state.user.role) {
                    "admin" -> Destinations.RESIDENT_HOME // Admins start as normal users
                    "collector" -> Destinations.COLLECTOR_DASHBOARD
                    "unassigned" -> Destinations.ONBOARDING
                    else -> Destinations.RESIDENT_HOME
                }
                navController.navigate(route) {
                    popUpTo(0) // clear backstack
                }
            }
            else -> {}
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destinations.CHECK_AUTH,
        modifier = modifier.then(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF0F1115))),
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)) }
    ) {
        composable(Destinations.CHECK_AUTH) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            }
        }

        composable(Destinations.LOGIN) {
            val context = androidx.compose.ui.platform.LocalContext.current
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate("register")
                },
                onLoginClick = { email, password ->
                    authViewModel.signIn(email, password)
                },
                onForgotPasswordClick = { email ->
                    if (email.isBlank()) {
                        android.widget.Toast.makeText(context, "Please enter your email first", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        authViewModel.sendPasswordResetEmail(email) { success, error ->
                            val msg = if (success) "Password reset email sent" else "Error: $error"
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                },
                authState = authState
            )
        }

        composable(Destinations.REGISTER) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterClick = { email, password, name, phone ->
                    authViewModel.signUp(email, password, name, phone)
                },
                authState = authState
            )
        }

        composable(Destinations.ONBOARDING) {
            com.waste.link.ui.auth.OnboardingScreen(
                onRoleSelected = { role ->
                    authViewModel.setRole(role)
                },
                authState = authState
            )
        }

        composable(Destinations.RESIDENT_HOME) {
            com.waste.link.ui.MainScreen(
                rootNavController = navController,
                onNewRequest = { /* Handle request */ },
                onNavigateToAdmin = { navController.navigate(Destinations.ADMIN_SHELL) }
            )
        }

        composable(Destinations.SETTINGS) {
            com.waste.link.ui.settings.SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("resident_tracking") {
            com.waste.link.ui.resident.ResidentTrackingScreen()
        }

        composable(Destinations.COLLECTOR_DASHBOARD) {
            com.waste.link.ui.collector.CollectorShellScreen(
                rootNavController = navController,
                onAcceptMission = { navController.navigate("collector_mission") }
            )
        }

        composable("collector_mission") {
            com.waste.link.ui.collector.CollectorMissionScreen(
                onFinishJob = { navController.popBackStack() }
            )
        }

        composable(Destinations.ADMIN_SHELL) {
            AdminShellScreen(
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0)
                    }
                }
            )
        }
    }
}
