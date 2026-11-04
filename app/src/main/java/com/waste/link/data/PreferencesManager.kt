package com.waste.link.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private val PUSH_ENABLED = booleanPreferencesKey("push_enabled")
    private val DARK_MODE = stringPreferencesKey("dark_mode") // "SYSTEM", "LIGHT", "DARK"
    private val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    private val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")

    val pushEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PUSH_ENABLED] ?: true
    }

    val darkModeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: "SYSTEM"
    }

    val wifiOnlyFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[WIFI_ONLY] ?: false
    }
    
    val analyticsEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ANALYTICS_ENABLED] ?: true
    }

    suspend fun setPushEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PUSH_ENABLED] = enabled
        }
    }

    suspend fun setDarkMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[DARK_MODE] = mode
        }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[WIFI_ONLY] = enabled
        }
    }
    
    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[ANALYTICS_ENABLED] = enabled
        }
    }
}
