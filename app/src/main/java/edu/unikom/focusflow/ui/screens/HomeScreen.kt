package edu.unikom.focusflow.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import edu.unikom.focusflow.ui.components.BottomNavigationBar
import edu.unikom.focusflow.ui.theme.*
import edu.unikom.focusflow.data.models.*
import edu.unikom.focusflow.data.repository.FirebaseRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown

// Enhanced HomeStats with separated metrics
data class HomeStats(
    // Session-based metrics (dari Pomodoro Sessions)
    val totalFocusTime: Long = 0,           // Total focus time all-time (minutes)
    val todayFocusTime: Long = 0,           // Today's focus time (minutes)
    val completedSessions: Int = 0,         // Total completed work sessions all-time
    val todaySessions: Int = 0,             // Today's completed work sessions
    val currentStreak: Int = 0,             // Current streak (days)
    val weeklyFocusProgress: Float = 0f,    // Weekly goal progress (0-1) - FROM SESSIONS

    // Task-based metrics (dari Tasks)
    val activeTasks: Int = 0,               // Active tasks count
    val completedTasks: Int = 0,            // Total completed tasks all-time
    val tasksCompletedToday: Int = 0,       // Tasks completed today
    val overdueTasks: Int = 0,              // Overdue tasks count
    val todayTaskProgress: Float = 0f,      // Today's task completion rate (0-1) - FROM TASKS

    // Combined metrics
    val productivityScore: Int = 0          // Overall productivity score (0-100)
)

data class QuickAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)

data class StatItem(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val color: Color,
    val progress: Float
)

