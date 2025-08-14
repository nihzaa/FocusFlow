package edu.unikom.focusflow.ui.screens

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import edu.unikom.focusflow.data.models.*
import edu.unikom.focusflow.data.repository.FirebaseRepository
import edu.unikom.focusflow.viewmodel.PomodoroViewModel
import java.text.SimpleDateFormat
import java.util.*
import edu.unikom.focusflow.ui.components.BottomNavigationBar
import edu.unikom.focusflow.ui.theme.DarkGreen
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.*
import edu.unikom.focusflow.data.models.Task
import edu.unikom.focusflow.data.models.TaskPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONArray
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.foundation.layout.wrapContentHeight
import com.google.android.datatransport.BuildConfig
import android.media.AudioAttributes
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarDuration

enum class TimerState {
    STOPPED, RUNNING, PAUSED
}

enum class AmbientSound(val displayName: String, val fileName: String) {
    NONE("None", ""),
    RAIN("Rain", "rain.mp3"),
    WHITE_NOISE("White Noise", "white_noise.mp3"),
    FOREST("Forest", "forest.mp3"),
    LIBRARY("Library", "library.mp3"),
    OCEAN("Ocean Waves", "ocean.mp3")
}

data class Quote(
    val text: String = "",
    val author: String = ""
)

// Singleton untuk menyimpan state Pomodoro
object PomodoroStateManager {
    var currentSession: SessionType = SessionType.WORK
    var timerState: TimerState = TimerState.STOPPED
    var timeLeftInSeconds: Int = 25 * 60 // Default 25 menit
    var totalTimeInSeconds: Int = 25 * 60
    var completedSessions: Int = 0
    var selectedTaskId: String? = null
    var selectedAmbientSound: AmbientSound = AmbientSound.RAIN
    var autoPlayAmbient: Boolean = true
    var ambientVolume: Float = 1.0f
    var isInitialized: Boolean = false

