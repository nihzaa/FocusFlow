// File: notifications/TaskNotificationHelper.kt
package edu.unikom.focusflow.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import edu.unikom.focusflow.R
import edu.unikom.focusflow.data.models.Task

object TaskNotificationHelper {

    private const val CHANNEL_ID = "task_reminders"
    private const val CHANNEL_NAME = "Task Reminders"
    private const val CHANNEL_DESCRIPTION = "Notifications for task due dates and reminders"

    /**
     * Create notification channel for Android O+
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    null
                )
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            Log.d("TaskNotification", "Notification channel created")
        }
    }

    /**
     * Schedule task reminder notification
     */
    fun scheduleTaskReminder(context: Context, task: Task) {
        if (task.dueDate == null) {
            Log.d("TaskNotification", "Task ${task.title} has no due date, skipping reminder")
            return
        }

        createNotificationChannel(context)

        val reminderTime = task.dueDate!!.time - (task.reminderMinutes * 60 * 1000L)
        val now = System.currentTimeMillis()

        // Schedule reminder notification (before due date)
        if (reminderTime > now) {
            scheduleNotification(
                context = context,
                taskId = task.id,
                title = "📋 Task Reminder",
                message = "${task.title} is due in ${task.reminderMinutes} minutes",
                triggerTime = reminderTime,
                requestCode = task.id.hashCode()
            )

            Log.d("TaskNotification", "Reminder scheduled for '${task.title}' at ${java.util.Date(reminderTime)}")
        }

        // Schedule overdue notification (at due date)
        if (task.dueDate!!.time > now) {
            scheduleNotification(
                context = context,
                taskId = "${task.id}_overdue",
                title = "⚠️ Task Overdue",
                message = "${task.title} is now overdue!",
                triggerTime = task.dueDate!!.time,
                requestCode = "${task.id}_overdue".hashCode()
            )

            Log.d("TaskNotification", "Overdue alert scheduled for '${task.title}' at ${task.dueDate}")
        }
    }

    /**
     * Schedule a notification using AlarmManager
     */
    private fun scheduleNotification(
        context: Context,
        taskId: String,
        title: String,
        message: String,
        triggerTime: Long,
        requestCode: Int
    ) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, TaskReminderReceiver::class.java).apply {
                putExtra("task_id", taskId)
                putExtra("title", title)
                putExtra("message", message)
                putExtra("request_code", requestCode)
                action = "TASK_REMINDER_$requestCode" // Unique action for each alarm
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Check Android version and permission for exact alarms
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        Log.d("TaskNotification", "Exact alarm scheduled")
                    } else {
                        // Fallback to inexact alarm
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        Log.d("TaskNotification", "Inexact alarm scheduled (no exact alarm permission)")
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
                else -> {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            }

            Log.d("TaskNotification", "Alarm scheduled for: ${java.util.Date(triggerTime)}")

        } catch (e: Exception) {
            Log.e("TaskNotification", "Error scheduling notification: ${e.message}", e)
        }
    }

    /**
     * Cancel all notifications for a task
     */
    fun cancelTaskReminder(context: Context, taskId: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Cancel reminder notification
            cancelSingleNotification(context, alarmManager, taskId, taskId.hashCode())

            // Cancel overdue notification
            val overdueId = "${taskId}_overdue"
            cancelSingleNotification(context, alarmManager, overdueId, overdueId.hashCode())

            Log.d("TaskNotification", "All reminders cancelled for task: $taskId")

        } catch (e: Exception) {
            Log.e("TaskNotification", "Error cancelling reminders: ${e.message}", e)
        }
    }

    /**
     * Cancel a single notification
     */
    private fun cancelSingleNotification(
        context: Context,
        alarmManager: AlarmManager,
        taskId: String,
        requestCode: Int
    ) {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = "TASK_REMINDER_$requestCode"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Cancel notification from notification bar
     */
    fun dismissNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    /**
     * Check if exact alarm permission is granted (Android 12+)
     */
    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // No permission needed for older versions
        }
    }

    /**
     * Request exact alarm permission (Android 12+)
     */
    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            context.startActivity(intent)
        }
    }

    fun debugNotificationStatus(context: Context): String {
        val status = StringBuilder()

        // Check notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            status.append("POST_NOTIFICATIONS: ${if (hasPermission) "✅" else "❌"}\n")
        }

        // Check exact alarm permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            status.append("EXACT_ALARM: ${if (alarmManager.canScheduleExactAlarms()) "✅" else "❌"}\n")
        }

        // Check notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(CHANNEL_ID)
            status.append("Channel exists: ${if (channel != null) "✅" else "❌"}\n")
            if (channel != null) {
                status.append("Channel importance: ${channel.importance}\n")
                status.append("Channel enabled: ${channel.importance != NotificationManager.IMPORTANCE_NONE}\n")
            }
        }

        // Check if notifications are enabled
        val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        status.append("Notifications enabled: ${if (areNotificationsEnabled) "✅" else "❌"}\n")

        return status.toString()
    }
}