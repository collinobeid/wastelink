package com.waste.link.ui.resident

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonPinCircle
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.waste.link.R
import com.waste.link.ui.components.GlassCard
import com.waste.link.ui.request.RequestViewModel
import com.waste.link.ui.request.VolumeTier
import com.waste.link.ui.request.WasteCategory
import kotlinx.coroutines.launch
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.compose.expressions.dsl.const
import kotlinx.serialization.json.JsonObject
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import android.location.Geocoder
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import org.maplibre.spatialk.geojson.LineString
import com.waste.link.ui.request.LocationCoords
import com.waste.link.data.models.LiveLocation
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import org.maplibre.compose.util.ClickResult
import com.waste.link.utils.IntentUtils

enum class ResidentSheetMode {
    REQUEST,
    LOCATION_DETAILS,
    COLLECTOR_DETAILS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResidentHomeScreen(
    onNavigateToTracking: () -> Unit,
    viewModel: RequestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeCollectors by viewModel.activeCollectors.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )
    
    val categories = listOf(
        WasteCategory.HOUSEHOLD to "🏠 Household",
        WasteCategory.ELECTRONIC to "📱 Electronic",
        WasteCategory.GARDEN to "🌿 Garden",
        WasteCategory.CONSTRUCTION to "🏗️ Construction",
        WasteCategory.BULK_ITEMS to "🛋️ Bulk"
    )
    
    var selectedCategory by remember { mutableStateOf(WasteCategory.HOUSEHOLD) }
    var volume by remember { mutableStateOf(50f) }
    
