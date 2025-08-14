// File: notifications/BootReceiver.kt
package edu.unikom.focusflow.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import edu.unikom.focusflow.data.repository.FirebaseRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * BroadcastReceiver to handle device boot completed
 * Reschedules all task reminders after device restart
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d("BootReceiver", "Device boot completed, rescheduling task notifications")

            // Use coroutine to handle async Firebase operations
            CoroutineScope(Dispatchers.IO).launch {
                rescheduleAllTaskNotifications(context)
            }
        }
    }

    /**
     * Reschedule notifications for all active tasks
     */
    private suspend fun rescheduleAllTaskNotifications(context: Context) {
        try {
            val currentUser = Firebase.auth.currentUser
            if (currentUser == null) {
                Log.d("BootReceiver", "User not authenticated, skipping notification reschedule")
                return
            }

            val repository = FirebaseRepository()

            // Get all tasks
            repository.getTasks().collect { tasks ->
                val activeTasks = tasks.filter { !it.isCompleted && it.dueDate != null }

                Log.d("BootReceiver", "Rescheduling notifications for ${activeTasks.size} active tasks")

                activeTasks.forEach { task ->
                    // Only reschedule if due date is in the future
                    if (task.dueDate!!.time > System.currentTimeMillis()) {
                        TaskNotificationHelper.scheduleTaskReminder(context, task)
                        Log.d("BootReceiver", "Rescheduled notification for task: ${task.title}")
                    }
                }

                Log.d("BootReceiver", "All task notifications rescheduled successfully")
                return@collect // Exit after first collection
            }

        } catch (e: Exception) {
            Log.e("BootReceiver", "Error rescheduling task notifications: ${e.message}", e)
        }
    }
}