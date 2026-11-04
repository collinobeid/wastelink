package com.waste.link.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waste.link.data.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val pushEnabled: StateFlow<Boolean> = preferencesManager.pushEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val darkMode: StateFlow<String> = preferencesManager.darkModeFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, "SYSTEM")

    val wifiOnly: StateFlow<Boolean> = preferencesManager.wifiOnlyFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val analyticsEnabled: StateFlow<Boolean> = preferencesManager.analyticsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun togglePush(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setPushEnabled(enabled)
        }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch {
            preferencesManager.setDarkMode(mode)
        }
    }

    fun toggleWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setWifiOnly(enabled)
        }
    }

    fun toggleAnalytics(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAnalyticsEnabled(enabled)
        }
    }
}
