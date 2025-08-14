package edu.unikom.focusflow.data.models

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.*

@Keep
data class Task(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String = "",

    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,

    @get:PropertyName("isInProgress")
    @set:PropertyName("isInProgress")
    var isInProgress: Boolean = false,

    val lastWorkedOn: Date? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val category: String = "",
    val createdAt: Date = Date(),
    val completedAt: Date? = null,
    val dueDate: Date? = null,
    val pomodoroSessions: Int = 0,
    val estimatedPomodoros: Int = 1,
    val tags: String = "",
    val difficulty: Int = 3,
    val reminderMinutes: Int = 30,
    val subtasks: List<Subtask> = emptyList(),
    val attachments: List<String> = emptyList(),

    @get:PropertyName("isRecurring")
    @set:PropertyName("isRecurring")
    var isRecurring: Boolean = false,

    val recurrenceType: RecurrenceType = RecurrenceType.NONE
) {
    // Tambahkan no-arg constructor eksplisit
    constructor() : this(
        id = "",
        title = "",
        description = "",
        isCompleted = false,
        isInProgress = false,
        lastWorkedOn = null,
        priority = TaskPriority.MEDIUM,
        category = "",
        createdAt = Date(),
        completedAt = null,
        dueDate = null,
        pomodoroSessions = 0,
        estimatedPomodoros = 1,
        tags = "",
        difficulty = 3,
        reminderMinutes = 30,
        subtasks = emptyList(),
        attachments = emptyList(),
        isRecurring = false,
        recurrenceType = RecurrenceType.NONE
    )
}

@Keep
data class Subtask(
    val id: String = "",
    val title: String = "",

    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,

    val createdAt: Date = Date()
)

@Keep
enum class TaskPriority(val displayName: String, val color: androidx.compose.ui.graphics.Color) {
    LOW("Low", androidx.compose.ui.graphics.Color(0xFF4CAF50)),
    MEDIUM("Medium", androidx.compose.ui.graphics.Color(0xFFFF9800)),
    HIGH("High", androidx.compose.ui.graphics.Color(0xFFF44336))
}

@Keep
enum class RecurrenceType(val displayName: String) {
    NONE("None"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}