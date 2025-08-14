package edu.unikom.focusflow.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import edu.unikom.focusflow.data.models.PomodoroSession
import edu.unikom.focusflow.data.models.SessionType
import edu.unikom.focusflow.data.repository.FirebaseRepository
import edu.unikom.focusflow.ui.components.BottomNavigationBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// Enhanced enum with custom date ranges
                    enum class TimePeriod(val displayName: String, val days: Int) {
        TODAY("Today", 1),
        WEEK("This Week", 7),
        MONTH("This Month", 30),
        THREE_MONTHS("3 Months", 90),
        YEAR("This Year", 365),
        CUSTOM("Custom", -1)
    }

        // Helper functions
        suspend fun loadAnalyticsData(repository: FirebaseRepository, period: TimePeriod): AnalyticsData {
            val calendar = Calendar.getInstance()
            val endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            val today = endDate

            calendar.add(Calendar.DAY_OF_YEAR, -period.days)
            val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

            return loadAnalyticsDataForDateRange(
                repository,
                Pair(calendar.time, Calendar.getInstance().time)
            )
        }

        suspend fun loadAnalyticsDataForDateRange(
            repository: FirebaseRepository,
            dateRange: Pair<Date, Date>
        ): AnalyticsData {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startDate = dateFormat.format(dateRange.first)
            val endDate = dateFormat.format(dateRange.second)
            val today = dateFormat.format(Date())

            // Load data from Firebase
            val sessions = repository.getSessionsForDateRange(startDate, endDate)
            val totalFocusTime = repository.getTotalFocusTimeInDateRange(startDate, endDate)
            val completedTasks = repository.getTasksCompletedInDateRange(startDate, endDate)

            // Calculate various metrics
            val completedSessions = sessions.filter { it.isCompleted && it.sessionType == SessionType.WORK }
            val skippedSessions = sessions.filter { !it.isCompleted && it.sessionType == SessionType.WORK }
            val breakSessions = sessions.filter { it.sessionType != SessionType.WORK && it.isCompleted }
            val totalBreakTime = breakSessions.sumOf { it.duration.toLong() }

            // Generate daily stats
            val dailyStats = generateDailyStats(sessions, dateRange, today)

            // Generate weekly progress for longer periods
            val weeklyProgress = if ((dateRange.second.time - dateRange.first.time) / (1000 * 60 * 60 * 24) > 7) {
                generateWeeklyProgress(sessions, dateRange)
            } else emptyList()

            // Calculate session type breakdown
            val sessionTypeBreakdown = sessions.groupBy { it.sessionType }
                .mapValues { it.value.count { session -> session.isCompleted } }

            // Calculate hourly distribution
            val hourlyDistribution = sessions.filter { it.isCompleted && it.sessionType == SessionType.WORK }
                .groupBy {
                    val calendar = Calendar.getInstance()
                    calendar.time = it.startTime ?: Date()
                    calendar.get(Calendar.HOUR_OF_DAY)
                }
                .mapValues { it.value.size }

            // Calculate category breakdown (mock data for now - would need tasks data)
            val categoryBreakdown = mapOf(
                "Work" to (totalFocusTime * 0.4).toInt(),
                "Study" to (totalFocusTime * 0.3).toInt(),
                "Personal" to (totalFocusTime * 0.2).toInt(),
                "Health" to (totalFocusTime * 0.1).toInt()
            )

            // Calculate best day
            val bestDayStat = dailyStats.maxByOrNull { it.focusTime }
            val bestDay = bestDayStat?.dayName ?: ""
            val bestDayMinutes = bestDayStat?.focusTime ?: 0

            // Calculate streaks
            val currentStreak = calculateCurrentStreak(sessions)
            val longestStreak = calculateLongestStreak(sessions)

            // Calculate productivity score
            val dayCount = ((dateRange.second.time - dateRange.first.time) / (1000 * 60 * 60 * 24)).toInt() + 1
            val productivityScore = calculateProductivityScore(
                totalFocusTime,
                completedSessions.size,
                currentStreak,
                dayCount
            )

            // Calculate average session time
            val averageSessionTime = if (completedSessions.isNotEmpty()) {
                completedSessions.map { it.duration }.average()
            } else 0.0

            return AnalyticsData(
                totalFocusTime = totalFocusTime,
                completedSessions = completedSessions.size,
                completedTasks = completedTasks,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                averageSessionTime = averageSessionTime,
                productivityScore = productivityScore,
                bestDay = bestDay,
                bestDayMinutes = bestDayMinutes,
                dailyStats = dailyStats,
                weeklyProgress = weeklyProgress,
                sessionTypeBreakdown = sessionTypeBreakdown,
                hourlyDistribution = hourlyDistribution,
                categoryBreakdown = categoryBreakdown,
                totalBreakTime = totalBreakTime,
                skippedSessions = skippedSessions.size,
                dateRange = Pair(startDate, endDate)
            )
        }

        fun generateDailyStats(
            sessions: List<PomodoroSession>,
            dateRange: Pair<Date, Date>,
            today: String
        ): List<DailyStats> {
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
            val statsMap = mutableMapOf<String, DailyStats>()

            // Initialize all days with zero stats
            calendar.time = dateRange.first
            while (calendar.time <= dateRange.second) {
                val date = dateFormat.format(calendar.time)
                val dayName = dayFormat.format(calendar.time)
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

                statsMap[date] = DailyStats(
                    date = date,
                    dayName = dayName,
                    focusTime = 0,
                    sessions = 0,
                    tasks = 0,
                    isToday = date == today,
                    isWeekend = isWeekend
                )
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            // Populate with actual data
            sessions.groupBy { it.date }.forEach { (date, daySessions) ->
                val focusTime = daySessions.filter { it.sessionType == SessionType.WORK && it.isCompleted }
                    .sumOf { it.duration.toLong() }
                val sessionCount = daySessions.count { it.isCompleted }

                statsMap[date]?.let { existing ->
                    statsMap[date] = existing.copy(
                        focusTime = focusTime,
                        sessions = sessionCount
                    )
                }
            }

            return statsMap.values.sortedBy { it.date }
        }

        fun generateWeeklyProgress(
            sessions: List<PomodoroSession>,
            dateRange: Pair<Date, Date>
        ): List<WeeklyProgress> {
            val calendar = Calendar.getInstance()
            val weekFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            val weeklyData = mutableListOf<WeeklyProgress>()

            calendar.time = dateRange.first
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

            var previousWeekTime: Long? = null

            while (calendar.time <= dateRange.second) {
                val weekStart = calendar.time
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                val weekEnd = if (calendar.time > dateRange.second) dateRange.second else calendar.time

                val weekLabel = "${weekFormat.format(weekStart)} - ${weekFormat.format(weekEnd)}"

                val weekSessions = sessions.filter { session ->
                    val sessionDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(session.date)
                    sessionDate != null && sessionDate >= weekStart && sessionDate <= weekEnd
                }

                val focusTime = weekSessions.filter { it.sessionType == SessionType.WORK && it.isCompleted }
                    .sumOf { it.duration.toLong() }
                val sessionCount = weekSessions.count { it.isCompleted }

                val improvement = previousWeekTime?.let {
                    if (it > 0) ((focusTime - it).toDouble() / it) * 100 else 0.0
                } ?: 0.0

                weeklyData.add(
                    WeeklyProgress(
                        week = weekLabel,
                        focusTime = focusTime,
                        sessions = sessionCount,
                        improvement = improvement
                    )
                )

                previousWeekTime = focusTime
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            return weeklyData
        }

        fun calculateCurrentStreak(sessions: List<PomodoroSession>): Int {
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            var streak = 0

            for (i in 0 until 365) {
                val date = dateFormat.format(calendar.time)
                val hasCompletedSessions = sessions.any {
                    it.date == date && it.isCompleted && it.sessionType == SessionType.WORK
                }

                if (hasCompletedSessions) {
                    streak++
                } else if (i > 0) {
                    break
                }

                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }

            return streak
        }

        fun calculateLongestStreak(sessions: List<PomodoroSession>): Int {
            if (sessions.isEmpty()) return 0

            val sortedDates = sessions
                .filter { it.isCompleted && it.sessionType == SessionType.WORK }
                .map { it.date }
                .distinct()
                .sorted()

            if (sortedDates.isEmpty()) return 0

            var longestStreak = 1
            var currentStreak = 1
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            for (i in 1 until sortedDates.size) {
                val prevDate = dateFormat.parse(sortedDates[i - 1])!!
                val currDate = dateFormat.parse(sortedDates[i])!!
                val daysDiff = ((currDate.time - prevDate.time) / (1000 * 60 * 60 * 24)).toInt()

                if (daysDiff == 1) {
                    currentStreak++
                    longestStreak = maxOf(longestStreak, currentStreak)
                } else {
                    currentStreak = 1
                }
            }

            return longestStreak
        }

        fun calculateProductivityScore(
            focusTime: Long,
            completedSessions: Int,
            streak: Int,
            periodDays: Int
        ): Int {
            val dailyAverage = if (periodDays > 0) focusTime / periodDays else 0
            val timeScore = minOf(40, (dailyAverage / 3).toInt()) // Max 40 points for 120min/day
            val sessionScore = minOf(30, completedSessions * 2) // Max 30 points for 15+ sessions
            val streakScore = minOf(30, streak * 3) // Max 30 points for 10+ day streak

            return timeScore + sessionScore + streakScore
        }

        fun generateSmartInsights(data: AnalyticsData): List<Insight> {
            val insights = mutableListOf<Insight>()

            // Productivity insights
            when {
                data.productivityScore >= 80 -> {
                    insights.add(
                        Insight(
                            "Outstanding productivity! You're in the top 10% of users",
                            InsightType.POSITIVE,
                            Icons.Default.EmojiEvents
                        )
                    )
                }
                data.productivityScore >= 60 -> {
                    insights.add(
                        Insight(
                            "Great job! Your productivity is above average",
                            InsightType.POSITIVE,
                            Icons.Default.ThumbUp
                        )
                    )
                }
                data.productivityScore >= 40 -> {
                    insights.add(
                        Insight(
                            "You're building good habits. Keep pushing!",
                            InsightType.NEUTRAL,
                            Icons.Default.TrendingUp
                        )
                    )
                }
                else -> {
                    insights.add(
                        Insight(
                            "Start with just one focus session today to build momentum",
                            InsightType.SUGGESTION,
                            Icons.Default.Lightbulb
                        )
                    )
                }
            }

            // Streak insights
            when {
                data.currentStreak >= 7 -> {
                    insights.add(
                        Insight(
                            "${data.currentStreak}-day streak! You're on fire!",
                            InsightType.POSITIVE,
                            Icons.Default.LocalFireDepartment
                        )
                    )
                }
                data.currentStreak >= 3 -> {
                    insights.add(
                        Insight(
                            "Keep it up! ${7 - data.currentStreak} more days to a week streak",
                            InsightType.NEUTRAL,
                            Icons.Default.CalendarMonth
                        )
                    )
                }
                data.currentStreak == 0 -> {
                    insights.add(
                        Insight(
                            "Start a new streak today with a focus session",
                            InsightType.SUGGESTION,
                            Icons.Default.PlayArrow
                        )
                    )
                }
            }

            // Time-based insights
            if (data.averageSessionTime > 0) {
                when {
                    data.averageSessionTime >= 25 -> {
                        insights.add(
                            Insight(
                                "Perfect session length! You're mastering the Pomodoro technique",
                                InsightType.POSITIVE,
                                Icons.Default.Timer
                            )
                        )
                    }
                    data.averageSessionTime < 20 -> {
                        insights.add(
                            Insight(
                                "Try extending sessions to 25 minutes for optimal focus",
                                InsightType.SUGGESTION,
                                Icons.Default.Schedule
                            )
                        )
                    }
                }
            }

            // Best day insight
            if (data.bestDayMinutes > 0) {
                insights.add(
                    Insight(
                        "Your best day was ${data.bestDay} with ${data.bestDayMinutes} minutes",
                        InsightType.NEUTRAL,
                        Icons.Default.EmojiEvents
                    )
                )
            }

            return insights.take(4)
        }

        fun formatDateDisplay(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                dateString
            }
        }

        fun formatAnalyticsDuration(minutes: Long): String {
            val hours = minutes / 60
            val mins = minutes % 60

            return when {
                hours > 0 -> "${hours}h ${mins}m"
                else -> "${mins}m"
            }
        }

        // Enhanced data class with more metrics
        data class AnalyticsData(
            val totalFocusTime: Long = 0, // in minutes
            val completedSessions: Int = 0,
            val completedTasks: Int = 0,
            val currentStreak: Int = 0,
            val longestStreak: Int = 0,
            val averageSessionTime: Double = 0.0,
            val productivityScore: Int = 0,
            val bestDay: String = "",
            val bestDayMinutes: Long = 0,
            val dailyStats: List<DailyStats> = emptyList(),
            val weeklyProgress: List<WeeklyProgress> = emptyList(),
            val sessionTypeBreakdown: Map<SessionType, Int> = emptyMap(),
            val hourlyDistribution: Map<Int, Int> = emptyMap(), // Hour of day -> session count
            val categoryBreakdown: Map<String, Int> = emptyMap(), // Task category -> minutes
            val totalBreakTime: Long = 0,
            val skippedSessions: Int = 0,
            val dateRange: Pair<String, String> = Pair("", "")
        )

        data class DailyStats(
            val date: String,
            val dayName: String,
            val focusTime: Long,
            val sessions: Int,
            val tasks: Int,
            val isToday: Boolean = false,
            val isWeekend: Boolean = false
        )

        data class WeeklyProgress(
            val week: String,
            val focusTime: Long,
            val sessions: Int,
            val improvement: Double = 0.0
        )

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun AnalyticsScreen(navController: NavController) {
            var selectedPeriod by remember { mutableStateOf(TimePeriod.WEEK) }
            var analyticsData by remember { mutableStateOf(AnalyticsData()) }
            var isLoading by remember { mutableStateOf(true) }
            var showDatePicker by remember { mutableStateOf(false) }
            var customDateRange by remember { mutableStateOf<Pair<Date, Date>?>(null) }
            var showExportDialog by remember { mutableStateOf(false) }
            var selectedView by remember { mutableStateOf("overview") } // overview, detailed, trends

            val repository = remember { FirebaseRepository() }
            val coroutineScope = rememberCoroutineScope()

            // Load analytics data
            LaunchedEffect(selectedPeriod, customDateRange) {
                coroutineScope.launch {
                    isLoading = true
                    analyticsData = if (selectedPeriod == TimePeriod.CUSTOM && customDateRange != null) {
                        loadAnalyticsDataForDateRange(repository, customDateRange!!)
                    } else {
                        loadAnalyticsData(repository, selectedPeriod)
                    }
                    isLoading = false
                }
            }

            Scaffold(
                topBar = {
                    ModernAnalyticsTopBar(
                        onExportClick = { showExportDialog = true },
                        onRefreshClick = {
                            coroutineScope.launch {
                                isLoading = true
                                analyticsData = loadAnalyticsData(repository, selectedPeriod)
                                isLoading = false
                            }
                        }
                    )
                },
                bottomBar = {
                    BottomNavigationBar(navController = navController)
                },
                containerColor = Color(0xFFF8F9FA)
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Modern Header with Date Range
                    ModernAnalyticsHeader(
                        dateRange = analyticsData.dateRange,
                        onDateRangeClick = { showDatePicker = true }
                    )

                    // View Toggle Tabs
                    ModernViewToggle(
                        selectedView = selectedView,
                        onViewChange = { selectedView = it }
                    )

                    // Period Selection with Custom option
                    ModernPeriodSelector(
                        selectedPeriod = selectedPeriod,
                        onPeriodSelected = { period ->
                            selectedPeriod = period
                            if (period == TimePeriod.CUSTOM) {
                                showDatePicker = true
                            }
                        }
                    )

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF4A6741),
                                strokeWidth = 3.dp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            when (selectedView) {
                                "overview" -> {
                                    item {
                                        // Productivity Score Card
                                        ModernProductivityScoreCard(analyticsData)
                                    }

                                    item {
                                        // Key Metrics Grid
                                        ModernMetricsGrid(analyticsData)
                                    }

                                    item {
                                        // Interactive Focus Chart
                                        ModernFocusChart(analyticsData.dailyStats, selectedPeriod)
                                    }

                                    item {
                                        // Quick Insights
                                        ModernQuickInsights(analyticsData)
                                    }
                                }

                                "detailed" -> {
                                    item {
                                        // Session Analysis
                                        ModernSessionAnalysis(analyticsData)
                                    }

                                    item {
                                        // Hourly Distribution Heatmap
                                        ModernHourlyHeatmap(analyticsData.hourlyDistribution)
                                    }

                                    item {
                                        // Category Breakdown
                                        ModernCategoryBreakdown(analyticsData.categoryBreakdown)
                                    }

                                    item {
                                        // Session Type Distribution
                                        ModernSessionTypeChart(analyticsData.sessionTypeBreakdown)
                                    }
                                }

                                "trends" -> {
                                    item {
                                        // Streak Tracker
                                        ModernStreakTracker(
                                            currentStreak = analyticsData.currentStreak,
                                            longestStreak = analyticsData.longestStreak
                                        )
                                    }

                                    item {
                                        // Weekly Comparison
                                        if (analyticsData.weeklyProgress.isNotEmpty()) {
                                            ModernWeeklyComparison(analyticsData.weeklyProgress)
                                        }
                                    }

                                    item {
                                        // Performance Trends
                                        ModernPerformanceTrends(analyticsData)
                                    }

                                    item {
                                        // Goal Progress
                                        ModernGoalProgress(analyticsData)
                                    }
                                }
                            }

                            item {
                                // Bottom spacing
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }

            // Date Range Picker Dialog
            if (showDatePicker) {
                DateRangePickerDialog(
                    onDateRangeSelected = { startDate, endDate ->
                        customDateRange = Pair(startDate, endDate)
                        selectedPeriod = TimePeriod.CUSTOM
                        showDatePicker = false
                    },
                    onDismiss = {
                        showDatePicker = false
                        if (selectedPeriod == TimePeriod.CUSTOM && customDateRange == null) {
                            selectedPeriod = TimePeriod.WEEK
                        }
                    }
                )
            }
        }

        @Composable
        fun ModernAnalyticsTopBar(
            onExportClick: () -> Unit,
            onRefreshClick: () -> Unit
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Column {
                    // Spacer for status bar and camera notch
                    Spacer(modifier = Modifier.height(40.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Analytics",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E3A3A)
                            )
                            Text(
                                text = "Track your productivity journey",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8E8E93)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onRefreshClick) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = Color(0xFF4A6741)
                                )
                            }
                            IconButton(onClick = onExportClick) {
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = "Export",
                                    tint = Color(0xFF4A6741)
                                )
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun ModernAnalyticsHeader(
            dateRange: Pair<String, String>,
            onDateRangeClick: () -> Unit
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable { onDateRangeClick() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4A6741)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Analyzing Period",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = if (dateRange.first.isNotEmpty() && dateRange.second.isNotEmpty()) {
                                "${formatDateDisplay(dateRange.first)} - ${formatDateDisplay(dateRange.second)}"
                            } else "Select date range",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = "Select Date",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        @Composable
        fun ModernViewToggle(
            selectedView: String,
            onViewChange: (String) -> Unit
        ) {
            val views = listOf(
                "overview" to "Overview",
                "detailed" to "Detailed",
                "trends" to "Trends"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                views.forEach { (key, label) ->
                    Surface(
                        onClick = { onViewChange(key) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedView == key) Color(0xFF4A6741) else Color.White,
                        shadowElevation = if (selectedView == key) 4.dp else 0.dp
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedView == key) Color.White else Color(0xFF8E8E93),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun ModernPeriodSelector(
            selectedPeriod: TimePeriod,
            onPeriodSelected: (TimePeriod) -> Unit
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(TimePeriod.values().toList()) { period ->
                    FilterChip(
                        onClick = { onPeriodSelected(period) },
                        label = {
                            Text(
                                period.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        selected = selectedPeriod == period,
                        leadingIcon = if (period == TimePeriod.CUSTOM) {
                            { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4A6741),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }
            }
        }

        @Composable
        fun ModernProductivityScoreCard(data: AnalyticsData) {
            val animatedScore by animateIntAsState(
                targetValue = data.productivityScore,
                animationSpec = tween(1000),
                label = "score_animation"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Productivity Score",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF8E8E93)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(160.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = animatedScore / 100f,
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 12.dp,
                            color = when {
                                animatedScore >= 80 -> Color(0xFF4CAF50)
                                animatedScore >= 60 -> Color(0xFFFF9800)
                                else -> Color(0xFFE57373)
                            },
                            trackColor = Color(0xFFE0E0E0)
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$animatedScore",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E3A3A)
                            )
                            Text(
                                text = when {
                                    animatedScore >= 80 -> "Excellent!"
                                    animatedScore >= 60 -> "Good Job!"
                                    animatedScore >= 40 -> "Keep Going!"
                                    else -> "Let's Start!"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    animatedScore >= 80 -> Color(0xFF4CAF50)
                                    animatedScore >= 60 -> Color(0xFFFF9800)
                                    else -> Color(0xFFE57373)
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Score breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ScoreComponent("Focus", data.totalFocusTime / 60, Icons.Default.Timer)
                        ScoreComponent("Sessions", data.completedSessions, Icons.Default.PlayCircle)
                        ScoreComponent("Streak", data.currentStreak, Icons.Default.LocalFireDepartment)
                    }
                }
            }
        }

        @Composable
        fun ScoreComponent(label: String, value: Number, icon: ImageVector) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color(0xFF4A6741),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E3A3A)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93)
                )
            }
        }

        @Composable
        fun ModernMetricsGrid(data: AnalyticsData) {
            val metrics = listOf(
                MetricItem(
                    title = "Total Focus",
                    value = formatAnalyticsDuration(data.totalFocusTime),
                    icon = Icons.Default.Timer,
                    color = Color(0xFF4A6741),
                    trend = "+12%"
                ),
                MetricItem(
                    title = "Completed",
                    value = "${data.completedSessions}",
                    subtitle = "sessions",
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF4CAF50),
                    trend = "+8%"
                ),
                MetricItem(
                    title = "Avg Session",
                    value = "${data.averageSessionTime.roundToInt()}",
                    subtitle = "minutes",
                    icon = Icons.Default.Speed,
                    color = Color(0xFF2196F3),
                    trend = "+5%"
                ),
                MetricItem(
                    title = "Best Day",
                    value = "${data.bestDayMinutes}",
                    subtitle = "minutes",
                    icon = Icons.Default.EmojiEvents,
                    color = Color(0xFFFFD700),
                    trend = null
                )
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(metrics) { metric ->
                    ModernMetricCard(metric)
                }
            }
        }

        data class MetricItem(
            val title: String,
            val value: String,
            val subtitle: String = "",
            val icon: ImageVector,
            val color: Color,
            val trend: String?
        )

        @Composable
        fun ModernMetricCard(metric: MetricItem) {
            Card(
                modifier = Modifier
                    .width(150.dp)
                    .height(140.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        Icon(
                            metric.icon,
                            contentDescription = null,
                            tint = metric.color,
                            modifier = Modifier.size(24.dp)
                        )

                        metric.trend?.let {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Column {
                        Text(
                            text = metric.value,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E3A3A)
                        )
                        if (metric.subtitle.isNotEmpty()) {
                            Text(
                                text = metric.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8E8E93)
                            )
                        }
                        Text(
                            text = metric.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8E8E93),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        @Composable
        fun ModernFocusChart(dailyStats: List<DailyStats>, period: TimePeriod) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Focus Trend",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E3A3A)
                        )

                        if (dailyStats.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF4A6741).copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "Avg: ${formatAnalyticsDuration(dailyStats.map { it.focusTime }.average().toLong())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4A6741),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (dailyStats.isEmpty()) {
                        EmptyChartState()
                    } else {
                        InteractiveFocusChart(dailyStats, period)
                    }
                }
            }
        }

        @Composable
        fun InteractiveFocusChart(dailyStats: List<DailyStats>, period: TimePeriod) {
            var selectedDay by remember { mutableStateOf<DailyStats?>(null) }
            val maxFocusTime = dailyStats.maxOfOrNull { it.focusTime } ?: 1L

            // Group data for better visualization
            val displayData = when {
                period == TimePeriod.YEAR -> dailyStats.chunked(30).map { chunk ->
                    chunk.first().copy(
                        focusTime = chunk.sumOf { it.focusTime } / chunk.size,
                        sessions = chunk.sumOf { it.sessions }
                    )
                }
                period == TimePeriod.THREE_MONTHS -> dailyStats.chunked(3).map { chunk ->
                    chunk.first().copy(
                        focusTime = chunk.sumOf { it.focusTime } / chunk.size,
                        sessions = chunk.sumOf { it.sessions }
                    )
                }
                else -> dailyStats.takeLast(minOf(14, dailyStats.size))
            }

            Column {
                // Chart
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    displayData.forEach { stat ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedDay = stat }
                        ) {
                            // Value label on hover/selection
                            AnimatedVisibility(
                                visible = selectedDay == stat,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF4A6741),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                ) {
                                    Text(
                                        text = "${stat.focusTime}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            val barHeight = if (maxFocusTime > 0) {
                                (140.dp * (stat.focusTime.toFloat() / maxFocusTime.toFloat())).coerceAtLeast(4.dp)
                            } else 4.dp

                            Box(
                                modifier = Modifier
                                    .width(if (displayData.size > 10) 16.dp else 24.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        when {
                                            stat.isToday -> Color(0xFF4A6741)
                                            stat.isWeekend -> Color(0xFF4A6741).copy(alpha = 0.5f)
                                            selectedDay == stat -> Color(0xFF4A6741).copy(alpha = 0.9f)
                                            else -> Color(0xFF4A6741).copy(alpha = 0.7f)
                                        }
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // X-axis labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    displayData.forEach { stat ->
                        Text(
                            text = when {
                                period == TimePeriod.WEEK || period == TimePeriod.TODAY -> stat.dayName.take(1)
                                else -> stat.date.split("-").last().toIntOrNull()?.toString() ?: ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (stat.isToday) Color(0xFF4A6741) else Color(0xFF8E8E93),
                            fontWeight = if (stat.isToday) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Selected day details
                selectedDay?.let { day ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF4A6741).copy(alpha = 0.05f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            DetailItem("Date", formatDateDisplay(day.date))
                            DetailItem("Focus", "${day.focusTime} min")
                            DetailItem("Sessions", day.sessions.toString())
                        }
                    }
                }
            }
        }

        @Composable
        fun DetailItem(label: String, value: String) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E3A3A)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93)
                )
            }
        }

        @Composable
        fun EmptyChartState() {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.BarChart,
                        contentDescription = null,
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No data available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8E8E93)
                    )
                    Text(
                        text = "Start a focus session to see your progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93)
                    )
                }
            }
        }

        @Composable
        fun ModernQuickInsights(data: AnalyticsData) {
            val insights = generateSmartInsights(data)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "AI Insights",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E3A3A)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    insights.forEach { insight ->
                        InsightCard(insight)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        data class Insight(
            val text: String,
            val type: InsightType,
            val icon: ImageVector
        )

        enum class InsightType {
            POSITIVE, NEGATIVE, NEUTRAL, SUGGESTION
        }

        @Composable
        fun InsightCard(insight: Insight) {
            val color = when (insight.type) {
                InsightType.POSITIVE -> Color(0xFF4CAF50)
                InsightType.NEGATIVE -> Color(0xFFE57373)
                InsightType.NEUTRAL -> Color(0xFF2196F3)
                InsightType.SUGGESTION -> Color(0xFFFF9800)
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    insight.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = insight.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2E3A3A),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        @Composable
        fun ModernSessionAnalysis(data: AnalyticsData) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Session Analysis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E3A3A)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Success rate
                    val successRate = if (data.completedSessions + data.skippedSessions > 0) {
                        (data.completedSessions.toFloat() / (data.completedSessions + data.skippedSessions) * 100).roundToInt()
                    } else 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Success Rate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF8E8E93)
                        )
                        Text(
                            text = "$successRate%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                successRate >= 80 -> Color(0xFF4CAF50)
                                successRate >= 60 -> Color(0xFFFF9800)
                                else -> Color(0xFFE57373)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = successRate / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            successRate >= 80 -> Color(0xFF4CAF50)
                            successRate >= 60 -> Color(0xFFFF9800)
                            else -> Color(0xFFE57373)
                        },
                        trackColor = Color(0xFFE0E0E0)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Session stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SessionStatItem("Completed", data.completedSessions, Color(0xFF4CAF50))
                        SessionStatItem("Skipped", data.skippedSessions, Color(0xFFE57373))
                        SessionStatItem("Break Time", "${data.totalBreakTime}m", Color(0xFF2196F3))
                    }
                }
            }
        }

        @Composable
        fun SessionStatItem(label: String, value: Any, color: Color) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = value.toString(),
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
        fun ModernHourlyHeatmap(hourlyDistribution: Map<Int, Int>) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Peak Productivity Hours",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E3A3A)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (hourlyDistribution.isEmpty()) {
                        Text(
                            text = "Not enough data to show productivity patterns",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF8E8E93),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val maxSessions = hourlyDistribution.values.maxOrNull() ?: 1

                        // Group hours into time blocks
                        val timeBlocks = listOf(
                            "Morning" to (6..11),
                            "Afternoon" to (12..16),
                            "Evening" to (17..21),
                            "Night" to listOf(22, 23, 0, 1, 2, 3, 4, 5).toList()
                        )

                        timeBlocks.forEach { (blockName, hours) ->
                            val blockSessions = hours.sumOf { hourlyDistribution[it] ?: 0 }
                            if (blockSessions > 0) {
                                TimeBlockRow(blockName, blockSessions, maxSessions * hours.count())
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun TimeBlockRow(blockName: String, sessions: Int, maxPossible: Int) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = blockName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2E3A3A),
                    modifier = Modifier.width(80.dp)
                )

                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    LinearProgressIndicator(
                        progress = sessions.toFloat() / maxPossible,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        color = Color(0xFF4A6741),
                        trackColor = Color(0xFFE0E0E0)
                    )

                    Text(
                        text = "$sessions sessions",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                    )
                }
            }
        }

        @Composable
        fun ModernCategoryBreakdown(categoryBreakdown: Map<String, Int>) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Focus by Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E3A3A)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (categoryBreakdown.isEmpty()) {
                        Text(
                            text = "No category data available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF8E8E93),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val colors = listOf(
                            Color(0xFF4A6741),
                            Color(0xFF2196F3),
                            Color(0xFFFF9800),
                            Color(0xFF9C27B0),
                            Color(0xFF4CAF50)
                        )

                        categoryBreakdown.entries.forEachIndexed { index, (category, minutes) ->
                            CategoryRow(
                                category = category.ifEmpty { "Uncategorized" },
                                minutes = minutes,
                                color = colors[index % colors.size]
                            )
                            if (index < categoryBreakdown.size - 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun CategoryRow(category: String, minutes: Int, color: Color) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2E3A3A)
                    )
                }

                Text(
                    text = formatAnalyticsDuration(minutes.toLong()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }

        @Composable
        fun ModernSessionTypeChart(sessionTypes: Map<SessionType, Int>) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Session Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E3A3A)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (sessionTypes.isEmpty()) {
                        Text(
                            text = "No session data available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF8E8E93),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val total = sessionTypes.values.sum()

                        sessionTypes.forEach { (type, count) ->
                            val percentage = if (total > 0) (count.toFloat() / total * 100).roundToInt() else 0
                            val color = when (type) {
                                SessionType.WORK -> Color(0xFF4A6741)
                                SessionType.SHORT_BREAK -> Color(0xFF2196F3)
                                SessionType.LONG_BREAK -> Color(0xFF9C27B0)
                            }

                            SessionTypeRow(type, count, percentage, color)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        @Composable
        fun SessionTypeRow(type: SessionType, count: Int, percentage: Int, color: Color) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            when (type) {
                                SessionType.WORK -> Icons.Default.Timer
                                SessionType.SHORT_BREAK -> Icons.Default.Coffee
                                SessionType.LONG_BREAK -> Icons.Default.Weekend
                            },
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2E3A3A)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = color.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "$percentage%",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = percentage / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = color,
                    trackColor = Color(0xFFE0E0E0)
                )
            }
        }

