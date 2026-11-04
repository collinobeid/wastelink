package com.waste.link.ui.collector.panes

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import com.waste.link.data.models.WasteRequest
import com.waste.link.ui.collector.CollectorMissionViewModel
import com.waste.link.util.ImageUtil

@Composable
fun CollectorMissionPane() {
    val viewModel: CollectorMissionViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    var requestToComplete by remember { mutableStateOf<WasteRequest?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null && requestToComplete != null) {
            viewModel.updateRequestStatus(requestToComplete!!.id, "COMPLETED", bitmap)
            requestToComplete = null
        }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.isSimulatingGps) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("GPS Simulation Active (En Route)", color = MaterialTheme.colorScheme.onTertiary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Live Route Queue",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = { viewModel.optimizeRoute() }) {
                Text("Optimize Route")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.activeRequests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No active requests. You're all caught up!", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.activeRequests) { request ->
                    MissionCard(
                        request = request,
                        onUpdateStatus = { newStatus ->
                            if (newStatus == "COMPLETED") {
                                requestToComplete = request
                                cameraLauncher.launch(null) // Require POC photo
                            } else {
                                viewModel.updateRequestStatus(request.id, newStatus)
                            }
                        },
                        onMoveToTop = { viewModel.moveToTop(request.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun MissionCard(request: WasteRequest, onUpdateStatus: (String) -> Unit, onMoveToTop: () -> Unit = {}) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (request.status == "EN_ROUTE") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn, 
                    contentDescription = null, 
                    tint = if (request.status == "EN_ROUTE") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    request.pickupAddress.ifEmpty { "Unknown Address" }, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 18.sp,
                    color = if (request.status == "EN_ROUTE") MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Ref: ${request.requestRef} • ${request.category} • ${request.volumeTier}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status-based actions
            when (request.status) {
                "ASSIGNED_TO_FLEET" -> {
                    Button(
                        onClick = { 
                            onUpdateStatus("EN_ROUTE") 
                            val gmmIntentUri = Uri.parse("google.navigation:q=${request.lat},${request.lng}")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                            mapIntent.setPackage("com.google.android.apps.maps")
                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(mapIntent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Navigation, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Route")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onMoveToTop,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Prioritize (Move to Top)")
                    }
                }
                "EN_ROUTE" -> {
                    Button(
                        onClick = { 
                            onUpdateStatus("ARRIVED_AT_LOCATION") 
                            // Simulate local notification
                            android.widget.Toast.makeText(context, "Resident Notified of Arrival", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("Mark Arrived")
                    }
                }
                "ARRIVED_AT_LOCATION" -> {
                    Button(
                        onClick = { onUpdateStatus("COMPLETED") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Capture Proof & Complete")
                    }
                }
                else -> {
                    Text("Status: ${request.status}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
