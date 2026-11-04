package com.waste.link.ui.resident

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.waste.link.ui.components.GlassCard
import com.waste.link.ui.request.RequestViewModel

@Composable
fun ResidentTrackingScreen(
    viewModel: RequestViewModel = hiltViewModel()
) {
    val activeRequest by viewModel.activeRequest.collectAsState()
    
    val collectorLocation by viewModel.collectorLocation.collectAsState()
    
    // Fallback coordinates (e.g. city center) if location is null
    val targetLat = activeRequest?.lat ?: 0.0
    val targetLng = activeRequest?.lng ?: 0.0

    // Camera targets collector if they are active, otherwise targets the pickup
    val cameraTargetLat = if (collectorLocation != null && activeRequest?.status == "EN_ROUTE") collectorLocation!!.lat else targetLat
    val cameraTargetLng = if (collectorLocation != null && activeRequest?.status == "EN_ROUTE") collectorLocation!!.lng else targetLng

    val cameraPositionState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(longitude = cameraTargetLng, latitude = cameraTargetLat),
            zoom = 15.0
        )
    )

    // Auto-pan camera when collector location updates
    LaunchedEffect(collectorLocation) {
        if (collectorLocation != null && activeRequest?.status == "EN_ROUTE") {
            // we will add animate later
        }
    }
    
    // Calculate Mock ETA
    var etaMinutes by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(collectorLocation, activeRequest) {
        if (collectorLocation != null && activeRequest != null && activeRequest!!.status == "EN_ROUTE") {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                collectorLocation!!.lat, collectorLocation!!.lng,
                targetLat, targetLng,
                results
            )
            val distanceMeters = results[0]
            // assume speed of 400 meters per minute (24km/h)
            etaMinutes = (distanceMeters / 400).toInt().coerceAtLeast(1)
        } else {
            etaMinutes = null
        }
    }

    if (activeRequest == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF16181C)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No Active Requests", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("You don't have any requests in progress.", color = Color.White.copy(alpha = 0.5f))
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Live MapLibre Map
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraPositionState,
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/bright")
        ) {
            // We will add SymbolLayers for markers next
        }

        // ETA Pill
        GlassCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            shape = RoundedCornerShape(100.dp),
            backgroundColor = Color(0xFF16181C).copy(alpha = 0.9f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (activeRequest?.status == "PENDING_DISPATCH") Color.Yellow else MaterialTheme.colorScheme.primary
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (activeRequest?.status) {
                        "PENDING_DISPATCH" -> "Waiting for Truck..."
                        "EN_ROUTE" -> if (etaMinutes != null) "Truck arriving in ~$etaMinutes min" else "Truck is on the way!"
                        "ARRIVED_AT_LOCATION" -> "Truck has arrived!"
                        else -> "Processing..."
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        // Bottom Driver Profile Card (Visible if En Route or later)
        if (activeRequest?.status == "EN_ROUTE" || activeRequest?.status == "ARRIVED_AT_LOCATION") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF16181C).copy(alpha = 0.95f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Driver Avatar
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // Driver Info
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Collector on Duty", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("4.9", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                                }
                            }
                            
                            // WhatsApp Chat Button
                            val context = androidx.compose.ui.platform.LocalContext.current
                            IconButton(
                                onClick = {
                                    val phone = activeRequest?.collectorPhoneNumber ?: "+255000000000"
                                    val reqId = activeRequest?.id?.take(5) ?: "Unknown"
                                    val msg = "Hello! I am the resident for pickup request #$reqId. Is the truck close?"
                                    com.waste.link.utils.WhatsAppUtils.openWhatsAppChat(context, phone, msg)
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFF25D366)) // WhatsApp Green
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "WhatsApp Chat", tint = Color.White) // Could use a custom drawable if available
                            }
                        }
                        
                        // Agreed Price Display
                        if (activeRequest?.agreedPrice != null && activeRequest?.agreedPrice!! > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Text("Agreed Price", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${activeRequest?.agreedPrice} TZS",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Please pay the collector directly via Cash or Mobile Money.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
