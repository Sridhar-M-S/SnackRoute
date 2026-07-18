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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import com.example.ui.AppViewModel
import com.example.ui.GamificationEvent
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

                var xpToastState by remember { mutableStateOf<Pair<Int, String>?>(null) }

                LaunchedEffect(viewModel) {
                    viewModel.gamificationEvents.collect { event ->
                        if (event is GamificationEvent.XpGain) {
                            val mappedReason = when {
                                event.reason == "Shop Registered" -> "New Shop Added"
                                event.reason == "Sales Completed" -> "New Sales Record Added"
                                event.reason.startsWith("Imported ") -> event.reason
                                event.reason.startsWith("Mission: ") -> "Completed Today's Target"
                                else -> event.reason
                            }
                            xpToastState = Pair(event.amount, mappedReason)
                        }
                    }
                }

                LaunchedEffect(xpToastState) {
                    if (xpToastState != null) {
                        kotlinx.coroutines.delay(2000)
                        xpToastState = null
                    }
                }
                var currentTab by remember { mutableStateOf("Dashboard") }
                var isAiChatOpen by remember { mutableStateOf(false) }
                var isTimetableOpen by remember { mutableStateOf(false) }
                var isDebugOpen by remember { mutableStateOf(false) }
                var showExitConfirmationDialog by remember { mutableStateOf(false) }

                val navigationHistory = remember { mutableStateListOf<String>("Dashboard") }

                fun navigateToParentTab(tab: String) {
                    navigationHistory.clear()
                    navigationHistory.add("Dashboard")
                    if (tab != "Dashboard") {
                        navigationHistory.add(tab)
                    }
                    currentTab = tab
                }

                fun navigateToChildTab(tab: String) {
                    if (navigationHistory.isEmpty() || navigationHistory.last() != tab) {
                        navigationHistory.add(tab)
                    }
                    currentTab = tab
                }

                fun navigateBack() {
                    if (navigationHistory.size > 1) {
                        navigationHistory.removeAt(navigationHistory.lastIndex)
                        currentTab = navigationHistory.last()
                    } else {
                        showExitConfirmationDialog = true
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
                        navigateBack()
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
                                        onClick = { navigateToParentTab(item.label) },
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
                                if (navigationHistory.contains("Dashboard")) {
                                    Box(modifier = if (currentTab == "Dashboard") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                        DashboardScreen(
                                            viewModel = viewModel,
                                            onNavigateToTab = { navigateToChildTab(it) },
                                            onQuickAddSales = { navigateToChildTab("Sales") },
                                            onOpenChat = { isAiChatOpen = true },
                                            onOpenTimetable = { isTimetableOpen = true }
                                        )
                                    }
                                }
                                if (navigationHistory.contains("Locations")) {
                                    Box(modifier = if (currentTab == "Locations") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                        LocationsScreen(
                                            viewModel = viewModel,
                                            onNavigateToTab = { navigateToChildTab(it) },
                                            onOpenChat = { isAiChatOpen = true },
                                            onOpenTimetable = { isTimetableOpen = true },
                                            showBackButton = navigationHistory.size > 2 && navigationHistory.last() == "Locations",
                                            onBack = { navigateBack() }
                                        )
                                    }
                                }
                                if (navigationHistory.contains("Shops")) {
                                    Box(modifier = if (currentTab == "Shops") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                        ShopsScreen(
                                            viewModel = viewModel,
                                            onNavigateToTab = { navigateToChildTab(it) },
                                            onOpenChat = { isAiChatOpen = true },
                                            onOpenTimetable = { isTimetableOpen = true },
                                            showBackButton = navigationHistory.size > 2 && navigationHistory.last() == "Shops",
                                            onBack = { navigateBack() }
                                        )
                                    }
                                }
                                if (navigationHistory.contains("Products")) {
                                    Box(modifier = if (currentTab == "Products") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                        ProductsScreen(
                                            viewModel = viewModel,
                                            onOpenChat = { isAiChatOpen = true },
                                            onOpenTimetable = { isTimetableOpen = true },
                                            showBackButton = navigationHistory.size > 2 && navigationHistory.last() == "Products",
                                            onBack = { navigateBack() }
                                        )
                                    }
                                }
                                if (navigationHistory.contains("Sales")) {
                                    Box(modifier = if (currentTab == "Sales") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                        SalesScreen(
                                            viewModel = viewModel,
                                            onOpenChat = { isAiChatOpen = true },
                                            onOpenTimetable = { isTimetableOpen = true },
                                            onBackToParent = { navigateBack() },
                                            showBackButton = navigationHistory.size > 2 && navigationHistory.last() == "Sales"
                                        )
                                    }
                                }
                                if (navigationHistory.contains("Reports")) {
                                    Box(modifier = if (currentTab == "Reports") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                        ReportsScreen(
                                            viewModel = viewModel,
                                            onOpenChat = { isAiChatOpen = true },
                                            onOpenTimetable = { isTimetableOpen = true },
                                            showBackButton = navigationHistory.size > 2 && navigationHistory.last() == "Reports",
                                            onBack = { navigateBack() }
                                        )
                                    }
                                }
                                if (navigationHistory.contains("Settings")) {
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
                                }
                                if (navigationHistory.contains("Levels")) {
                                    Box(modifier = if (currentTab == "Levels") Modifier.fillMaxSize() else Modifier.size(0.dp).graphicsLayer { alpha = 0f }) {
                                        LevelsScreen(
                                            viewModel = viewModel,
                                            onBack = { navigateBack() }
                                        )
                                    }
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

                    AnimatedVisibility(
                        visible = xpToastState != null,
                        enter = fadeIn(animationSpec = spring()) + slideInVertically(initialOffsetY = { -it / 2 }),
                        exit = fadeOut(animationSpec = spring()) + slideOutVertically(targetOffsetY = { -it / 2 }),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 90.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            if (xpToastState != null) {
                                val (amount, reason) = xpToastState!!
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    modifier = Modifier.testTag("xp_toast")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val isNegative = amount < 0
                                        val isNoChanges = amount == 0 && reason == "No Changes Detected"

                                        val emoji = when {
                                            isNoChanges -> "ℹ️"
                                            isNegative -> "📉"
                                            else -> "✨"
                                        }

                                        val xpText = when {
                                            isNoChanges -> "No XP Awarded"
                                            isNegative -> "$amount XP"
                                            else -> "+$amount XP"
                                        }

                                        val xpColor = when {
                                            isNoChanges -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            isNegative -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.primary
                                        }

                                        Text(
                                            text = emoji,
                                            fontSize = 18.sp
                                        )
                                        Text(
                                            text = xpText,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                            color = xpColor
                                        )
                                        Text(
                                            text = "—",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = reason,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
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