data class ActiveSessionInfo(
    val sessionType: SessionType,
    val timeLeft: Int,
    val taskTitle: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    // Auth check first
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    // Redirect if not authenticated
    if (currentUser == null) {
        LaunchedEffect(Unit) {
            navController.navigate("onboarding") {
                popUpTo(0) { inclusive = true }
            }
        }
        return
    }

    // State variables
    var homeStats by remember { mutableStateOf(HomeStats()) }
    var recentSessions by remember { mutableStateOf<List<PomodoroSession>>(emptyList()) }
    var upcomingTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var userDisplayName by remember { mutableStateOf("User") }
    var activeSessionInfo by remember { mutableStateOf<ActiveSessionInfo?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val repository = remember { FirebaseRepository() }
    val coroutineScope = rememberCoroutineScope()

    // Auto-refresh when screen becomes visible
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
                Log.d("HomeScreen", "Screen resumed, refreshing data...")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-refresh every 30 seconds
    LaunchedEffect(refreshTrigger) {
        kotlinx.coroutines.delay(30000L)
        refreshTrigger++
    }

    // Check for active Pomodoro session
    LaunchedEffect(refreshTrigger) {
        if (PomodoroStateManager.timerState == TimerState.RUNNING) {
            activeSessionInfo = ActiveSessionInfo(
                sessionType = PomodoroStateManager.currentSession,
                timeLeft = PomodoroStateManager.timeLeftInSeconds,
                taskTitle = null
            )
        } else {
            activeSessionInfo = null
        }
    }

    // Main data loading with separated metrics
    LaunchedEffect(refreshTrigger) {
        coroutineScope.launch {
            try {
                Log.d("HomeScreen", "Starting data load for user: ${currentUser.uid}")

                // Get user display name
                userDisplayName = when {
                    !currentUser.displayName.isNullOrEmpty() -> currentUser.displayName!!
                    !currentUser.email.isNullOrEmpty() -> currentUser.email!!.substringBefore("@")
                    else -> "User"
                }

                val profile = repository.getUserProfile()
                if (profile?.name?.isNotEmpty() == true) {
                    userDisplayName = profile.name
                }

                // Date setup
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val calendar = Calendar.getInstance()
                val currentDate = Date()

                // ============= LOAD SESSIONS DATA =============
                val allSessionsRaw = repository.getAllSessions()
                Log.d("HomeScreen", "Loaded ${allSessionsRaw.size} sessions from Firebase")

                // Debug sessions
                allSessionsRaw.take(5).forEach { session ->
                    Log.d("HomeScreen", "Session: type=${session.sessionType}, completed=${session.isCompleted}, duration=${session.duration}, date=${session.date}")
                }

                // Filter sessions for different time periods
                calendar.time = Date()
                val todayTime = calendar.time
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                val thirtyDaysAgo = calendar.time

                val sessionsLast30Days = allSessionsRaw.filter { session ->
                    if (!session.date.isNullOrEmpty()) {
                        try {
                            val sessionDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(session.date)
                            sessionDate != null && sessionDate.after(thirtyDaysAgo) && sessionDate.before(Date(todayTime.time + 86400000))
                        } catch (e: Exception) {
                            true
                        }
                    } else if (session.startTime != null) {
                        session.startTime!!.after(thirtyDaysAgo) && session.startTime!!.before(Date(todayTime.time + 86400000))
                    } else {
                        true
                    }
                }

                // Today's sessions
                val todaySessions = sessionsLast30Days.filter { session ->
                    session.date == today || (session.startTime != null &&
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(session.startTime!!) == today)
                }

                // Weekly sessions (for weekly goal)
                calendar.time = Date()
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val weekStart = calendar.time

                val weekSessions = sessionsLast30Days.filter { session ->
                    if (!session.date.isNullOrEmpty()) {
                        try {
                            val sessionDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(session.date)
                            sessionDate != null && sessionDate.after(weekStart)
                        } catch (e: Exception) {
                            false
                        }
                    } else if (session.startTime != null) {
                        session.startTime!!.after(weekStart)
                    } else {
                        false
                    }
                }

                // Calculate session-based metrics
                val totalFocusTime = allSessionsRaw.filter { it.isCompleted && it.sessionType == SessionType.WORK }
                    .sumOf { it.duration.toLong() }

                val todayFocusTime = todaySessions.filter { it.isCompleted && it.sessionType == SessionType.WORK }
                    .sumOf { it.duration.toLong() }

                val weeklyFocusTime = weekSessions.filter { it.isCompleted && it.sessionType == SessionType.WORK }
                    .sumOf { it.duration.toLong() }

                val completedSessionsCount = allSessionsRaw.count { it.isCompleted && it.sessionType == SessionType.WORK }
                val todaySessionsCount = todaySessions.count { it.isCompleted && it.sessionType == SessionType.WORK }

                // Weekly goal progress (25 hours = 1500 minutes)
                val weeklyGoalMinutes = 25 * 60
                val weeklyFocusProgress = (weeklyFocusTime.toFloat() / weeklyGoalMinutes).coerceAtMost(1f)

                Log.d("HomeScreen", "Session metrics - Total focus: $totalFocusTime min, Today: $todayFocusTime min, Weekly: $weeklyFocusTime min")

                // ============= LOAD TASKS DATA =============
                val allTasks = repository.getTasks().firstOrNull() ?: emptyList()
                Log.d("HomeScreen", "Loaded ${allTasks.size} tasks")

                // Calculate task-based metrics
                val activeTasks = allTasks.count { !it.isCompleted }
                val completedTasksTotal = allTasks.count { it.isCompleted }

                // Tasks completed today
                val tasksCompletedToday = allTasks.count { task ->
                    task.isCompleted && task.completedAt?.let {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it) == today
                    } == true
                }

                // Tasks relevant for today (created today, due today, or completed today)
                val todayRelevantTasks = allTasks.filter { task ->
                    val createdToday = task.createdAt?.let {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it) == today
                    } ?: false

                    val dueToday = task.dueDate?.let {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it) == today
                    } ?: false

                    val completedToday = task.isCompleted && task.completedAt?.let {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it) == today
                    } == true

                    createdToday || dueToday || completedToday
                }

                // Calculate today's task progress
                val todayTaskProgress = if (todayRelevantTasks.isNotEmpty()) {
                    todayRelevantTasks.count { it.isCompleted }.toFloat() / todayRelevantTasks.size
                } else if (activeTasks > 0 || completedTasksTotal > 0) {
                    // Fallback to overall progress if no tasks specific to today
                    completedTasksTotal.toFloat() / (activeTasks + completedTasksTotal)
                } else {
                    0f
                }

                // Overdue tasks
                val overdueTasks = allTasks.count { task ->
                    !task.isCompleted && task.dueDate?.before(currentDate) == true
                }

                Log.d("HomeScreen", "Task metrics - Active: $activeTasks, Completed today: $tasksCompletedToday, Today progress: ${(todayTaskProgress * 100).roundToInt()}%")

                // ============= CALCULATE COMBINED METRICS =============
                val currentStreak = calculateStreak(allSessionsRaw)

                // Productivity score (combined metric)
                val productivityScore = calculateProductivityScore(
                    totalFocusTime = totalFocusTime,
                    completedTasks = tasksCompletedToday,
                    currentStreak = currentStreak,
                    sessionProgress = weeklyFocusProgress,
                    taskProgress = todayTaskProgress
                )

                // Create final HomeStats with separated metrics
                homeStats = HomeStats(
                    // Session metrics
                    totalFocusTime = totalFocusTime,
                    todayFocusTime = todayFocusTime,
                    completedSessions = completedSessionsCount,
                    todaySessions = todaySessionsCount,
                    currentStreak = currentStreak,
                    weeklyFocusProgress = weeklyFocusProgress,

                    // Task metrics
                    activeTasks = activeTasks,
                    completedTasks = completedTasksTotal,
                    tasksCompletedToday = tasksCompletedToday,
                    overdueTasks = overdueTasks,
                    todayTaskProgress = todayTaskProgress,

                    // Combined
                    productivityScore = productivityScore
                )

                // Load recent sessions for activity display
                recentSessions = allSessionsRaw.filter { it.isCompleted }
                    .sortedByDescending { it.startTime ?: Date(0) }
                    .take(5)

                // Load upcoming tasks
                upcomingTasks = allTasks.filter { !it.isCompleted }
                    .sortedWith(
                        compareBy<Task> { task ->
                            if (task.dueDate?.before(currentDate) == true) 0 else 1
                        }.thenBy { task ->
                            when (task.priority) {
                                TaskPriority.HIGH -> 0
                                TaskPriority.MEDIUM -> 1
                                TaskPriority.LOW -> 2
                            }
                        }.thenBy { task ->
                            task.dueDate ?: Date(Long.MAX_VALUE)
                        }.thenBy { task ->
                            task.createdAt
                        }
                    )
                    .take(5)

                isLoading = false
                Log.d("HomeScreen", "Data loading completed successfully")

            } catch (e: Exception) {
                Log.e("HomeScreen", "Error loading data", e)
                isLoading = false
            }
        }
    }

    // UI Scaffold
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "FocusFlow",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF2E3A3A)
                    )
                },
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF2E3A3A)
                        )
                    }

                    if (homeStats.overdueTasks > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = Color(0xFFE57373)
                                ) {
                                    Text(homeStats.overdueTasks.toString())
                                }
                            }
                        ) {
                            IconButton(onClick = { navController.navigate("tasks") }) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Color(0xFF2E3A3A)
                                )
                            }
                        }
                    }

                    IconButton(onClick = { navController.navigate("profile") }) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Color(0xFF2E3A3A)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        bottomBar = {
            BottomNavigationBar(navController = navController)
        },
        containerColor = Color(0xFFF8F9FA)
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF4A6741),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                activeSessionInfo?.let { session ->
                    item {
                        ActiveSessionBanner(
                            sessionInfo = session,
                            onContinue = { navController.navigate("pomodoro") }
                        )
                    }
                }

                item {
                    EnhancedWelcomeSection(
                        userName = userDisplayName,
                        todayFocusTime = homeStats.todayFocusTime,
                        todaySessions = homeStats.todaySessions,
                        weeklyProgress = homeStats.weeklyFocusProgress, // FROM SESSIONS
                        currentStreak = homeStats.currentStreak
                    )
                }

                item {
                    TodayOverviewSection(
                        tasksCompleted = homeStats.tasksCompletedToday,
                        activeTasks = homeStats.activeTasks,
                        overdueTasks = homeStats.overdueTasks,
                        taskProgress = homeStats.todayTaskProgress, // FROM TASKS
                        onViewTasks = { navController.navigate("tasks") }
                    )
                }

                item {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E3A3A)
                    )
                }

                item {
                    EnhancedQuickActionsGrid(navController = navController)
                }

                item {
                    Text(
                        text = "Productivity Overview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E3A3A)
                    )
                }

                item {
                    EnhancedStatisticsGrid(homeStats = homeStats)
                }

                if (recentSessions.isNotEmpty()) {
                    item {
                        EnhancedRecentActivitySection(
                            recentSessions = recentSessions,
                            allTasks = upcomingTasks
                        )
                    }
                }

                if (upcomingTasks.isNotEmpty()) {
                    item {
                        EnhancedUpcomingTasksSection(
                            tasks = upcomingTasks,
                            onTaskClick = { task ->
                                navController.navigate("pomodoro")
                            },
                            onViewAll = { navController.navigate("tasks") }
                        )
                    }
                } else {
                    item {
                        EmptyTasksCard(
                            onAddTask = { navController.navigate("tasks") }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

// Extension function for FirebaseRepository
suspend fun FirebaseRepository.getAllSessions(): List<PomodoroSession> {
    return try {
        val calendar = Calendar.getInstance()
        val endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -90)
        val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        getSessionsForDateRange(startDate, endDate)
    } catch (e: Exception) {
        Log.e("HomeScreen", "Error getting all sessions", e)
        emptyList()
    }
}

@Composable
fun ActiveSessionBanner(
    sessionInfo: ActiveSessionInfo,
    onContinue: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onContinue() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (sessionInfo.sessionType) {
                SessionType.WORK -> Color(0xFF4A6741)
                SessionType.SHORT_BREAK -> Color(0xFF2196F3)
                SessionType.LONG_BREAK -> Color(0xFF9C27B0)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when (sessionInfo.sessionType) {
                        SessionType.WORK -> "Focus Session Active"
                        SessionType.SHORT_BREAK -> "Short Break Active"
                        SessionType.LONG_BREAK -> "Long Break Active"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    text = formatTimeFromSeconds(sessionInfo.timeLeft),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                sessionInfo.taskTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Continue",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun EnhancedWelcomeSection(
    userName: String,
    todayFocusTime: Long,
    todaySessions: Int,
    weeklyProgress: Float,
    currentStreak: Int
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient background - darker for better contrast
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isExpanded) 280.dp else 220.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF3E5F4E), // Darker top
                                Color(0xFF4A6741), // Original middle
                                Color(0xFF3A5641)  // Darker bottom for contrast
                            )
                        )
                    )
            )

            // Decorative circles (subtle)
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .offset(x = (-30).dp, y = (-30).dp)
                    .background(
                        Color.White.copy(alpha = 0.08f),
                        CircleShape
                    )
            )

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = 60.dp)
                    .background(
                        Color.White.copy(alpha = 0.05f),
                        CircleShape
                    )
            )

            // Main content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isExpanded = !isExpanded }
            ) {
                // Header section stays the same
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        AnimatedContent(
                            targetState = getGreeting(),
                            transitionSpec = {
                                fadeIn() + slideInVertically() togetherWith
                                        fadeOut() + slideOutVertically()
                            },
                            label = "greeting"
                        ) { greeting ->
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = userName.uppercase(),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 28.sp,
                                letterSpacing = 1.5.sp
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Streak badge with better contrast
                    if (currentStreak > 0) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF2A3F2A), // Darker background
                            border = BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp, 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = "Streak",
                                    tint = if (currentStreak >= 7) Color(0xFFFFD700) else Color(0xFFFFB74D),
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "$currentStreak",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "days",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Motivational message
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        when {
                            todaySessions == 0 -> Icons.Default.Rocket
                            todaySessions < 4 -> Icons.Default.TrendingUp
                            else -> Icons.Default.Celebration
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = getMotivationalMessage(todaySessions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // FIXED: Stats row with MUCH better contrast
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E2E1E).copy(alpha = 0.85f), // Much darker background
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItemEnhanced(
                            icon = Icons.Default.Timer,
                            label = "Focus",
                            value = formatDuration(todayFocusTime),
                            iconColor = Color(0xFFFFD54F) // Brighter yellow
                        )

                        // Vertical divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(50.dp)
                                .background(Color.White.copy(alpha = 0.3f))
                        )

                        StatItemEnhanced(
                            icon = Icons.Default.PlayCircle,
                            label = "Sessions",
                            value = "$todaySessions",
                            subtitle = "/ 8",
                            iconColor = Color(0xFF64B5F6) // Brighter blue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // FIXED: Progress section with better visibility
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "Weekly Goal",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black, // Full white
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "25 hours focus time",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Black.copy(alpha = 0.85f)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E2E1E).copy(alpha = 0.7f) // Dark background for percentage
                        ) {
                            Text(
                                text = "${(weeklyProgress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Progress bar with better contrast
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1A1A1A).copy(alpha = 0.6f)) // Darker track
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(weeklyProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF66BB6A), // Brighter green
                                            Color(0xFF42A5F5)  // Brighter blue
                                        )
                                    )
                                )
                                .animateContentSize()
                        )
                    }
                }

                // Expandable section dengan kontras yang lebih baik
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.3f),
                            thickness = 1.dp
                        )

                        // Additional stats dengan background
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E2E1E).copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                MiniStatItem(
                                    label = "Best Streak",
                                    value = "${currentStreak + 3}d",
                                    icon = Icons.Default.EmojiEvents
                                )
                                MiniStatItem(
                                    label = "Avg Focus",
                                    value = "${todayFocusTime / maxOf(todaySessions, 1)}m",
                                    icon = Icons.Default.Analytics
                                )
                                MiniStatItem(
                                    label = "Rank",
                                    value = "#12",
                                    icon = Icons.Default.Leaderboard
                                )
                            }
                        }
                    }
                }

                // Expand indicator
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = Color.DarkGray.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(20.dp)
                )
            }
        }
    }
}

