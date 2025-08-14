package edu.unikom.focusflow.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.*

data class PomodoroSession(
    @DocumentId
    val id: String = "",
    val taskId: String? = null,
    val taskTitle: String? = null,
    @get:PropertyName("sessionType")
    @set:PropertyName("sessionType")
    var sessionType: SessionType = SessionType.WORK,

    val duration: Int = 25,
    val startTime: Date? = null,
    val endTime: Date? = null,

    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,

    val date: String = "",
    val createdAt: Date = Date()
)

enum class SessionType {
    WORK, SHORT_BREAK, LONG_BREAK
}