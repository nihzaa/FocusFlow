package edu.unikom.focusflow.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import edu.unikom.focusflow.data.models.Task
import edu.unikom.focusflow.data.models.TaskPriority
import edu.unikom.focusflow.data.models.Subtask
import edu.unikom.focusflow.data.models.RecurrenceType
import edu.unikom.focusflow.data.repository.FirebaseRepository
import edu.unikom.focusflow.ui.components.BottomNavigationBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import edu.unikom.focusflow.notifications.TaskNotificationHelper
import java.util.*
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.NotificationManagerCompat

enum class DeadlineFilter {
    ALL, OVERDUE, NEAR, FAR, NO_DATE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(navController: NavController) {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showEditTaskDialog by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    var showTaskDetail by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var filterCompleted by remember { mutableStateOf(false) }
    var deadlineFilter by remember { mutableStateOf(DeadlineFilter.ALL) }

    var refreshTrigger by remember { mutableStateOf(0) }

    val repository = remember { FirebaseRepository() }
    val coroutineScope = rememberCoroutineScope()

    // Permission launcher untuk notifikasi
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("TasksScreen", "Notification permission granted")
        } else {
            Log.e("TasksScreen", "Notification permission denied")
        }
    }

    // Check dan request permission saat compose
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Initialize notification channel
        TaskNotificationHelper.createNotificationChannel(context)
    }

    LaunchedEffect(Unit, refreshTrigger) {
        coroutineScope.launch {
            try {
                Log.d("TasksScreen", "Starting to load tasks from Firebase...")
                val currentUser = Firebase.auth.currentUser
                Log.d("TasksScreen", "Current user: ${currentUser?.uid}")

                if (currentUser == null) {
                    Log.e("TasksScreen", "User not authenticated!")
                    tasks = getSampleTasks()
                    isLoading = false
                    return@launch
                }

                repository.getTasks().collect { firebaseTasks ->
                    Log.d("TasksScreen", "Loaded ${firebaseTasks.size} tasks from Firebase")
                    tasks = firebaseTasks
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e("TasksScreen", "Error loading tasks: ${e.message}", e)
                tasks = getSampleTasks()
                isLoading = false
            }
        }
    }

    // Kategorisasi tasks berdasarkan deadline
    val now = Date()
    val threeDaysFromNow = Date(now.time + (3 * 24 * 60 * 60 * 1000))

    val overdueTasks = tasks.filter {
        !it.isCompleted && it.dueDate != null && it.dueDate.before(now)
    }

    val nearDeadlineTasks = tasks.filter {
        !it.isCompleted && it.dueDate != null &&
                !it.dueDate.before(now) && it.dueDate.before(threeDaysFromNow)
    }

    val farDeadlineTasks = tasks.filter {
        !it.isCompleted && it.dueDate != null &&
                !it.dueDate.before(threeDaysFromNow)
    }

    val noDateTasks = tasks.filter {
        !it.isCompleted && it.dueDate == null
    }

    // Filter berdasarkan completed/active terlebih dahulu
    val baseFilteredTasks = if (filterCompleted) {
        tasks.filter { it.isCompleted }
    } else {
        tasks.filter { !it.isCompleted }
    }

    // Kemudian filter berdasarkan deadline (hanya untuk active tasks)
    val filteredTasks = if (filterCompleted) {
        baseFilteredTasks // Jika completed, tampilkan semua completed tasks
    } else {
        when(deadlineFilter) {
            DeadlineFilter.ALL -> baseFilteredTasks
            DeadlineFilter.OVERDUE -> overdueTasks
            DeadlineFilter.NEAR -> nearDeadlineTasks
            DeadlineFilter.FAR -> farDeadlineTasks
            DeadlineFilter.NO_DATE -> noDateTasks
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        },
        floatingActionButton = {
            ModernFAB(onClick = { showAddTaskDialog = true })
        },
        containerColor = Color(0xFFF8F9FA)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ProgressRingHeader(
                activeTasks = tasks.count { !it.isCompleted },
                completedTasks = tasks.count { it.isCompleted }
            )

            Column(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Filter Active/Completed
                ModernFilterTabs(
                    filterCompleted = filterCompleted,
                    onFilterChange = {
                        filterCompleted = it
                        // Reset deadline filter when switching to completed
                        if (it) deadlineFilter = DeadlineFilter.ALL
                    },
                    activeTasks = tasks.count { !it.isCompleted },
                    completedTasks = tasks.count { it.isCompleted }
                )

                // Filter Deadline (hanya tampil jika Active yang dipilih)
                if (!filterCompleted) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DeadlineFilterTabs(
                        selectedFilter = deadlineFilter,
                        onFilterChange = { deadlineFilter = it },
                        overdueTasks = overdueTasks.size,
                        nearDeadlineTasks = nearDeadlineTasks.size,
                        farDeadlineTasks = farDeadlineTasks.size,
                        noDateTasks = noDateTasks.size
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isLoading) {
                    ModernLoadingState()
                } else if (filteredTasks.isEmpty()) {
                    ModernEmptyState(filterCompleted)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(
                            items = filteredTasks,
                            key = { it.id }
                        ) { task ->
                            ModernTaskItem(
                                task = task,
                                onTaskClick = {
                                    selectedTask = task
                                    showEditTaskDialog = true
                                },
                                onTaskComplete = { taskToComplete ->
                                    coroutineScope.launch {
                                        try {
                                            repository.completeTask(taskToComplete.id)
                                            // Cancel notifications when completed
                                            TaskNotificationHelper.cancelTaskReminder(context, taskToComplete.id)
                                        } catch (e: Exception) {
                                            Log.e("TasksScreen", "Error completing task", e)
                                        }
                                    }
                                },
                                onTaskDelete = { taskToDelete ->
                                    coroutineScope.launch {
                                        try {
                                            repository.deleteTask(taskToDelete.id)
                                            // Cancel notifications when deleted
                                            TaskNotificationHelper.cancelTaskReminder(context, taskToDelete.id)
                                        } catch (e: Exception) {
                                            Log.e("TasksScreen", "Error deleting task", e)
                                        }
                                    }
                                },
                                onTaskUncomplete = { taskToUncomplete ->
                                    coroutineScope.launch {
                                        try {
                                            repository.uncompleteTask(taskToUncomplete.id)
                                            // Reschedule notifications when uncompleted
                                            taskToUncomplete.dueDate?.let {
                                                TaskNotificationHelper.scheduleTaskReminder(context, taskToUncomplete)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("TasksScreen", "Error uncompleting task", e)
                                        }
                                    }
                                },
                                onSubtaskToggle = { taskId, subtaskId, completed ->
                                    coroutineScope.launch {
                                        try {
                                            Log.d("TasksScreen", "Starting subtask toggle: $subtaskId to $completed")

                                            // Toggle subtask
                                            repository.toggleSubtask(taskId, subtaskId, completed)

                                            // Update local state immediately untuk UI responsiveness
                                            tasks = tasks.map { task ->
                                                if (task.id == taskId) {
                                                    task.copy(
                                                        subtasks = task.subtasks.map { subtask ->
                                                            if (subtask.id == subtaskId) {
                                                                subtask.copy(isCompleted = completed)
                                                            } else {
                                                                subtask
                                                            }
                                                        }
                                                    )
                                                } else {
                                                    task
                                                }
                                            }

                                            Log.d("TasksScreen", "Subtask toggled successfully")
                                        } catch (e: Exception) {
                                            Log.e("TasksScreen", "Error toggling subtask", e)
                                            // Reload tasks on error
                                            repository.getTasks().collect { firebaseTasks ->
                                                tasks = firebaseTasks
                                            }
                                        }
                                    }
                                },
                                onInfoClick = {
                                    selectedTask = task
                                    showTaskDetail = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Task Dialog
    if (showAddTaskDialog) {
        ModernTaskDialog(
            title = "Add New Task",
            task = null,
            onDismiss = { showAddTaskDialog = false },
            onTaskSave = { newTask ->
                coroutineScope.launch {
                    try {
                        val taskId = repository.addTask(newTask)
                        val taskWithId = newTask.copy(id = taskId)

                        // Schedule notification if due date is set
                        newTask.dueDate?.let {
                            TaskNotificationHelper.scheduleTaskReminder(context, taskWithId)
                        }
                    } catch (e: Exception) {
                        Log.e("TasksScreen", "Error adding task", e)
                    }
                }
                showAddTaskDialog = false
            }
        )
    }

    // Edit Task Dialog
    if (showEditTaskDialog && selectedTask != null) {
        ModernTaskDialog(
            title = "Edit Task",
            task = selectedTask,
            onDismiss = {
                showEditTaskDialog = false
                selectedTask = null
            },
            onTaskSave = { updatedTask ->
                coroutineScope.launch {
                    try {
                        repository.updateTask(updatedTask)

                        // Cancel old notifications
                        TaskNotificationHelper.cancelTaskReminder(context, updatedTask.id)

                        // Schedule new notifications if due date exists and not completed
                        if (!updatedTask.isCompleted && updatedTask.dueDate != null) {
                            TaskNotificationHelper.scheduleTaskReminder(context, updatedTask)
                        }
                    } catch (e: Exception) {
                        Log.e("TasksScreen", "Error updating task", e)
                    }
                }
                showEditTaskDialog = false
                selectedTask = null
            }
        )
    }

    // Task Detail Dialog (Read-only)
    if (showTaskDetail && selectedTask != null) {
        ModernTaskDetailDialog(
            task = selectedTask!!,
            onDismiss = {
                showTaskDetail = false
                selectedTask = null
            }
        )
    }
}

@Composable
fun DeadlineFilterTabs(
    selectedFilter: DeadlineFilter,
    onFilterChange: (DeadlineFilter) -> Unit,
    overdueTasks: Int,
    nearDeadlineTasks: Int,
    farDeadlineTasks: Int,
    noDateTasks: Int
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            DeadlineFilterChip(
                selected = selectedFilter == DeadlineFilter.ALL,
                onClick = { onFilterChange(DeadlineFilter.ALL) },
                label = "All",
                count = overdueTasks + nearDeadlineTasks + farDeadlineTasks + noDateTasks,
                color = Color(0xFF4A6741),
                icon = Icons.Default.List
            )
        }
        item {
            DeadlineFilterChip(
                selected = selectedFilter == DeadlineFilter.OVERDUE,
                onClick = { onFilterChange(DeadlineFilter.OVERDUE) },
                label = "Overdue",
                count = overdueTasks,
                color = Color(0xFFE53935),
                icon = Icons.Default.Warning
            )
        }
        item {
            DeadlineFilterChip(
                selected = selectedFilter == DeadlineFilter.NEAR,
                onClick = { onFilterChange(DeadlineFilter.NEAR) },
                label = "< 3 days",
                count = nearDeadlineTasks,
                color = Color(0xFFFF9800),
                icon = Icons.Default.Schedule
            )
        }
        item {
            DeadlineFilterChip(
                selected = selectedFilter == DeadlineFilter.FAR,
                onClick = { onFilterChange(DeadlineFilter.FAR) },
                label = "> 3 days",
                count = farDeadlineTasks,
                color = Color(0xFF52C49C),
                icon = Icons.Default.DateRange
            )
        }
        item {
            DeadlineFilterChip(
                selected = selectedFilter == DeadlineFilter.NO_DATE,
                onClick = { onFilterChange(DeadlineFilter.NO_DATE) },
                label = "No date",
                count = noDateTasks,
                color = Color(0xFF9E9E9E),
                icon = Icons.Default.RemoveCircleOutline
            )
        }
    }
}

@Composable
fun DeadlineFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (selected) Color.White else color
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                if (count > 0) {
                    Surface(
                        shape = CircleShape,
                        color = if (selected)
                            Color.White.copy(alpha = 0.2f)
                        else
                            color.copy(alpha = 0.1f)
                    ) {
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) Color.White else color,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
            containerColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = color.copy(alpha = 0.3f),
            selectedBorderColor = Color.Transparent,
            borderWidth = 1.dp,
            selectedBorderWidth = 0.dp
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = FilterChipDefaults.filterChipElevation(
            elevation = if (selected) 4.dp else 1.dp
        )
    )
}

@Composable
fun ModernTaskItem(
    task: Task,
    onTaskClick: () -> Unit,
    onTaskComplete: (Task) -> Unit,
    onTaskDelete: (Task) -> Unit,
    onTaskUncomplete: (Task) -> Unit = {},
    onSubtaskToggle: (String, String, Boolean) -> Unit,
    onInfoClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    var expandedSubtasks by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "task_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                // Checkbox on the left
                ModernCheckbox(
                    checked = task.isCompleted,
                    onCheckedChange = {
                        if (task.isCompleted) {
                            onTaskUncomplete(task)
                        } else {
                            onTaskComplete(task)
                        }
                    },
                    priority = task.priority
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Main content
                Column(modifier = Modifier.weight(1f)) {
                    // Title without edit icon
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                        color = if (task.isCompleted)
                            Color.Gray.copy(alpha = 0.6f)
                        else
                            Color(0xFF2E3A3A),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            isPressed = true
                            onTaskClick()
                        }
                    )

                    if (task.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (task.isCompleted)
                                Color.Gray.copy(alpha = 0.4f)
                            else
                                Color(0xFF8E8E93),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PriorityBadge(priority = task.priority, isCompleted = task.isCompleted)

                        if (task.category.isNotEmpty()) {
                            CategoryBadge(category = task.category, isCompleted = task.isCompleted)
                        }

                        if (task.estimatedPomodoros > 0) {
                            ProgressBadge(
                                current = task.pomodoroSessions,
                                total = task.estimatedPomodoros,
                                isCompleted = task.isCompleted
                            )
                        }

                        if (task.isRecurring) {
                            RecurringBadge(recurrenceType = task.recurrenceType)
                        }

                        if (task.attachments.isNotEmpty()) {
                            AttachmentBadge(count = task.attachments.size)
                        }
                    }

                    task.dueDate?.let { dueDate ->
                        Spacer(modifier = Modifier.height(8.dp))
                        DueDateBadge(dueDate = dueDate, isCompleted = task.isCompleted)
                    }
                }

                // Action buttons - repositioned
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Edit button with better styling
                    IconButton(
                        onClick = {
                            isPressed = true
                            onTaskClick()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4A6741).copy(alpha = 0.1f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Task",
                                    tint = Color(0xFF4A6741),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { onInfoClick() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Task Info",
                            tint = Color.Gray.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { onTaskDelete(task) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Task",
                            tint = Color(0xFFE53935).copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Subtasks Section - Fixed with expand/collapse
            if (task.subtasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                // Show first 3 or all if expanded
                val subtasksToShow = if (expandedSubtasks) task.subtasks else task.subtasks.take(3)

                subtasksToShow.forEach { subtask ->
                    SubtaskItem(
                        subtask = subtask,
                        parentTaskCompleted = task.isCompleted,
                        onToggle = { completed ->
                            if (!task.isCompleted) {
                                Log.d("ModernTaskItem", "Toggling subtask: ${subtask.id} to $completed")
                                onSubtaskToggle(task.id, subtask.id, completed)
                            }
                        }
                    )
                }

                // Show more/less button
                if (task.subtasks.size > 3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expandedSubtasks = !expandedSubtasks }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (expandedSubtasks) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expandedSubtasks) "Show less" else "Show more",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF4A6741)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (expandedSubtasks)
                                "Show less"
                            else
                                "+${task.subtasks.size - 3} more subtasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4A6741),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
}

@Composable
fun SubtaskItem(
    subtask: Subtask,
    parentTaskCompleted: Boolean = false,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // Checkbox - clickable independently
        Checkbox(
            checked = subtask.isCompleted,
            onCheckedChange = { checked ->
                if (!parentTaskCompleted) {
                    Log.d("SubtaskItem", "Checkbox clicked: ${subtask.id} -> $checked")
                    onToggle(checked)
                }
            },
            enabled = !parentTaskCompleted,
            modifier = Modifier.size(20.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF52C49C),
                uncheckedColor = Color.Gray.copy(alpha = 0.5f),
                checkmarkColor = Color.White,
                disabledCheckedColor = Color.Gray.copy(alpha = 0.3f),
                disabledUncheckedColor = Color.Gray.copy(alpha = 0.2f)
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Text - also clickable
        Text(
            text = subtask.title,
            style = MaterialTheme.typography.bodySmall,
            textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            color = when {
                parentTaskCompleted -> Color.Gray.copy(alpha = 0.4f)
                subtask.isCompleted -> Color.Gray.copy(alpha = 0.6f)
                else -> Color(0xFF2E3A3A)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = !parentTaskCompleted) {
                    Log.d("SubtaskItem", "Text clicked: ${subtask.id} -> ${!subtask.isCompleted}")
                    onToggle(!subtask.isCompleted)
                }
        )

        // Visual feedback for completed subtask
        if (subtask.isCompleted && !parentTaskCompleted) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Completed",
                tint = Color(0xFF52C49C),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernTaskDialog(
    title: String,
    task: Task? = null,
    onDismiss: () -> Unit,
    onTaskSave: (Task) -> Unit
) {
    val isEdit = task != null

    var taskTitle by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var category by remember { mutableStateOf(task?.category ?: "") }
    var priority by remember { mutableStateOf(task?.priority ?: TaskPriority.MEDIUM) }
    var estimatedPomodoros by remember { mutableIntStateOf(task?.estimatedPomodoros ?: 1) }
    var showPriorityDropdown by remember { mutableStateOf(false) }

    // Enhanced fields
    var dueDate by remember { mutableStateOf<Date?>(task?.dueDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(task?.tags ?: "") }
    var difficulty by remember { mutableIntStateOf(task?.difficulty ?: 3) }
    var remindBefore by remember { mutableIntStateOf(task?.reminderMinutes ?: 30) }

    // Subtasks management - Fixed
    var subtasks by remember {
        mutableStateOf<List<Subtask>>(
            task?.subtasks ?: emptyList()
        )
    }
    var newSubtask by remember { mutableStateOf("") }

    var attachments by remember {
        mutableStateOf<List<String>>(
            task?.attachments ?: emptyList()
        )
    }
    var newAttachment by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(task?.isRecurring ?: false) }
    var recurrenceType by remember { mutableStateOf(task?.recurrenceType ?: RecurrenceType.DAILY) }
    var showRecurrenceDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(600.dp)
            ) {
                // Basic Info
                item {
                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        label = { Text("Task Title *") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Assignment, contentDescription = null)
                        }
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null)
                        }
                    )
                }

                // Category & Tags
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Folder, contentDescription = null)
                            }
                        )

                        OutlinedTextField(
                            value = tags,
                            onValueChange = { tags = it },
                            label = { Text("Tags") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            placeholder = { Text("#work #urgent") },
                            leadingIcon = {
                                Icon(Icons.Default.Tag, contentDescription = null)
                            }
                        )
                    }
                }

                // Due Date
                item {
                    Column {
                        OutlinedTextField(
                            value = dueDate?.let {
                                SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(it)
                            } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Due Date") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Schedule, contentDescription = null)
                            },
                            trailingIcon = {
                                Row {
                                    if (dueDate != null) {
                                        IconButton(onClick = { dueDate = null }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear date")
                                        }
                                    }
                                    IconButton(onClick = { showDatePicker = true }) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                                    }
                                }
                            },
                            placeholder = { Text("Tap calendar to set due date") },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        // Real-time Countdown Display
                        dueDate?.let { date ->
                            Spacer(modifier = Modifier.height(8.dp))
                            DueDateCountdown(dueDate = date)
                        }
                    }
                }

                // Reminder Settings (show only if due date is set)
                dueDate?.let {
                    item {
                        Text("Reminder Settings", fontWeight = FontWeight.Medium)

                        Text(
                            "Remind me $remindBefore minutes before due date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF8E8E93)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val reminderOptions = listOf(15, 30, 60, 120)
                            reminderOptions.forEach { minutes ->
                                FilterChip(
                                    onClick = { remindBefore = minutes },
                                    label = { Text("${minutes}m") },
                                    selected = remindBefore == minutes,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF4A6741),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        // Custom reminder input
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Custom:", style = MaterialTheme.typography.bodySmall)

                            var customReminder by remember { mutableStateOf(remindBefore.toString()) }

                            OutlinedTextField(
                                value = customReminder,
                                onValueChange = {
                                    customReminder = it
                                    it.toIntOrNull()?.let { minutes ->
                                        if (minutes > 0) remindBefore = minutes
                                    }
                                },
                                modifier = Modifier.width(80.dp),
                                singleLine = true,
                                suffix = { Text("min", style = MaterialTheme.typography.bodySmall) }
                            )
                        }

                        // Preview reminder time
                        val reminderTime = Date(it.time - (remindBefore * 60 * 1000L))
                        Text(
                            "📱 Notification will appear at: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(reminderTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4A6741),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // Priority
                item {
                    ExposedDropdownMenuBox(
                        expanded = showPriorityDropdown,
                        onExpandedChange = { showPriorityDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = priority.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Priority") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPriorityDropdown) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Flag, contentDescription = null, tint = priority.color)
                            }
                        )

                        ExposedDropdownMenu(
                            expanded = showPriorityDropdown,
                            onDismissRequest = { showPriorityDropdown = false }
                        ) {
                            TaskPriority.entries.forEach { priorityOption ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(priorityOption.color)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(priorityOption.displayName)
                                        }
                                    },
                                    onClick = {
                                        priority = priorityOption
                                        showPriorityDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Recurring Task
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = isRecurring,
                            onCheckedChange = { isRecurring = it }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Recurring Task", fontWeight = FontWeight.Medium)
                    }

                    if (isRecurring) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = showRecurrenceDropdown,
                            onExpandedChange = { showRecurrenceDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = recurrenceType.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Recurrence") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRecurrenceDropdown) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = {
                                    Icon(Icons.Default.Repeat, contentDescription = null)
                                }
                            )

                            ExposedDropdownMenu(
                                expanded = showRecurrenceDropdown,
                                onDismissRequest = { showRecurrenceDropdown = false }
                            ) {
                                RecurrenceType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.displayName) },
                                        onClick = {
                                            recurrenceType = type
                                            showRecurrenceDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Subtasks - Fixed
                item {
                    Text("Subtasks", fontWeight = FontWeight.Medium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newSubtask,
                            onValueChange = { newSubtask = it },
                            label = { Text("Add subtask") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        IconButton(
                            onClick = {
                                if (newSubtask.isNotBlank()) {
                                    val newSubtaskItem = Subtask(
                                        id = UUID.randomUUID().toString(),
                                        title = newSubtask.trim(),
                                        isCompleted = false
                                    )
                                    subtasks = subtasks.plus(newSubtaskItem)
                                    newSubtask = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add subtask")
                        }
                    }

                    subtasks.forEachIndexed { index, subtask ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Checkbox for subtask in dialog
                            Checkbox(
                                checked = subtask.isCompleted,
                                onCheckedChange = { checked ->
                                    subtasks = subtasks.mapIndexed { i, s ->
                                        if (i == index) s.copy(isCompleted = checked) else s
                                    }
                                },
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                subtask.title,
                                modifier = Modifier.weight(1f),
                                textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else null
                            )

                            IconButton(
                                onClick = {
                                    subtasks = subtasks.filterIndexed { i, _ -> i != index }
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }

                // Attachments
                item {
                    Text("Attachments", fontWeight = FontWeight.Medium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newAttachment,
                            onValueChange = { newAttachment = it },
                            label = { Text("Add link/URL") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        IconButton(
                            onClick = {
                                if (newAttachment.isNotBlank()) {
                                    attachments = attachments.plus(newAttachment.trim())
                                    newAttachment = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add attachment")
                        }
                    }

                    attachments.forEachIndexed { index, attachment ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                attachment,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = {
                                    attachments = attachments.filterIndexed { i, _ -> i != index }
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }

                // Difficulty & Pomodoros
                item {
                    Text("Difficulty Level: ${getDifficultyText(difficulty)}", fontWeight = FontWeight.Medium)
                    Slider(
                        value = difficulty.toFloat(),
                        onValueChange = { difficulty = it.toInt() },
                        valueRange = 1f..5f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFD700),
                            activeTrackColor = Color(0xFFFFD700)
                        )
                    )
                }

                item {
                    Text("Estimated Pomodoros: $estimatedPomodoros (${estimatedPomodoros * 25} min)", fontWeight = FontWeight.Medium)
                    Slider(
                        value = estimatedPomodoros.toFloat(),
                        onValueChange = { estimatedPomodoros = it.toInt() },
                        valueRange = 1f..8f,
                        steps = 6,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF4A6741),
                            activeTrackColor = Color(0xFF4A6741)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (taskTitle.isNotBlank()) {
                        val savedTask = if (isEdit) {
                            task!!.copy(
                                title = taskTitle.trim(),
                                description = description.trim(),
                                category = category.trim(),
                                priority = priority,
                                estimatedPomodoros = estimatedPomodoros,
                                dueDate = dueDate,
                                tags = if (tags.isNotBlank()) tags.trim() else "",
                                difficulty = difficulty,
                                reminderMinutes = remindBefore,
                                subtasks = subtasks,
                                attachments = attachments,
                                isRecurring = isRecurring,
                                recurrenceType = if (isRecurring) recurrenceType else RecurrenceType.NONE
                            )
                        } else {
                            Task(
                                title = taskTitle.trim(),
                                description = description.trim(),
                                category = category.trim(),
                                priority = priority,
                                estimatedPomodoros = estimatedPomodoros,
                                createdAt = Date(),
                                dueDate = dueDate,
                                tags = if (tags.isNotBlank()) tags.trim() else "",
                                difficulty = difficulty,
                                reminderMinutes = remindBefore,
                                subtasks = subtasks,
                                attachments = attachments,
                                isRecurring = isRecurring,
                                recurrenceType = if (isRecurring) recurrenceType else RecurrenceType.NONE
                            )
                        }
                        onTaskSave(savedTask)
                    }
                },
                enabled = taskTitle.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A6741)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isEdit) "Update Task" else "Add Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8E8E93))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )

    if (showDatePicker) {
        EnhancedDateTimePickerDialog(
            onDateTimeSelected = { selectedDate ->
                dueDate = selectedDate
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

// Continue with remaining composables (ProgressRingHeader, ModernFilterTabs, etc.)
// [Rest of the code remains exactly the same from line 1019 onwards in the original file...]

@Composable
fun ProgressRingHeader(
    activeTasks: Int,
    completedTasks: Int
) {
    val total = activeTasks + completedTasks
    val progress = if (total > 0) completedTasks.toFloat() / total else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = EaseInOutCubic),
        label = "progress_animation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        Text(
            text = "MY TASKS",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4A6741),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(100.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(100.dp),
                        strokeWidth = 8.dp,
                        color = Color(0xFF52C49C).copy(alpha = 0.1f),
                        strokeCap = StrokeCap.Round
                    )

                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(100.dp),
                        strokeWidth = 8.dp,
                        color = Color(0xFF52C49C),
                        strokeCap = StrokeCap.Round
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = completedTasks.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF52C49C)
                        )
                        Text(
                            text = "completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8E8E93)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4A6741).copy(alpha = 0.1f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = Color(0xFF4A6741),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = activeTasks.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4A6741)
                            )
                            Text(
                                text = "Active Tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8E8E93)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF52C49C).copy(alpha = 0.1f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = Color(0xFF52C49C),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF52C49C)
                            )
                            Text(
                                text = "Progress",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8E8E93)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernFilterTabs(
    filterCompleted: Boolean,
    onFilterChange: (Boolean) -> Unit,
    activeTasks: Int,
    completedTasks: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModernFilterChip(
            selected = !filterCompleted,
            onClick = { onFilterChange(false) },
            label = "Active",
            count = activeTasks,
            modifier = Modifier.weight(1f)
        )

        ModernFilterChip(
            selected = filterCompleted,
            onClick = { onFilterChange(true) },
            label = "Completed",
            count = completedTasks,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ModernFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    val animatedElevation by animateDpAsState(
        targetValue = if (selected) 6.dp else 2.dp,
        animationSpec = tween(200),
        label = "elevation_animation"
    )

    Card(
        modifier = modifier
            .height(48.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF4A6741) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else Color(0xFF2E3A3A)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = if (selected)
                    Color.White.copy(alpha = 0.2f)
                else
                    Color(0xFF4A6741).copy(alpha = 0.1f)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else Color(0xFF4A6741),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ModernCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    priority: TaskPriority
) {
    val color = if (checked) Color(0xFF52C49C) else priority.color
    val animatedSize by animateFloatAsState(
        targetValue = if (checked) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "checkbox_size"
    )

    Box(
        modifier = Modifier
            .size(24.dp)
            .scale(animatedSize)
            .clip(CircleShape)
            .background(
                if (checked) color else Color.Transparent
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
fun PriorityBadge(priority: TaskPriority, isCompleted: Boolean = false) {
    val badgeColor = if (isCompleted) Color.Gray.copy(alpha = 0.4f) else priority.color

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = badgeColor.copy(alpha = 0.15f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Text(
                text = priority.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = badgeColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun CategoryBadge(category: String, isCompleted: Boolean = false) {
    val badgeColor = if (isCompleted) Color.Gray.copy(alpha = 0.4f) else Color(0xFF8E8E93)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = badgeColor.copy(alpha = 0.1f)
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.bodySmall,
            color = badgeColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ProgressBadge(current: Int, total: Int, isCompleted: Boolean = false) {
    val badgeColor = if (isCompleted) Color.Gray.copy(alpha = 0.4f) else Color(0xFF52C49C)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = badgeColor.copy(alpha = 0.15f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "$current/$total",
                style = MaterialTheme.typography.bodySmall,
                color = badgeColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun DueDateBadge(dueDate: Date, isCompleted: Boolean = false) {
    val now = Date()
    val isOverdue = dueDate.before(now) && !isCompleted
    val timeLeft = getTimeLeft(dueDate)

    val badgeColor = when {
        isCompleted -> Color.Gray.copy(alpha = 0.4f)
        isOverdue -> Color.Red
        else -> Color(0xFF4A6741)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = badgeColor.copy(alpha = 0.15f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = if (isCompleted)
                    SimpleDateFormat("MMM dd", Locale.getDefault()).format(dueDate)
                else
                    timeLeft,
                style = MaterialTheme.typography.bodySmall,
                color = badgeColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun RecurringBadge(recurrenceType: RecurrenceType) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF9C27B0).copy(alpha = 0.15f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                Icons.Default.Repeat,
                contentDescription = null,
                tint = Color(0xFF9C27B0),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = recurrenceType.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9C27B0),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AttachmentBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFF9800).copy(alpha = 0.15f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                Icons.Default.Attachment,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF9800),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ModernFAB(onClick: () -> Unit) {
    val animatedScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fab_scale"
    )

    FloatingActionButton(
        onClick = onClick,
        containerColor = Color(0xFF4A6741),
        contentColor = Color.White,
        modifier = Modifier
            .size(64.dp)
            .scale(animatedScale),
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "Add Task",
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun ModernLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = Color(0xFF4A6741),
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Loading your tasks...",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF8E8E93)
        )
    }
}

@Composable
fun ModernEmptyState(showingCompleted: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    Color(0xFF8E8E93).copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (showingCompleted) Icons.Outlined.TaskAlt else Icons.Outlined.Assignment,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = Color(0xFF8E8E93).copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (showingCompleted) "No completed tasks yet" else "No active tasks",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2E3A3A)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (showingCompleted)
                "Complete some tasks to see them here"
            else "Tap the + button to add your first task",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun ModernTaskDetailDialog(
    task: Task,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = task.priority.color
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(400.dp)
            ) {
                if (task.description.isNotEmpty()) {
                    item {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2E3A3A)
                        )
                    }
                }

                item {
                    TaskDetailRow("Priority", task.priority.displayName, task.priority.color)
                    if (task.category.isNotEmpty()) {
                        TaskDetailRow("Category", task.category)
                    }
                    TaskDetailRow("Created", SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(task.createdAt))
                    TaskDetailRow("Progress", "${task.pomodoroSessions}/${task.estimatedPomodoros} pomodoros")

                    task.dueDate?.let { dueDate ->
                        TaskDetailRow("Due Date", SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(dueDate))
                        TaskDetailRow("Reminder", "${task.reminderMinutes} minutes before")
                    }

                    if (task.isRecurring) {
                        TaskDetailRow("Recurrence", task.recurrenceType.displayName)
                    }

                    if (task.difficulty != 3) {
                        TaskDetailRow("Difficulty", getDifficultyText(task.difficulty))
                    }

                    if (task.isCompleted && task.completedAt != null) {
                        TaskDetailRow("Completed", SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(task.completedAt!!))
                    }
                }

                if (task.subtasks.isNotEmpty()) {
                    item {
                        Text("Subtasks", fontWeight = FontWeight.Medium)
                        task.subtasks.forEach { subtask ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    if (subtask.isCompleted) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    subtask.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else null
                                )
                            }
                        }
                    }
                }

                if (task.attachments.isNotEmpty()) {
                    item {
                        Text("Attachments", fontWeight = FontWeight.Medium)
                        task.attachments.forEach { attachment ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    attachment,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Blue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF4A6741))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun DueDateCountdown(dueDate: Date) {
    var timeLeft by remember { mutableStateOf("") }
    var isOverdue by remember { mutableStateOf(false) }

    LaunchedEffect(dueDate) {
        while (true) {
            val now = Date()
            val diff = dueDate.time - now.time

            isOverdue = diff < 0
            timeLeft = when {
                diff < 0 -> {
                    val overdue = Math.abs(diff)
                    when {
                        overdue < 60 * 60 * 1000 -> "Overdue by ${overdue / (60 * 1000)} minutes"
                        overdue < 24 * 60 * 60 * 1000 -> "Overdue by ${overdue / (60 * 60 * 1000)} hours"
                        else -> "Overdue by ${overdue / (24 * 60 * 60 * 1000)} days"
                    }
                }
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes remaining"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours remaining"
                diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} days remaining"
                else -> SimpleDateFormat("MMM dd 'at' HH:mm", Locale.getDefault()).format(dueDate)
            }
            kotlinx.coroutines.delay(60000) // Update every minute
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isOverdue) Color.Red.copy(alpha = 0.1f) else Color(0xFF4A6741).copy(alpha = 0.1f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                if (isOverdue) Icons.Default.Warning else Icons.Default.Schedule,
                contentDescription = null,
                tint = if (isOverdue) Color.Red else Color(0xFF4A6741),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = timeLeft,
                style = MaterialTheme.typography.bodySmall,
                color = if (isOverdue) Color.Red else Color(0xFF4A6741),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun EnhancedDateTimePickerDialog(
    onDateTimeSelected: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(Calendar.getInstance().time) }
    var selectedHour by remember { mutableIntStateOf(17) }
    var selectedMinute by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Set Due Date & Time",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Quick Date Buttons
                Text("Quick Select:", fontWeight = FontWeight.Medium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickOptions = listOf(
                        "Today" to 0,
                        "Tomorrow" to 1,
                        "Next Week" to 7
                    )
                    quickOptions.forEach { (label, days) ->
                        FilterChip(
                            onClick = {
                                val cal = Calendar.getInstance()
                                cal.add(Calendar.DAY_OF_MONTH, days)
                                selectedDate = cal.time
                            },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            selected = false,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF4A6741).copy(alpha = 0.1f)
                            )
                        )
                    }
                }

                HorizontalDivider()

                // Date Display
                Text(
                    "Date: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedDate)}",
                    fontWeight = FontWeight.Medium
                )

                // Time Selection
                Text("Time:", fontWeight = FontWeight.Medium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hour
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hour", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    selectedHour = if (selectedHour > 0) selectedHour - 1 else 23
                                }
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease hour")
                            }
                            Text(
                                String.format("%02d", selectedHour),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = {
                                    selectedHour = if (selectedHour < 23) selectedHour + 1 else 0
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase hour")
                            }
                        }
                    }

                    Text(":", style = MaterialTheme.typography.titleLarge)

                    // Minute
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Minute", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    selectedMinute = if (selectedMinute >= 15) selectedMinute - 15 else 45
                                }
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease minute")
                            }
                            Text(
                                String.format("%02d", selectedMinute),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = {
                                    selectedMinute = if (selectedMinute < 45) selectedMinute + 15 else 0
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase minute")
                            }
                        }
                    }
                }

                // Quick Time Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val timeOptions = listOf(
                        "9 AM" to Pair(9, 0),
                        "1 PM" to Pair(13, 0),
                        "5 PM" to Pair(17, 0),
                        "9 PM" to Pair(21, 0)
                    )
                    timeOptions.forEach { (label, time) ->
                        FilterChip(
                            onClick = {
                                selectedHour = time.first
                                selectedMinute = time.second
                            },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            selected = selectedHour == time.first && selectedMinute == time.second,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF4A6741),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cal = Calendar.getInstance()
                    cal.time = selectedDate
                    cal.set(Calendar.HOUR_OF_DAY, selectedHour)
                    cal.set(Calendar.MINUTE, selectedMinute)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    onDateTimeSelected(cal.time)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A6741))
            ) {
                Text("Set Date")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun TaskDetailRow(label: String, value: String, color: Color = Color(0xFF2E3A3A)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

fun getTimeLeft(dueDate: Date): String {
    val now = Date()
    val diff = dueDate.time - now.time

    return when {
        diff < 0 -> "Overdue"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes left"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours left"
        else -> "${diff / (24 * 60 * 60 * 1000)} days left"
    }
}

fun getDifficultyText(difficulty: Int): String {
    return when (difficulty) {
        1 -> "Very Easy"
        2 -> "Easy"
        3 -> "Medium"
        4 -> "Hard"
        5 -> "Very Hard"
        else -> "Medium"
    }
}

fun getSampleTasks(): List<Task> {
    return listOf(
        Task(
            id = "1",
            title = "Complete project proposal",
            description = "Write and submit the final project proposal for the new client",
            priority = TaskPriority.HIGH,
            category = "Work",
            estimatedPomodoros = 4,
            pomodoroSessions = 2,
            subtasks = listOf(
                Subtask("s1", "Research requirements", true),
                Subtask("s2", "Write proposal", false),
                Subtask("s3", "Review with team", false)
            ),
            attachments = listOf("https://docs.google.com/proposal"),
            isRecurring = false,
            dueDate = Date(System.currentTimeMillis() + 86400000),
            reminderMinutes = 30
        ),
        Task(
            id = "2",
            title = "Daily standup meeting",
            description = "Team daily standup",
            priority = TaskPriority.MEDIUM,
            category = "Meeting",
            estimatedPomodoros = 1,
            pomodoroSessions = 0,
            isRecurring = true,
            recurrenceType = RecurrenceType.DAILY,
            reminderMinutes = 15
        ),
        Task(
            id = "3",
            title = "Study Jetpack Compose",
            description = "Learn advanced Compose concepts",
            priority = TaskPriority.LOW,
            category = "Learning",
            estimatedPomodoros = 3,
            pomodoroSessions = 0,
            attachments = listOf("https://developer.android.com/jetpack/compose"),
            reminderMinutes = 60
        ),
        Task(
            id = "4",
            title = "Fix authentication bug",
            description = "Resolve the login issue",
            priority = TaskPriority.HIGH,
            category = "Bug Fix",
            estimatedPomodoros = 2,
            pomodoroSessions = 2,
            isCompleted = true,
            completedAt = Date(System.currentTimeMillis() - 86400000),
            reminderMinutes = 30
        )
    )
}