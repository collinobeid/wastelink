package com.waste.link.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.waste.link.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val pushEnabled by viewModel.pushEnabled.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val analyticsEnabled by viewModel.analyticsEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.SpacingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
        ) {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = Dimens.SpacingSmall)
            )

            // Dark Mode Group
            SettingGroup(title = "Appearance") {
                SettingRowSelector(
                    icon = Icons.Default.DarkMode,
                    title = "Theme",
                    options = listOf("SYSTEM", "LIGHT", "DARK"),
                    selectedOption = darkMode,
                    onOptionSelected = { viewModel.setDarkMode(it) }
                )
            }

            // Notifications Group
            SettingGroup(title = "Notifications") {
                SettingRowToggle(
                    icon = Icons.Default.Notifications,
                    title = "Push Notifications",
                    subtitle = "Receive updates on your pickups",
                    checked = pushEnabled,
                    onCheckedChange = { viewModel.togglePush(it) }
                )
            }

            // Data & Storage
            SettingGroup(title = "Data & Storage") {
                SettingRowToggle(
                    icon = Icons.Default.Wifi,
                    title = "Wi-Fi Only",
                    subtitle = "Only download high-res images on Wi-Fi",
                    checked = wifiOnly,
                    onCheckedChange = { viewModel.toggleWifiOnly(it) }
                )
            }

            // Privacy
            SettingGroup(title = "Privacy") {
                SettingRowToggle(
                    icon = Icons.Default.Analytics,
                    title = "Analytics",
                    subtitle = "Help us improve WasteLink by sharing usage data",
                    checked = analyticsEnabled,
                    onCheckedChange = { viewModel.toggleAnalytics(it) }
                )
            }
        }
    }
}

@Composable
fun SettingGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.RadiusMedium))
            .padding(vertical = Dimens.SpacingSmall)
    ) {
        content()
    }
}

@Composable
fun SettingRowToggle(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Dimens.SpacingMedium, vertical = Dimens.SpacingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(Dimens.SpacingMedium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun SettingRowSelector(
    icon: ImageVector,
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = Dimens.SpacingMedium, vertical = Dimens.SpacingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(Dimens.SpacingMedium))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Box {
            Text(
                text = selectedOption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
