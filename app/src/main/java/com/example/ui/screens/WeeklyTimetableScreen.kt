package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TimetableEntry
import com.example.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyTimetableScreen(
    viewModel: AppViewModel,
    onClose: () -> Unit,
    onNavigateToTab: (String) -> Unit = {}
) {
    val timetableEntries by viewModel.timetable.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val shops by viewModel.shops.collectAsStateWithLifecycle()

    var editingEntry by remember { mutableStateOf<TimetableEntry?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val daysOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Weekly Timetable", fontWeight = FontWeight.Bold)
                        Text(
                            "Daily Schedule & Reminders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("timetable_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showResetConfirmDialog = true },
                        modifier = Modifier.testTag("clear_timetable_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Weekly Timetable"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (daysOrder.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Weekly timetable changes automatically clear every Sunday at 11:00 PM.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("weekly_timetable_list"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(daysOrder) { day ->
                            val entry = timetableEntries.find { it.dayOfWeek == day } ?: TimetableEntry(dayOfWeek = day)
                            
                            // Resolve actual locations
                            val resolvedLocations = entry.locationNumbers.mapNotNull { locNum ->
                                locations.find { it.locationNumber == locNum }
                            }

                            // Resolve shops at these locations
                            val resolvedShops = shops.filter { shop ->
                                entry.locationNumbers.contains(shop.locationNumber)
                            }

                            TimetableDayCard(
                                day = day,
                                entry = entry,
                                locations = resolvedLocations,
                                shops = resolvedShops,
                                onEdit = { editingEntry = entry },
                                onOpenShopMaster = { shopName ->
                                    viewModel.setPrefilledShopSearchQuery(shopName)
                                    onNavigateToTab("Shops")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Dialog
    if (editingEntry != null) {
        EditTimetableDialog(
            entry = editingEntry!!,
            allLocations = locations,
            onDismiss = { editingEntry = null },
            onSave = { updatedEntry: TimetableEntry ->
                viewModel.updateTimetableEntry(updatedEntry)
                editingEntry = null
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Clear Weekly Timetable?") },
            text = { Text("This will reset all location assignments and notes for every day of the week.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.resetWeeklyTimetable()
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
