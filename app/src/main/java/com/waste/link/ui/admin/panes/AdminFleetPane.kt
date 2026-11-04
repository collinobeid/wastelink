package com.waste.link.ui.admin.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import com.waste.link.data.models.AppUser
import com.waste.link.data.models.WasteRequest
import com.waste.link.ui.admin.AdminFleetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFleetPane() {
    val viewModel: AdminFleetViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    var selectedRequestForAssignment by remember { mutableStateOf<WasteRequest?>(null) }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val cameraPositionState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(longitude = 39.191, latitude = -6.162),
            zoom = 10.0
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Section 0: Fleet Map removed. Use Live Map tab.

        // Section 1: Verification Queue
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Collector Verification Queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (uiState.unverifiedCollectors.isEmpty()) {
                Text("No pending verifications.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        items(uiState.unverifiedCollectors) { collector ->
            VerificationCard(
                collector = collector,
                onApprove = { viewModel.approveCollector(collector.uid) },
                onReject = { viewModel.rejectCollector(collector.uid) }
            )
        }
        
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        // Section 2: Route Assignment
        item {
            Text("Pending Route Assignments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (uiState.pendingRequests.isEmpty()) {
                Text("No pending requests to assign.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        items(uiState.pendingRequests) { request ->
            PendingRequestCard(
                request = request,
                onClick = { selectedRequestForAssignment = request }
            )
        }
        
        item { Spacer(modifier = Modifier.height(64.dp)) }
    }

    // Assignment Bottom Sheet
    if (selectedRequestForAssignment != null) {
        AssignmentBottomSheet(
            request = selectedRequestForAssignment!!,
            activeCollectors = uiState.activeCollectors,
            onDismiss = { selectedRequestForAssignment = null },
            onAssign = { collectorId ->
                viewModel.assignRequest(selectedRequestForAssignment!!.id, collectorId)
                selectedRequestForAssignment = null
            }
        )
    }
}

@Composable
fun VerificationCard(collector: AppUser, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(collector.name.ifEmpty { "Unknown Name" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(collector.phone, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject")
                }
                
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
}

@Composable
fun PendingRequestCard(request: WasteRequest, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(request.pickupAddress.ifEmpty { "Unknown Address" }, fontWeight = FontWeight.SemiBold)
                Text("${request.category} • ${request.volumeTier}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClick) {
                Text("Assign")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentBottomSheet(
    request: WasteRequest,
    activeCollectors: List<AppUser>,
    onDismiss: () -> Unit,
    onAssign: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Assign Collector", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("For request at ${request.pickupAddress.ifEmpty { "Unknown Address" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (activeCollectors.isEmpty()) {
                Text("No active collectors available. Please verify collectors first.", color = MaterialTheme.colorScheme.error)
            } else {
                LazyColumn {
                    items(activeCollectors) { collector ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAssign(collector.uid) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(collector.name.ifEmpty { "Unknown" }, fontWeight = FontWeight.Medium)
                                Text(collector.phone, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Check, tint = MaterialTheme.colorScheme.primary, contentDescription = null)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
