package com.waste.link.ui.request

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import org.maplibre.compose.util.ClickResult
import com.waste.link.R

@Composable
fun NewRequestLocationScreen(
    viewModel: RequestViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Default to Dar es Salaam if location is null
    val defaultLocation = LocationCoords(-6.7924, 39.2083) 
    val mapLocation = uiState.location ?: defaultLocation
    
    val cameraPositionState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(longitude = mapLocation.lng, latitude = mapLocation.lat),
            zoom = 15.0
        )
    )

    // Permission handling
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                          permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            hasLocationPermission = granted
            if (granted) {
                viewModel.fetchCurrentLocation(context)
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else if (uiState.location == null) {
            viewModel.fetchCurrentLocation(context)
        }
    }

    LaunchedEffect(uiState.location) {
        uiState.location?.let {
            cameraPositionState.position = CameraPosition(
                target = Position(longitude = it.lng, latitude = it.lat),
                zoom = 15.0
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RequestTopAppBar(
            title = stringResource(R.string.confirmLocation),
            subtitle = stringResource(R.string.locationSubtitle),
            onBack = onBack
        )

        // MapLibre Maps View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            MaplibreMap(
                modifier = Modifier.fillMaxSize(),
                cameraState = cameraPositionState,
                baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/bright"),
                onMapClick = { position, _ ->
                    viewModel.updateLocation(LocationCoords(position.latitude, position.longitude), context)
                    ClickResult.Consume
                }
            ) {
                // Symbols for marker will be added
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Address Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.addressLabel).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Notes
            Column {
                Text(
                    text = stringResource(R.string.notesLabel).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = { if (it.length <= 280) viewModel.updateNotes(it) },
                    placeholder = { Text(stringResource(R.string.notesPlaceholder)) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFEEF3EC),
                        focusedContainerColor = Color(0xFFEEF3EC),
                        unfocusedBorderColor = Color.Transparent
                    )
                )
                Text(
                    text = "${uiState.notes.length}/280",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 12.dp)
        ) {
            PrimaryButton(
                label = stringResource(R.string.continueBtn),
                onClick = onNext,
                enabled = uiState.location != null,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            )
        }
    }
}