@Composable
fun StatItemEnhanced(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String? = null,
    iconColor: Color = Color.White
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    iconColor.copy(alpha = 0.25f), // Slightly more opaque
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White, // Full white for better contrast
                fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f) // Better contrast
        )
    }
}

@Composable
fun MiniStatItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun TodayOverviewSection(
    tasksCompleted: Int,
    activeTasks: Int,
    overdueTasks: Int,
    taskProgress: Float, // FROM TASKS - Renamed from completionRate
    onViewTasks: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Tasks Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E3A3A)
                )

                if (overdueTasks > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE57373).copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "$overdueTasks overdue",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE57373),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TodayStatItem(
                    value = tasksCompleted.toString(),
                    label = "Completed",
                    color = Color(0xFF52C49C)
                )
                TodayStatItem(
                    value = activeTasks.toString(),
                    label = "Active",
                    color = Color(0xFF2196F3)
                )
                TodayStatItem(
                    value = "${(taskProgress * 100).roundToInt()}%",
                    label = "Task Progress",
                    color = Color(0xFF4A6741)
                )
            }
        }
    }
}

@Composable
fun TodayStatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8E8E93)
        )
    }
}

@Composable
fun StatItemNew(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun EnhancedQuickActionsGrid(navController: NavController) {
    val quickActions = listOf(
        QuickAction("Start Focus", "Be Productive", Icons.Default.PlayArrow, Color(0xFF4A6741), "pomodoro"),
        QuickAction("My Tasks", "Manage todos", Icons.Default.Assignment, Color(0xFF2196F3), "tasks"),
        QuickAction("Analytics", "View progress", Icons.Default.BarChart, Color(0xFF9C27B0), "analytics"),
        QuickAction("Settings", "Preferences", Icons.Default.Settings, Color(0xFF8E8E93), "settings")
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.height(180.dp)
    ) {
        items(quickActions) { action ->
            EnhancedQuickActionCard(
                action = action,
                onClick = { navController.navigate(action.route) }
            )
        }
    }
}

@Composable
fun EnhancedQuickActionCard(
    action: QuickAction,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(action.color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    action.icon,
                    contentDescription = action.title,
                    tint = action.color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E3A3A)
                )
                Text(
                    text = action.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93)
                )
            }
        }
    }
}

