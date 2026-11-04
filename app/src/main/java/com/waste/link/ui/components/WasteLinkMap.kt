package com.waste.link.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.compose.expressions.dsl.const
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.waste.link.data.models.LiveLocation
import com.waste.link.data.models.WasteRequest
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import androidx.compose.ui.unit.DpOffset

@Composable
fun WasteLinkMap(
    modifier: Modifier = Modifier,
    isTrackingLocation: Boolean = false,
    updateIntervalMs: Long = 5000L,
    activeCollectors: List<LiveLocation> = emptyList(),
    activeRequests: List<WasteRequest> = emptyList(),
    userLocation: Position? = null,
    onLocationUpdated: (Double, Double) -> Unit = { _, _ -> },
    onMapClick: (Position, DpOffset) -> ClickResult = { _, _ -> ClickResult.Pass }
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    // Default to Dar es Salaam
    val cameraPositionState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(longitude = 39.2083, latitude = -6.7924),
            zoom = 13.0
        )
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        onLocationUpdated(location.latitude, location.longitude)
                        cameraPositionState.position = CameraPosition(
                            target = Position(longitude = location.longitude, latitude = location.latitude),
                            zoom = cameraPositionState.position.zoom
                        )
                    }
                }
            } catch (e: SecurityException) {
                // Ignore
            }
        }
    }

    LaunchedEffect(Unit) {
        val fineLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineLocationGranted) {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        onLocationUpdated(location.latitude, location.longitude)
                        cameraPositionState.position = CameraPosition(
                            target = Position(longitude = location.longitude, latitude = location.latitude),
                            zoom = cameraPositionState.position.zoom
                        )
                    }
                }
            } catch (e: SecurityException) {
                // Ignore
            }
        }
    }

    LaunchedEffect(isTrackingLocation) {
        while (isTrackingLocation) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        onLocationUpdated(location.latitude, location.longitude)
                        cameraPositionState.position = CameraPosition(
                            target = Position(longitude = location.longitude, latitude = location.latitude),
                            zoom = cameraPositionState.position.zoom
                        )
                    }
                }
            } catch (e: SecurityException) {
                // Ignored
            }
            delay(updateIntervalMs)
        }
    }

    MaplibreMap(
        modifier = modifier.fillMaxSize(),
        cameraState = cameraPositionState,
        baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/bright"),
        onMapClick = onMapClick
    ) {
        // Render Active Collectors
        activeCollectors.forEach { collector ->
            val collectorSource = rememberGeoJsonSource(
                data = GeoJsonData.Features(
                    FeatureCollection(
                        features = listOf(Feature(geometry = Point(Position(collector.lng, collector.lat)), properties = null))
                    )
                )
            )
            CircleLayer(
                id = "collector-layer-${collector.collectorId}",
                source = collectorSource,
                color = const(Color(0xFF10B981)), // Emerald Green
                radius = const(10.dp),
                strokeWidth = const(2.dp),
                strokeColor = const(Color.White)
            )
        }

        // Render Active Requests
        activeRequests.forEach { request ->
            val requestSource = rememberGeoJsonSource(
                data = GeoJsonData.Features(
                    FeatureCollection(
                        features = listOf(Feature(geometry = Point(Position(request.lng, request.lat)), properties = null))
                    )
                )
            )
            CircleLayer(
                id = "request-layer-${request.id}",
                source = requestSource,
                color = const(Color(0xFFF59E0B)), // Amber
                radius = const(8.dp),
                strokeWidth = const(2.dp),
                strokeColor = const(Color.White)
            )
        }

        // Render User Location
        if (userLocation != null) {
            val userLocationSource = rememberGeoJsonSource(
                data = GeoJsonData.Features(
                    FeatureCollection(
                        features = listOf(Feature(geometry = Point(userLocation), properties = null))
                    )
                )
            )
            // User Location Outer Pulse
            CircleLayer(
                id = "user-location-pulse-layer",
                source = userLocationSource,
                color = const(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                radius = const(24.dp),
            )
            // User Location Inner Dot
            CircleLayer(
                id = "user-location-dot-layer",
                source = userLocationSource,
                color = const(Color(0xFF3B82F6)),
                radius = const(8.dp),
                strokeWidth = const(3.dp),
                strokeColor = const(Color.White)
            )
        }
    }
}
