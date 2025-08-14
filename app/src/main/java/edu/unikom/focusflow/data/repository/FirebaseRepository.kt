// FirebaseRepository.kt - Fixed sesuai dengan PomodoroSession model yang ada
package edu.unikom.focusflow.data.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import edu.unikom.focusflow.data.models.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.firestore.FieldValue

class FirebaseRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Helper method untuk mendapatkan current user ID
    private fun getCurrentUserIdInternal(): String? = auth.currentUser?.uid

    // Tasks Collection
    private val tasksCollection
        get() = getCurrentUserIdInternal()?.let {
            firestore.collection("users").document(it).collection("tasks")
        }

    // Pomodoro Sessions Collection
    private val sessionsCollection
        get() = getCurrentUserIdInternal()?.let {
            firestore.collection("users").document(it).collection("sessions")
        }

    // User Profile Document
    private val userProfileDocument
        get() = getCurrentUserIdInternal()?.let {
            firestore.collection("users").document(it)
        }

    // ============= TASK OPERATIONS =============

    /**
     * Get tasks as Flow for real-time updates
     */
    fun getTasks(): Flow<List<Task>> = callbackFlow {
        val currentUser = Firebase.auth.currentUser

        Log.d("FirebaseRepository", "=== GET TASKS DEBUG ===")
        Log.d("FirebaseRepository", "User Email: ${currentUser?.email}")
        Log.d("FirebaseRepository", "User UID: ${currentUser?.uid}")

        if (currentUser == null) {
            Log.e("FirebaseRepository", "User not authenticated!")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val tasksPath = "users/${currentUser.uid}/tasks"
        Log.d("FirebaseRepository", "Listening to path: $tasksPath")

        val listener = firestore
            .collection("users")
            .document(currentUser.uid)
            .collection("tasks")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseRepository", "Listen failed: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                Log.d("FirebaseRepository", "Snapshot received: ${snapshot?.documents?.size ?: 0} documents")

                // Debug first document
                snapshot?.documents?.firstOrNull()?.let { doc ->
                    Log.d("FirebaseRepository", "Sample task data: ${doc.data}")
                }

                val tasks = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Log.d("FirebaseRepository", "Parsing task: ${doc.id}")

                        val task = doc.toObject(Task::class.java)?.copy(id = doc.id)

                        // Handle both field names for compatibility
                        val isCompleted = doc.getBoolean("isCompleted")
                            ?: doc.getBoolean("completed")
                            ?: false

                        task?.copy(isCompleted = isCompleted)?.also {
                            Log.d("FirebaseRepository", "Task parsed: ${it.title} (completed: ${it.isCompleted})")
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseRepository", "Error parsing task ${doc.id}: ${e.message}", e)
                        null
                    }
                } ?: emptyList()

                Log.d("FirebaseRepository", "Total tasks loaded: ${tasks.size}")
                trySend(tasks)
            }

        awaitClose {
            Log.d("FirebaseRepository", "Closing tasks listener")
            listener.remove()
        }
    }

    /**
     * Add new task
     */
    suspend fun addTask(task: Task): String {
        return try {
            val collection = tasksCollection
                ?: throw IllegalStateException("User not authenticated")

            Log.d("FirebaseRepository", "Adding task to Firestore: ${task.title}")

            val result = collection.add(task).await()
            Log.d("FirebaseRepository", "Task added successfully with ID: ${result.id}")
            result.id
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error adding task to Firestore", e)
            throw e
        }
    }

    /**
     * Update existing task
     */
    suspend fun updateTask(task: Task) {
        try {
            if (task.id.isEmpty()) {
                throw IllegalArgumentException("Task ID cannot be empty")
            }

            tasksCollection?.document(task.id)?.set(task)?.await()
                ?: throw IllegalStateException("User not authenticated")

            Log.d("FirebaseRepository", "Task updated successfully: ${task.id}")
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error updating task", e)
            throw e
        }
    }

    /**
     * Delete task
     */
    suspend fun deleteTask(taskId: String) {
        try {
            if (taskId.isEmpty()) {
                throw IllegalArgumentException("Task ID cannot be empty")
            }

            tasksCollection?.document(taskId)?.delete()?.await()
                ?: throw IllegalStateException("User not authenticated")

            Log.d("FirebaseRepository", "Task deleted successfully: $taskId")
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error deleting task", e)
            throw e
        }
    }

    /**
     * Mark task as completed
     */
    suspend fun completeTask(taskId: String) {
        try {
            if (taskId.isEmpty()) {
                throw IllegalArgumentException("Task ID cannot be empty")
            }

            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("FirebaseRepository", "User not authenticated in completeTask")
                throw IllegalStateException("User not authenticated")
            }

            // Gunakan path lengkap tanpa tasksCollection
            firestore.collection("users")
                .document(currentUser.uid)
                .collection("tasks")
                .document(taskId)
                .update(
                    mapOf(
                        "isCompleted" to true,  // Update both fields for compatibility
                        "completed" to true,     // For backward compatibility
                        "completedAt" to Date(),
                        "isInProgress" to false
                    )
                ).await()

            Log.d("FirebaseRepository", "Task completed successfully: $taskId")
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error completing task", e)
            throw e
        }
    }

    suspend fun uncompleteTask(taskId: String) {
        try {
            if (taskId.isEmpty()) {
                throw IllegalArgumentException("Task ID cannot be empty")
            }

            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("FirebaseRepository", "User not authenticated in uncompleteTask")
                throw IllegalStateException("User not authenticated")
            }

            firestore.collection("users")
                .document(currentUser.uid)
                .collection("tasks")
                .document(taskId)
                .update(
                    mapOf(
                        "isCompleted" to false,
                        "completed" to false,    // For backward compatibility
                        "completedAt" to null,
                        "isInProgress" to false
                    )
                ).await()

            Log.d("FirebaseRepository", "Task uncompleted successfully: $taskId")
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error uncompleting task", e)
            throw e
        }
    }

    /**
     * Set task in progress status
     */
    suspend fun setTaskInProgress(taskId: String, inProgress: Boolean) {
        try {
            if (taskId.isEmpty()) {
                throw IllegalArgumentException("Task ID cannot be empty")
            }

            val updates = mutableMapOf<String, Any?>(
                "isInProgress" to inProgress
            )

            if (inProgress) {
                updates["lastWorkedOn"] = Date()
            }

            tasksCollection?.document(taskId)?.update(updates)?.await()
                ?: throw IllegalStateException("User not authenticated")

            Log.d("FirebaseRepository", "Task progress status updated: $taskId -> $inProgress")
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error updating task progress status", e)
            throw e
        }
    }

    /**
     * Get task by ID
     */
    suspend fun getTaskById(taskId: String): Task? {
        return try {
            val doc = tasksCollection?.document(taskId)?.get()?.await()
            val task = doc?.toObject(Task::class.java)?.copy(id = doc.id)
            // Handle field mapping
            val isCompleted = doc?.getBoolean("isCompleted") ?: doc?.getBoolean("completed") ?: false
            task?.copy(isCompleted = isCompleted)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error getting task by ID", e)
            null
        }
    }

    /**
     * Increment task pomodoro sessions
     */
    suspend fun incrementTaskPomodoroSessions(taskId: String) {
        try {
            tasksCollection?.document(taskId)?.update(
                "pomodoroSessions", FieldValue.increment(1)
            )?.await() ?: throw IllegalStateException("User not authenticated")

            Log.d("FirebaseRepository", "Task pomodoro sessions incremented: $taskId")
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error incrementing pomodoro sessions", e)
            throw e
        }
    }

    // ============= SUBTASK OPERATIONS =============

    /**
     * Toggle subtask completion status
     */
    suspend fun toggleSubtask(taskId: String, subtaskId: String, completed: Boolean) {
        try {
            if (taskId.isEmpty() || subtaskId.isEmpty()) {
                throw IllegalArgumentException("Task ID or Subtask ID cannot be empty")
            }

            // Gunakan Firebase.auth langsung, jangan dari tasksCollection
            val currentUser = Firebase.auth.currentUser
            if (currentUser == null) {
                Log.e("FirebaseRepository", "User not authenticated in toggleSubtask")
                throw IllegalStateException("User not authenticated")
            }

            // Akses collection langsung dengan path lengkap
            val taskDoc = firestore.collection("users")
                .document(currentUser.uid)
                .collection("tasks")
                .document(taskId)
                .get()
                .await()

            val task = taskDoc.toObject(Task::class.java) ?: throw Exception("Task not found")

            // Update subtask
            val updatedSubtasks = task.subtasks.map { subtask ->
                if (subtask.id == subtaskId) {
                    subtask.copy(isCompleted = completed)
                } else {
                    subtask
                }
            }

            // Update dengan path lengkap
            val updates = hashMapOf<String, Any>(
                "subtasks" to updatedSubtasks.map { subtask ->
                    hashMapOf(
                        "id" to subtask.id,
                        "title" to subtask.title,
                        "isCompleted" to subtask.isCompleted,
                        "createdAt" to subtask.createdAt
                    )
                }
            )

            firestore.collection("users")
                .document(currentUser.uid)
                .collection("tasks")
                .document(taskId)
                .update(updates)
                .await()

            Log.d("FirebaseRepository", "Subtask toggled successfully: $subtaskId to $completed")

        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error toggling subtask: ${e.message}", e)
            throw e
        }
    }

    // ============= POMODORO SESSION OPERATIONS =============

    /**
     * Add new pomodoro session
     */
    suspend fun addPomodoroSession(session: PomodoroSession): String {
        return try {
            val collection = sessionsCollection
                ?: throw IllegalStateException("User not authenticated")

            val result = collection.add(session).await()
            Log.d("FirebaseRepository", "Session added successfully with ID: ${result.id}")
            result.id
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error adding session", e)
            throw e
        }
    }

    /**
     * Get all sessions (without date filter) - For HomeScreen and general use
     */
    // Di FirebaseRepository.kt, update getAllSessions dengan debug detail:
    suspend fun getAllSessions(): List<PomodoroSession> {
        return try {
            val collection = sessionsCollection ?: return emptyList()

            val result = collection
                .limit(10) // Ambil 10 dulu untuk debug
                .get()
                .await()

            Log.d("FirebaseRepository", "=== SESSION DEBUG ===")
            Log.d("FirebaseRepository", "Total documents found: ${result.documents.size}")

            // Debug raw data
            result.documents.firstOrNull()?.let { doc ->
                Log.d("FirebaseRepository", "Sample raw data: ${doc.data}")
            }

            val sessions = result.documents.mapNotNull { doc ->
                try {
                    // Manual parsing untuk debug
                    val sessionTypeString = doc.getString("sessionType") ?: "WORK"
                    val isCompleted = doc.getBoolean("isCompleted") ?: false
                    val duration = doc.getLong("duration")?.toInt() ?: 0
                    val date = doc.getString("date") ?: ""

                    // Parse SessionType dari string
                    val sessionType = try {
                        SessionType.valueOf(sessionTypeString)
                    } catch (e: Exception) {
                        Log.e("FirebaseRepository", "Failed to parse sessionType: $sessionTypeString")
                        SessionType.WORK
                    }

                    // Create PomodoroSession manually
                    PomodoroSession(
                        id = doc.id,
                        sessionType = sessionType,
                        duration = duration,
                        isCompleted = isCompleted,
                        date = date,
                        startTime = doc.getDate("startTime"),
                        endTime = doc.getDate("endTime"),
                        taskId = doc.getString("taskId"),
                        createdAt = doc.getDate("createdAt") ?: Date()
                    )
                } catch (e: Exception) {
                    Log.e("FirebaseRepository", "Error parsing session ${doc.id}: ${e.message}")
                    null
                }
            }

            // Count completed WORK sessions
            val workSessions = sessions.filter {
                it.sessionType == SessionType.WORK && it.isCompleted
            }

            Log.d("FirebaseRepository", "Parsed ${sessions.size} sessions")
            Log.d("FirebaseRepository", "Completed WORK sessions: ${workSessions.size}")
            Log.d("FirebaseRepository", "Total focus time: ${workSessions.sumOf { it.duration }} minutes")

            // Now get ALL sessions without limit
            if (sessions.isNotEmpty()) {
                val allResult = collection.get().await()
                val allSessions = allResult.documents.mapNotNull { doc ->
                    try {
                        val sessionTypeString = doc.getString("sessionType") ?: "WORK"
                        val sessionType = SessionType.valueOf(sessionTypeString)

                        PomodoroSession(
                            id = doc.id,
                            sessionType = sessionType,
                            duration = doc.getLong("duration")?.toInt() ?: 0,
                            isCompleted = doc.getBoolean("isCompleted") ?: false,
                            date = doc.getString("date") ?: "",
                            startTime = doc.getDate("startTime"),
                            endTime = doc.getDate("endTime"),
                            taskId = doc.getString("taskId"),
                            createdAt = doc.getDate("createdAt") ?: Date()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                Log.d("FirebaseRepository", "TOTAL sessions loaded: ${allSessions.size}")
                return allSessions
            }

            sessions
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error getting all sessions", e)
            emptyList()
        }
    }

    /**
     * Get sessions for specific date
     */
    suspend fun getSessionsForDate(date: String): List<PomodoroSession> {
        return try {
            val collection = sessionsCollection ?: return emptyList()

            val result = collection
                .whereEqualTo("date", date)
                .orderBy("startTime", Query.Direction.ASCENDING)
                .get()
                .await()

            result.documents.mapNotNull { doc ->
                doc.toObject(PomodoroSession::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error getting sessions for date: $date", e)
            emptyList()
        }
    }

    /**
     * Get sessions for date range - FIXED for analytics
     */
    suspend fun getSessionsForDateRange(startDate: String, endDate: String): List<PomodoroSession> {
        return try {
            val collection = sessionsCollection ?: return emptyList()

            Log.d("FirebaseRepository", "Querying sessions from $startDate to $endDate")

            // Use only date field for query
            val result = collection
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .get()
                .await()

            val sessions = result.documents.mapNotNull { doc ->
                doc.toObject(PomodoroSession::class.java)?.copy(id = doc.id)
            }

            Log.d("FirebaseRepository", "Found ${sessions.size} sessions in date range")

            sessions
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error getting sessions for date range: $e", e)
            emptyList()
        }
    }

    /**
     * Create sample data for testing
     */
    suspend fun createSampleData() {
        val userId = getCurrentUserIdInternal() ?: return

        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val calendar = Calendar.getInstance()

            // Create sessions for the past 30 days
            for (i in 0..29) {
                calendar.time = Date()
                calendar.add(Calendar.DAY_OF_YEAR, -i)
                val date = dateFormat.format(calendar.time)

                // Create 2-5 sessions per day
                val sessionsPerDay = (2..5).random()
                for (j in 0 until sessionsPerDay) {
                    // Set different times for each session
                    calendar.set(Calendar.HOUR_OF_DAY, 9 + (j * 2))
                    calendar.set(Calendar.MINUTE, 0)

                    val startTime = calendar.time
                    val endTime = Date(startTime.time + 25 * 60 * 1000)

                    val session = PomodoroSession(
                        date = date,
                        startTime = startTime,
                        endTime = endTime,
                        duration = 25,
                        sessionType = if (j % 4 == 3) SessionType.LONG_BREAK
                        else if (j % 2 == 1) SessionType.SHORT_BREAK
                        else SessionType.WORK,
                        isCompleted = true,
                        taskId = if (j % 2 == 0) "sample-task-${j/2}" else null,
                        createdAt = startTime
                    )

                    sessionsCollection?.add(session)?.await()
                }
            }

            Log.d("FirebaseRepository", "Sample data created successfully")
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error creating sample data", e)
            throw e
        }
    }

    // ============= USER PROFILE OPERATIONS =============

    /**
     * Get user profile
     */
    suspend fun getUserProfile(): UserProfile? {
        return try {
            val document = userProfileDocument ?: return null

            val snapshot = document.get().await()
            val profile = snapshot.toObject(UserProfile::class.java)

            if (profile != null) {
                Log.d("FirebaseRepository", "User profile loaded successfully")
            } else {
                Log.d("FirebaseRepository", "User profile not found, creating default")
                // Create default profile if not exists
                val defaultProfile = UserProfile(
                    userId = getCurrentUserIdInternal() ?: "",
                    name = auth.currentUser?.displayName ?: "User",
                    email = auth.currentUser?.email ?: "",
                    preferences = UserPreferences()
                )
                document.set(defaultProfile).await()
                return defaultProfile
            }

            profile
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error getting user profile", e)
            // Return default profile instead of throwing
            UserProfile(
                userId = getCurrentUserIdInternal() ?: "",
                name = auth.currentUser?.displayName ?: "User",
                email = auth.currentUser?.email ?: "",
                preferences = UserPreferences()
            )
        }
    }

    /**
     * Update user profile
     */
    suspend fun updateUserProfile(profile: UserProfile) {
        try {
            val document = userProfileDocument
                ?: throw IllegalStateException("User not authenticated")

            document.set(profile).await()
            Log.d("FirebaseRepository", "User profile updated successfully")
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error updating user profile", e)
            throw e
        }
    }

    // ============= ANALYTICS HELPER FUNCTIONS =============

    /**
     * Get tasks completed in date range
     */
    // Update getTasksCompletedInDateRange untuk handle seeder data
    suspend fun getTasksCompletedInDateRange(startDate: String, endDate: String): Int {
        return try {
            val collection = tasksCollection ?: return 0

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startTime = dateFormat.parse(startDate) ?: return 0
            val endTime = dateFormat.parse(endDate)?.let {
                Calendar.getInstance().apply {
                    time = it
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }.time
            } ?: return 0

            val result = collection.get().await()

            result.documents.count { document ->
                val isCompleted = document.getBoolean("isCompleted") ?:
                document.getBoolean("completed") ?: false

                if (!isCompleted) return@count false

                val completedAt = document.getDate("completedAt")
                val createdAt = document.getDate("createdAt")

                // Jika ada completedAt, gunakan itu
                if (completedAt != null) {
                    completedAt >= startTime && completedAt <= endTime
                }
                // Jika tidak ada completedAt tapi completed (data seeder lama),
                // gunakan createdAt sebagai fallback
                else if (createdAt != null) {
                    createdAt >= startTime && createdAt <= endTime
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error getting completed tasks", e)
            0
        }
    }

    /**
     * Get total focus time in date range
     */
    suspend fun getTotalFocusTimeInDateRange(startDate: String, endDate: String): Long {
        return try {
            val sessions = getSessionsForDateRange(startDate, endDate)
            sessions.filter { it.sessionType == SessionType.WORK && it.isCompleted }
                .sumOf { it.duration.toLong() }
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error getting focus time", e)
            0L
        }
    }

    /**
     * Save pomodoro session (for pause/auto-save)
     */
    suspend fun savePomodoroSession(
        sessionType: SessionType,
        taskId: String?,
        duration: Int,
        isCompleted: Boolean
    ) {
        try {
            val collection = sessionsCollection
                ?: throw IllegalStateException("User not authenticated")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val session = PomodoroSession(
                id = "",
                taskId = taskId,
                sessionType = sessionType,
                duration = duration,
                startTime = Date(System.currentTimeMillis() - (duration * 60 * 1000L)),
                endTime = if (isCompleted) Date() else null,
                date = currentDate,
                isCompleted = isCompleted,
                createdAt = Date()
            )

            val result = collection.add(session).await()
            Log.d("FirebaseRepository", "Session saved successfully with ID: ${result.id}")

        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error saving session", e)
            throw e
        }
    }

    /**
     * Get active (incomplete) session for today
     */
    suspend fun getActiveSession(): PomodoroSession? {
        return try {
            val collection = sessionsCollection ?: return null

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val result = collection
                .whereEqualTo("date", currentDate)
                .whereEqualTo("isCompleted", false)
                .limit(1)
                .get()
                .await()

            result.documents.firstOrNull()?.let { doc ->
                doc.toObject(PomodoroSession::class.java)?.copy(id = doc.id)
            }

        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error getting active session", e)
            null
        }
    }

    /**
     * Get user preferences
     */
    suspend fun getUserPreferences(): UserPreferences? {
        return try {
            val profile = getUserProfile()
            profile?.preferences
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error getting preferences", e)
            UserPreferences()
        }
    }

    /**
     * Update user preferences
     */
    suspend fun updateUserPreferences(preferences: UserPreferences) {
        try {
            val profile = getUserProfile() ?: return
            val updatedProfile = profile.copy(preferences = preferences)
            updateUserProfile(updatedProfile)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error updating preferences", e)
            throw e
        }
    }

    // ============= UTILITY METHODS =============

    /**
     * Check if user is authenticated
     */
    fun isUserAuthenticated(): Boolean = getCurrentUserIdInternal() != null
}