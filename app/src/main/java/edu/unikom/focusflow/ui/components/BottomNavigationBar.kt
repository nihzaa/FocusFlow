package edu.unikom.focusflow.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import edu.unikom.focusflow.ui.theme.* // Import warna DarkGreen

data class BottomNavItem(
    val route: String,
    val iconFilled: ImageVector, // Add filled icon version
    val iconOutline: ImageVector, // Add outline icon version
    val label: String
)

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        BottomNavItem("home", Icons.Filled.Home, Icons.Outlined.Home, "Home"),
        BottomNavItem("pomodoro", Icons.Filled.Timer, Icons.Outlined.Timer, "Focus"),
        BottomNavItem("tasks", Icons.Filled.List, Icons.Outlined.List, "Tasks"),
        BottomNavItem("analytics", Icons.Filled.Analytics, Icons.Outlined.Analytics, "Stats")
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background, // No background color
        contentColor = MaterialTheme.colorScheme.onBackground // Text and icons color
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    val icon = if (currentRoute == item.route) item.iconFilled else item.iconOutline // Change icon based on active state
                    Icon(
                        icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(32.dp), // Larger icon size
                        tint = if (currentRoute == item.route) DarkGreen else Gray100 // DarkGreen for active, Gray for inactive
                    )
                },
                label = {
                    Text(
                        item.label,
                        color = if (currentRoute == item.route) DarkGreen else Gray100 // DarkGreen for active, Gray for inactive
                    )
                },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent, // Menghilangkan background aktif
                    selectedIconColor = DarkGreen, // Warna icon saat aktif
                    unselectedIconColor = Gray100, // Warna icon saat tidak aktif
                    selectedTextColor = DarkGreen, // Warna text saat aktif
                    unselectedTextColor = Gray100 // Warna text saat tidak aktif
                ),
                alwaysShowLabel = true // Always show label even when unselected
            )
        }
    }
}