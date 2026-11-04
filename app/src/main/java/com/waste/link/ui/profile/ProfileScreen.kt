package com.waste.link.ui.profile

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.waste.link.data.models.AppUser
import com.waste.link.data.models.WasteRequest
import com.waste.link.ui.admin.components.DashboardMetricCard
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    isAdmin: Boolean = false,
    onNavigateToAdmin: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onWhatsAppClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val user = uiState.userProfile ?: return@Scaffold
        val effectiveRole = if (isAdmin && user.role != "admin") "admin" else user.role

        var showLogoutSheet by remember { mutableStateOf(false) }
        var showDeleteSheet by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        val logoutSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val deleteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Profile Header (Centered)
            Spacer(modifier = Modifier.height(24.dp))
            ProfileHeaderCentered(user, effectiveRole, onUpdateProfile = { name, phone ->
                viewModel.updateProfile(name, phone)
            })

            Spacer(modifier = Modifier.height(24.dp))

            // Stats Row
            StatsRow(user, effectiveRole, uiState.wasteRequests.size)

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Settings", color = MaterialTheme.colorScheme.onSurface)
                }

                Button(
                    onClick = { showLogoutSheet = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Language Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val currentLocales = AppCompatDelegate.getApplicationLocales()
                val isSwahili = currentLocales.toLanguageTags().contains("sw")
                
                Button(
                    onClick = { 
                        val newLocale = if (isSwahili) "en" else "sw"
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLocale))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSwahili) "Switch to English" else "Badili lugha (Swahili)", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Admin Panel Button (Full Width)
            if (effectiveRole == "admin") {
                Button(
                    onClick = onNavigateToAdmin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Admin Panel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Tabs for Content
            var selectedTab by remember { mutableIntStateOf(0) }
            val tabs = if (effectiveRole == "collector") listOf("Performance", "Routes") else listOf("Active Requests", "History")

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    )
                }
            }

            // Tab Content
            Box(modifier = Modifier
                .weight(1f)
                .padding(16.dp)) {
                when (effectiveRole) {
                    "collector" -> {
                        if (selectedTab == 0) {
                            CollectorDashboard(user, uiState.totalPickups, uiState.activeRoutes)
                        } else {
                            Text("No Active Routes", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        val activeRequests = uiState.wasteRequests.filter { it.status != "COMPLETED" && it.status != "CANCELLED" }
                        val pastRequests = uiState.wasteRequests.filter { it.status == "COMPLETED" || it.status == "CANCELLED" }
                        
                        if (selectedTab == 0) {
                            ResidentPortfolio(user, activeRequests, scope)
                        } else {
                            ResidentPortfolio(user, pastRequests, scope)
                        }
                    }
                }
            }
        }

        // Modals (Logout, Delete)
        if (showLogoutSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLogoutSheet = false },
                sheetState = logoutSheetState
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    Text("Logout", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Are you sure you want to log out of your account?")
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            scope.launch { logoutSheetState.hide() }
                            showLogoutSheet = false
                            Toast.makeText(context, "Logout clicked", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Yes, Logout")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileHeaderCentered(user: AppUser, effectiveRole: String, onUpdateProfile: (String, String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular Avatar with primary border
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = user.name.take(1).uppercase(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = user.name.ifEmpty { "Unknown User" }.uppercase(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (user.status == "ACTIVE") {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Verified, contentDescription = "Verified", tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
            }
        }

        // Email / Role subtitle
        Text(
            text = "${effectiveRole.uppercase()} • ${user.email}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatsRow(user: AppUser, role: String, requestCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(
            value = if (role == "collector") "12" else requestCount.toString(),
            label = if (role == "collector") "Pickups" else "Requests"
        )
        StatItem(
            value = if (user.status == "ACTIVE") "1" else "0",
            label = "Active"
        )
        StatItem(
            value = "4.9★",
            label = "Rating"
        )
    }
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResidentPortfolio(user: AppUser, wasteRequests: List<WasteRequest>, scope: kotlinx.coroutines.CoroutineScope) {
    var showRatingSheet by remember { mutableStateOf<WasteRequest?>(null) }
    val ratingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rating by remember { mutableStateOf(0) }
    
    if (user.status == "UNVERIFIED") {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Verification Required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Please upload your ID to start requesting waste collection.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { /* Launch verification flow */ }) {
                    Text("Verify Now")
                }
            }
        }
    } else {
        if (wasteRequests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No items found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(wasteRequests) { request ->
                    WasteRequestCard(request) {
                        if (request.status == "COMPLETED") {
                            showRatingSheet = request
                            rating = 0
                        }
                    }
                }
            }
        }
    }

    if (showRatingSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { showRatingSheet = null },
            sheetState = ratingSheetState
        ) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Rate your Collector", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("How was your waste collection service?")
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { star ->
                        Icon(
                            if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { rating = star }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        scope.launch { ratingSheetState.hide() }
                        showRatingSheet = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = rating > 0
                ) {
                    Text("Submit Rating")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun WasteRequestCard(request: WasteRequest, onClick: () -> Unit = {}) {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateStr = request.createdAt?.let { formatter.format(it) } ?: "Just now"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = request.category.ifEmpty { "General Waste" }, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = request.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = request.pickupAddress, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "Requested on $dateStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CollectorDashboard(user: AppUser, totalPickups: Int, activeRoutes: Int) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardMetricCard("Total Pickups", totalPickups.toString(), Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            DashboardMetricCard("Active Routes", activeRoutes.toString(), Icons.Default.DirectionsCar, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { /* Launch Route Planning Flow */ }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create New Route")
        }
    }
}
