package com.waste.link.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.waste.link.ui.theme.AppBgFrom
import com.waste.link.ui.theme.AppBgMid
import com.waste.link.ui.theme.AppBgTo
import com.waste.link.util.NetworkConnectivityObserver

@Composable
fun MainScaffold(
    navController: NavHostController,
    content: @Composable (Modifier) -> Unit
) {
    val bgBrush = Brush.verticalGradient(
        colors = listOf(AppBgFrom, AppBgMid, AppBgTo)
    )
    val context = LocalContext.current
    val isOnline by remember(context) {
        NetworkConnectivityObserver(context).isOnline
    }.collectAsState(initial = true)

    Box(modifier = Modifier
        .fillMaxSize()
        .background(brush = bgBrush)) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                WasteLinkBNB(navController = navController)
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                content(Modifier)
            }
        }
        
        AnimatedVisibility(
            visible = !isOnline,
            enter = slideInVertically(animationSpec = tween(300)) { -it },
            exit = slideOutVertically(animationSpec = tween(300)) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFBA1A1A)) // LightDestructive equivalent
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("No internet connection", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