@Composable
fun EnhancedStatisticsGrid(homeStats: HomeStats) {
    val statsItems = listOf(
        StatItem(
            "Focus Time",
            formatDuration(homeStats.todayFocusTime),
            Icons.Default.Timer,
            Color(0xFF4A6741),
            homeStats.todayFocusTime / 480f // Target: 8 hours = 480 minutes
        ),
        StatItem(
            "Tasks Done",
            "${homeStats.tasksCompletedToday}",
            Icons.Default.CheckCircle,
            Color(0xFF52C49C),
            homeStats.todayTaskProgress // Use task progress directly
        ),
        StatItem(
            "Sessions",
            "${homeStats.todaySessions}/8",
            Icons.Default.PlayCircleFilled,
            Color(0xFF2196F3),
            homeStats.todaySessions / 8f
        ),
        StatItem(
            "Productivity",
            "${homeStats.productivityScore}",
            Icons.Default.TrendingUp,
            Color(0xFFFF9800),
            homeStats.productivityScore / 100f
        )
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.height(180.dp)
    ) {
        items(statsItems) { stat ->
            EnhancedStatCard(
                title = stat.title,
                value = stat.value,
                icon = stat.icon,
                color = stat.color,
                progress = stat.progress.coerceAtMost(1f)
            )
        }
    }
}

