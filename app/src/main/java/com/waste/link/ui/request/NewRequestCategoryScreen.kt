package com.waste.link.ui.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.waste.link.R

data class CategoryConfig(
    val type: WasteCategory,
    val icon: ImageVector,
    val color: Color,
    val bg: Color,
    val labelRes: Int
)

val categoryConfigs = listOf(
    CategoryConfig(WasteCategory.HOUSEHOLD, Icons.Default.Home, Color(0xFF1A6B38), Color(0xFFDCFCE7), R.string.categoryHousehold),
    CategoryConfig(WasteCategory.CONSTRUCTION, Icons.Default.Delete, Color(0xFFC2410C), Color(0xFFFFEDD5), R.string.categoryConstruction),
    CategoryConfig(WasteCategory.GARDEN, Icons.Default.Delete, Color(0xFF15803D), Color(0xFFD1FAE5), R.string.categoryGarden),
    CategoryConfig(WasteCategory.ELECTRONIC, Icons.Default.Delete, Color(0xFF1D4ED8), Color(0xFFDBEAFE), R.string.categoryElectronic),
    CategoryConfig(WasteCategory.HAZARDOUS, Icons.Default.Warning, Color(0xFF991B1B), Color(0xFFFEE2E2), R.string.categoryHazardous),
    CategoryConfig(WasteCategory.BULK_ITEMS, Icons.Default.Delete, Color(0xFFB45309), Color(0xFFFEF3C7), R.string.categoryBulk)
)

@Composable
fun NewRequestCategoryScreen(
    viewModel: RequestViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RequestTopAppBar(
            title = stringResource(R.string.selectCategory),
            subtitle = stringResource(R.string.selectCategorySubtitle),
            onBack = onBack
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(categoryConfigs) { config ->
                val isSelected = uiState.selectedCategory == config.type
                
                Box(
                    modifier = Modifier
                        .clickable { viewModel.selectCategory(config.type) }
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
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(config.bg, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = config.icon,
                                contentDescription = null,
                                tint = config.color
                            )
                        }
                        
                        Text(
                            text = stringResource(config.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp)
                                .size(16.dp)
                        )
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
                enabled = uiState.selectedCategory != null,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            )
        }
    }
}
