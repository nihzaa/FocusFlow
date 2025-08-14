// File: notifications/TaskReminderReceiver.kt
package edu.unikom.focusflow.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import edu.unikom.focusflow.R
import edu.unikom.focusflow.MainActivity

/**
 * BroadcastReceiver to handle task reminder notifications
 */
class TaskReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "task_reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("TaskReminderReceiver", "Notification received")

        // Extract data from intent
        val taskId = intent.getStringExtra("task_id") ?: return
        val title = intent.getStringExtra("title") ?: "Task Reminder"
        val message = intent.getStringExtra("message") ?: "You have a task due soon"
        val requestCode = intent.getIntExtra("request_code", 0)

        Log.d("TaskReminderReceiver", "Showing notification for task: $taskId")
        Log.d("TaskReminderReceiver", "Title: $title")
        Log.d("TaskReminderReceiver", "Message: $message")

        // Show the notification
        showNotification(context, taskId, title, message, requestCode)
    }

    /**
     * Create and show the notification
     */
    private fun showNotification(
        context: Context,
        taskId: String,
        title: String,
        message: String,
        requestCode: Int
    ) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create intent to open app when notification is tapped
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("open_tasks", true)
                putExtra("task_id", taskId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create action buttons
            val markCompleteIntent = Intent(context, TaskActionReceiver::class.java).apply {
                action = "MARK_COMPLETE"
                putExtra("task_id", taskId)
            }
            val markCompletePendingIntent = PendingIntent.getBroadcast(
                context,
                "${taskId}_complete".hashCode(),
                markCompleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = Intent(context, TaskActionReceiver::class.java).apply {
                action = "SNOOZE"
                putExtra("task_id", taskId)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                "${taskId}_snooze".hashCode(),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Determine notification color based on message content
            val isOverdue = message.contains("overdue", ignoreCase = true)

            // ✅ GUNAKAN IC_LAUNCHER APLIKASI
            val notificationIcon = R.drawable.logo_focusflow_trans

            // Tentukan warna berdasarkan status
            val notificationColor = if (isOverdue) {
                context.getColor(android.R.color.holo_red_dark) // Merah untuk overdue
            } else {
                0xFF4A6741.toInt() // Hijau tema aplikasi untuk reminder
            }

            // Build notification
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(notificationIcon)          // ✅ Logo aplikasi
                .setColor(notificationColor)              // ✅ Warna background icon
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 500, 250, 500))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .addAction(
                    android.R.drawable.ic_menu_agenda,
                    "Mark Complete",
                    markCompletePendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_recent_history,
                    "Snooze 15m",
                    snoozePendingIntent
                )
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            // Show notification
            notificationManager.notify(requestCode, notification)

            Log.d("TaskReminderReceiver", "Notification displayed successfully")

        } catch (e: Exception) {
            Log.e("TaskReminderReceiver", "Error showing notification: ${e.message}", e)
        }
    }

    /**
     * BroadcastReceiver to handle notification action buttons
     */
    class TaskActionReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val taskId = intent.getStringExtra("task_id") ?: return

            Log.d("TaskActionReceiver", "Action received: $action for task: $taskId")

            when (action) {
                "MARK_COMPLETE" -> {
                    handleMarkComplete(context, taskId)
                }

                "SNOOZE" -> {
                    handleSnooze(context, taskId)
                }
            }

            // Dismiss the notification
            dismissNotification(context, taskId)
        }

        /**
         * Handle mark task as complete action
         */
        private fun handleMarkComplete(context: Context, taskId: String) {
            try {
                // You can implement this to directly update Firebase
                // For now, we'll just log and dismiss
                Log.d("TaskActionReceiver", "Task marked as complete: $taskId")

                // Show a quick toast or small notification
                showActionConfirmation(context, "Task marked as complete!")

            } catch (e: Exception) {
                Log.e("TaskActionReceiver", "Error marking task complete: ${e.message}", e)
            }
        }

        /**
         * Handle snooze task action (reschedule for 15 minutes later)
         */
        private fun handleSnooze(context: Context, taskId: String) {
            try {
                // Reschedule notification for 15 minutes later
                val snoozeTime = System.currentTimeMillis() + (15 * 60 * 1000L) // 15 minutes

                // Create a simple snooze notification
                val intent = Intent(context, TaskReminderReceiver::class.java).apply {
                    putExtra("task_id", taskId)
                    putExtra("title", "📋 Snoozed Task Reminder")
                    putExtra("message", "Your snoozed task is ready for attention")
                    putExtra("request_code", "${taskId}_snooze".hashCode())
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    "${taskId}_snooze".hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val alarmManager =
                    context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    snoozeTime,
                    pendingIntent
                )

                Log.d("TaskActionReceiver", "Task snoozed for 15 minutes: $taskId")
                showActionConfirmation(context, "Task snoozed for 15 minutes")

            } catch (e: Exception) {
                Log.e("TaskActionReceiver", "Error snoozing task: ${e.message}", e)
            }
        }

        /**
         * Show a small confirmation notification
         */
        private fun showActionConfirmation(context: Context, message: String) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val notification = NotificationCompat.Builder(context, "task_reminders")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setTimeoutAfter(3000) // Auto dismiss after 3 seconds
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        }

        /**
         * Dismiss the original notification
         */
        private fun dismissNotification(context: Context, taskId: String) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(taskId.hashCode())
        }
    }
}