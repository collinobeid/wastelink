package com.waste.link.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.waste.link.ui.components.MainScaffold
import com.waste.link.ui.components.WasteLinkMap
import com.waste.link.ui.navigation.Screen
import com.waste.link.ui.history.HistoryScreen
import com.waste.link.ui.profile.ProfileScreen
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import androidx.compose.runtime.LaunchedEffect

@Composable
fun MainScreen(
    rootNavController: NavHostController,
    onNewRequest: () -> Unit = {},
    onNavigateToAdmin: () -> Unit = {}
) {
    val bnbNavController = rememberNavController()
    val authViewModel: com.waste.link.ui.auth.AuthViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val authState by authViewModel.authState.collectAsState()
    val isAdmin = (authState as? com.waste.link.ui.auth.AuthState.Authenticated)?.user?.role == "admin"

    LaunchedEffect(Unit) {
        authViewModel.checkCurrentUser()
    }

    MainScaffold(navController = bnbNavController) {
        NavHost(
            navController = bnbNavController,
            startDestination = Screen.Home.route,
            modifier = androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF0F1115)),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) }
        ) {
            composable(Screen.Home.route) {
                com.waste.link.ui.resident.ResidentHomeScreen(
                    onNavigateToTracking = {
                        rootNavController.navigate("resident_tracking")
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen()
            }
            composable(Screen.Map.route) {
                MapScreen()
            }
            composable(Screen.Alerts.route) {
                AlertsScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    isAdmin = isAdmin,
                    onNavigateToAdmin = onNavigateToAdmin,
                    onNavigateToSettings = { rootNavController.navigate("settings") },
                    onWhatsAppClick = {
                        // TODO: Intent to WhatsApp
                    }
                )
            }
        }
    }
}

@Composable
fun MapScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        WasteLinkMap(
            modifier = Modifier.fillMaxSize(),
            isTrackingLocation = true,
            activeRequests = emptyList(), // For now, just show the user's location
            onMapClick = { _, _ -> org.maplibre.compose.util.ClickResult.Pass }
        )
    }
}

@Composable
fun AlertsScreen() {
    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0F1115)), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Default.Notifications,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            Text("No new alerts", color = androidx.compose.ui.graphics.Color.White, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        }
    }
}
