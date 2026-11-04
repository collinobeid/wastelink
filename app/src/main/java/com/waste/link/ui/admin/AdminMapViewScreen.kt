package com.waste.link.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.compose.expressions.dsl.const
import kotlinx.serialization.json.JsonObject

@Composable
fun AdminMapViewScreen(
    modifier: Modifier = Modifier,
    viewModel: AdminMapViewModel = hiltViewModel()
) {
    val requests by viewModel.requests.collectAsState()
    val liveLocations by viewModel.liveLocations.collectAsState()
    
    // Hardcoded start position for MVP
    val defaultLat = 40.7128
    val defaultLng = -74.0060
    val cameraPositionState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(longitude = defaultLng, latitude = defaultLat),
            zoom = 10.0
        )
    )
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live Request Map",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Dar es Salaam · ${requests.size} active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { /*TODO*/ },
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Filter",
                    tint = Color(0xFF52634F),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { /*TODO*/ },
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFF52634F),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        HorizontalDivider()

        // Map Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            MaplibreMap(
                modifier = Modifier.fillMaxSize(),
                cameraState = cameraPositionState,
                baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/bright")
            ) {
                val pendingRequestsSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(
                        FeatureCollection(
                            features = requests.filter { it.status == "PENDING" }.map { Feature(geometry = Point(Position(longitude = it.lng, latitude = it.lat)), properties = null as JsonObject?) }
                        )
                    )
                )

                val assignedRequestsSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(
                        FeatureCollection(
                            features = requests.filter { it.status != "PENDING" }.map { Feature(geometry = Point(Position(longitude = it.lng, latitude = it.lat)), properties = null as JsonObject?) }
                        )
                    )
                )

                val fleetSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(
                        FeatureCollection(
                            features = liveLocations.map { Feature(geometry = Point(Position(longitude = it.lng, latitude = it.lat)), properties = null as JsonObject?) }
                        )
                    )
                )

                // Pending Requests - Orange
                CircleLayer(
                    id = "pending-layer",
                    source = pendingRequestsSource,
                    color = const(Color(0xFFF59E0B)),
                    radius = const(8.dp),
                    strokeWidth = const(2.dp),
                    strokeColor = const(Color.White)
                )
                // Assigned/Transit Requests - Blue
                CircleLayer(
                    id = "assigned-layer",
                    source = assignedRequestsSource,
                    color = const(Color(0xFF3B82F6)),
                    radius = const(8.dp),
                    strokeWidth = const(2.dp),
                    strokeColor = const(Color.White)
                )
                // Active Fleet - Green
                CircleLayer(
                    id = "fleet-layer",
                    source = fleetSource,
                    color = const(Color(0xFF22C55E)),
                    radius = const(10.dp),
                    strokeWidth = const(3.dp),
                    strokeColor = const(Color.White)
                )
            }
            
            // Legend
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                LegendItem(color = Color(0xFFF59E0B), label = "Pending")
                LegendItem(color = Color(0xFF3B82F6), label = "Assigned")
                LegendItem(color = Color(0xFF22C55E), label = "In Transit")
            }
        }

        // Active Queue Bottom Sheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .height(240.dp)
        ) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE QUEUE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${requests.size} requests",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                requests.forEach { req ->
                    val statusColor = when (req.status) {
                        "PENDING" -> Color(0xFFF59E0B)
                        "ASSIGNED" -> Color(0xFF3B82F6)
                        else -> Color(0xFF22C55E)
                    }
                    QueueItem(
                        ref = req.requestRef,
                        category = req.category,
                        statusColor = statusColor,
                        statusLabel = req.status,
                        onClick = {
                            val newStatus = when (req.status) {
                                "PENDING" -> "ASSIGNED"
                                "ASSIGNED" -> "IN_TRANSIT"
                                else -> "COMPLETED"
                            }
                            viewModel.updateRequestStatus(req.id, newStatus)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Black)
    }
}

@Composable
private fun QueueItem(ref: String, category: String, statusColor: Color, statusLabel: String, onClick: () -> Unit = {}) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = ref, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = statusLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
