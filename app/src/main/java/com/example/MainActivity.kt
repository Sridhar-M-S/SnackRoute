package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.ui.AppViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkThemeSet = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(isDarkThemeSet) }

            MyApplicationTheme(darkTheme = isDarkMode) {
                val viewModel: AppViewModel = viewModel()
                var currentTab by remember { mutableStateOf("Dashboard") }
                var isAiChatOpen by remember { mutableStateOf(false) }
                var isTimetableOpen by remember { mutableStateOf(false) }
                var isDebugOpen by remember { mutableStateOf(false) }
                var showExitConfirmationDialog by remember { mutableStateOf(false) }

                val navigationHistory = remember { mutableStateListOf<String>("Dashboard") }

                LaunchedEffect(currentTab) {
                    if (navigationHistory.isEmpty() || navigationHistory.last() != currentTab) {
                        navigationHistory.add(currentTab)
                    }
                }

                BackHandler(enabled = true) {
                    if (isAiChatOpen) {
                        isAiChatOpen = false
                    } else if (isTimetableOpen) {
                        isTimetableOpen = false
                    } else if (isDebugOpen) {
                        isDebugOpen = false
                    } else {
                        if (navigationHistory.size > 1) {
                            navigationHistory.removeAt(navigationHistory.lastIndex)
                            currentTab = navigationHistory.last()
                        } else {
                            showExitConfirmationDialog = true
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            val items = listOf(
                                NavigationItem("Dashboard", Icons.Default.Dashboard),
                                NavigationItem("Locations", Icons.Default.Map),
                                NavigationItem("Shops", Icons.Default.Storefront),
                                NavigationItem("Products", Icons.Default.Category),
                                NavigationItem("Sales", Icons.Default.ReceiptLong),
                                NavigationItem("Reports", Icons.Default.Assessment),
                                NavigationItem("Settings", Icons.Default.Settings)
                            )
                            NavigationBar(
                                modifier = Modifier.testTag("app_bottom_nav_bar")
                            ) {
                                items.forEach { item ->
                                    NavigationBarItem(
                                        selected = currentTab == item.label,
                                        onClick = { currentTab = item.label },
                                        icon = { Icon(item.icon, contentDescription = item.label) },
                                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                        alwaysShowLabel = false,
                                        modifier = Modifier.testTag("nav_item_${item.label.lowercase()}")
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = if (currentTab == "Dashboard") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                    DashboardScreen(
                                        viewModel = viewModel,
                                        onNavigateToTab = { currentTab = it },
                                        onQuickAddSales = { currentTab = "Sales" },
                                        onOpenChat = { isAiChatOpen = true },
                                        onOpenTimetable = { isTimetableOpen = true }
                                    )
                                }
                                Box(modifier = if (currentTab == "Locations") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                    LocationsScreen(
                                        viewModel = viewModel,
                                        onNavigateToTab = { currentTab = it },
                                        onOpenChat = { isAiChatOpen = true },
                                        onOpenTimetable = { isTimetableOpen = true }
                                    )
                                }
                                Box(modifier = if (currentTab == "Shops") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                    ShopsScreen(
                                        viewModel = viewModel,
                                        onNavigateToTab = { currentTab = it },
                                        onOpenChat = { isAiChatOpen = true },
                                        onOpenTimetable = { isTimetableOpen = true }
                                    )
                                }
                                Box(modifier = if (currentTab == "Products") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                    ProductsScreen(
                                        viewModel = viewModel,
                                        onOpenChat = { isAiChatOpen = true },
                                        onOpenTimetable = { isTimetableOpen = true }
                                    )
                                }
                                Box(modifier = if (currentTab == "Sales") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                    SalesScreen(
                                        viewModel = viewModel,
                                        onOpenChat = { isAiChatOpen = true },
                                        onOpenTimetable = { isTimetableOpen = true }
                                    )
                                }
                                Box(modifier = if (currentTab == "Reports") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                    ReportsScreen(
                                        viewModel = viewModel,
                                        onOpenChat = { isAiChatOpen = true },
                                        onOpenTimetable = { isTimetableOpen = true }
                                    )
                                }
                                Box(modifier = if (currentTab == "Settings") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                    SettingsScreen(
                                        viewModel = viewModel,
                                        isDarkMode = isDarkMode,
                                        onToggleDarkMode = { isDarkMode = it },
                                        onOpenChat = { isAiChatOpen = true },
                                        onOpenTimetable = { isTimetableOpen = true },
                                        onOpenDebug = { isDebugOpen = true }
                                    )
                                }
                                Box(modifier = if (currentTab == "Levels") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                    LevelsScreen(
                                        viewModel = viewModel,
                                        onBack = { currentTab = "Dashboard" }
                                    )
                                }
                            }
                        }
                    }

                    if (isAiChatOpen) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            AiAssistantScreen(viewModel = viewModel, onClose = { isAiChatOpen = false })
                        }
                    }

                    if (isTimetableOpen) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            WeeklyTimetableScreen(viewModel = viewModel, onClose = { isTimetableOpen = false })
                        }
                    }

                    if (isDebugOpen) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            DebugScreen(viewModel = viewModel, onClose = { isDebugOpen = false })
                        }
                    }

                    GlobalErrorDisplay(viewModel = viewModel)

                    if (showExitConfirmationDialog) {
                        AlertDialog(
                            onDismissRequest = { showExitConfirmationDialog = false },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            title = {
                                Text(
                                    text = "Close Application",
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            text = {
                                Text(
                                    text = "Do you want to close this app?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showExitConfirmationDialog = false
                                        this@MainActivity.finish()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.testTag("confirm_exit_button")
                                ) {
                                    Text("Yes")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showExitConfirmationDialog = false },
                                    modifier = Modifier.testTag("cancel_exit_button")
                                ) {
                                    Text("No")
                                }
                            },
                            modifier = Modifier.testTag("exit_confirmation_dialog")
                        )
                    }
                }
            }
        }
    }
}

data class NavigationItem(
    val label: String,
    val icon: ImageVector
)
