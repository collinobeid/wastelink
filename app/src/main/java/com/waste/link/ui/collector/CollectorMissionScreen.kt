package com.waste.link.ui.collector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.input.KeyboardType
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.waste.link.ui.components.GlassCard
import com.waste.link.ui.components.SwipeToActionButton

@Composable
fun CollectorMissionScreen(
    onFinishJob: () -> Unit,
    viewModel: CollectorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentMission = uiState.currentMission

    var showPriceDialog by remember { mutableStateOf(false) }
    var agreedPriceInput by remember { mutableStateOf("") }


    val targetLat = currentMission?.lat ?: 0.0
    val targetLng = currentMission?.lng ?: 0.0

    val cameraPositionState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(longitude = targetLng, latitude = targetLat),
            zoom = 15.0
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Live MapLibre Map
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraPositionState,
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/bright")
        ) {
            // Marker for Pickup Location
            if (currentMission != null) {
                // Symbols will be added
            }
        }

        // Top Navigation Banner
        GlassCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = 32.dp),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = Color(0xFF16181C).copy(alpha = 0.95f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text("Turn right onto", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Text(currentMission?.pickupAddress ?: "Destination", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                
                Text("En Route", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        // Bottom Action Sheet
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF16181C).copy(alpha = 0.95f),
                shape = RoundedCornerShape(32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Customer Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(uiState.currentResidentName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("${currentMission?.category} Waste • ${currentMission?.volumeTier}", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                        
                        val context = androidx.compose.ui.platform.LocalContext.current
                        IconButton(
                            onClick = { 
                                val phone = currentMission?.residentPhoneNumber ?: "+255000000000"
                                val reqId = currentMission?.id?.take(5) ?: "Unknown"
                                val msg = "Hello! I am your WasteLink Collector. I'm en route for pickup request #$reqId."
                                com.waste.link.utils.WhatsAppUtils.openWhatsAppChat(context, phone, msg)
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF25D366))
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "WhatsApp Chat", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Gate Photo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!currentMission?.gatePhotoUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = currentMission?.gatePhotoUrl,
                                contentDescription = "Gate Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
                                Text("Gate Photo: (Not provided)", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Swipe to Action
                    SwipeToActionButton(
                        text = if (currentMission?.status == "EN_ROUTE") "SWIPE TO ARRIVE" else "SWIPE TO COMPLETE",
                        onSwipeComplete = {
                            if (currentMission?.status == "EN_ROUTE") {
                                viewModel.updateMissionStatus("ARRIVED_AT_LOCATION") {}
                            } else {
                                showPriceDialog = true
                            }
                        }
                    )
                }
            }
        }
        
        if (showPriceDialog) {
            AlertDialog(
                onDismissRequest = { showPriceDialog = false },
                title = { Text("Equilibrium Pricing") },
                text = {
                    Column {
                        Text("Enter the final agreed price with the resident for this pickup.")
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = agreedPriceInput,
                            onValueChange = { agreedPriceInput = it },
                            label = { Text("Agreed Price (TZS)") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val price = agreedPriceInput.toDoubleOrNull()
                        if (price != null) {
                            showPriceDialog = false
                            viewModel.updateMissionStatus("COMPLETED", price) {
                                onFinishJob()
                            }
                        }
                    }) {
                        Text("CONFIRM")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPriceDialog = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }
}