@Composable
fun EnhancedStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    progress: Float
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8E8E93),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E3A3A)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
fun EnhancedRecentActivitySection(
    recentSessions: List<PomodoroSession>,
    allTasks: List<Task>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Recent Activity",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E3A3A)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                recentSessions.take(3).forEach { session ->
                    RecentSessionItem(session = session)
                    if (session != recentSessions.take(3).last()) {
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

@Composable
fun RecentSessionItem(session: PomodoroSession) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                when (session.sessionType) {
                    SessionType.WORK -> Icons.Default.Timer
                    SessionType.SHORT_BREAK -> Icons.Default.Coffee
                    SessionType.LONG_BREAK -> Icons.Default.Weekend
                },
                contentDescription = null,
                tint = when (session.sessionType) {
                    SessionType.WORK -> Color(0xFF4A6741)
                    SessionType.SHORT_BREAK -> Color(0xFF2196F3)
                    SessionType.LONG_BREAK -> Color(0xFF9C27B0)
                },
                modifier = Modifier.size(20.dp)
            )

            Column {
                Text(
                    text = when (session.sessionType) {
                        SessionType.WORK -> "Focus Session"
                        SessionType.SHORT_BREAK -> "Short Break"
                        SessionType.LONG_BREAK -> "Long Break"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2E3A3A)
                )
                Text(
                    text = getTimeAgo(session.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93)
                )
            }
        }

        Text(
            text = "${session.duration} min",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = when (session.sessionType) {
                SessionType.WORK -> Color(0xFF4A6741)
                SessionType.SHORT_BREAK -> Color(0xFF2196F3)
                SessionType.LONG_BREAK -> Color(0xFF9C27B0)
            }
        )
    }
}

