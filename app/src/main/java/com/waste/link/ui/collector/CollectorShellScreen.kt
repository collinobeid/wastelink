package com.waste.link.ui.collector

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun CollectorShellScreen(
    rootNavController: NavHostController,
    onAcceptMission: () -> Unit
) {
    val bnbNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F1115),
                contentColor = Color.White
            ) {
                val navBackStackEntry by bnbNavController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val items = listOf(
                    Triple("Missions", Icons.Default.Map, "missions"),
                    Triple("Earnings", Icons.Default.AttachMoney, "earnings"),
                    Triple("Profile", Icons.Default.Person, "profile")
                )

                items.forEach { (title, icon, route) ->
                    val selected = currentDestination?.hierarchy?.any { it.route == route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            bnbNavController.navigate(route) {
                                popUpTo(bnbNavController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = title) },
                        label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = Color.White.copy(alpha = 0.5f),
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = bnbNavController,
            startDestination = "missions",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F1115)),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) }
        ) {
            composable("missions") {
                CollectorDashboardScreen(onAcceptMission = onAcceptMission)
            }
            composable("earnings") {
                CollectorEarningsScreen()
            }
            composable("profile") {
                CollectorProfileScreen(
                    onNavigateToSettings = { rootNavController.navigate("settings") }
                )
            }
        }
    }
}
