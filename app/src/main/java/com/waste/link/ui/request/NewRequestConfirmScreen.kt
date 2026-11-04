package com.waste.link.ui.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waste.link.R

@Composable
fun NewRequestConfirmScreen(
    viewModel: RequestViewModel,
    onBack: () -> Unit,
    onSubmitSuccess: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val categoryConfig = categoryConfigs.find { it.type == uiState.selectedCategory }
    val volumeConfig = volumeConfigs.find { it.type == uiState.selectedVolume }
    
    var saveAsTemplate by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RequestTopAppBar(
            title = stringResource(R.string.reviewRequest),
            subtitle = stringResource(R.string.reviewSubtitle),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                // Category
                if (categoryConfig != null) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier.size(32.dp).background(Color(0xFFE8F0E6), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = categoryConfig.icon, contentDescription = null, tint = categoryConfig.color, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.selectCategory).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(categoryConfig.labelRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                // Volume
                if (volumeConfig != null) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier.size(32.dp).background(Color(0xFFE8F0E6), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = volumeConfig.icon)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.selectVolume).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(volumeConfig.labelRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                // Address
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.size(32.dp).background(Color(0xFFE8F0E6), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.addressLabel).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(uiState.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Notes
                if (uiState.notes.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier.size(32.dp).background(Color(0xFFE8F0E6), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Create, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.notesLabel).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(uiState.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Warning
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFEF9EC), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Your GPS coordinates will be submitted and stored with this request. Location cannot be changed after submission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF92400E)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save as template
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Save as Template", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Quickly create similar requests in the future", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = saveAsTemplate, onCheckedChange = { saveAsTemplate = it })
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 12.dp)
        ) {
            PrimaryButton(
                label = if (uiState.isLoading) "Submitting..." else stringResource(R.string.submitRequest),
                onClick = { viewModel.submitRequest(null, onSubmitSuccess) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                icon = if (uiState.isLoading) {
                    { CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) }
                } else null
            )
        }
    }
}
