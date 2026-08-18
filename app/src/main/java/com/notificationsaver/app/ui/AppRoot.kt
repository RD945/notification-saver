package com.notificationsaver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.notificationsaver.app.NotificationSaverApp
import com.notificationsaver.app.ui.apps.AppsScreen
import com.notificationsaver.app.ui.home.HomeScreen
import com.notificationsaver.app.ui.logs.LogsScreen
import com.notificationsaver.app.ui.setup.SetupScreen
import com.notificationsaver.app.ui.telegram.TelegramScreen
import com.notificationsaver.app.ui.theme.AppleBackground
import com.notificationsaver.app.ui.theme.AppleBlue

private data class Dest(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    Dest("home", "Home", Icons.Outlined.Home),
    Dest("telegram", "Telegram", Icons.AutoMirrored.Outlined.Send),
    Dest("apps", "Apps", Icons.Outlined.Apps),
    Dest("logs", "Logs", Icons.Outlined.History),
)

@Composable
fun AppRoot() {
    val app = LocalContext.current.applicationContext as NotificationSaverApp
    val ready by app.container.settings.ready.collectAsStateWithLifecycle()
    val settings by app.container.settings.snapshot.collectAsStateWithLifecycle()

    if (!ready) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppleBackground),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = AppleBlue)
        }
        return
    }

    if (!settings.telegramConfigured) {
        SetupScreen()
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { dest ->
                    val selected = current?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    onOpenApps = {
                        navController.navigate("apps") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("telegram") { TelegramScreen() }
            composable("apps") { AppsScreen() }
            composable("logs") { LogsScreen() }
        }
    }
}
