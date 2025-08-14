package edu.unikom.focusflow.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.firebase.auth.FirebaseAuth
import edu.unikom.focusflow.ui.screens.*

@Composable
fun FocusFlowNavigation(navController: NavHostController) {
    val auth = FirebaseAuth.getInstance()

    // Observe auth state changes
    var isAuthenticated by remember { mutableStateOf(auth.currentUser != null) }

    DisposableEffect(Unit) {
        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            isAuthenticated = firebaseAuth.currentUser != null
        }
        auth.addAuthStateListener(authListener)

        onDispose {
            auth.removeAuthStateListener(authListener)
        }
    }

    // Start navigation based on login status
    val startDestination = if (isAuthenticated) "home" else "onboarding"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Authentication Screens (No auth required)
        composable("onboarding") {
            OnboardingScreen(navController = navController)
        }

        composable("login") {
            LoginScreen(navController = navController)
        }

        // Add auth route for compatibility
        composable("auth") {
            LoginScreen(navController = navController)
        }

        // Protected Screens (Auth required)
        composable("home") {
            // HomeScreen akan handle auth check sendiri
            HomeScreen(navController = navController)
        }

        composable("pomodoro") {
            if (isAuthenticated) {
                PomodoroScreen(navController = navController)
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        composable("tasks") {
            if (isAuthenticated) {
                TasksScreen(navController = navController)
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        composable("analytics") {
            if (isAuthenticated) {
                AnalyticsScreen(navController = navController)
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        composable("settings") {
            if (isAuthenticated) {
                SettingsScreen(navController = navController)
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        composable("profile") {
            if (isAuthenticated) {
                ProfileScreen(navController = navController)
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        // Additional Screens with Parameters
        composable("task_detail/{taskId}") { backStackEntry ->
            if (isAuthenticated) {
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                // TaskDetailScreen(navController = navController, taskId = taskId)
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        composable("edit_profile") {
            if (isAuthenticated) {
                // EditProfileScreen(navController = navController)
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        composable("help") {
            // Help screen bisa diakses tanpa login
            // HelpScreen(navController = navController)
        }

        composable("about") {
            // About screen bisa diakses tanpa login
            // AboutScreen(navController = navController)
        }

        composable("notifications") {
            if (isAuthenticated) {
                // NotificationsScreen(navController = navController)
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        composable("backup") {
            if (isAuthenticated) {
                // BackupScreen(navController = navController)
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

    }
}