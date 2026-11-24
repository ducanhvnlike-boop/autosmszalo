package com.autosend.scheduler

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.autosend.scheduler.ui.LogsScreen
import com.autosend.scheduler.ui.MainViewModel
import com.autosend.scheduler.ui.SchedulesScreen
import com.autosend.scheduler.ui.SetupScreen
import com.autosend.scheduler.ui.TaskEditorScreen
import com.autosend.scheduler.ui.theme.AutoSendZaloTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoSendZaloTheme {
                AutoSendRootApp()
            }
        }
    }
}

@Composable
private fun AutoSendRootApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val tasks by viewModel.tasks.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val savedGroups by viewModel.savedGroupNames.collectAsState()

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "schedules"
    val showBottomBar = currentRoute in setOf("schedules", "logs", "setup")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "schedules",
                        onClick = {
                            navController.navigate("schedules") {
                                popUpTo("schedules") { inclusive = true }
                            }
                        },
                        icon = { Icon(Icons.Default.Schedule, contentDescription = "Lịch gửi") },
                        label = { Text("Lịch gửi") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "logs",
                        onClick = {
                            navController.navigate("logs") {
                                popUpTo("schedules")
                            }
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = "Lịch sử") },
                        label = { Text("Lịch sử") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "setup",
                        onClick = {
                            navController.navigate("setup") {
                                popUpTo("schedules")
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Thiết lập") },
                        label = { Text("Thiết lập") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "schedules",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("schedules") {
                SchedulesScreen(
                    tasks = tasks,
                    onAddClick = { navController.navigate("editor/-1") },
                    onEditClick = { id -> navController.navigate("editor/$id") },
                    onToggleEnabled = { task, enabled -> viewModel.toggleTaskEnabled(task, enabled) },
                    onDeleteTask = { task -> viewModel.deleteTask(task) },
                    onDryRunNow = { task -> viewModel.runDryTestNow(task) },
                    onGoToSetup = { navController.navigate("setup") }
                )
            }
            composable("logs") {
                LogsScreen(
                    logs = logs,
                    onClearLogs = { viewModel.clearLogs() }
                )
            }
            composable("setup") {
                SetupScreen(
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
            composable(
                route = "editor/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.LongType })
            ) { entry ->
                val rawId = entry.arguments?.getLong("taskId") ?: -1L
                TaskEditorScreen(
                    taskId = if (rawId > 0L) rawId else null,
                    savedGroupNames = savedGroups,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
