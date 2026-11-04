package com.waste.link.ui.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waste.link.R

data class VolumeConfig(
    val type: VolumeTier,
    val icon: String,
    val labelRes: Int,
    val descRes: Int
)

val volumeConfigs = listOf(
    VolumeConfig(VolumeTier.SMALL_BAG, "🛍️", R.string.volSmallBag, R.string.volSmallBagDesc),
    VolumeConfig(VolumeTier.STANDARD_BIN, "🗑️", R.string.volStandardBin, R.string.volStandardBinDesc),
    VolumeConfig(VolumeTier.LARGE_LOAD, "📦", R.string.volLargeLoad, R.string.volLargeLoadDesc),
    VolumeConfig(VolumeTier.TRUCK_LOAD, "🚛", R.string.volTruckLoad, R.string.volTruckLoadDesc)
)

@Composable
fun NewRequestVolumeScreen(
    viewModel: RequestViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val categoryConfig = categoryConfigs.find { it.type == uiState.selectedCategory }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RequestTopAppBar(
            title = stringResource(R.string.selectVolume),
            subtitle = stringResource(R.string.selectVolumeSubtitle),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category recap
            if (categoryConfig != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(categoryConfig.bg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = categoryConfig.icon,
                        contentDescription = null,
                        tint = categoryConfig.color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(categoryConfig.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = categoryConfig.color
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Volume options
            volumeConfigs.forEach { config ->
                val isSelected = uiState.selectedVolume == config.type
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectVolume(config.type) }
                        .border(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(
                            color = if (isSelected) Color(0xFFE8F5EC) else Color.White,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = config.icon, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(config.labelRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(config.descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF8DA88A),
                                shape = CircleShape
                            )
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
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
                enabled = uiState.selectedVolume != null,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            )
        }
    }
}