@Composable
fun EnhancedUpcomingTasksSection(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    onViewAll: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Upcoming Tasks",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E3A3A)
            )

            TextButton(onClick = onViewAll) {
                Text(
                    text = "View All",
                    color = Color(0xFF4A6741),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                tasks.forEachIndexed { index, task ->
                    EnhancedUpcomingTaskItem(
                        task = task,
                        onClick = { onTaskClick(task) }
                    )
                    if (index < tasks.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.Gray.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedUpcomingTaskItem(
    task: Task,
    onClick: () -> Unit
) {
    val isOverdue = task.dueDate?.before(Date()) == true
    val progress = if (task.estimatedPomodoros > 0)
        task.pomodoroSessions.toFloat() / task.estimatedPomodoros
    else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue)
                Color(0xFFE57373).copy(alpha = 0.05f)
            else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isOverdue)
                Color(0xFFE57373).copy(alpha = 0.2f)
            else Color.Gray.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Title with priority
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (task.priority) {
                                    TaskPriority.HIGH -> Color(0xFFE57373)
                                    TaskPriority.MEDIUM -> Color(0xFFFFB74D)
                                    TaskPriority.LOW -> Color(0xFF81C784)
                                }
                            )
                    )
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2E3A3A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Status badge
                if (isOverdue) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE57373)
                    ) {
                        Text(
                            text = "OVERDUE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Task metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side - Category, date, subtasks
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.category.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Label,
                                contentDescription = null,
                                tint = Color(0xFF8E8E93),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = task.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8E8E93)
                            )
                        }
                    }

                    task.dueDate?.let { date ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = if (isOverdue) Color(0xFFE57373) else Color(0xFF8E8E93),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(date),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOverdue) Color(0xFFE57373) else Color(0xFF8E8E93),
                                fontWeight = if (isOverdue) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }

                    if (task.subtasks.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF8E8E93),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${task.subtasks.count { it.isCompleted }}/${task.subtasks.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8E8E93)
                            )
                        }
                    }
                }

                // Right side - Pomodoro progress
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${task.pomodoroSessions}/${task.estimatedPomodoros}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF4A6741),
                        fontWeight = FontWeight.Medium
                    )

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Gray.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(
                                    if (progress >= 1f) Color(0xFF4CAF50)
                                    else Color(0xFF4A6741)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyTasksCard(
    onAddTask: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAddTask() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.AddTask,
                contentDescription = null,
                tint = Color(0xFF4A6741),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No tasks yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E3A3A)
            )
            Text(
                text = "Tap to create your first task",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

// Enhanced helper functions
fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }
}

