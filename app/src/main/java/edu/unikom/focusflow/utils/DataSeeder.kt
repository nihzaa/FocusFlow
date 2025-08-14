package edu.unikom.focusflow.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import edu.unikom.focusflow.data.models.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class DataSeeder {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Generate dan upload semua data dummy
    suspend fun seedAllData() {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")

        println("🌱 Starting data seeding for user: $userId")

        // Clear existing data first to avoid conflicts
        try {
            clearAllData()
        } catch (e: Exception) {
            println("Warning: Failed to clear existing data: ${e.message}")
        }

        // 1. Seed Tasks dengan subcollection
        seedTasks(userId)

        // 2. Seed Pomodoro Sessions dengan subcollection
        seedPomodoroSessions(userId)

        // 3. Create/Update user profile
        createOrUpdateUserProfile(userId)

        println("✅ Data seeding completed!")
    }

    // Generate Tasks menggunakan subcollection structure
    private suspend fun seedTasks(userId: String) {
        val taskTemplates = listOf(
            Triple("Complete project proposal", "Work", "Finish the Q4 project proposal with budget estimates"),
            Triple("Review quarterly report", "Work", "Analyze Q3 performance metrics and KPIs"),
            Triple("Team meeting preparation", "Work", "Prepare slides for Monday's team sync"),
            Triple("Client presentation", "Work", "Design pitch deck for new client meeting"),
            Triple("Code review", "Work", "Review pull requests from team members"),
            Triple("Study Kotlin coroutines", "Study", "Complete chapter on async programming"),
            Triple("Read Clean Code book", "Study", "Finish chapters 3-5 on functions and comments"),
            Triple("Complete online course", "Study", "Finish Android Architecture Components module"),
            Triple("Practice algorithms", "Study", "Solve 5 LeetCode problems - medium difficulty"),
            Triple("Learn Jetpack Compose", "Study", "Build sample app with Compose UI"),
            Triple("Morning workout", "Health", "30 min cardio + 20 min strength training"),
            Triple("Meditation session", "Health", "15 minute mindfulness meditation"),
            Triple("Grocery shopping", "Personal", "Buy ingredients for meal prep"),
            Triple("Call family", "Personal", "Weekly video call with parents"),
            Triple("Plan weekend trip", "Personal", "Research hotels and activities"),
            Triple("Design new feature", "Project", "Create wireframes for analytics dashboard"),
            Triple("Database optimization", "Project", "Optimize slow queries and add indexes"),
            Triple("Write documentation", "Project", "Update API documentation with new endpoints"),
            Triple("Bug fixing", "Project", "Fix critical issues from bug tracker"),
            Triple("Performance testing", "Project", "Run load tests and optimize bottlenecks")
        )

        val calendar = Calendar.getInstance()
        val tasksCollection = db.collection("users").document(userId).collection("tasks")
        var totalTasks = 0

        // Generate tasks for last 30 days
        for (daysAgo in 0..30) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)

            // Reset time to start of day
            calendar.set(Calendar.HOUR_OF_DAY, Random.nextInt(6, 10))
            calendar.set(Calendar.MINUTE, Random.nextInt(0, 59))
            calendar.set(Calendar.SECOND, 0)

            val tasksPerDay = Random.nextInt(3, 8)

            for (i in 0 until tasksPerDay) {
                val template = taskTemplates.random()
                val isCompleted = Random.nextFloat() < 0.7 // 70% completion rate
                val estimatedPomodoros = Random.nextInt(1, 5)
                val pomodoroSessions = if (isCompleted) estimatedPomodoros else Random.nextInt(0, estimatedPomodoros)

                // Generate subtasks for some tasks
                val subtasks = if (Random.nextFloat() < 0.4) { // 40% chance to have subtasks
                    generateSubtasks(template.first)
                } else emptyList()

                val createdAt = calendar.time
                val completedAt = if (isCompleted) {
                    Date(createdAt.time + Random.nextInt(1, 8) * 60 * 60 * 1000L) // 1-8 hours later
                } else null

                // Create task map sesuai dengan model Task yang ada
                // Di method seedTasks, update bagian pembuatan taskData:
                val taskData = hashMapOf(
                    "title" to template.first,
                    "description" to template.third,
                    "category" to template.second,
                    "priority" to TaskPriority.values().random().name,
                    "estimatedPomodoros" to estimatedPomodoros,
                    "pomodoroSessions" to pomodoroSessions,
                    "isCompleted" to isCompleted,
                    "completed" to isCompleted,  // TAMBAHKAN untuk compatibility
                    "createdAt" to createdAt,
                    "difficulty" to Random.nextInt(1, 6),
                    "reminderMinutes" to listOf(15, 30, 60).random(),
                    "tags" to generateTagsString(template.second),
                    "isRecurring" to false,
                    "recurrenceType" to RecurrenceType.NONE.name,
                    "attachments" to emptyList<String>(),
                    "isInProgress" to false,
                    "subtasks" to subtasks.map { subtask ->
                        hashMapOf(
                            "id" to subtask.id,
                            "title" to subtask.title,
                            "isCompleted" to subtask.isCompleted,
                            "createdAt" to subtask.createdAt
                        )
                    }
                )

// PENTING: Tambahkan completedAt jika task completed
                if (isCompleted) {
                    taskData["completedAt"] = Date(createdAt.time + Random.nextInt(1, 8) * 60 * 60 * 1000L) // 1-8 jam setelah created
                }

// Tambahkan dueDate
                if (Random.nextFloat() < 0.6) {
                    taskData["dueDate"] = Date(createdAt.time + Random.nextInt(1, 7) * 24 * 60 * 60 * 1000L)
                }

                // Add optional fields
                if (completedAt != null) {
                    taskData["completedAt"] = completedAt
                }

                if (Random.nextFloat() < 0.6) { // 60% have due date
                    taskData["dueDate"] = Date(createdAt.time + Random.nextInt(1, 7) * 24 * 60 * 60 * 1000L)
                }

                tasksCollection.add(taskData).await()
                totalTasks++
            }
        }

        println("📝 Tasks seeded successfully: $totalTasks tasks created")
    }

    // Generate subtasks
    private fun generateSubtasks(taskTitle: String): List<Subtask> {
        val subtaskTemplates = mapOf(
            "Complete project proposal" to listOf(
                "Research competitor analysis",
                "Draft executive summary",
                "Create budget breakdown",
                "Review with team lead"
            ),
            "Code review" to listOf(
                "Check code standards",
                "Test functionality",
                "Write feedback comments"
            ),
            "Design new feature" to listOf(
                "Create user flow",
                "Design wireframes",
                "Create mockups",
                "Get feedback"
            )
        )

        val templates = subtaskTemplates.entries.firstOrNull { taskTitle.contains(it.key) }?.value
            ?: listOf("Research", "Plan", "Execute", "Review")

        val numberOfSubtasks = Random.nextInt(2, minOf(templates.size + 1, 5))
        return templates.shuffled().take(numberOfSubtasks).map { title ->
            Subtask(
                id = UUID.randomUUID().toString(),
                title = title,
                isCompleted = Random.nextFloat() < 0.6, // 60% completion rate
                createdAt = Date()
            )
        }
    }

    // Generate tags sebagai String
    private fun generateTagsString(category: String): String {
        val tagMap = mapOf(
            "Work" to listOf("urgent", "client", "meeting", "deadline", "team"),
            "Study" to listOf("learning", "course", "practice", "reading", "tutorial"),
            "Health" to listOf("fitness", "wellness", "exercise", "meditation", "nutrition"),
            "Personal" to listOf("family", "shopping", "planning", "leisure", "hobby"),
            "Project" to listOf("development", "feature", "bug", "optimization", "testing")
        )

        val availableTags = tagMap[category] ?: listOf("general")
        val numberOfTags = Random.nextInt(0, 4) // 0-3 tags
        return availableTags.shuffled().take(numberOfTags).joinToString(",")
    }

    // Generate Pomodoro Sessions
    private suspend fun seedPomodoroSessions(userId: String) {
        val calendar = Calendar.getInstance()
        val sessionsCollection = db.collection("users").document(userId).collection("sessions")
        var totalSessions = 0
        var totalFocusTime = 0L

        // Get task IDs that were created
        val tasksSnapshot = db.collection("users").document(userId).collection("tasks")
            .whereEqualTo("isCompleted", true)
            .get()
            .await()

        val completedTaskIds = tasksSnapshot.documents.map { it.id }

        // Generate sessions for last 90 days for better analytics
        for (daysAgo in 0..90) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)

            // Skip some days randomly (to make streak data more realistic)
            if (Random.nextFloat() < 0.15) continue // 15% chance to skip a day

            val dateStr = dateFormat.format(calendar.time)

            val sessionsPerDay = when {
                daysAgo < 7 -> Random.nextInt(4, 10)  // More recent days have more sessions
                daysAgo < 30 -> Random.nextInt(2, 8)
                else -> Random.nextInt(1, 5)
            }

            for (sessionNum in 0 until sessionsPerDay) {
                // Set random hour of day (working hours)
                calendar.set(Calendar.HOUR_OF_DAY, Random.nextInt(8, 22))
                calendar.set(Calendar.MINUTE, Random.nextInt(0, 59))

                val sessionType = when (Random.nextInt(10)) {
                    in 0..6 -> SessionType.WORK
                    in 7..8 -> SessionType.SHORT_BREAK
                    else -> SessionType.LONG_BREAK
                }

                val baseDuration = when (sessionType) {
                    SessionType.WORK -> 25
                    SessionType.SHORT_BREAK -> 5
                    SessionType.LONG_BREAK -> 15
                }

                val isCompleted = Random.nextFloat() < 0.85 // 85% completion rate
                val duration = if (isCompleted) baseDuration else Random.nextInt(1, baseDuration)

                val startTime = calendar.time
                val endTime = if (isCompleted) {
                    Date(startTime.time + duration * 60 * 1000L)
                } else null

                // Link some work sessions to completed tasks
                val taskId = if (sessionType == SessionType.WORK &&
                    completedTaskIds.isNotEmpty() &&
                    Random.nextFloat() < 0.6) {
                    completedTaskIds.random()
                } else null

                // Create session data as HashMap
                val sessionData = hashMapOf(
                    "sessionType" to sessionType.name,
                    "duration" to duration,
                    "isCompleted" to isCompleted,
                    "startTime" to startTime,
                    "date" to dateStr,
                    "createdAt" to startTime
                )

                // Add optional fields
                if (endTime != null) {
                    sessionData["endTime"] = endTime
                }

                if (taskId != null) {
                    sessionData["taskId"] = taskId
                }

                sessionsCollection.add(sessionData).await()
                totalSessions++

                if (sessionType == SessionType.WORK && isCompleted) {
                    totalFocusTime += duration
                }

                // Move calendar forward for next session
                calendar.add(Calendar.MINUTE, duration + Random.nextInt(5, 30))
            }
        }

        println("⏰ Pomodoro sessions seeded successfully: $totalSessions sessions, ${totalFocusTime} minutes focus time")
    }

    // Create or update user profile with stats
    private suspend fun createOrUpdateUserProfile(userId: String) {
        val userDoc = db.collection("users").document(userId)

        // Calculate stats from seeded data
        val tasksCollection = userDoc.collection("tasks")
        val sessionsCollection = userDoc.collection("sessions")

        // Get completed tasks count
        val completedTasksSnapshot = tasksCollection
            .whereEqualTo("isCompleted", true)
            .get()
            .await()
        val completedTasks = completedTasksSnapshot.size()

        // Get sessions stats
        val sessionsSnapshot = sessionsCollection.get().await()
        var totalFocusTime = 0L
        var totalSessions = 0

        sessionsSnapshot.documents.forEach { doc ->
            val sessionType = doc.getString("sessionType")
            val isCompleted = doc.getBoolean("isCompleted") ?: false
            val duration = doc.getLong("duration")?.toInt() ?: 0

            if (sessionType == "WORK" && isCompleted) {
                totalFocusTime += duration
                totalSessions++
            }
        }

        // Calculate streak
        val currentStreak = calculateCurrentStreak(sessionsSnapshot.documents)
        val longestStreak = maxOf(currentStreak, Random.nextInt(7, 30))

        // Create user profile data
        val profileData = hashMapOf(
            "id" to userId,
            "name" to (auth.currentUser?.displayName ?: "Test User"),
            "email" to (auth.currentUser?.email ?: "test@example.com"),
            "profileImageUrl" to "",
            "totalFocusTime" to totalFocusTime,
            "totalCompletedTasks" to completedTasks,
            "totalPomodoroSessions" to totalSessions,
            "currentStreak" to currentStreak,
            "longestStreak" to longestStreak,
            "joinedDate" to Date(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000), // 90 days ago
            "preferences" to hashMapOf(
                "workDuration" to 25,
                "shortBreakDuration" to 5,
                "longBreakDuration" to 15,
                "autoStartBreaks" to false,
                "autoStartPomodoros" to false,
                "soundEnabled" to true,
                "darkMode" to false
            )
        )

        userDoc.set(profileData, com.google.firebase.firestore.SetOptions.merge()).await()

        println("📊 User profile updated with stats:")
        println("   - Total focus time: ${totalFocusTime} minutes")
        println("   - Completed tasks: $completedTasks")
        println("   - Total sessions: $totalSessions")
        println("   - Current streak: $currentStreak days")
        println("   - Longest streak: $longestStreak days")
    }

    private fun calculateCurrentStreak(sessionDocs: List<com.google.firebase.firestore.DocumentSnapshot>): Int {
        val calendar = Calendar.getInstance()
        var streak = 0

        for (i in 0 until 30) {
            val dateStr = dateFormat.format(calendar.time)
            val hasCompletedSession = sessionDocs.any { doc ->
                doc.getString("date") == dateStr &&
                        doc.getString("sessionType") == "WORK" &&
                        doc.getBoolean("isCompleted") == true
            }

            if (hasCompletedSession) {
                streak++
            } else if (i > 0) {
                break
            }

            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        return streak
    }

    suspend fun fixMissingCompletedAt() {
        val userId = auth.currentUser?.uid ?: return
        val tasksCollection = db.collection("users").document(userId).collection("tasks")

        val tasks = tasksCollection.get().await()
        var fixed = 0

        tasks.documents.forEach { doc ->
            val isCompleted = doc.getBoolean("isCompleted") ?: false
            val completedAt = doc.getDate("completedAt")
            val createdAt = doc.getDate("createdAt")

            // Fix tasks yang completed tapi tidak punya completedAt
            if (isCompleted && completedAt == null && createdAt != null) {
                doc.reference.update(
                    mapOf(
                        "completedAt" to Date(createdAt.time + 2 * 60 * 60 * 1000L), // 2 jam setelah created
                        "completed" to true // tambahkan field completed juga
                    )
                ).await()
                fixed++
            }
        }

        println("✅ Fixed $fixed tasks with missing completedAt")
    }

    // Clear all existing data
    suspend fun clearAllData() {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")

        println("🗑️ Clearing existing data for user: $userId")

        val userDoc = db.collection("users").document(userId)

        // Clear tasks subcollection
        val tasks = userDoc.collection("tasks").get().await()
        tasks.documents.forEach { it.reference.delete().await() }
        println("   - Cleared ${tasks.size()} tasks")

        // Clear sessions subcollection
        val sessions = userDoc.collection("sessions").get().await()
        sessions.documents.forEach { it.reference.delete().await() }
        println("   - Cleared ${sessions.size()} sessions")

        println("✅ Data cleared successfully")
    }
}