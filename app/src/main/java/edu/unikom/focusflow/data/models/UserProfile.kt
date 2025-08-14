package edu.unikom.focusflow.data.models

import com.google.firebase.firestore.DocumentId
import java.util.*

data class UserProfile(
    @DocumentId
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val profileImageUrl: String = "",
    val totalFocusTime: Long = 0, // in minutes
    val totalCompletedTasks: Int = 0,
    val totalPomodoroSessions: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val joinedDate: Date = Date(),
    val preferences: UserPreferences = UserPreferences()
)

data class UserPreferences(
    val workDuration: Int = 25,
    val shortBreakDuration: Int = 5,
    val longBreakDuration: Int = 15,
    val autoStartBreaks: Boolean = false,
    val autoStartPomodoros: Boolean = false,
    val soundEnabled: Boolean = true,
    val darkMode: Boolean = false
)

data class UserStats(
    val totalFocusTime: Long = 0,
    val totalSessions: Int = 0,
    val lastSessionDate: Date? = null
)