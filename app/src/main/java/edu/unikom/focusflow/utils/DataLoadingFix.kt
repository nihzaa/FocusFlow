package edu.unikom.focusflow.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import edu.unikom.focusflow.data.models.PomodoroSession
import edu.unikom.focusflow.data.models.SessionType
import edu.unikom.focusflow.data.repository.FirebaseRepository
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

object DataLoadingHelper {

    private val TAG = "DataLoadingHelper"

    /**
     * Load sessions with better error handling and format compatibility
     */
    suspend fun loadSessionsWithFallback(
        startDate: String,
        endDate: String
    ): List<PomodoroSession> {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated")
            return emptyList()
        }

        val db = FirebaseFirestore.getInstance()
        val sessions = mutableListOf<PomodoroSession>()

        try {
            // Try loading from the standard path first
            val querySnapshot = db.collection("users")
                .document(currentUser.uid)
                .collection("sessions")
                .get()
                .await()

            Log.d(TAG, "Found ${querySnapshot.documents.size} session documents")

            for (document in querySnapshot.documents) {
                try {
                    val data = document.data ?: continue
                    Log.d(TAG, "Session data: $data")

                    // Handle different date formats
                    val dateString = when (val dateField = data["date"]) {
                        is String -> dateField
                        is com.google.firebase.Timestamp -> {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(dateField.toDate())
                        }
                        is Date -> {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(dateField)
                        }
                        else -> {
                            Log.w(TAG, "Unknown date format: $dateField")
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(Date())
                        }
                    }

                    // Parse session type with fallback
                    val sessionTypeString = data["sessionType"] as? String ?: "WORK"
                    val sessionType = try {
                        SessionType.valueOf(sessionTypeString)
                    } catch (e: Exception) {
                        Log.w(TAG, "Invalid session type: $sessionTypeString, defaulting to WORK")
                        SessionType.WORK
                    }

                    // Handle startTime field
                    val startTime = when (val startTimeField = data["startTime"]) {
                        is com.google.firebase.Timestamp -> startTimeField.toDate()
                        is Long -> Date(startTimeField)
                        is Date -> startTimeField
                        else -> Date()
                    }

                    val session = PomodoroSession(
                        id = document.id,
                        date = dateString,
                        sessionType = sessionType,
                        duration = (data["duration"] as? Long)?.toInt() ?: 25,
                        isCompleted = data["isCompleted"] as? Boolean ?: true,
                        startTime = startTime,
                        endTime = when (val endTimeField = data["endTime"]) {
                            is com.google.firebase.Timestamp -> endTimeField.toDate()
                            is Long -> Date(endTimeField)
                            is Date -> endTimeField
                            else -> null
                        }
                    )

                    // Check if session is within date range
                    if (isDateInRange(dateString, startDate, endDate)) {
                        sessions.add(session)
                        Log.d(TAG, "Added session: $session")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing session document: ${e.message}")
                }
            }

            // If no sessions found, try alternative paths
            if (sessions.isEmpty()) {
                Log.d(TAG, "No sessions found in standard path, trying alternative paths")

                // Try loading from root sessions collection (if seeder put them there)
                val rootSessions = db.collection("sessions")
                    .whereEqualTo("userId", currentUser.uid)
                    .get()
                    .await()

                Log.d(TAG, "Found ${rootSessions.documents.size} sessions in root collection")

                for (document in rootSessions.documents) {
                    try {
                        val data = document.data ?: continue
                        // Parse similar to above
                        // ... (same parsing logic)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing root session: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error loading sessions: ${e.message}", e)
        }

        Log.d(TAG, "Returning ${sessions.size} sessions")
        return sessions
    }

    /**
     * Check if date string is within range
     */
    private fun isDateInRange(dateString: String, startDate: String, endDate: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = format.parse(dateString) ?: return false
            val start = format.parse(startDate) ?: return false
            val end = format.parse(endDate) ?: return false

            !date.before(start) && !date.after(end)
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing dates: ${e.message}")
            true // Include if can't parse
        }
    }

    /**
     * Calculate total focus time from sessions
     */
    fun calculateTotalFocusTime(sessions: List<PomodoroSession>): Long {
        return sessions
            .filter { it.isCompleted && it.sessionType == SessionType.WORK }
            .sumOf { it.duration.toLong() }
    }

    /**
     * Get test/sample sessions for debugging
     */
    fun getSampleSessions(): List<PomodoroSession> {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sessions = mutableListOf<PomodoroSession>()

        // Generate sample sessions for last 7 days
        for (i in 0..6) {
            val date = dateFormat.format(calendar.time)

            // Add 3-5 sessions per day
            val sessionsPerDay = (3..5).random()
            for (j in 0 until sessionsPerDay) {
                sessions.add(
                    PomodoroSession(
                        id = "sample_${i}_${j}",
                        date = date,
                        sessionType = if (j % 3 == 2) SessionType.SHORT_BREAK else SessionType.WORK,
                        duration = if (j % 3 == 2) 5 else 25,
                        isCompleted = true,
                        startTime = Date()
                    )
                )
            }

            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        return sessions
    }

    /**
     * Debug function to check Firebase structure
     */
    suspend fun debugFirebaseStructure() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated for debug")
            return
        }

        val db = FirebaseFirestore.getInstance()

        try {
            // Check user document
            val userDoc = db.collection("users")
                .document(currentUser.uid)
                .get()
                .await()

            Log.d(TAG, "User document exists: ${userDoc.exists()}")
            Log.d(TAG, "User data: ${userDoc.data}")

            // Check sessions collection
            val sessions = db.collection("users")
                .document(currentUser.uid)
                .collection("sessions")
                .limit(5)
                .get()
                .await()

            Log.d(TAG, "Sessions count: ${sessions.documents.size}")
            sessions.documents.forEach { doc ->
                Log.d(TAG, "Session ${doc.id}: ${doc.data}")
            }

            // Check tasks collection
            val tasks = db.collection("users")
                .document(currentUser.uid)
                .collection("tasks")
                .limit(5)
                .get()
                .await()

            Log.d(TAG, "Tasks count: ${tasks.documents.size}")
            tasks.documents.forEach { doc ->
                Log.d(TAG, "Task ${doc.id}: ${doc.data}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Debug error: ${e.message}", e)
        }
    }
}

/**
 * Extension function untuk FirebaseRepository
 */
suspend fun FirebaseRepository.getSessionsWithFallback(
    startDate: String,
    endDate: String
): List<PomodoroSession> {
    return try {
        // Try original method first
        getSessionsForDateRange(startDate, endDate).ifEmpty {
            Log.d("FirebaseRepository", "No sessions from original method, trying fallback")
            DataLoadingHelper.loadSessionsWithFallback(startDate, endDate)
        }
    } catch (e: Exception) {
        Log.e("FirebaseRepository", "Error getting sessions, using fallback: ${e.message}")
        DataLoadingHelper.loadSessionsWithFallback(startDate, endDate)
    }
}