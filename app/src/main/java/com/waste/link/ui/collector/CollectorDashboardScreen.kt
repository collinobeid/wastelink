package com.waste.link.ui.collector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.Chat
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
import com.waste.link.data.models.WasteRequest
import com.waste.link.ui.components.GlassCard
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.compose.util.ClickResult
import android.location.Location
import com.waste.link.utils.IntentUtils
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay

@Composable
fun CollectorDashboardScreen(
    onAcceptMission: () -> Unit,
    viewModel: CollectorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isOnline = uiState.isOnline
    
    val startLat = -6.7924 // Dar es Salaam approx start
    val startLng = 39.2083
    val cameraPositionState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(longitude = startLng, latitude = startLat),
            zoom = 13.0
        )
    )

    var selectedRequest by remember { mutableStateOf<WasteRequest?>(null) }
    val currentContext = LocalContext.current
    var collectorLocation by remember { mutableStateOf<Position?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Map / Radar Background
        com.waste.link.ui.components.WasteLinkMap(
            modifier = Modifier.fillMaxSize(),
            isTrackingLocation = isOnline,
            activeRequests = if (isOnline) uiState.activeRequests else emptyList(),
            userLocation = collectorLocation, // Display as blue dot
            onLocationUpdated = { lat, lng ->
                if (isOnline || collectorLocation == null) {
                    collectorLocation = Position(longitude = lng, latitude = lat)
                    if (isOnline) {
                        viewModel.updateLocation(lat, lng)
                    }
                }
            },
            onMapClick = { position, offset ->
                var clickedRequest: WasteRequest? = null
                
                if (isOnline) {
                    for (request in uiState.activeRequests) {
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            request.lat, request.lng,
                            position.latitude, position.longitude,
                            distance
                        )
                        if (distance[0] < 80f) {
                            clickedRequest = request
                            break
                        }
                    }
                }
                
                if (clickedRequest != null) {
                    selectedRequest = clickedRequest
                    ClickResult.Consume
                } else {
                    selectedRequest = null
                    ClickResult.Pass
                }
            }
        )

        // Top Status Bar
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = 32.dp),
            shape = RoundedCornerShape(100.dp),
            backgroundColor = Color.Black.copy(alpha = 0.5f),
            borderColor = Color.White.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Today's Earnings", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Text("Tsh ${uiState.todayEarnings.toInt()}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Row {
                    if (isOnline) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (uiState.isOnBreak) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))
                                .clickable { viewModel.toggleBreakStatus() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Coffee, contentDescription = "Break", tint = if (uiState.isOnBreak) Color(0xFFF59E0B) else Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))
                            .clickable { viewModel.toggleOnlineStatus() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isOnline) "ONLINE" else "OFFLINE",
                            color = if (isOnline) MaterialTheme.colorScheme.primary else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // On Break Overlay
        if (uiState.isOnBreak) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Coffee, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("You are on a break", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.toggleBreakStatus() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                    ) {
                        Text("Resume Work")
                    }
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Floating Request Card (Only visible if online and a request is selected or available)
            val requestToShow = selectedRequest ?: uiState.activeRequests.firstOrNull { it.status == "PENDING_DISPATCH" }
            
            if (isOnline && requestToShow != null) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text("New Request: ${requestToShow.volumeTier}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${requestToShow.category} Waste", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(requestToShow.pickupAddress.ifEmpty { "Location Pinned" }, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ready for Pickup", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { 
                                    val phone = requestToShow.residentPhoneNumber
                                    if (!phone.isNullOrBlank()) {
                                        IntentUtils.openWhatsApp(currentContext, phone, "Hi! I'm your WasteLink collector. I'm 12 mins away from your location at ${requestToShow.lat}, ${requestToShow.lng}.")
                                    } else {
                                        android.widget.Toast.makeText(currentContext, "Resident didn't provide a phone number", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Chat", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            
                            Button(
                                onClick = { 
                                    IntentUtils.copyToClipboard(currentContext, "Coordinates", "${requestToShow.lat}, ${requestToShow.lng}")
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copy", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { 
                                viewModel.acceptMission(requestToShow) {
                                    onAcceptMission()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("ACCEPT MISSION", color = Color(0xFF0F1115), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // GO ONLINE Toggle
            Button(
                onClick = { viewModel.toggleOnlineStatus() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        brush = Brush.linearGradient(
                            if (isOnline) listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                            else listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                        ),
                        shape = RoundedCornerShape(100.dp)
                    ),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(100.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isOnline) Icons.Default.DirectionsCar else Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isOnline) "GO OFFLINE" else "GO ONLINE",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
