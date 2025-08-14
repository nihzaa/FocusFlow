package edu.unikom.focusflow

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import edu.unikom.focusflow.notifications.TaskNotificationHelper
import edu.unikom.focusflow.ui.navigation.FocusFlowNavigation
import edu.unikom.focusflow.ui.theme.*
import edu.unikom.focusflow.utils.DataSeeder
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved settings saat app startup
        loadAppSettings()

        // Initialize notification channel immediately
        TaskNotificationHelper.createNotificationChannel(this)

        // Check exact alarm permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!TaskNotificationHelper.hasExactAlarmPermission(this)) {
                TaskNotificationHelper.requestExactAlarmPermission(this)
            }
        }


        setContent {
            val context = LocalContext.current
            val navController = rememberNavController()

            // Auth state listener untuk handle logout/login changes
            DisposableEffect(Unit) {
                val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                    if (firebaseAuth.currentUser == null) {
                        // User logged out, clear all backstack and navigate to onboarding
                        navController.navigate("onboarding") {
                            popUpTo(0) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                }

                FirebaseAuth.getInstance().addAuthStateListener(authListener)

                onDispose {
                    FirebaseAuth.getInstance().removeAuthStateListener(authListener)
                }
            }

            // Provide ThemeManager ke seluruh app
            ProvideThemeManager(context) { themeManager ->
                // Observe keep screen on setting
                val keepScreenOn by themeManager.keepScreenOn

                // Apply keep screen on setting
                LaunchedEffect(keepScreenOn) {
                    if (keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Gunakan theme dengan real-time switching
                FocusFlowThemeWithManager(themeManager) {
                    FocusFlowNavigation(navController = navController)
                }
            }
        }
        lifecycleScope.launch {
            try {
                val seeder = DataSeeder()
                seeder.fixMissingCompletedAt()
                Log.d("MainActivity", "Fixed missing completedAt fields")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fixing data: ${e.message}")
            }
        }
    }

    private fun loadAppSettings() {
        val prefs = getSharedPreferences("FocusFlowSettings", Context.MODE_PRIVATE)

        // Apply language setting saat startup
        val language = prefs.getString("language", "English") ?: "English"
        applyLanguage(language)

        // Apply keep screen on setting saat startup
        val keepScreenOn = prefs.getBoolean("keep_screen_on", true)
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyLanguage(language: String) {
        val locale = when (language) {
            "Indonesian" -> Locale("id")
            "Spanish" -> Locale("es")
            "French" -> Locale("fr")
            "German" -> Locale("de")
            else -> Locale("en")
        }

        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

}

