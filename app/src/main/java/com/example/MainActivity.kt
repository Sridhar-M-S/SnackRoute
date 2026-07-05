package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
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
                        when (currentTab) {
                            "Dashboard" -> DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToTab = { currentTab = it },
                                onQuickAddSales = { currentTab = "Sales" }
                            )
                            "Locations" -> LocationsScreen(viewModel = viewModel)
                            "Shops" -> ShopsScreen(viewModel = viewModel)
                            "Products" -> ProductsScreen(viewModel = viewModel)
                            "Sales" -> SalesScreen(viewModel = viewModel)
                            "Reports" -> ReportsScreen(viewModel = viewModel)
                            "Settings" -> SettingsScreen(
                                viewModel = viewModel,
                                isDarkMode = isDarkMode,
                                onToggleDarkMode = { isDarkMode = it }
                            )
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
