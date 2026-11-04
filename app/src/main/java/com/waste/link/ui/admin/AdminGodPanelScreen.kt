package com.waste.link.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.waste.link.data.models.AppUser
import com.waste.link.data.models.OperationalUnit
import com.waste.link.data.models.ServiceDispute
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminGodPanelScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminGodPanelViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val users by viewModel.users.collectAsState()
    val units by viewModel.operationalUnits.collectAsState()
    val disputes by viewModel.disputes.collectAsState()

    var selectedUser by remember { mutableStateOf<AppUser?>(null) }
    var selectedUnit by remember { mutableStateOf<OperationalUnit?>(null) }
    var showOnlyNeedsAttention by remember { mutableStateOf(false) }

    val userSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val unitSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column {
                // Gradient Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFE11D48), Color(0xFF9F1239))
                            )
                        )
                        .padding(top = 48.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "⭐ GOD MODE",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Admin Control Panel",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                // Warning Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEE2E2))
                        .padding(vertical = 12.dp, horizontal = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "You have full administrative privileges. Use responsibly.",
                            color = Color(0xFF991B1B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs
            var selectedTab by remember { mutableIntStateOf(0) }
            val tabs = listOf("Users", "Fleet", "Disputes")

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

            // Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text("Needs Attention", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = showOnlyNeedsAttention,
                    onCheckedChange = { showOnlyNeedsAttention = it },
                    modifier = Modifier.scale(0.8f)
                )
            }

            Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                when (selectedTab) {
                    0 -> {
                        val filteredUsers = if (showOnlyNeedsAttention) {
                            users.filter { it.status == "UNVERIFIED" || disputes.any { d -> d.dispute.status == "PENDING_REVIEW" && (d.dispute.disputedEntityId == it.uid || d.dispute.reporterId == it.uid) } }
                        } else {
                            users
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            items(filteredUsers) { user ->
                                UserListItem(
                                    user = user,
                                    onClick = {
                                        selectedUser = user
                                        scope.launch { userSheetState.show() }
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                    1 -> {
                        val filteredUnits = if (showOnlyNeedsAttention) {
                            units.filter { it.status == "DRAFT" }
                        } else {
                            units
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            items(filteredUnits) { unit ->
                                FleetListItem(
                                    unit = unit,
                                    onClick = {
                                        selectedUnit = unit
                                        scope.launch { unitSheetState.show() }
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                    2 -> {
                        val filteredDisputes = if (showOnlyNeedsAttention) {
                            disputes.filter { it.dispute.status == "PENDING_REVIEW" }
                        } else {
                            disputes
                        }
                        DisputesList(disputes = filteredDisputes)
                    }
                }
            }
        }
        
        // Bottom Sheets for details
        if (selectedUser != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedUser = null },
                sheetState = userSheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                IdentityComplianceContent(
                    user = selectedUser!!,
                    onUpdateStatus = { status, banUntil ->
                        viewModel.updateUserStatus(selectedUser!!.uid, status, banUntil)
                        scope.launch { userSheetState.hide() }.invokeOnCompletion {
                            selectedUser = null
                        }
                    }
                )
            }
        }

        if (selectedUnit != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedUnit = null },
                sheetState = unitSheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                FleetDetailContent(
                    unit = selectedUnit!!,
                    onUpdateStatus = { unitId, newStatus ->
                        viewModel.updateFleetStatus(unitId, newStatus)
                        scope.launch { unitSheetState.hide() }.invokeOnCompletion {
                            selectedUnit = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun UserListItem(
    user: AppUser,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = user.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "${user.role.uppercase()} • ${user.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FleetListItem(
    unit: OperationalUnit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = unit.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "Plate: ${unit.licensePlate} • Status: ${unit.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IdentityComplianceContent(
    user: AppUser,
    onUpdateStatus: (String, Long?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("User Controls: ${user.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Current Status: ${user.status}", fontWeight = FontWeight.Bold)
                Text("Role: ${user.role}")
                Text("Email: ${user.email}")
            }
        }
        
        HorizontalDivider()
        Text("Nuclear Launch Codes", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onUpdateStatus("ACTIVE", null) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                ) {
                    Text("Activate")
                }
                Button(
                    onClick = { onUpdateStatus("RESTRICTED", null) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308))
                ) {
                    Text("Restrict")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { 
                        val banUntil = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000)
                        onUpdateStatus("SUSPENDED", banUntil) 
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
                ) {
                    Text("Suspend")
                }
                Button(
                    onClick = { onUpdateStatus("BANNED", null) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ban")
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FleetDetailContent(
    unit: OperationalUnit,
    onUpdateStatus: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Fleet Unit: ${unit.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Plate: ${unit.licensePlate} • Status: ${unit.status}")
                Text("Pickups: ${unit.totalPickups}")
            }
        }
        
        HorizontalDivider()
        Text("Operations Control", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (unit.status == "DRAFT") {
                Button(
                    onClick = { onUpdateStatus(unit.id, "ACTIVE") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                ) {
                    Text("Approve Unit")
                }
            } else {
                Button(
                    onClick = { onUpdateStatus(unit.id, "GROUNDED") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ground Unit")
                }
                if (unit.status == "GROUNDED") {
                    Button(
                        onClick = { onUpdateStatus(unit.id, "ACTIVE") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                    ) {
                        Text("Reactivate")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DisputesList(disputes: List<HydratedServiceDispute>) {
    if (disputes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No disputes reported.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(disputes) { hydrated ->
                val dispute = hydrated.dispute
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${hydrated.reporterName} reported ${hydrated.targetEntityName}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Reason: ${dispute.reason}", color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Status: ${dispute.status}", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
