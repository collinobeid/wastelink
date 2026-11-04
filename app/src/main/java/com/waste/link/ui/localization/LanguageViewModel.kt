package com.waste.link.ui.localization

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val LANGUAGE_KEY = stringPreferencesKey("user_language")
    
    private val _currentLocale = MutableStateFlow<AppLocale>(AppLocale.English)
    val currentLocale: StateFlow<AppLocale> = _currentLocale

    init {
        viewModelScope.launch {
            dataStore.data.map { prefs ->
                AppLocale.fromCode(prefs[LANGUAGE_KEY] ?: "en")
            }.collect {
                _currentLocale.value = it
            }
        }
    }

    fun setLanguage(locale: AppLocale) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[LANGUAGE_KEY] = locale.code
            }
        }
    }
}