fun getMotivationalMessage(todaySessions: Int): String {
    return when {
        todaySessions == 0 -> "Ready to start your first session?"
        todaySessions < 4 -> "Great start! Keep the momentum going!"
        todaySessions < 8 -> "You're on fire! Halfway to your daily goal!"
        else -> "Amazing productivity today! 🎉"
    }
}

fun formatDuration(minutes: Long): String {
    val hours = minutes / 60
    val mins = minutes % 60

    return when {
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}

fun formatTimeFromSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

fun getTimeAgo(date: Date?): String {
    if (date == null) return "Unknown"

    val now = Date()
    val diffMs = now.time - date.time
    val diffMinutes = diffMs / (1000 * 60)
    val diffHours = diffMs / (1000 * 60 * 60)
    val diffDays = diffMs / (1000 * 60 * 60 * 24)

    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "$diffMinutes min ago"
        diffHours < 24 -> "$diffHours hours ago"
        diffDays < 7 -> "$diffDays days ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
    }
}

fun calculateStreak(sessions: List<PomodoroSession>): Int {
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var streak = 0
    var checkDate = calendar.time

    for (i in 0 until 30) {
        val date = dateFormat.format(checkDate)
        val hasCompletedSessions = sessions.any {
            it.date == date && it.isCompleted && it.sessionType == SessionType.WORK
        }

        if (hasCompletedSessions) {
            streak++
        } else if (i > 0) {
            break
        }

        calendar.add(Calendar.DAY_OF_YEAR, -1)
        checkDate = calendar.time
    }

    return streak
}

// New enhanced productivity score calculation
fun calculateProductivityScore(
    totalFocusTime: Long,
    completedTasks: Int,
    currentStreak: Int,
    sessionProgress: Float,
    taskProgress: Float
): Int {
    // Weight distribution:
    // - Focus time: 30%
    // - Task completion: 30%
    // - Streak: 20%
    // - Session progress: 10%
    // - Task progress: 10%

    val focusScore = minOf(30, (totalFocusTime / 60).toInt()) // Max 30 points for 30+ hours
    val taskScore = minOf(30, completedTasks * 3) // Max 30 points for 10+ tasks
    val streakScore = minOf(20, currentStreak * 2) // Max 20 points for 10+ day streak
    val sessionScore = (sessionProgress * 10).roundToInt() // Max 10 points
    val taskProgressScore = (taskProgress * 10).roundToInt() // Max 10 points

    return focusScore + taskScore + streakScore + sessionScore + taskProgressScore
}