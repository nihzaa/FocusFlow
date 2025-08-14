package edu.unikom.focusflow.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import java.util.*

// Theme State Manager - Sesuaikan dengan existing theme
class ThemeManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("FocusFlowSettings", Context.MODE_PRIVATE)

    // Dark mode state
    private val _isDarkMode = mutableStateOf(prefs.getBoolean("dark_mode", false))
    val isDarkMode: State<Boolean> = _isDarkMode

    // Language state
    private val _currentLanguage = mutableStateOf(prefs.getString("language", "English") ?: "English")
    val currentLanguage: State<String> = _currentLanguage

    // Keep screen on state
    private val _keepScreenOn = mutableStateOf(prefs.getBoolean("keep_screen_on", true))
    val keepScreenOn: State<Boolean> = _keepScreenOn

    fun toggleDarkMode() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    fun setLanguage(language: String) {
        _currentLanguage.value = language
        prefs.edit().putString("language", language).apply()
        // Apply locale change immediately
        applyLanguageChange(context, language)
    }

    fun setKeepScreenOn(value: Boolean) {
        _keepScreenOn.value = value
        prefs.edit().putBoolean("keep_screen_on", value).apply()
    }

    private fun applyLanguageChange(context: Context, language: String) {
        val locale = when (language) {
            "Indonesian" -> Locale("id")
            "Spanish" -> Locale("es")
            "French" -> Locale("fr")
            "German" -> Locale("de")
            else -> Locale("en")
        }

        Locale.setDefault(locale)

        val config = context.resources.configuration
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    companion object {
        @Volatile
        private var INSTANCE: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

// Composable untuk provide theme manager
@Composable
fun ProvideThemeManager(
    context: Context,
    content: @Composable (ThemeManager) -> Unit
) {
    val themeManager = remember { ThemeManager.getInstance(context) }
    content(themeManager)
}

// Settings ViewModel untuk state management
class SettingsViewModel(private val themeManager: ThemeManager) : ViewModel() {
    val isDarkMode = themeManager.isDarkMode
    val currentLanguage = themeManager.currentLanguage
    val keepScreenOn = themeManager.keepScreenOn

    fun toggleDarkMode() = themeManager.toggleDarkMode()
    fun setLanguage(language: String) = themeManager.setLanguage(language)
    fun setKeepScreenOn(value: Boolean) = themeManager.setKeepScreenOn(value)
}

@Composable
fun rememberSettingsViewModel(themeManager: ThemeManager): SettingsViewModel {
    return remember { SettingsViewModel(themeManager) }
}