    var gatePhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            gatePhotoBitmap = bitmap
        }
    }

    var sheetMode by remember { mutableStateOf(ResidentSheetMode.REQUEST) }
    var pickupAddress by remember { mutableStateOf("Fetching address...") }
    var selectedCollector by remember { mutableStateOf<LiveLocation?>(null) }
    var selectedCollectorPhone by remember { mutableStateOf<String?>(null) }
    val currentContext = LocalContext.current
    
    // --- Location Logic ---
    var userLocation by remember { mutableStateOf<Position?>(null) }
    var isTracking by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        viewModel.watchActiveCollectors()
    }
    
    LaunchedEffect(selectedCollector) {
        if (selectedCollector != null) {
            selectedCollectorPhone = viewModel.getCollectorPhone(selectedCollector!!.collectorId)
        } else {
            selectedCollectorPhone = null
        }
    }

    BottomSheetScaffold(
        scaffoldState = bottomSheetScaffoldState,
        sheetContainerColor = Color(0xFF0F1115),
        sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        sheetPeekHeight = 240.dp, // Shows "What needs picking up?" and categories
        sheetContent = {
            AnimatedContent(
                targetState = sheetMode,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "SheetModeTransition"
            ) { mode ->
                if (mode == ResidentSheetMode.REQUEST) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 120.dp) // extra padding for BNB navigation
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = stringResource(R.string.whatNeedsPickingUp),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        // Category Pills (Animated)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 32.dp)
                        ) {
                            items(categories) { (category, label) ->
                                val isSelected = selectedCategory == category
                                val backgroundColor by animateColorAsState(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f),
                                    animationSpec = tween(300)
                                )
                                val textColor by animateColorAsState(
                                    if (isSelected) Color(0xFF0F1115) else Color.White,
                                    animationSpec = tween(300)
                                )
                                val scale by animateFloatAsState(
                                    if (isSelected) 1.05f else 1f,
                                    animationSpec = tween(300)
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .scale(scale)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(backgroundColor)
                                        .clickable { selectedCategory = category }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = textColor,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        // Volume Slider with Custom Markers
                        Text(
                            text = stringResource(R.string.estimatedVolume),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Column(modifier = Modifier.padding(bottom = 32.dp)) {
                            Slider(
                                value = volume,
                                onValueChange = { volume = it },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Small", color = if (volume < 33f) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Medium", color = if (volume in 33f..66f) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Large", color = if (volume > 66f) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Gate Photo (Sleek inner card)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .clickable { cameraLauncher.launch(null) }
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (gatePhotoBitmap != null) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = if (gatePhotoBitmap != null) "Photo Added" else stringResource(R.string.addGatePhoto), 
                                    color = Color.White, 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = if (gatePhotoBitmap != null) stringResource(R.string.readyForPickup) else stringResource(R.string.helpDriverFindYou), 
                                    color = Color.White.copy(alpha = 0.5f), 
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        // Request Button (Animated Glow)
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val buttonScale by animateFloatAsState(if (isPressed) 0.95f else 1f, animationSpec = tween(150))
                        
                        Button(
                            onClick = {
                                viewModel.selectCategory(selectedCategory)
                                
                                val selectedVolumeTier = when {
                                    volume < 33f -> VolumeTier.SMALL_BAG
                                    volume < 66f -> VolumeTier.STANDARD_BIN
                                    else -> VolumeTier.LARGE_LOAD
                                }
                                viewModel.selectVolume(selectedVolumeTier)
                                
                                viewModel.submitRequest(gatePhotoBitmap, onSuccess = { documentId ->
                                    viewModel.watchRequest(documentId)
                                    onNavigateToTracking()
                                })
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .scale(buttonScale)
                                .background(
                                    brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
                                    shape = RoundedCornerShape(20.dp)
                                ),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(20.dp),
                            enabled = !uiState.isLoading,
                            interactionSource = interactionSource,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(color = Color(0xFF0F1115), modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            } else {
                                Text(
                                    stringResource(R.string.requestPickupPrice), 
                                    color = Color(0xFF0F1115), 
                                    fontWeight = FontWeight.ExtraBold, 
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF0F1115))
                            }
                        }
                    }
                } else if (mode == ResidentSheetMode.LOCATION_DETAILS) {
                    // Location Details UI
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 120.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "GPS Details",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("Mapped Address", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(pickupAddress, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text("Raw Coordinates", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                val coordsString = "${userLocation?.latitude}, ${userLocation?.longitude}"
                                Text(
                                    text = coordsString, 
                                    color = Color.White.copy(alpha = 0.7f), 
                                    fontSize = 14.sp, 
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { 
                                    val coordsString = "${userLocation?.latitude}, ${userLocation?.longitude}"
                                    IntentUtils.copyToClipboard(currentContext, "Coordinates", coordsString)
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copy", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            
                            Button(
                                onClick = { sheetMode = ResidentSheetMode.REQUEST },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Back", color = Color(0xFF0F1115), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (mode == ResidentSheetMode.COLLECTOR_DETAILS) {
                    // Collector Details UI
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 120.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Collector nearby",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PersonPinCircle, contentDescription = null, tint = Color(0xFF10B981))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Waste Collector", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("Active & Ready", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { 
                                    if (!selectedCollectorPhone.isNullOrBlank()) {
                                        IntentUtils.openWhatsApp(currentContext, selectedCollectorPhone!!, "Hi, are you available for a waste pickup at ${userLocation?.latitude}, ${userLocation?.longitude}?")
                                    } else {
                                        Toast.makeText(currentContext, "Collector phone number not available", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Chat", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            
                            Button(
                                onClick = { sheetMode = ResidentSheetMode.REQUEST },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Reverse Geocode userLocation and update ViewModel
            LaunchedEffect(userLocation) {
                if (userLocation != null) {
                    viewModel.updateLocation(LocationCoords(userLocation!!.latitude, userLocation!!.longitude), context)
                    
                    withContext(Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addresses = geocoder.getFromLocation(userLocation!!.latitude, userLocation!!.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val name = addresses[0].thoroughfare ?: addresses[0].featureName ?: "Unnamed Road"
                                pickupAddress = name
                            } else {
                                pickupAddress = "Unknown Location"
                            }
                        } catch (e: Exception) {
                            pickupAddress = "Pickup Location"
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                com.waste.link.ui.components.WasteLinkMap(
                    modifier = Modifier.fillMaxSize(),
                    isTrackingLocation = isTracking,
                    activeCollectors = activeCollectors,
                    userLocation = userLocation,
                    onLocationUpdated = { lat, lng ->
                        if (isTracking || userLocation == null) {
                            userLocation = Position(longitude = lng, latitude = lat)
                        }
                    },
                    onMapClick = { position, offset ->
                        var clickedCollector: LiveLocation? = null
                        var isUserPinClicked = false
                        
                        // Check if a collector was clicked
                        for (collector in activeCollectors) {
                            val distance = FloatArray(1)
                            Location.distanceBetween(
                                collector.lat, collector.lng,
                                position.latitude, position.longitude,
                                distance
                            )
                            if (distance[0] < 80f) { // 80m tolerance for collectors
                                clickedCollector = collector
                                break
                            }
                        }
                        
                        if (clickedCollector == null && userLocation != null) {
                            val distance = FloatArray(1)
                            Location.distanceBetween(
                                userLocation!!.latitude, userLocation!!.longitude,
                                position.latitude, position.longitude,
                                distance
                            )
                            if (distance[0] < 50f) {
                                isUserPinClicked = true
                            }
                        }
                        
                        if (clickedCollector != null) {
                            selectedCollector = clickedCollector
                            sheetMode = ResidentSheetMode.COLLECTOR_DETAILS
                            coroutineScope.launch { bottomSheetScaffoldState.bottomSheetState.expand() }
                            ClickResult.Consume
                        } else if (isUserPinClicked) {
                            sheetMode = ResidentSheetMode.LOCATION_DETAILS
                            coroutineScope.launch { bottomSheetScaffoldState.bottomSheetState.expand() }
                            ClickResult.Consume
                        } else {
                            sheetMode = ResidentSheetMode.REQUEST
                            ClickResult.Pass
                        }
                    }
                )
                // Top Address Banner
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Current Location", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(pickupAddress, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

            }

            // Vignette Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F1115).copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Transparent,
                                Color(0xFF0F1115).copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            // Top Search Bar (Glassmorphism) wired to expand bottom sheet
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 40.dp)
                    .clickable {
                        // Interactive wiring: expand the bottom sheet and show a toast
                        coroutineScope.launch {
                            bottomSheetScaffoldState.bottomSheetState.expand()
                        }
                    },
                shape = RoundedCornerShape(100.dp),
                backgroundColor = Color(0xFF16181C).copy(alpha = 0.6f),
                borderColor = Color.White.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.whereToPickup), 
                        color = Color.White.copy(alpha = 0.8f), 
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    // Profile Avatar
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Floating Action Button (My Location)
            if (userLocation != null) {
                FloatingActionButton(
                    onClick = { isTracking = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 100.dp), // sits above the bottom sheet peek height
                    containerColor = if (isTracking) MaterialTheme.colorScheme.primary else Color.White,
                    contentColor = if (isTracking) Color.White else Color.Black,
                    shape = CircleShape
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.MyLocation, contentDescription = "Center Location")
                }
            }
        }
    }
}
