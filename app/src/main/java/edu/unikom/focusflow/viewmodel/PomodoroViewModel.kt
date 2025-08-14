// PomodoroViewModel.kt - Complete dengan progress tracking
package edu.unikom.focusflow.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import edu.unikom.focusflow.data.models.*
import edu.unikom.focusflow.data.repository.FirebaseRepository
import java.text.SimpleDateFormat
import java.util.*

class PomodoroViewModel(
    private val repository: FirebaseRepository
) : ViewModel() {

    // Private mutable states
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    private val _selectedTask = MutableStateFlow<Task?>(null)
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    private val _todaySessions = MutableStateFlow<List<PomodoroSession>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    // Public read-only states
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()
    val selectedTask: StateFlow<Task?> = _selectedTask.asStateFlow()
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()
    val todaySessions: StateFlow<List<PomodoroSession>> = _todaySessions.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    // Computed properties
    val completedSessionsToday: StateFlow<Int> = todaySessions.map { sessions ->
        sessions.count { it.isCompleted && it.sessionType == SessionType.WORK }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val totalFocusTimeToday: StateFlow<Int> = todaySessions.map { sessions ->
        sessions.filter { it.isCompleted && it.sessionType == SessionType.WORK }
            .sumOf { it.duration }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val activeTasks: StateFlow<List<Task>> = tasks.map { taskList ->
        taskList.filter { !it.isCompleted }.sortedBy { it.priority }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Get user preferences from profile
    val userPreferences: StateFlow<UserPreferences?> = userProfile.map { profile ->
        profile?.preferences
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val currentDate = dateFormat.format(Date())

    init {
        loadInitialData()
    }

    /**
     * Load all initial data when ViewModel is created
     */
    fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Load user profile and preferences
                loadUserProfile()

                // Load tasks
                loadTasks()

                // Load today's sessions
                loadTodaySessions()

            } catch (e: Exception) {
                _error.value = "Failed to load data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load user profile and preferences from repository
     */
    private suspend fun loadUserProfile() {
        try {
            val profile = repository.getUserProfile()
            _userProfile.value = profile
        } catch (e: Exception) {
            _error.value = "Failed to load user profile: ${e.message}"
            // Create default profile if loading fails
            _userProfile.value = createDefaultUserProfile()
        }
    }

    /**
     * Load all tasks from repository
     */
    private fun loadTasks() {
        viewModelScope.launch {
            try {
                repository.getTasks().collect { taskList ->
                    _tasks.value = taskList
                }
            } catch (e: Exception) {
                _error.value = "Failed to load tasks: ${e.message}"
                _tasks.value = emptyList()
            }
        }
    }

    /**
     * Load today's sessions from repository
     */
    private suspend fun loadTodaySessions() {
        try {
            val sessions = repository.getSessionsForDate(currentDate)
            _todaySessions.value = sessions
        } catch (e: Exception) {
            _error.value = "Failed to load sessions: ${e.message}"
            _todaySessions.value = emptyList()
        }
    }

    /**
     * Select a task for focus session - SET AS IN PROGRESS
     */
    fun selectTask(task: Task?) {
        _selectedTask.value = task

        // Set task as in progress when selected
        if (task != null) {
            viewModelScope.launch {
                try {
                    repository.setTaskInProgress(task.id, true)
                    Log.d("PomodoroViewModel", "Task ${task.id} set as in progress")
                } catch (e: Exception) {
                    Log.e("PomodoroViewModel", "Error setting task in progress", e)
                }
            }
        }
    }

    /**
     * Clear selected task - CLEAR IN PROGRESS STATUS
     */
    fun clearSelectedTask() {
        _selectedTask.value?.let { task ->
            viewModelScope.launch {
                try {
                    repository.setTaskInProgress(task.id, false)
                    Log.d("PomodoroViewModel", "Task ${task.id} progress cleared")
                } catch (e: Exception) {
                    Log.e("PomodoroViewModel", "Error clearing task progress", e)
                }
            }
        }
        _selectedTask.value = null
    }

    /**
     * Complete a pomodoro session
     */
    fun completeSession(sessionType: SessionType, task: Task?): Result<String> {
        return runCatching {
            viewModelScope.launch {
                try {
                    val preferences = _userProfile.value?.preferences ?: UserPreferences()
                    val sessionDuration = when (sessionType) {
                        SessionType.WORK -> preferences.workDuration
                        SessionType.SHORT_BREAK -> preferences.shortBreakDuration
                        SessionType.LONG_BREAK -> preferences.longBreakDuration
                    }

                    val completedSession = PomodoroSession(
                        taskId = task?.id,
                        taskTitle = task?.title ?: "Free Focus",
                        sessionType = sessionType,
                        duration = sessionDuration,
                        startTime = Date(System.currentTimeMillis() - (sessionDuration * 60 * 1000)),
                        endTime = Date(),
                        isCompleted = true,
                        date = currentDate,
                        createdAt = Date()
                    )

                    val sessionId = repository.addPomodoroSession(completedSession)
                    Log.d("PomodoroViewModel", "✅ Session saved to Firebase with ID: $sessionId")

                    // Update task progress if it was a work session
                    if (sessionType == SessionType.WORK && task != null) {
                        repository.incrementTaskPomodoroSessions(task.id)

                        val updatedTask = repository.getTaskById(task.id)
                        if (updatedTask != null) {
                            if (updatedTask.pomodoroSessions >= updatedTask.estimatedPomodoros) {
                                repository.completeTask(updatedTask.id)
                                repository.setTaskInProgress(updatedTask.id, false)
                                clearSelectedTask()
                                Log.d("PomodoroViewModel", "✅ Task ${task.title} completed!")
                            }
                        }
                    }

                    // Update user stats
                    if (sessionType == SessionType.WORK) {
                        updateUserStats(
                            additionalFocusTime = sessionDuration.toLong(),
                            additionalSessions = 1
                        )
                    }

                    loadTodaySessions()

                } catch (e: Exception) {
                    Log.e("PomodoroViewModel", "❌ Failed to save session: ${e.message}")
                    _error.value = "Failed to complete session: ${e.message}"
                    throw e
                }
            }
            "Session saved successfully"
        }
    }

    /**
     * Skip current session (mark as incomplete)
     */
    fun skipSession(sessionType: SessionType, task: Task?) {
        viewModelScope.launch {
            try {
                val preferences = _userProfile.value?.preferences ?: UserPreferences()
                val sessionDuration = when (sessionType) {
                    SessionType.WORK -> preferences.workDuration
                    SessionType.SHORT_BREAK -> preferences.shortBreakDuration
                    SessionType.LONG_BREAK -> preferences.longBreakDuration
                }

                val skippedSession = PomodoroSession(
                    taskId = task?.id,
                    sessionType = sessionType,
                    duration = sessionDuration,
                    startTime = Date(System.currentTimeMillis() - (sessionDuration * 60 * 1000 / 2)),
                    endTime = Date(),
                    isCompleted = false,
                    date = currentDate,
                    createdAt = Date()
                )

                val sessionId = repository.addPomodoroSession(skippedSession)
                Log.d("PomodoroViewModel", "Session skipped successfully with ID: $sessionId")

                // Reload today's sessions
                loadTodaySessions()

            } catch (e: Exception) {
                _error.value = "Failed to skip session: ${e.message}"
            }
        }
    }

    /**
     * Save pomodoro session (for pause/auto-save)
     */
    suspend fun savePomodoroSession(
        sessionType: SessionType,
        task: Task?,
        secondsCompleted: Int,
        isCompleted: Boolean
    ) {
        try {
            // Convert seconds to minutes for storage
            val minutesCompleted = secondsCompleted / 60

            repository.savePomodoroSession(
                sessionType = sessionType,
                taskId = task?.id,
                duration = minutesCompleted,
                isCompleted = isCompleted
            )

            // Reload sessions if needed
            if (isCompleted) {
                loadTodaySessions()
            }

        } catch (e: Exception) {
            Log.e("PomodoroViewModel", "Failed to save session: ${e.message}")
            _error.value = "Failed to save session: ${e.message}"
        }
    }

    /**
     * Create sample data for testing
     */
    fun createSampleData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.createSampleData()
                Log.d("PomodoroViewModel", "Sample data created successfully")
                // Reload sessions after creating sample data
                loadTodaySessions()
            } catch (e: Exception) {
                Log.e("PomodoroViewModel", "Error creating sample data", e)
                _error.value = "Failed to create sample data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============= USER PREFERENCES =============

    /**
     * Update work duration
     */
    fun updateWorkDuration(duration: Int) {
        viewModelScope.launch {
            try {
                val currentProfile = _userProfile.value ?: createDefaultUserProfile()
                val currentPrefs = currentProfile.preferences
                val updatedPrefs = currentPrefs.copy(workDuration = duration)
                val updatedProfile = currentProfile.copy(preferences = updatedPrefs)

                updateUserProfile(updatedProfile)
            } catch (e: Exception) {
                _error.value = "Failed to update work duration: ${e.message}"
            }
        }
    }

    /**
     * Update short break duration
     */
    fun updateShortBreakDuration(duration: Int) {
        viewModelScope.launch {
            try {
                val currentProfile = _userProfile.value ?: createDefaultUserProfile()
                val currentPrefs = currentProfile.preferences
                val updatedPrefs = currentPrefs.copy(shortBreakDuration = duration)
                val updatedProfile = currentProfile.copy(preferences = updatedPrefs)

                updateUserProfile(updatedProfile)
            } catch (e: Exception) {
                _error.value = "Failed to update short break duration: ${e.message}"
            }
        }
    }

    /**
     * Update long break duration
     */
    fun updateLongBreakDuration(duration: Int) {
        viewModelScope.launch {
            try {
                val currentProfile = _userProfile.value ?: createDefaultUserProfile()
                val currentPrefs = currentProfile.preferences
                val updatedPrefs = currentPrefs.copy(longBreakDuration = duration)
                val updatedProfile = currentProfile.copy(preferences = updatedPrefs)

                updateUserProfile(updatedProfile)
            } catch (e: Exception) {
                _error.value = "Failed to update long break duration: ${e.message}"
            }
        }
    }

    /**
     * Update auto start breaks setting
     */
    fun updateAutoStartBreaks(autoStart: Boolean) {
        viewModelScope.launch {
            try {
                val currentProfile = _userProfile.value ?: createDefaultUserProfile()
                val currentPrefs = currentProfile.preferences
                val updatedPrefs = currentPrefs.copy(autoStartBreaks = autoStart)
                val updatedProfile = currentProfile.copy(preferences = updatedPrefs)

                updateUserProfile(updatedProfile)
            } catch (e: Exception) {
                _error.value = "Failed to update auto start breaks: ${e.message}"
            }
        }
    }

    /**
     * Update auto start pomodoros setting
     */
    fun updateAutoStartPomodoros(autoStart: Boolean) {
        viewModelScope.launch {
            try {
                val currentProfile = _userProfile.value ?: createDefaultUserProfile()
                val currentPrefs = currentProfile.preferences
                val updatedPrefs = currentPrefs.copy(autoStartPomodoros = autoStart)
                val updatedProfile = currentProfile.copy(preferences = updatedPrefs)

                updateUserProfile(updatedProfile)
            } catch (e: Exception) {
                _error.value = "Failed to update auto start pomodoros: ${e.message}"
            }
        }
    }

    /**
     * Update user profile
     */
    private suspend fun updateUserProfile(profile: UserProfile) {
        try {
            repository.updateUserProfile(profile)
            _userProfile.value = profile
        } catch (e: Exception) {
            _error.value = "Failed to update user profile: ${e.message}"
            throw e
        }
    }

    /**
     * Update user statistics
     */
    private suspend fun updateUserStats(
        additionalFocusTime: Long = 0,
        additionalSessions: Int = 0,
        completedTasks: Int = 0
    ) {
        try {
            val currentProfile = _userProfile.value ?: return

            val updatedProfile = currentProfile.copy(
                totalFocusTime = currentProfile.totalFocusTime + additionalFocusTime,
                totalPomodoroSessions = currentProfile.totalPomodoroSessions + additionalSessions,
                totalCompletedTasks = currentProfile.totalCompletedTasks + completedTasks
            )

            updateUserProfile(updatedProfile)
        } catch (e: Exception) {
            _error.value = "Failed to update user stats: ${e.message}"
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Get or create active session
     */
    suspend fun getActiveSession(): PomodoroSession? {
        return try {
            repository.getActiveSession()
        } catch (e: Exception) {
            Log.e("PomodoroViewModel", "Failed to get active session: ${e.message}")
            null
        }
    }

    /**
     * Create default user profile when none exists
     */
    private fun createDefaultUserProfile(): UserProfile {
        return UserProfile(
            userId = "",
            name = "User",
            email = "",
            profileImageUrl = "",
            totalFocusTime = 0,
            totalCompletedTasks = 0,
            totalPomodoroSessions = 0,
            currentStreak = 0,
            longestStreak = 0,
            joinedDate = Date(),
            preferences = UserPreferences()
        )
    }
}