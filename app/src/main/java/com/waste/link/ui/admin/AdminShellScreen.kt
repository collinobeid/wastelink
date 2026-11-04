package com.waste.link.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.waste.link.ui.admin.panes.AdminClientsPane
import com.waste.link.ui.admin.panes.AdminDisputesPane
import com.waste.link.ui.admin.panes.AdminFleetPane
import com.waste.link.ui.admin.panes.AdminOverviewPane
import com.waste.link.ui.admin.AdminMapViewScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminShellScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: AdminDashboardViewModel = hiltViewModel()
    var activeTab by remember { mutableStateOf("Live Map") }
    val tabs = listOf("Live Map", "Overview", "Clients", "Fleet & Routes", "Disputes")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Console", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = { viewModel.fetchDashboardData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Data", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Contextual Tabs
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(activeTab),
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = { 
                            Text(
                                text = tab, 
                                fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Master-Detail Content Rendering
            when (activeTab) {
                "Live Map" -> AdminMapViewScreen()
                "Overview" -> AdminOverviewPane(viewModel)
                "Clients" -> AdminClientsPane()
                "Fleet & Routes" -> AdminFleetPane()
                "Disputes" -> AdminDisputesPane()
            }
        }
    }
}
