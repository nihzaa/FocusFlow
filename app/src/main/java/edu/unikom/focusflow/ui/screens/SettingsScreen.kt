package edu.unikom.focusflow.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import edu.unikom.focusflow.data.repository.FirebaseRepository
import edu.unikom.focusflow.ui.theme.ThemeManager
import edu.unikom.focusflow.ui.theme.rememberSettingsViewModel
import edu.unikom.focusflow.utils.DataSeeder
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.delay

// Data classes for settings
data class AppTimerSettings(
    val workDuration: Int = 25,
    val shortBreakDuration: Int = 5,
    val longBreakDuration: Int = 15,
    val longBreakInterval: Int = 4,
    val autoStartBreaks: Boolean = false,
    val autoStartPomodoros: Boolean = false
)

data class AppNotificationSettings(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val notificationEnabled: Boolean = true,
    val soundType: String = "Default"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { FirebaseRepository() }

    // Theme Manager for real-time updates
    val themeManager = remember { ThemeManager.getInstance(context) }
    val settingsViewModel = rememberSettingsViewModel(themeManager)

    // Observe theme states
    val isDarkMode by settingsViewModel.isDarkMode
    val currentLanguage by settingsViewModel.currentLanguage
    val keepScreenOn by settingsViewModel.keepScreenOn

    // SharedPreferences for other settings
    val sharedPrefs = remember {
        context.getSharedPreferences("FocusFlowSettings", Context.MODE_PRIVATE)
    }

    // Settings states for non-theme settings
    var timerSettings by remember { mutableStateOf(loadAppTimerSettings(sharedPrefs)) }
    var notificationSettings by remember { mutableStateOf(loadAppNotificationSettings(sharedPrefs)) }
    var showProgress by remember { mutableStateOf(sharedPrefs.getBoolean("show_progress", true)) }

    // Dialog states
    var showWorkDurationDialog by remember { mutableStateOf(false) }
    var showShortBreakDialog by remember { mutableStateOf(false) }
    var showLongBreakDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSoundDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // User info
    var userName by remember { mutableStateOf("User") }
    var userEmail by remember { mutableStateOf("") }

    // Apply keep screen on setting
    LaunchedEffect(keepScreenOn) {
        activity?.window?.let { window ->
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val profile = repository.getUserProfile()
                userName = profile?.name ?: "User"
                userEmail = profile?.email ?: FirebaseAuth.getInstance().currentUser?.email ?: ""
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Profile Section
            item {
                SettingsProfileSection(
                    userName = userName,
                    userEmail = userEmail,
                    onEditProfile = { navController.navigate("profile") }
                )
            }

            // Timer Settings Section
            item {
                SettingsGroupSection(title = "Timer Settings") {
                    SettingsClickableItem(
                        icon = Icons.Default.Timer,
                        title = "Work Duration",
                        subtitle = "${timerSettings.workDuration} minutes",
                        onClick = { showWorkDurationDialog = true }
                    )

                    SettingsClickableItem(
                        icon = Icons.Default.Coffee,
                        title = "Short Break",
                        subtitle = "${timerSettings.shortBreakDuration} minutes",
                        onClick = { showShortBreakDialog = true }
                    )

                    SettingsClickableItem(
                        icon = Icons.Default.Restaurant,
                        title = "Long Break",
                        subtitle = "${timerSettings.longBreakDuration} minutes",
                        onClick = { showLongBreakDialog = true }
                    )

                    SettingsSwitchItem(
                        icon = Icons.Default.PlayArrow,
                        title = "Auto-start Breaks",
                        subtitle = "Automatically start break timers",
                        isChecked = timerSettings.autoStartBreaks,
                        onToggle = { checked ->
                            timerSettings = timerSettings.copy(autoStartBreaks = checked)
                            saveAppTimerSettings(sharedPrefs, timerSettings)
                        }
                    )

                    SettingsSwitchItem(
                        icon = Icons.Default.Refresh,
                        title = "Auto-start Pomodoros",
                        subtitle = "Automatically start work sessions",
                        isChecked = timerSettings.autoStartPomodoros,
                        onToggle = { checked ->
                            timerSettings = timerSettings.copy(autoStartPomodoros = checked)
                            saveAppTimerSettings(sharedPrefs, timerSettings)
                        }
                    )
                }
            }

            // Notification Settings Section
            item {
                SettingsGroupSection(title = "Notifications") {
                    SettingsSwitchItem(
                        icon = Icons.Default.Notifications,
                        title = "Enable Notifications",
                        subtitle = "Show timer completion alerts",
                        isChecked = notificationSettings.notificationEnabled,
                        onToggle = { checked ->
                            notificationSettings = notificationSettings.copy(notificationEnabled = checked)
                            saveAppNotificationSettings(sharedPrefs, notificationSettings)
                        }
                    )

                    SettingsSwitchItem(
                        icon = Icons.Default.VolumeUp,
                        title = "Sound",
                        subtitle = "Play notification sounds",
                        isChecked = notificationSettings.soundEnabled,
                        onToggle = { checked ->
                            notificationSettings = notificationSettings.copy(soundEnabled = checked)
                            saveAppNotificationSettings(sharedPrefs, notificationSettings)
                        }
                    )

                    SettingsClickableItem(
                        icon = Icons.Default.MusicNote,
                        title = "Notification Sound",
                        subtitle = notificationSettings.soundType,
                        onClick = { showSoundDialog = true }
                    )

                    SettingsSwitchItem(
                        icon = Icons.Default.Vibration,
                        title = "Vibration",
                        subtitle = "Vibrate on notifications",
                        isChecked = notificationSettings.vibrationEnabled,
                        onToggle = { checked ->
                            notificationSettings = notificationSettings.copy(vibrationEnabled = checked)
                            saveAppNotificationSettings(sharedPrefs, notificationSettings)
                        }
                    )
                }
            }

            // App Settings Section
            item {
                SettingsGroupSection(title = "App Preferences") {
                    SettingsSwitchItem(
                        icon = Icons.Default.DarkMode,
                        title = "Dark Mode",
                        subtitle = if (isDarkMode) "Dark theme enabled" else "Light theme enabled",
                        isChecked = isDarkMode,
                        onToggle = {
                            settingsViewModel.toggleDarkMode()
                            Toast.makeText(context,
                                if (!isDarkMode) "Dark mode enabled" else "Light mode enabled",
                                Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsClickableItem(
                        icon = Icons.Default.Language,
                        title = "Language",
                        subtitle = currentLanguage,
                        onClick = { showLanguageDialog = true }
                    )

                    SettingsSwitchItem(
                        icon = Icons.Default.ScreenLockPortrait,
                        title = "Keep Screen On",
                        subtitle = if (keepScreenOn) "Screen stays on during sessions" else "Screen can turn off normally",
                        isChecked = keepScreenOn,
                        onToggle = {
                            settingsViewModel.setKeepScreenOn(it)
                            Toast.makeText(context,
                                if (it) "Screen will stay on during sessions" else "Screen can turn off normally",
                                Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsSwitchItem(
                        icon = Icons.Default.ShowChart,
                        title = "Show Progress",
                        subtitle = "Display progress indicators",
                        isChecked = showProgress,
                        onToggle = { checked ->
                            showProgress = checked
                            sharedPrefs.edit().putBoolean("show_progress", checked).apply()
                        }
                    )
                }
            }

            // Account Section
            item {
                SettingsGroupSection(title = "Account") {
                    SettingsClickableItem(
                        icon = Icons.Default.Backup,
                        title = "Backup Data",
                        subtitle = "Sync your data to cloud",
                        onClick = {
                            Toast.makeText(context, "Data backup started", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsClickableItem(
                        icon = Icons.Default.Download,
                        title = "Export Data",
                        subtitle = "Download your statistics",
                        onClick = {
                            Toast.makeText(context, "Export feature coming soon", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsClickableItem(
                        icon = Icons.Default.Logout,
                        title = "Logout",
                        subtitle = "Sign out of your account",
                        onClick = { showLogoutDialog = true },
                        isDestructive = true
                    )
                }
            }

            // About Section - REVISED WITH UNITRACK BRANDING
            item {
                SettingsGroupSection(title = "About") {
                    // App Info with UniTrack Branding
                    SettingsClickableItem(
                        icon = Icons.Default.Apps,
                        title = "UniTrack FocusFlow",
                        subtitle = "Part of UniTrack Productivity Suite",
                        onClick = { showAboutDialog = true }
                    )

                    // App Version with Developer Menu
                    AppVersionItem(navController = navController)

                    // Developer Info - Ndigi Solutions
                    SettingsClickableItem(
                        icon = Icons.Default.Business,
                        title = "Developer",
                        subtitle = "Ndigi Solutions",
                        onClick = {
                            // Open website or portfolio
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/ndigi.solutions"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Visit: ndigisolutions.com", Toast.LENGTH_LONG).show()
                            }
                        }
                    )

                    // Contact Support
                    SettingsClickableItem(
                        icon = Icons.Default.Email,
                        title = "Contact Support",
                        subtitle = "ndigisolutions@gmail.com",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:ndigisolutions@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "UniTrack FocusFlow Support")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Email: ndigisolutions@gmail.com", Toast.LENGTH_LONG).show()
                            }
                        }
                    )

                    // Privacy Policy
                    SettingsClickableItem(
                        icon = Icons.Default.PrivacyTip,
                        title = "Privacy Policy",
                        subtitle = "View our privacy policy",
                        onClick = {
                            Toast.makeText(context, "Privacy policy page coming soon", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Terms of Service
                    SettingsClickableItem(
                        icon = Icons.Default.Description,
                        title = "Terms of Service",
                        subtitle = "View terms and conditions",
                        onClick = {
                            Toast.makeText(context, "Terms of service page coming soon", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Rate App
                    SettingsClickableItem(
                        icon = Icons.Default.Star,
                        title = "Rate UniTrack FocusFlow",
                        subtitle = "Rate us on Play Store",
                        onClick = {
                            Toast.makeText(context, "Thank you for your support!", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Share App
                    SettingsClickableItem(
                        icon = Icons.Default.Share,
                        title = "Share App",
                        subtitle = "Share UniTrack FocusFlow with friends",
                        onClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT,
                                    "Check out UniTrack FocusFlow - A powerful productivity app by Ndigi Solutions!\n\n" +
                                            "Download from Play Store: https://play.google.com/store/apps/details?id=edu.unikom.focusflow")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share UniTrack FocusFlow"))
                        }
                    )
                }
            }

            // Footer with Copyright
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "© 2025 Ndigi Solutions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "All rights reserved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // About Dialog - NEW
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "UniTrack FocusFlow",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Version 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // App Description
                    Text(
                        text = "UniTrack FocusFlow is part of the UniTrack Productivity Suite, " +
                                "designed to help you stay focused and productive using the Pomodoro Technique.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // UniTrack Suite Apps
                    Column {
                        Text(
                            text = "UniTrack Suite:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• FocusFlow - Time Management", style = MaterialTheme.typography.bodySmall)
                        Text("• MoneyMate - Finance Tracker", style = MaterialTheme.typography.bodySmall)
                        Text("• LifeMap - Goal Planning", style = MaterialTheme.typography.bodySmall)
                        Text("• ReadLog - Reading Tracker", style = MaterialTheme.typography.bodySmall)
                        Text("• EatSmart - Nutrition Guide", style = MaterialTheme.typography.bodySmall)
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Developer Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Developed by Ndigi Solutions",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Other Dialogs (existing)
    if (showWorkDurationDialog) {
        SettingsDurationPickerDialog(
            title = "Work Duration",
            currentValue = timerSettings.workDuration,
            onValueSelected = { duration ->
                timerSettings = timerSettings.copy(workDuration = duration)
                saveAppTimerSettings(sharedPrefs, timerSettings)
                showWorkDurationDialog = false
                Toast.makeText(context, "Work duration set to $duration minutes", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showWorkDurationDialog = false }
        )
    }

    if (showShortBreakDialog) {
        SettingsDurationPickerDialog(
            title = "Short Break Duration",
            currentValue = timerSettings.shortBreakDuration,
            onValueSelected = { duration ->
                timerSettings = timerSettings.copy(shortBreakDuration = duration)
                saveAppTimerSettings(sharedPrefs, timerSettings)
                showShortBreakDialog = false
                Toast.makeText(context, "Short break set to $duration minutes", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showShortBreakDialog = false }
        )
    }

    if (showLongBreakDialog) {
        SettingsDurationPickerDialog(
            title = "Long Break Duration",
            currentValue = timerSettings.longBreakDuration,
            onValueSelected = { duration ->
                timerSettings = timerSettings.copy(longBreakDuration = duration)
                saveAppTimerSettings(sharedPrefs, timerSettings)
                showLongBreakDialog = false
                Toast.makeText(context, "Long break set to $duration minutes", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showLongBreakDialog = false }
        )
    }

    if (showLanguageDialog) {
        SettingsLanguagePickerDialog(
            currentLanguage = currentLanguage,
            onLanguageSelected = { language ->
                settingsViewModel.setLanguage(language)
                showLanguageDialog = false
                Toast.makeText(context, "Language changed to $language", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showSoundDialog) {
        SettingsSoundPickerDialog(
            currentSound = notificationSettings.soundType,
            onSoundSelected = { sound ->
                notificationSettings = notificationSettings.copy(soundType = sound)
                saveAppNotificationSettings(sharedPrefs, notificationSettings)
                showSoundDialog = false
                Toast.makeText(context, "Notification sound changed to $sound", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSoundDialog = false }
        )
    }

    if (showLogoutDialog) {
        SettingsLogoutConfirmationDialog(
            onConfirm = {
                coroutineScope.launch {
                    try {
                        FirebaseAuth.getInstance().signOut()
                        sharedPrefs.edit().clear().apply()
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Logout failed", Toast.LENGTH_SHORT).show()
                    }
                }
                showLogoutDialog = false
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}

// ... (rest of the existing components remain the same)

@Composable
fun SettingsProfileSection(
    userName: String,
    userEmail: String,
    onEditProfile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEditProfile() }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = userEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsGroupSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(4.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (isDestructive) Color.Red.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isDestructive) Color.Red else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) Color.Red else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

// App Version Item with Hidden Developer Menu - REVISED
@Composable
fun AppVersionItem(navController: NavController) {
    var tapCount by remember { mutableStateOf(0) }
    var showDevMenu by remember { mutableStateOf(false) }
    var isSeeding by remember { mutableStateOf(false) }
    var seedingMessage by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val seeder = remember { DataSeeder() }
    val context = LocalContext.current

    Column {
        // App Version Item (tap 7 times to show dev menu)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    tapCount++
                    if (tapCount >= 7 && !showDevMenu) {
                        showDevMenu = true
                        Toast.makeText(context, "🔧 Developer mode activated!", Toast.LENGTH_SHORT).show()
                    } else if (tapCount < 7) {
                        // Show subtle hint
                        val remaining = 7 - tapCount
                        if (remaining <= 3) {
                            Toast.makeText(context, "$remaining more taps...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "App Version",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "UniTrack FocusFlow 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showDevMenu) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF6F00).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "DEV",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF6F00),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Developer Options (shown after 7 taps)
        AnimatedVisibility(visible = showDevMenu) {
            Column {
                Divider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(
                            Color(0xFFFFECB3).copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = Color(0xFFFF6F00),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Developer Options",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6F00)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Seed Data Button
                        OutlinedButton(
                            onClick = {
                                pendingAction = "seed"
                                showConfirmDialog = true
                            },
                            enabled = !isSeeding,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF4CAF50)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50))
                        ) {
                            if (isSeeding && seedingMessage.contains("Generating")) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF4CAF50),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSeeding && seedingMessage.contains("Generating")) "Seeding..." else "Seed Dummy Data",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Clear Data Button
                        OutlinedButton(
                            onClick = {
                                pendingAction = "clear"
                                showConfirmDialog = true
                            },
                            enabled = !isSeeding,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFE57373)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFE57373))
                        ) {
                            if (isSeeding && seedingMessage.contains("Clearing")) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFFE57373),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSeeding && seedingMessage.contains("Clearing")) "Clearing..." else "Clear All Data",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        // Status message
                        AnimatedVisibility(visible = seedingMessage.isNotEmpty()) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        seedingMessage.contains("✅") -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                                        seedingMessage.contains("❌") -> Color(0xFFE57373).copy(alpha = 0.1f)
                                        else -> Color(0xFF2196F3).copy(alpha = 0.1f)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = seedingMessage,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            seedingMessage.contains("✅") -> Color(0xFF4CAF50)
                                            seedingMessage.contains("❌") -> Color(0xFFE57373)
                                            else -> Color(0xFF2196F3)
                                        },
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Info text
                        Text(
                            text = "⚠️ This will generate/clear 90 days of sample data",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Confirmation Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSeeding) {
                    showConfirmDialog = false
                    pendingAction = null
                }
            },
            icon = {
                Icon(
                    if (pendingAction == "seed") Icons.Default.Science else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (pendingAction == "seed") Color(0xFF4CAF50) else Color(0xFFE57373),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    if (pendingAction == "seed") "Generate Test Data?" else "Clear All Data?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        if (pendingAction == "seed")
                            "This will generate comprehensive test data including:"
                        else
                            "This will permanently delete all your data including:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BulletPoint(if (pendingAction == "seed") "30 days of tasks" else "All tasks")
                        BulletPoint(if (pendingAction == "seed") "90 days of sessions" else "All sessions")
                        BulletPoint(if (pendingAction == "seed") "Profile statistics" else "Profile data")
                        BulletPoint(if (pendingAction == "seed") "Realistic completion rates" else "All statistics")
                    }

                    if (pendingAction == "seed") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFECB3).copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Note: Existing data will be cleared first",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF6F00),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false

                        coroutineScope.launch {
                            isSeeding = true

                            try {
                                if (pendingAction == "seed") {
                                    // Generate test data
                                    seedingMessage = "Step 1/3: Clearing old data..."
                                    Log.d("SettingsScreen", "Starting data seeding...")
                                    delay(500)

                                    seedingMessage = "Step 2/3: Generating tasks and sessions..."
                                    delay(500)

                                    seedingMessage = "Step 3/3: Updating statistics..."
                                    seeder.seedAllData()

                                    seedingMessage = "✅ Test data generated successfully!"
                                    Toast.makeText(context, "✅ Test data generated!", Toast.LENGTH_LONG).show()
                                    Log.d("SettingsScreen", "Data seeding completed successfully")

                                    // Navigate to home after delay to refresh
                                    delay(1500)
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }

                                } else {
                                    // Clear data only
                                    seedingMessage = "Clearing all data..."
                                    Log.d("SettingsScreen", "Starting data clearing...")
                                    seeder.clearAllData()

                                    seedingMessage = "✅ All data cleared successfully!"
                                    Toast.makeText(context, "✅ All data cleared!", Toast.LENGTH_LONG).show()
                                    Log.d("SettingsScreen", "Data clearing completed successfully")

                                    // Navigate to home after delay
                                    delay(1500)
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }

                            } catch (e: Exception) {
                                seedingMessage = "❌ Error: ${e.message}"
                                Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
                                Log.e("SettingsScreen", "Error in data operation", e)
                            } finally {
                                isSeeding = false
                                pendingAction = null

                                // Clear message after delay
                                delay(3000)
                                seedingMessage = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingAction == "seed") Color(0xFF4CAF50) else Color(0xFFE57373)
                    )
                ) {
                    Text(if (pendingAction == "seed") "Generate" else "Clear")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        pendingAction = null
                    },
                    enabled = !isSeeding
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Dialog Components (existing)
@Composable
fun SettingsDurationPickerDialog(
    title: String,
    currentValue: Int,
    onValueSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedValue by remember { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Select duration: $selectedValue minutes")
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = selectedValue.toFloat(),
                    onValueChange = { selectedValue = it.toInt() },
                    valueRange = 1f..60f,
                    steps = 0,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onValueSelected(selectedValue) }) {
                Text("Confirm", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun SettingsLanguagePickerDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = listOf("English", "Indonesian", "Spanish", "French", "German")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Language") },
        text = {
            LazyColumn {
                items(languages.size) { index ->
                    val language = languages[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(language) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLanguage == language,
                            onClick = { onLanguageSelected(language) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = language,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
fun SettingsSoundPickerDialog(
    currentSound: String,
    onSoundSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sounds = listOf("Default", "Bell", "Chime", "Ding", "Notification")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Notification Sound") },
        text = {
            LazyColumn {
                items(sounds.size) { index ->
                    val sound = sounds[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSoundSelected(sound) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSound == sound,
                            onClick = { onSoundSelected(sound) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = sound,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
fun SettingsLogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logout") },
        text = { Text("Are you sure you want to logout? All local data will be cleared.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Logout", color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

// Helper functions for SharedPreferences
fun loadAppTimerSettings(prefs: SharedPreferences): AppTimerSettings {
    return AppTimerSettings(
        workDuration = prefs.getInt("work_duration", 25),
        shortBreakDuration = prefs.getInt("short_break_duration", 5),
        longBreakDuration = prefs.getInt("long_break_duration", 15),
        longBreakInterval = prefs.getInt("long_break_interval", 4),
        autoStartBreaks = prefs.getBoolean("auto_start_breaks", false),
        autoStartPomodoros = prefs.getBoolean("auto_start_pomodoros", false)
    )
}

fun saveAppTimerSettings(prefs: SharedPreferences, settings: AppTimerSettings) {
    prefs.edit().apply {
        putInt("work_duration", settings.workDuration)
        putInt("short_break_duration", settings.shortBreakDuration)
        putInt("long_break_duration", settings.longBreakDuration)
        putInt("long_break_interval", settings.longBreakInterval)
        putBoolean("auto_start_breaks", settings.autoStartBreaks)
        putBoolean("auto_start_pomodoros", settings.autoStartPomodoros)
        apply()
    }
}

fun loadAppNotificationSettings(prefs: SharedPreferences): AppNotificationSettings {
    return AppNotificationSettings(
        soundEnabled = prefs.getBoolean("sound_enabled", true),
        vibrationEnabled = prefs.getBoolean("vibration_enabled", true),
        notificationEnabled = prefs.getBoolean("notification_enabled", true),
        soundType = prefs.getString("sound_type", "Default") ?: "Default"
    )
}

fun saveAppNotificationSettings(prefs: SharedPreferences, settings: AppNotificationSettings) {
    prefs.edit().apply {
        putBoolean("sound_enabled", settings.soundEnabled)
        putBoolean("vibration_enabled", settings.vibrationEnabled)
        putBoolean("notification_enabled", settings.notificationEnabled)
        putString("sound_type", settings.soundType)
        apply()
    }
}