    fun resetTimer(workDuration: Int) {
        timeLeftInSeconds = workDuration * 60
        totalTimeInSeconds = workDuration * 60
        timerState = TimerState.STOPPED
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    navController: NavController
) {
    // Manual ViewModel creation
    val repository = remember { FirebaseRepository() }
    val viewModel = remember { PomodoroViewModel(repository) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Timer state dari StateManager
    var currentSession by remember { mutableStateOf(PomodoroStateManager.currentSession) }
    var timerState by remember { mutableStateOf(PomodoroStateManager.timerState) }
    var timeLeftInSeconds by remember { mutableIntStateOf(PomodoroStateManager.timeLeftInSeconds) }
    var totalTimeInSeconds by remember { mutableIntStateOf(PomodoroStateManager.totalTimeInSeconds) }
    var completedSessions by remember { mutableIntStateOf(PomodoroStateManager.completedSessions) }

    // UI state
    var showTaskSelector by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBreakReminder by remember { mutableStateOf(false) }
    var showFocusSettings by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // Focus Mode & Ambient Sound dari StateManager
    var selectedAmbientSound by remember { mutableStateOf(PomodoroStateManager.selectedAmbientSound) }
    var autoPlayAmbient by remember { mutableStateOf(PomodoroStateManager.autoPlayAmbient) }
    var ambientVolume by remember { mutableFloatStateOf(PomodoroStateManager.ambientVolume) }
    var isPlayingAmbient by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // ViewModel states
    val tasks by viewModel.tasks.collectAsState()
    val selectedTask by viewModel.selectedTask.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    var currentQuote by remember { mutableStateOf<Quote?>(null) }
    var showQuoteDialog by remember { mutableStateOf(false) }
    var isFetchingQuote by remember { mutableStateOf(false) }  // <- TAMBAHKAN INI

    // Simpan state ke StateManager saat berubah
    LaunchedEffect(currentSession, timerState, timeLeftInSeconds, totalTimeInSeconds, completedSessions, selectedTask, selectedAmbientSound, autoPlayAmbient, ambientVolume) {
        PomodoroStateManager.currentSession = currentSession
        PomodoroStateManager.timerState = timerState
        PomodoroStateManager.timeLeftInSeconds = timeLeftInSeconds
        PomodoroStateManager.totalTimeInSeconds = totalTimeInSeconds
        PomodoroStateManager.completedSessions = completedSessions
        PomodoroStateManager.selectedTaskId = selectedTask?.id
        PomodoroStateManager.selectedAmbientSound = selectedAmbientSound
        PomodoroStateManager.autoPlayAmbient = autoPlayAmbient
        PomodoroStateManager.ambientVolume = ambientVolume
    }

    // Initialize ambient sound
    LaunchedEffect(selectedAmbientSound) {
        val wasPlaying = isPlayingAmbient

        // Clean up previous player
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
                Log.d("MediaPlayer", "Released previous player")
            } catch (e: Exception) {
                Log.e("MediaPlayer", "Error releasing player: ${e.message}")
            }
        }
        mediaPlayer = null
        isPlayingAmbient = false

        if (selectedAmbientSound != AmbientSound.NONE && selectedAmbientSound.fileName.isNotEmpty()) {
            try {
                Log.d("MediaPlayer", "Attempting to load: ${selectedAmbientSound.fileName}")

                // Create new MediaPlayer
                val player = MediaPlayer().apply {
                    // Set audio attributes BEFORE setDataSource
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )

                    // Open and set data source
                    val afd = context.assets.openFd(selectedAmbientSound.fileName)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()

                    // Set properties
                    isLooping = true
                    setVolume(ambientVolume, ambientVolume)

                    // Prepare synchronously for testing
                    prepare()

                    Log.d("MediaPlayer", "MediaPlayer prepared successfully for ${selectedAmbientSound.fileName}")
                }

                mediaPlayer = player

                // Auto-play if needed
                if (wasPlaying || (timerState == TimerState.RUNNING && autoPlayAmbient)) {
                    player.start()
                    isPlayingAmbient = true
                    Log.d("MediaPlayer", "Started playing: ${selectedAmbientSound.fileName}")
                }

            } catch (e: Exception) {
                Log.e("MediaPlayer", "Failed to initialize: ${e.message}")
                e.printStackTrace()

                // Show error to user
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        "Failed to load audio: ${selectedAmbientSound.displayName}",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

// 2. Fix untuk Volume control LaunchedEffect
    LaunchedEffect(ambientVolume) {
        try {
            mediaPlayer?.setVolume(ambientVolume, ambientVolume)
            Log.d("MediaPlayer", "Volume set to: ${(ambientVolume * 100).toInt()}%")
        } catch (e: Exception) {
            Log.e("MediaPlayer", "Error setting volume: ${e.message}")
        }
    }

    // Load initial data dan restore state
    LaunchedEffect(Unit) {
        viewModel.loadInitialData()

        // Restore selected task jika ada
        if (PomodoroStateManager.selectedTaskId != null && !PomodoroStateManager.isInitialized) {
            val task = tasks.find { it.id == PomodoroStateManager.selectedTaskId }
            task?.let { viewModel.selectTask(it) }
        }

        // Set default timer jika belum diinisialisasi
        if (!PomodoroStateManager.isInitialized) {
            userPreferences?.let { prefs ->
                if (timeLeftInSeconds == 0 || !PomodoroStateManager.isInitialized) {
                    timeLeftInSeconds = prefs.workDuration * 60
                    totalTimeInSeconds = timeLeftInSeconds
                    PomodoroStateManager.timeLeftInSeconds = timeLeftInSeconds
                    PomodoroStateManager.totalTimeInSeconds = totalTimeInSeconds
                }
            }
            PomodoroStateManager.isInitialized = true
        }

        isLoading = false
    }

    // Lifecycle observer untuk pause saat app ke background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Simpan state saat pause
                    if (timerState == TimerState.RUNNING) {
                        coroutineScope.launch {
                            viewModel.savePomodoroSession(
                                sessionType = currentSession,
                                task = selectedTask,
                                secondsCompleted = totalTimeInSeconds - timeLeftInSeconds,
                                isCompleted = false
                            )
                        }
                    }
                    mediaPlayer?.pause()
                    isPlayingAmbient = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Resume ambient sound jika sebelumnya playing
                    if (timerState == TimerState.RUNNING && autoPlayAmbient && selectedAmbientSound != AmbientSound.NONE) {
                        mediaPlayer?.start()
                        isPlayingAmbient = true
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Timer logic dengan auto-save
    LaunchedEffect(timerState) {
        if (timerState == TimerState.RUNNING) {
            if (autoPlayAmbient && selectedAmbientSound != AmbientSound.NONE && !isPlayingAmbient) {
                try {
                    mediaPlayer?.start()
                    isPlayingAmbient = true
                } catch (e: Exception) {
                    Log.e("MediaPlayer", "Failed to auto-start: ${e.message}")
                }
            }

            while (timeLeftInSeconds > 0 && timerState == TimerState.RUNNING) {
                delay(1000L)
                timeLeftInSeconds--

                // Auto-save setiap 30 detik
                if (timeLeftInSeconds % 30 == 0) {
                    coroutineScope.launch {
                        viewModel.savePomodoroSession(
                            sessionType = currentSession,
                            task = selectedTask,
                            secondsCompleted = totalTimeInSeconds - timeLeftInSeconds,
                            isCompleted = false
                        )
                    }
                }
            }

            if (timeLeftInSeconds == 0) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                // Pause audio when timer completes
                try {
                    if (isPlayingAmbient && mediaPlayer != null) {
                        mediaPlayer?.pause()
                        isPlayingAmbient = false
                        Log.d("MediaPlayer", "Paused audio on timer completion")
                    }
                } catch (e: Exception) {
                    Log.e("MediaPlayer", "Error pausing on completion: ${e.message}")
                }

                coroutineScope.launch {
                    // Save session ke Firebase
                    viewModel.completeSession(currentSession, selectedTask)

                    // Fetch motivational quote setelah work session selesai
                    if (currentSession == SessionType.WORK) {
                        completedSessions++

                        // Fetch quote dari API
                        val quote = fetchMotivationalQuote()
                        currentQuote = quote
                        showQuoteDialog = true

                        if (completedSessions % 4 == 0) {
                            showBreakReminder = true
                        }
                    }

                    val sessionName = when (currentSession) {
                        SessionType.WORK -> "Focus session"
                        SessionType.SHORT_BREAK -> "Short break"
                        SessionType.LONG_BREAK -> "Long break"
                    }
                    snackbarHostState.showSnackbar("$sessionName completed! 🎉")

                    // Switch ke session berikutnya
                    currentSession = when (currentSession) {
                        SessionType.WORK -> {
                            if (completedSessions % 4 == 0) SessionType.LONG_BREAK
                            else SessionType.SHORT_BREAK
                        }
                        SessionType.SHORT_BREAK, SessionType.LONG_BREAK -> SessionType.WORK
                    }

                    // Reset timer
                    userPreferences?.let { prefs ->
                        timeLeftInSeconds = when (currentSession) {
                            SessionType.WORK -> prefs.workDuration * 60
                            SessionType.SHORT_BREAK -> prefs.shortBreakDuration * 60
                            SessionType.LONG_BREAK -> prefs.longBreakDuration * 60
                        }
                        totalTimeInSeconds = timeLeftInSeconds
                    }

                    timerState = TimerState.STOPPED

                    // Auto-start jika diaktifkan
                    userPreferences?.let { prefs ->
                        if ((currentSession != SessionType.WORK && prefs.autoStartBreaks) ||
                            (currentSession == SessionType.WORK && prefs.autoStartPomodoros)) {
                            delay(2000)
                            timerState = TimerState.RUNNING
                        }
                    }
                }
            }
        } else if (timerState == TimerState.PAUSED) {
            // Save saat pause
            coroutineScope.launch {
                viewModel.savePomodoroSession(
                    sessionType = currentSession,
                    task = selectedTask,
                    secondsCompleted = totalTimeInSeconds - timeLeftInSeconds,
                    isCompleted = false
                )
            }

            if (isPlayingAmbient) {
                mediaPlayer?.pause()
                isPlayingAmbient = false
            }
        } else {
            if (isPlayingAmbient) {
                mediaPlayer?.pause()
                isPlayingAmbient = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                    Log.d("MediaPlayer", "Released MediaPlayer on dispose")
                }
            } catch (e: Exception) {
                Log.e("MediaPlayer", "Error during dispose: ${e.message}")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { BottomNavigationBar(navController = navController) },
        containerColor = Color(0xFFF8F9FA)
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF4A6741),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Clean Header without gradient
                ModernCleanHeader(
                    onTaskSelectorClick = { showTaskSelector = true },
                    onSettingsClick = { showSettings = true },
                    onFocusModeClick = { showFocusSettings = true }
                )

                // Main content with proper spacing
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // Selected Task Display dengan data lengkap
                    selectedTask?.let { task ->
                        MinimalistTaskCard(
                            task = task,
                            onClearTask = {
                                viewModel.clearSelectedTask()
                                PomodoroStateManager.selectedTaskId = null
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Modern Session Progress
                    ModernSessionProgress(
                        currentSession = currentSession,
                        completedSessions = completedSessions
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Enhanced Timer Circle
                    ModernTimerCircle(
                        timeLeft = timeLeftInSeconds,
                        totalTime = totalTimeInSeconds,
                        currentSession = currentSession,
                        isRunning = timerState == TimerState.RUNNING
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Session Status
                    ModernSessionStatus(
                        currentSession = currentSession,
                        selectedTask = selectedTask,
                        timerState = timerState
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    // Control Buttons
                    ModernControlButtons(
                        timerState = timerState,
                        onStartPause = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            timerState = when (timerState) {
                                TimerState.STOPPED, TimerState.PAUSED -> {
                                    if (timeLeftInSeconds == 0) {
                                        userPreferences?.let { prefs ->
                                            timeLeftInSeconds = when (currentSession) {
                                                SessionType.WORK -> prefs.workDuration * 60
                                                SessionType.SHORT_BREAK -> prefs.shortBreakDuration * 60
                                                SessionType.LONG_BREAK -> prefs.longBreakDuration * 60
                                            }
                                        }
                                    }
                                    TimerState.RUNNING
                                }
                                TimerState.RUNNING -> TimerState.PAUSED
                            }
                        },
                        onReset = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            timerState = TimerState.STOPPED
                            userPreferences?.let { prefs ->
                                timeLeftInSeconds = when (currentSession) {
                                    SessionType.WORK -> prefs.workDuration * 60
                                    SessionType.SHORT_BREAK -> prefs.shortBreakDuration * 60
                                    SessionType.LONG_BREAK -> prefs.longBreakDuration * 60
                                }
                                totalTimeInSeconds = timeLeftInSeconds
                            }
                        },
                        onSkip = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch {
                                viewModel.skipSession(currentSession, selectedTask)
                                timeLeftInSeconds = 0
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Modern Stats Grid
                    ModernStatsGrid(
                        completedSessions = completedSessions,
                        focusTimeToday = completedSessions * (userPreferences?.workDuration ?: 25),
                        isPlayingAmbient = isPlayingAmbient,
                        ambientSound = selectedAmbientSound,
                        onToggleAmbient = {
                            Log.d("MediaPlayer", "Toggle ambient clicked. Current state: playing=$isPlayingAmbient, sound=${selectedAmbientSound.displayName}")

                            if (selectedAmbientSound == AmbientSound.NONE) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Please select an ambient sound first")
                                }
                                return@ModernStatsGrid
                            }

                            if (isPlayingAmbient) {
                                // Pause
                                try {
                                    mediaPlayer?.pause()
                                    isPlayingAmbient = false
                                    Log.d("MediaPlayer", "Paused successfully")
                                } catch (e: Exception) {
                                    Log.e("MediaPlayer", "Error pausing: ${e.message}")
                                }
                            } else {
                                // Play
                                try {
                                    if (mediaPlayer == null) {
                                        Log.d("MediaPlayer", "MediaPlayer is null, triggering reinitialization")
                                        // Force reinitialize by changing the value
                                        val current = selectedAmbientSound
                                        selectedAmbientSound = AmbientSound.NONE
                                        selectedAmbientSound = current
                                    } else {
                                        mediaPlayer?.start()
                                        isPlayingAmbient = true
                                        Log.d("MediaPlayer", "Started successfully")
                                    }
                                } catch (e: Exception) {
                                    Log.e("MediaPlayer", "Error starting: ${e.message}")
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Error playing audio: ${e.message}")
                                    }
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Dialogs
    if (showTaskSelector) {
        ModernTaskSelectorDialog(
            tasks = tasks,
            selectedTask = selectedTask,
            onTaskSelected = { task ->
                if (task != null) {
                    viewModel.selectTask(task)
                    PomodoroStateManager.selectedTaskId = task.id
                } else {
                    viewModel.clearSelectedTask()
                    PomodoroStateManager.selectedTaskId = null
                }
                showTaskSelector = false
            },
            onDismiss = { showTaskSelector = false }
        )
    }

    if (showSettings) {
        ModernSettingsDialog(
            workDuration = userPreferences?.workDuration ?: 25,
            shortBreakDuration = userPreferences?.shortBreakDuration ?: 5,
            longBreakDuration = userPreferences?.longBreakDuration ?: 15,
            autoStartBreaks = userPreferences?.autoStartBreaks ?: false,
            autoStartPomodoros = userPreferences?.autoStartPomodoros ?: false,
            onWorkDurationChange = { duration -> viewModel.updateWorkDuration(duration) },
            onShortBreakDurationChange = { duration -> viewModel.updateShortBreakDuration(duration) },
            onLongBreakDurationChange = { duration -> viewModel.updateLongBreakDuration(duration) },
            onAutoStartBreaksChange = { enabled -> viewModel.updateAutoStartBreaks(enabled) },
            onAutoStartPomodorosChange = { enabled -> viewModel.updateAutoStartPomodoros(enabled) },
            onDismiss = { showSettings = false },
            onApply = {
                showSettings = false
                if (timerState == TimerState.STOPPED) {
                    userPreferences?.let { prefs ->
                        timeLeftInSeconds = when (currentSession) {
                            SessionType.WORK -> prefs.workDuration * 60
                            SessionType.SHORT_BREAK -> prefs.shortBreakDuration * 60
                            SessionType.LONG_BREAK -> prefs.longBreakDuration * 60
                        }
                        totalTimeInSeconds = timeLeftInSeconds
                    }
                }
            }
        )
    }

    if (showFocusSettings) {
        ModernFocusModeDialog(
            selectedAmbientSound = selectedAmbientSound,
            autoPlayAmbient = autoPlayAmbient,
            ambientVolume = ambientVolume,
            onAmbientSoundChange = { sound -> selectedAmbientSound = sound },
            onAutoPlayChange = { enabled -> autoPlayAmbient = enabled },
            onVolumeChange = { volume -> ambientVolume = volume },
            onDismiss = { showFocusSettings = false }
        )
    }

    if (showBreakReminder) {
        ModernBreakReminderDialog(
            completedSessions = completedSessions,
            onStartBreak = {
                showBreakReminder = false
                currentSession = SessionType.LONG_BREAK
                userPreferences?.let { prefs ->
                    timeLeftInSeconds = prefs.longBreakDuration * 60
                    totalTimeInSeconds = timeLeftInSeconds
                }
                timerState = TimerState.RUNNING
            },
            onContinueWorking = {
                showBreakReminder = false
                currentSession = SessionType.WORK
                userPreferences?.let { prefs ->
                    timeLeftInSeconds = prefs.workDuration * 60
                    totalTimeInSeconds = timeLeftInSeconds
                }
            },
            onDismiss = { showBreakReminder = false }
        )
    }

    if (showQuoteDialog && currentQuote != null) {
        MotivationalQuoteDialog(
            quote = currentQuote,
            onDismiss = {
                showQuoteDialog = false
            },
            onStartNextSession = {
                showQuoteDialog = false
                // Start break timer automatically
                timerState = TimerState.RUNNING
            }
        )
    }
}

suspend fun fetchMotivationalQuote(): Quote? {
    return try {
        withContext(Dispatchers.IO) {
            val url = URL("https://zenquotes.io/api/random")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                // Parse JSON response
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val quoteObject = jsonArray.getJSONObject(0)
                    Quote(
                        text = quoteObject.getString("q"),
                        author = quoteObject.getString("a")
                    )
                } else null
            } else null
        }
    } catch (e: Exception) {
        Log.e("ZenQuotes", "Failed to fetch quote: ${e.message}")
        // Fallback quotes jika API gagal
        Quote(
            text = "The secret of getting ahead is getting started.",
            author = "Mark Twain"
        )
    }
}

@Composable
fun ModernCleanHeader(
    onTaskSelectorClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFocusModeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "FOCUS FLOW",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4A6741),
                letterSpacing = 1.sp
            )
            Text(
                text = "Stay focused, stay productive",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8E8E93)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModernCleanHeaderButton(
                icon = Icons.Outlined.MusicNote,
                onClick = onFocusModeClick,
                color = Color(0xFF2196F3)
            )
            ModernCleanHeaderButton(
                icon = Icons.Outlined.Assignment,
                onClick = onTaskSelectorClick,
                color = Color(0xFF4A6741)
            )
            ModernCleanHeaderButton(
                icon = Icons.Outlined.Settings,
                onClick = onSettingsClick,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

@Composable
fun ModernCleanHeaderButton(
    icon: ImageVector,
    onClick: () -> Unit,
    color: Color
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.size(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// Enhanced Task Card dengan data lengkap
@Composable
fun MinimalistTaskCard(
    task: Task,
    onClearTask: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(task.priority.color)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Task title
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E3A3A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Task description jika ada
                if (task.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Category badge
                    if (task.category.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF8E8E93).copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = task.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8E8E93),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Progress indicator
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = task.priority.color.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "${task.pomodoroSessions}/${task.estimatedPomodoros}",
                            style = MaterialTheme.typography.bodySmall,
                            color = task.priority.color,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Due date jika ada
                    task.dueDate?.let { due ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFF9800).copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(due),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF9800),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Close button
            Surface(
                onClick = onClearTask,
                shape = CircleShape,
                color = Color(0xFF8E8E93).copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear Task",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ModernSessionProgress(
    currentSession: SessionType,
    completedSessions: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Sessions completed today",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { index ->
                val isCompleted = index < completedSessions % 4
                val isActive = index == completedSessions % 4 && currentSession == SessionType.WORK

                val animatedSize by animateFloatAsState(
                    targetValue = if (isActive) 1.2f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "session_indicator"
                )

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer(scaleX = animatedSize, scaleY = animatedSize)
                        .clip(CircleShape)
                        .background(
                            when {
                                isActive -> Color(0xFF4A6741)
                                isCompleted -> Color(0xFF4A6741).copy(alpha = 0.6f)
                                else -> Color(0xFF8E8E93).copy(alpha = 0.2f)
                            }
                        )
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernTimerCircle(
    timeLeft: Int,
    totalTime: Int,
    currentSession: SessionType,
    isRunning: Boolean
) {
    val progress = if (totalTime > 0) (totalTime - timeLeft).toFloat() / totalTime else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000),
        label = "progress"
    )

    val sessionColor = when (currentSession) {
        SessionType.WORK -> Color(0xFF4A6741)
        SessionType.SHORT_BREAK -> Color(0xFF2196F3)
        SessionType.LONG_BREAK -> Color(0xFF9C27B0)
    }

    val pulseScale by animateFloatAsState(
        targetValue = if (isRunning) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(280.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
        ) {
            // Background circle
            drawCircle(
                color = Color(0xFF8E8E93).copy(alpha = 0.1f),
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )

            // Progress circle
            drawArc(
                color = sessionColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatTime(timeLeft),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = sessionColor,
                fontSize = 48.sp
            )
            Text(
                text = "minutes remaining",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

@Composable
fun ModernSessionStatus(
    currentSession: SessionType,
    selectedTask: Task?,
    timerState: TimerState
) {
    val statusText = when (currentSession) {
        SessionType.WORK -> {
            if (selectedTask != null) "Focus on: ${selectedTask.title}"
            else "Free Focus Session"
        }
        SessionType.SHORT_BREAK -> "Take a Short Break"
        SessionType.LONG_BREAK -> "Time for a Long Break"
    }

    val subText = when (timerState) {
        TimerState.RUNNING -> "Stay focused and avoid distractions"
        TimerState.PAUSED -> "Timer is paused - tap start to continue"
        TimerState.STOPPED -> "Ready to start your focus session"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = when (currentSession) {
                SessionType.WORK -> Color(0xFF4A6741)
                SessionType.SHORT_BREAK -> Color(0xFF2196F3)
                SessionType.LONG_BREAK -> Color(0xFF9C27B0)
            },
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Text(
            text = subText,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun ModernControlButtons(
    timerState: TimerState,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reset button
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.size(width = 80.dp, height = 56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF8E8E93)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = Color(0xFF8E8E93).copy(alpha = 0.3f)
            )
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Reset",
                modifier = Modifier.size(20.dp)
            )
        }

        // Main Play/Pause button
        Button(
            onClick = onStartPause,
            modifier = Modifier.size(width = 160.dp, height = 64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4A6741),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    when (timerState) {
                        TimerState.RUNNING -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when (timerState) {
                        TimerState.RUNNING -> "Pause"
                        else -> "Start"
                    },
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    when (timerState) {
                        TimerState.RUNNING -> "Pause"
                        else -> "Start"
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }

        // Skip button
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.size(width = 80.dp, height = 56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF8E8E93)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = Color(0xFF8E8E93).copy(alpha = 0.3f)
            )
        ) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Skip",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ModernStatsGrid(
    completedSessions: Int,
    focusTimeToday: Int,
    isPlayingAmbient: Boolean,
    ambientSound: AmbientSound,
    onToggleAmbient: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sessions card
        ModernStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Timer,
            value = "$completedSessions",
            label = "Sessions",
            color = Color(0xFF4A6741)
        )

        // Focus time card
        ModernStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Schedule,
            value = "${focusTimeToday}m",
            label = "Focus Time",
            color = Color(0xFF2196F3)
        )

        // Ambient sound card
        ModernAmbientCard(
            modifier = Modifier.weight(1f),
            isPlayingAmbient = isPlayingAmbient,
            ambientSound = ambientSound,
            onToggleAmbient = onToggleAmbient
        )
    }
}

@Composable
fun ModernAmbientCard(
    modifier: Modifier = Modifier,
    isPlayingAmbient: Boolean,
    ambientSound: AmbientSound,
    onToggleAmbient: () -> Unit
) {
    Surface(
        onClick = onToggleAmbient,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (isPlayingAmbient) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = null,
                tint = if (isPlayingAmbient) Color(0xFF9C27B0) else Color(0xFF8E8E93),
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = if (ambientSound != AmbientSound.NONE) ambientSound.displayName else "None",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isPlayingAmbient) Color(0xFF9C27B0) else Color(0xFF8E8E93),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isPlayingAmbient) "Playing" else "Sound",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

@Composable
fun ModernStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

@Composable
fun ModernTaskSelectorDialog(
    tasks: List<Task>,
    selectedTask: Task?,
    onTaskSelected: (Task?) -> Unit,
    onDismiss: () -> Unit
) {
    // Animation states
    val animatedAlpha = remember { Animatable(0f) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        animatedAlpha.animateTo(1f, tween(300))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f)
                .alpha(animatedAlpha.value),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Enhanced Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF4A6741),
                                    Color(0xFF5E7A55)
                                )
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Select Focus Task",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "${tasks.count { !it.isCompleted }} active tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        // Close button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Search Bar - Always show for better UX
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    placeholder = {
                        Text("Search tasks...", color = Color.Gray)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color.Gray
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A6741),
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                    ),
                    singleLine = true
                )

                // Task List with filtering
                val activeTasks = tasks.filter { !it.isCompleted }
                val filteredTasks = if (searchQuery.isEmpty()) {
                    activeTasks
                } else {
                    activeTasks.filter { task ->
                        task.title.contains(searchQuery, ignoreCase = true) ||
                                task.description?.contains(searchQuery, ignoreCase = true) == true
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // No Task Option
                    item {
                        EnhancedTaskSelectorItem(
                            task = null,
                            isSelected = selectedTask == null,
                            onClick = {
                                onTaskSelected(null)
                                onDismiss()
                            }
                        )
                    }

                    // Divider
                    if (filteredTasks.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = Color.Gray.copy(alpha = 0.2f)
                                )
                                Text(
                                    "  Available Tasks  ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = Color.Gray.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }

                    // Filtered Tasks or Empty State
                    if (filteredTasks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        if (searchQuery.isNotEmpty()) Icons.Default.SearchOff
                                        else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = Color.Gray.copy(alpha = 0.3f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        if (searchQuery.isNotEmpty())
                                            "No tasks found for \"$searchQuery\""
                                        else
                                            "All tasks completed!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredTasks) { task ->
                            EnhancedTaskSelectorItem(
                                task = task,
                                isSelected = selectedTask?.id == task.id,
                                onClick = {
                                    onTaskSelected(task)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                // Bottom Actions
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Gray.copy(alpha = 0.05f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Selected task info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Selected:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                            Text(
                                selectedTask?.title ?: "No specific task",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Action button
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4A6741)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp
                            )
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Confirm",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedTaskSelectorItem(
    task: Task?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale.value)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isSelected -> Color(0xFF4A6741)
                task == null -> Color.Gray.copy(alpha = 0.3f)
                else -> Color.Gray.copy(alpha = 0.2f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 3.dp else 1.dp
        )
    ) {
        LaunchedEffect(isSelected) {
            if (isSelected) {
                scale.animateTo(0.98f, tween(100))
                scale.animateTo(1f, tween(100))
            }
        }

        if (task == null) {
            // No specific task option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.AllInclusive,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF4A6741) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    "No specific task",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF4A6741) else Color.Black
                )

                Spacer(modifier = Modifier.weight(1f))

                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4A6741),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            // Task item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Project dot indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            getTaskPriorityColor(task.priority),
                            CircleShape
                        )
                )

                // Task title
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF4A6741) else Color.Black,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Tags/badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Priority badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when(task.priority) {
                            TaskPriority.HIGH -> Color(0xFFFFEBEE)
                            TaskPriority.MEDIUM -> Color(0xFFFFF3E0)
                            TaskPriority.LOW -> Color(0xFFE8F5E9)
                        }
                    ) {
                        Text(
                            when(task.priority) {
                                TaskPriority.HIGH -> "High"
                                TaskPriority.MEDIUM -> "Med"
                                TaskPriority.LOW -> "Low"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = getTaskPriorityColor(task.priority),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Due date badge if today or overdue
                    task.dueDate?.let { date ->
                        val today = Calendar.getInstance()
                        val dueCalendar = Calendar.getInstance().apply { time = date }

                        val isToday = today.get(Calendar.DAY_OF_YEAR) == dueCalendar.get(Calendar.DAY_OF_YEAR) &&
                                today.get(Calendar.YEAR) == dueCalendar.get(Calendar.YEAR)
                        val isOverdue = date.before(Date()) && !isToday

                        if (isToday || isOverdue) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isOverdue) Color(0xFFFFEBEE) else Color(0xFFFFF8E1)
                            ) {
                                Text(
                                    if (isOverdue) "Overdue" else "Today",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOverdue) Color(0xFFD32F2F) else Color(0xFFF57C00),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Text(
                                SimpleDateFormat("MMM d", Locale.getDefault()).format(date),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // Selection indicator
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4A6741),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// Helper function untuk TaskPriority enum
fun getTaskPriorityColor(priority: TaskPriority): Color {
    return when (priority) {
        TaskPriority.HIGH -> Color(0xFFE53935)
        TaskPriority.MEDIUM -> Color(0xFFFB8C00)
        TaskPriority.LOW -> Color(0xFF43A047)
    }
}

@Composable
fun MotivationalQuoteDialog(
    quote: Quote?,
    onDismiss: () -> Unit,
    onStartNextSession: () -> Unit
) {
    if (quote != null) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header with icon
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF4A6741).copy(alpha = 0.1f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = Color(0xFF4A6741),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Text(
                        "Great Work! 🎉",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4A6741)
                    )

                    // Quote content
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF5F5F5),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "\"${quote.text}\"",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                color = Color(0xFF2E3A3A),
                                lineHeight = 24.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "— ${quote.author}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFF8E8E93)
                            )
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF8E8E93)
                            )
                        ) {
                            Text("Close")
                        }

                        Button(
                            onClick = {
                                onStartNextSession()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4A6741)
                            )
                        ) {
                            Text("Start Break")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernSettingsDialog(
    workDuration: Int,
    shortBreakDuration: Int,
    longBreakDuration: Int,
    autoStartBreaks: Boolean,
    autoStartPomodoros: Boolean,
    onWorkDurationChange: (Int) -> Unit,
    onShortBreakDurationChange: (Int) -> Unit,
    onLongBreakDurationChange: (Int) -> Unit,
    onAutoStartBreaksChange: (Boolean) -> Unit,
    onAutoStartPomodorosChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Timer Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Column {
                        Text("Work Duration: $workDuration minutes")
                        Slider(
                            value = workDuration.toFloat(),
                            onValueChange = { onWorkDurationChange(it.toInt()) },
                            valueRange = 15f..60f,
                            colors = SliderDefaults.colors(
                                thumbColor = DarkGreen,
                                activeTrackColor = DarkGreen
                            )
                        )
                    }
                }

                item {
                    Column {
                        Text("Short Break: $shortBreakDuration minutes")
                        Slider(
                            value = shortBreakDuration.toFloat(),
                            onValueChange = { onShortBreakDurationChange(it.toInt()) },
                            valueRange = 3f..15f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2196F3),
                                activeTrackColor = Color(0xFF2196F3)
                            )
                        )
                    }
                }

                item {
                    Column {
                        Text("Long Break: $longBreakDuration minutes")
                        Slider(
                            value = longBreakDuration.toFloat(),
                            onValueChange = { onLongBreakDurationChange(it.toInt()) },
                            valueRange = 15f..30f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF9C27B0),
                                activeTrackColor = Color(0xFF9C27B0)
                            )
                        )
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF8E8E93).copy(alpha = 0.1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Auto-start breaks")
                                Switch(
                                    checked = autoStartBreaks,
                                    onCheckedChange = onAutoStartBreaksChange,
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4A6741))
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Auto-start work sessions")
                                Switch(
                                    checked = autoStartPomodoros,
                                    onCheckedChange = onAutoStartPomodorosChange,
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4A6741))
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A6741)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8E8E93))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun ModernFocusModeDialog(
    selectedAmbientSound: AmbientSound,
    autoPlayAmbient: Boolean,
    ambientVolume: Float,
    onAmbientSoundChange: (AmbientSound) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Focus Mode Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(350.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Ambient Sound", fontWeight = FontWeight.Medium)
                    AmbientSound.values().forEach { sound ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAmbientSound == sound,
                                onClick = { onAmbientSoundChange(sound) },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2196F3))
                            )
                            Text(sound.displayName, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-play when starting timer")
                        Switch(
                            checked = autoPlayAmbient,
                            onCheckedChange = onAutoPlayChange,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF2196F3))
                        )
                    }
                }

                item {
                    Text("Volume: ${(ambientVolume * 100).toInt()}%")
                    Slider(
                        value = ambientVolume,
                        onValueChange = onVolumeChange,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF2196F3),
                            activeTrackColor = Color(0xFF2196F3)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun ModernBreakReminderDialog(
    completedSessions: Int,
    onStartBreak: () -> Unit,
    onContinueWorking: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "🎉 Great Work!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "You've completed $completedSessions focus sessions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8E8E93),
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Time for a well-deserved break! Taking regular breaks helps maintain focus and productivity.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onStartBreak,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Long Break")
            }
        },
        dismissButton = {
            TextButton(onClick = onContinueWorking) {
                Text("Continue Working", color = Color(0xFF8E8E93))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}



fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}