@Composable
fun ModernStreakTracker(currentStreak: Int, longestStreak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Streak Tracker",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E3A3A)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Current Streak
            StreakMetricRow(
                label = "Current Streak",
                value = currentStreak,
                icon = Icons.Default.LocalFireDepartment,
                color = Color(0xFFFF5722),
                unit = if (currentStreak == 1) "day" else "days"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Longest Streak
            StreakMetricRow(
                label = "Longest Streak",
                value = longestStreak,
                icon = Icons.Default.EmojiEvents,
                color = Color(0xFFFFD700),
                unit = if (longestStreak == 1) "day" else "days"
            )

            if (currentStreak > 0) {
                Spacer(modifier = Modifier.height(16.dp))

                // Streak encouragement message
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFF5722).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = when {
                                currentStreak >= 30 -> "Incredible! You're unstoppable! 🔥"
                                currentStreak >= 14 -> "Amazing! Two weeks strong! 💪"
                                currentStreak >= 7 -> "Great job! One week streak! 🎯"
                                currentStreak >= 3 -> "Keep going! Building momentum! 🚀"
                                else -> "Good start! Keep it up! ⭐"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF5722),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreakMetricRow(
    label: String,
    value: Int,
    icon: ImageVector,
    color: Color,
    unit: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2E3A3A)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "$value",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Progress bar showing progress to next milestone
            val nextMilestone = when {
                value < 3 -> 3
                value < 7 -> 7
                value < 14 -> 14
                value < 30 -> 30
                value < 60 -> 60
                value < 100 -> 100
                else -> value + 100
            }

            val progress = (value.toFloat() / nextMilestone).coerceAtMost(1f)

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = Color(0xFFE0E0E0)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (value >= nextMilestone) "Milestone achieved!" else "Next milestone: $nextMilestone $unit",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

        @Composable
        fun StreakItem(label: String, value: Int, isCurrent: Boolean) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (isCurrent) Color(0xFFFF5722) else Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "$value",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) Color(0xFFFF5722) else Color(0xFFFF9800)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93)
                )
                Text(
                    text = if (value == 1) "day" else "days",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93)
                )
            }
        }

        @Composable
        fun ModernWeeklyComparison(weeklyProgress: List<WeeklyProgress>) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Weekly Comparison",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E3A3A)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    weeklyProgress.take(4).forEach { week ->
                        WeekComparisonRow(week)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        @Composable
        fun WeekComparisonRow(week: WeeklyProgress) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = week.week,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2E3A3A)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatAnalyticsDuration(week.focusTime),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4A6741)
                        )

                        if (week.improvement != 0.0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (week.improvement > 0)
                                    Color(0xFF4CAF50).copy(alpha = 0.1f)
                                else
                                    Color(0xFFE57373).copy(alpha = 0.1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        if (week.improvement > 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                        contentDescription = null,
                                        tint = if (week.improvement > 0) Color(0xFF4CAF50) else Color(0xFFE57373),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${week.improvement.roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (week.improvement > 0) Color(0xFF4CAF50) else Color(0xFFE57373),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "${week.sessions} sessions completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93)
                )
            }
        }

        @Composable
        fun ModernPerformanceTrends(data: AnalyticsData) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Performance Metrics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E3A3A)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val metrics = listOf(
                        PerformanceMetric(
                            "Focus Quality",
                            if (data.averageSessionTime > 0) (data.averageSessionTime / 25 * 100).roundToInt() else 0,
                            Icons.Default.Speed,
                            Color(0xFF4A6741)
                        ),
                        PerformanceMetric(
                            "Consistency",
                            minOf(100, data.currentStreak * 10),
                            Icons.Default.CalendarMonth,
                            Color(0xFF2196F3)
                        ),
                        PerformanceMetric(
                            "Efficiency",
                            data.productivityScore,
                            Icons.Default.TrendingUp,
                            Color(0xFF4CAF50)
                        )
                    )

                    metrics.forEach { metric ->
                        PerformanceMetricRow(metric)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        data class PerformanceMetric(
            val name: String,
            val value: Int,
            val icon: ImageVector,
            val color: Color
        )

        @Composable
        fun PerformanceMetricRow(metric: PerformanceMetric) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    metric.icon,
                    contentDescription = null,
                    tint = metric.color,
                    modifier = Modifier.size(24.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = metric.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2E3A3A)
                        )
                        Text(
                            text = "${metric.value}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = metric.color
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = metric.value / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = metric.color,
                        trackColor = Color(0xFFE0E0E0)
                    )
                }
            }
        }

        @Composable
        fun ModernGoalProgress(data: AnalyticsData) {
            val dailyGoal = 8 // 8 pomodoros per day
            val weeklyGoal = 40 // 40 pomodoros per week
            val monthlyGoal = 160 // 160 pomodoros per month

            val dailyProgress = (data.dailyStats.lastOrNull()?.sessions ?: 0).toFloat() / dailyGoal
            val weeklyProgress = data.completedSessions.toFloat() / weeklyGoal

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Goal Progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E3A3A)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    GoalProgressItem("Daily Goal", dailyProgress, "$dailyGoal sessions")
                    Spacer(modifier = Modifier.height(12.dp))
                    GoalProgressItem("Weekly Goal", weeklyProgress.coerceAtMost(1f), "$weeklyGoal sessions")
                }
            }
        }

        @Composable
        fun GoalProgressItem(label: String, progress: Float, target: String) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2E3A3A)
                    )
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            progress >= 1f -> Color(0xFF4CAF50)
                            progress >= 0.5f -> Color(0xFFFF9800)
                            else -> Color(0xFFE57373)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = progress.coerceAtMost(1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        progress >= 1f -> Color(0xFF4CAF50)
                        progress >= 0.5f -> Color(0xFFFF9800)
                        else -> Color(0xFFE57373)
                    },
                    trackColor = Color(0xFFE0E0E0)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Target: $target",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93)
                )
            }
        }

        @Composable
        fun DateRangePickerDialog(
            onDateRangeSelected: (Date, Date) -> Unit,
            onDismiss: () -> Unit
        ) {
            var startDate by remember { mutableStateOf<Date?>(null) }
            var endDate by remember { mutableStateOf<Date?>(null) }

            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        "Select Date Range",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Quick selection buttons
                        Text(
                            "Quick Select:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        val quickRanges = listOf(
                            "Last 7 days" to 7,
                            "Last 14 days" to 14,
                            "Last 30 days" to 30,
                            "Last 90 days" to 90
                        )

                        quickRanges.chunked(2).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { (label, days) ->
                                    OutlinedButton(
                                        onClick = {
                                            val calendar = Calendar.getInstance()
                                            endDate = calendar.time
                                            calendar.add(Calendar.DAY_OF_YEAR, -days)
                                            startDate = calendar.time
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Selected dates display
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF4A6741).copy(alpha = 0.1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = Color(0xFF4A6741),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "From: ${startDate?.let { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it) } ?: "Not selected"}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = Color(0xFF4A6741),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "To: ${endDate?.let { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it) } ?: "Not selected"}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (startDate != null && endDate != null) {
                                onDateRangeSelected(startDate!!, endDate!!)
                            }
                        },
                        enabled = startDate != null && endDate != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A6741)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF8E8E93))
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }

