package com.letstrack.app.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
private val ACCENT_THEME_KEY = stringPreferencesKey("accent_theme")

/**
 * Activity-scoped by convention: instantiate once via `hiltViewModel()` at the top of
 * MainActivity's `setContent { }` (before `LetsTrackTheme`), then pass its state/setter down
 * as plain parameters -- calling `hiltViewModel()` again inside a NavHost `composable { }`
 * block would resolve to a different, nav-entry-scoped instance.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = dataStore.data
        .map { preferences ->
            preferences[THEME_MODE_KEY]?.let { stored ->
                runCatching { ThemeMode.valueOf(stored) }.getOrNull()
            } ?: ThemeMode.SYSTEM
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            dataStore.edit { it[THEME_MODE_KEY] = mode.name }
        }
    }

    val accentTheme: StateFlow<AccentTheme> = dataStore.data
        .map { preferences ->
            preferences[ACCENT_THEME_KEY]?.let { stored ->
                runCatching { AccentTheme.valueOf(stored) }.getOrNull()
            } ?: AccentTheme.GREEN
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccentTheme.GREEN)

    fun setAccentTheme(theme: AccentTheme) {
        viewModelScope.launch {
            dataStore.edit { it[ACCENT_THEME_KEY] = theme.name }
        }
    }
}
