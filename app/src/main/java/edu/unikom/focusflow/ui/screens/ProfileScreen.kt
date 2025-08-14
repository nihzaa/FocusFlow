package edu.unikom.focusflow.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import edu.unikom.focusflow.data.models.UserProfile
import edu.unikom.focusflow.data.models.UserPreferences
import edu.unikom.focusflow.data.repository.FirebaseRepository
import edu.unikom.focusflow.ui.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import edu.unikom.focusflow.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    // Check if user is logged in
    if (currentUser == null) {
        NotLoggedInScreen(navController)
        return
    }

    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showPreferencesDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val repository = remember { FirebaseRepository() }
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current

    // Build GSO satu kali
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            // pastikan string ini ada dari google-services.json
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }

    // Buat GoogleSignInClient satu kali
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }





    // Load user profile
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                userProfile = repository.getUserProfile() ?: UserProfile(
                    name = currentUser.displayName ?: "User",
                    email = currentUser.email ?: "",
                    joinedDate = Date()
                )
            } catch (e: Exception) {
                userProfile = UserProfile(
                    name = currentUser.displayName ?: "User",
                    email = currentUser.email ?: "",
                    joinedDate = Date()
                )
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkGreen.copy(alpha = 0.05f),
                            Color.White
                        )
                    )
                )
        ) {
            if (isLoading) {
                LoadingScreen()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        ModernProfileHeader(
                            userProfile = userProfile!!,
                            onEditClick = { showEditDialog = true }
                        )
                    }

                    item {
                        ModernStatisticsSection(userProfile!!)
                    }

                    item {
                        ModernAchievementsSection(userProfile!!)
                    }

                    item {
                        ModernSettingsSection(
                            onPreferencesClick = { showPreferencesDialog = true },
                            onSignOutClick = { showLogoutDialog = true }
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showEditDialog) {
        ModernEditProfileDialog(
            userProfile = userProfile!!,
            onDismiss = { showEditDialog = false },
            onSave = { updatedProfile ->
                coroutineScope.launch {
                    repository.updateUserProfile(updatedProfile)
                    userProfile = updatedProfile
                }
                showEditDialog = false
            }
        )
    }

    if (showPreferencesDialog) {
        ModernPreferencesDialog(
            preferences = userProfile!!.preferences,
            onDismiss = { showPreferencesDialog = false },
            onSave = { updatedPreferences ->
                val updatedProfile = userProfile!!.copy(preferences = updatedPreferences)
                coroutineScope.launch {
                    repository.updateUserProfile(updatedProfile)
                    userProfile = updatedProfile
                }
                showPreferencesDialog = false
            }
        )
    }

    if (showLogoutDialog) {
        ModernLogoutDialog(
            onConfirm = {
                auth.signOut()
                // sign out dari Google agar pemilih akun muncul lagi
                googleSignInClient.signOut().addOnCompleteListener {
                    navController.navigate("onboarding") {
                        // pakai startDestinationId biar aman
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}



    @Composable
    fun NotLoggedInScreen(navController: NavController) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkGreen.copy(alpha = 0.1f),
                            Color.White
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                // Icon with animation
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .scale(scale),
                    tint = DarkGreen.copy(alpha = 0.6f)
                )

                Text(
                    text = "You're Not Logged In",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen
                )

                Text(
                    text = "Please sign in to access your profile and track your productivity journey",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGreen
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Sign In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    @Composable
    fun LoadingScreen() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = DarkGreen,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    "Loading profile...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }
    }

    @Composable
    fun ModernProfileHeader(
        userProfile: UserProfile,
        onEditClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = DarkGreen.copy(alpha = 0.1f)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Box {
                // Background gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    DarkGreen.copy(alpha = 0.8f),
                                    DarkGreen
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(40.dp))

                    // Profile Picture with border
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(4.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(DarkGreen, DarkGreen.copy(alpha = 0.8f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userProfile.name.take(2).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = userProfile.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Text(
                        text = userProfile.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Member since ${
                                SimpleDateFormat(
                                    "MMM yyyy",
                                    Locale.getDefault()
                                ).format(userProfile.joinedDate)
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onEditClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkGreen.copy(alpha = 0.1f),
                            contentColor = DarkGreen
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Profile", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    @Composable
    fun ModernStatisticsSection(userProfile: UserProfile) {
        Column {
            Text(
                text = "Your Statistics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModernStatCard(
                    title = "Focus Time",
                    value = formatProfileDuration(userProfile.totalFocusTime),
                    subtitle = "Total hours",
                    icon = Icons.Outlined.Timer,
                    gradient = listOf(
                        Color(0xFF667eea),
                        Color(0xFF764ba2)
                    ),
                    modifier = Modifier.weight(1f)
                )

                ModernStatCard(
                    title = "Sessions",
                    value = userProfile.totalPomodoroSessions.toString(),
                    subtitle = "Completed",
                    icon = Icons.Outlined.PlayCircle,
                    gradient = listOf(
                        Color(0xFF56CCF2),
                        Color(0xFF2F80ED)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModernStatCard(
                    title = "Tasks",
                    value = userProfile.totalCompletedTasks.toString(),
                    subtitle = "Finished",
                    icon = Icons.Outlined.CheckCircle,
                    gradient = listOf(
                        Color(0xFF11998e),
                        Color(0xFF38ef7d)
                    ),
                    modifier = Modifier.weight(1f)
                )

                ModernStatCard(
                    title = "Streak",
                    value = userProfile.currentStreak.toString(),
                    subtitle = "Days",
                    icon = Icons.Outlined.Whatshot,
                    gradient = listOf(
                        Color(0xFFfc4a1a),
                        Color(0xFFf7b733)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    @Composable
    fun ModernStatCard(
        title: String,
        value: String,
        subtitle: String,
        icon: ImageVector,
        gradient: List<Color>,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier
                .height(110.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = gradient[0].copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = gradient.map { it.copy(alpha = 0.1f) }
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            color = gradient[0],
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = gradient[0],
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = gradient[1]
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ModernAchievementsSection(userProfile: UserProfile) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Achievements",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                val unlockedCount = generateAchievements(userProfile).count { it.isUnlocked }
                Text(
                    text = "$unlockedCount/5",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkGreen,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    val achievements = generateAchievements(userProfile)

                    achievements.forEachIndexed { index, achievement ->
                        ModernAchievementItem(achievement)
                        if (index < achievements.size - 1) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(
                                color = Color.Gray.copy(alpha = 0.1f),
                                thickness = 1.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ModernAchievementItem(achievement: Achievement) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (achievement.isUnlocked) {
                            Brush.linearGradient(
                                colors = listOf(
                                    achievement.color.copy(alpha = 0.2f),
                                    achievement.color.copy(alpha = 0.1f)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Gray.copy(alpha = 0.1f),
                                    Color.Gray.copy(alpha = 0.05f)
                                )
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    achievement.icon,
                    contentDescription = null,
                    tint = if (achievement.isUnlocked) achievement.color else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (achievement.isUnlocked) Color.Black else Color.Gray
                    )
                    if (!achievement.isUnlocked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Gray.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "Locked",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                // Show progress for locked achievements
                if (!achievement.isUnlocked) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = achievement.progress,
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (achievement.isUnlocked) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Unlocked",
                    tint = achievement.color,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = "Locked",
                    tint = Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

@Composable
fun ModernSettingsSection(
    onPreferencesClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    // Tambahkan snackbar state
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Column {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(20.dp)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column {
                ModernSettingItem(
                    title = "Timer Preferences",
                    subtitle = "Customize your Pomodoro settings",
                    icon = Icons.Outlined.Timer,
                    onClick = onPreferencesClick,
                    iconColor = Color(0xFF667eea)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = Color.Gray.copy(alpha = 0.1f)
                )

                ModernSettingItem(
                    title = "Notifications",
                    subtitle = "Coming soon",
                    icon = Icons.Outlined.NotificationsActive,
                    onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Notifications feature coming soon!",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    iconColor = Color(0xFF56CCF2).copy(alpha = 0.6f),
                    enabled = false // Tambahkan parameter ini
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = Color.Gray.copy(alpha = 0.1f)
                )

                ModernSettingItem(
                    title = "Export Data",
                    subtitle = "Coming soon",
                    icon = Icons.Outlined.CloudDownload,
                    onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Export feature coming soon!",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    iconColor = Color(0xFF11998e).copy(alpha = 0.6f),
                    enabled = false // Tambahkan parameter ini
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = Color.Gray.copy(alpha = 0.1f)
                )

                ModernSettingItem(
                    title = "Sign Out",
                    subtitle = "Sign out from your account",
                    icon = Icons.Outlined.Logout,
                    onClick = onSignOutClick,
                    iconColor = Color(0xFFfc4a1a),
                    textColor = Color(0xFFfc4a1a)
                )
            }
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

// Update ModernSettingItem untuk support disabled state
@Composable
fun ModernSettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    iconColor: Color = DarkGreen,
    textColor: Color = Color.Black,
    enabled: Boolean = true // Tambahkan parameter
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(20.dp)
            .alpha(if (enabled) 1f else 0.6f), // Dim jika disabled
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) textColor else Color.Gray
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (subtitle == "Coming soon")
                    DarkGreen.copy(alpha = 0.6f)
                else
                    Color.Gray
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Go",
            tint = Color.Gray.copy(alpha = if (enabled) 0.5f else 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

    @Composable
    fun ModernSettingItem(
        title: String,
        subtitle: String,
        icon: ImageVector,
        onClick: () -> Unit,
        iconColor: Color = DarkGreen,
        textColor: Color = Color.Black
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Go",
                tint = Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    @Composable
    fun ModernEditProfileDialog(
        userProfile: UserProfile,
        onDismiss: () -> Unit,
        onSave: (UserProfile) -> Unit
    ) {
        var name by remember { mutableStateOf(userProfile.name) }

        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        "Edit Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DarkGreen,
                            focusedLabelColor = DarkGreen
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = userProfile.email,
                        onValueChange = { },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                onSave(userProfile.copy(name = name.trim()))
                            },
                            modifier = Modifier.weight(1f),
                            enabled = name.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ModernPreferencesDialog(
        preferences: UserPreferences,
        onDismiss: () -> Unit,
        onSave: (UserPreferences) -> Unit
    ) {
        var workDuration by remember { mutableIntStateOf(preferences.workDuration) }
        var shortBreakDuration by remember { mutableIntStateOf(preferences.shortBreakDuration) }
        var longBreakDuration by remember { mutableIntStateOf(preferences.longBreakDuration) }
        var autoStartBreaks by remember { mutableStateOf(preferences.autoStartBreaks) }
        var autoStartPomodoros by remember { mutableStateOf(preferences.autoStartPomodoros) }
        var soundEnabled by remember { mutableStateOf(preferences.soundEnabled) }

        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Timer Preferences",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Work Duration
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Work Duration",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "$workDuration min",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = workDuration.toFloat(),
                            onValueChange = { workDuration = it.toInt() },
                            valueRange = 15f..60f,
                            steps = 0,
                            colors = SliderDefaults.colors(
                                thumbColor = DarkGreen,
                                activeTrackColor = DarkGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Short Break
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Short Break",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "$shortBreakDuration min",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = shortBreakDuration.toFloat(),
                            onValueChange = { shortBreakDuration = it.toInt() },
                            valueRange = 3f..15f,
                            steps = 0,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2196F3),
                                activeTrackColor = Color(0xFF2196F3)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Long Break
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Long Break",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "$longBreakDuration min",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = longBreakDuration.toFloat(),
                            onValueChange = { longBreakDuration = it.toInt() },
                            valueRange = 15f..30f,
                            steps = 0,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF9C27B0),
                                activeTrackColor = Color(0xFF9C27B0)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Switches
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Gray.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Auto-start breaks",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = autoStartBreaks,
                                    onCheckedChange = { autoStartBreaks = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = DarkGreen,
                                        checkedTrackColor = DarkGreen.copy(alpha = 0.5f)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Auto-start pomodoros",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = autoStartPomodoros,
                                    onCheckedChange = { autoStartPomodoros = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = DarkGreen,
                                        checkedTrackColor = DarkGreen.copy(alpha = 0.5f)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Sound notifications",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = soundEnabled,
                                    onCheckedChange = { soundEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = DarkGreen,
                                        checkedTrackColor = DarkGreen.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                onSave(
                                    preferences.copy(
                                        workDuration = workDuration,
                                        shortBreakDuration = shortBreakDuration,
                                        longBreakDuration = longBreakDuration,
                                        autoStartBreaks = autoStartBreaks,
                                        autoStartPomodoros = autoStartPomodoros,
                                        soundEnabled = soundEnabled
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ModernLogoutDialog(
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFFfc4a1a)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Sign Out",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Are you sure you want to sign out from your account?",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFfc4a1a)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sign Out")
                        }
                    }
                }
            }
        }
    }

    // Helper functions
    data class Achievement(
        val title: String,
        val description: String,
        val icon: ImageVector,
        val color: Color,
        val isUnlocked: Boolean,
        val progress: String = ""
    )

    fun generateAchievements(userProfile: UserProfile): List<Achievement> {
        return listOf(
            Achievement(
                title = "First Focus",
                description = "Complete your first Pomodoro session",
                icon = Icons.Outlined.Timer,
                color = Color(0xFF667eea),
                isUnlocked = userProfile.totalPomodoroSessions > 0,
                progress = if (userProfile.totalPomodoroSessions == 0) "0/1 sessions" else "Completed!"
            ),
            Achievement(
                title = "Task Master",
                description = "Complete 10 tasks",
                icon = Icons.Outlined.CheckCircle,
                color = Color(0xFF11998e),
                isUnlocked = userProfile.totalCompletedTasks >= 10,
                progress = "${userProfile.totalCompletedTasks}/10 tasks"
            ),
            Achievement(
                title = "Streak Starter",
                description = "Maintain a 7-day streak",
                icon = Icons.Outlined.Whatshot,
                color = Color(0xFFfc4a1a),
                isUnlocked = userProfile.longestStreak >= 7,
                progress = "${userProfile.currentStreak}/7 days"
            ),
            Achievement(
                title = "Focus Marathon",
                description = "Focus for 25 hours total",
                icon = Icons.Outlined.EmojiEvents,
                color = Color(0xFFFFD700),
                isUnlocked = userProfile.totalFocusTime >= 1500,
                progress = "${userProfile.totalFocusTime}/1500 minutes (${(userProfile.totalFocusTime * 100 / 1500).toInt()}%)"
            ),
            Achievement(
                title = "Century Club",
                description = "Complete 100 Pomodoro sessions",
                icon = Icons.Outlined.Star,
                color = Color(0xFF9C27B0),
                isUnlocked = userProfile.totalPomodoroSessions >= 100,
                progress = "${userProfile.totalPomodoroSessions}/100 sessions"
            )
        )
    }

    fun formatProfileDuration(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60

        return when {
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }