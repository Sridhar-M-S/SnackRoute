package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.DailyTask
import com.example.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DailyTasksScreen(
    viewModel: AppViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedTaskDate.collectAsState()
    val tasks by viewModel.tasksForSelectedDate.collectAsState()
    val distinctDates by viewModel.distinctTaskDates.collectAsState()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<DailyTask?>(null) }
    var taskToDelete by remember { mutableStateOf<DailyTask?>(null) }

    // Generate last 7 days + today for quick date timeline select
    val quickDates = remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val list = mutableListOf<String>()
        // Add last 6 days and today
        for (i in 0..6) {
            list.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        list.reverse()
        list
    }

    // Ensure currently selected date or any custom dates are added if they are not in the quick list
    val allSelectableDates = remember(quickDates, distinctDates, selectedDate) {
        val set = mutableSetOf<String>()
        set.addAll(quickDates)
        set.addAll(distinctDates)
        set.add(selectedDate)
        set.toList().sortedDescending()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Daily Tasks Checklist",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("daily_tasks_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Let users add a task from the top bar too
                    IconButton(
                        onClick = { showAddTaskDialog = true },
                        modifier = Modifier.testTag("daily_tasks_add_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddTask,
                            contentDescription = "Add Task",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("daily_tasks_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Task")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Horizontal Date Selection Timeline
            Text(
                text = "Select Date",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allSelectableDates, key = { it }) { dateStr ->
                    val isSelected = dateStr == selectedDate
                    DateTimelinePill(
                        dateStr = dateStr,
                        isSelected = isSelected,
                        onClick = { viewModel.setSelectedTaskDate(dateStr) }
                    )
                }
            }

            Divider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
            )

            // Tasks Content Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                val completedCount = tasks.count { it.isCompleted }
                val totalCount = tasks.size

                // Progress Indicator (Animate progress value change)
                TaskProgressSection(completed = completedCount, total = totalCount)

                if (tasks.isEmpty()) {
                    EmptyTasksState(
                        selectedDate = selectedDate,
                        onCopyFromYesterday = {
                            viewModel.copyUnfinishedTasksFromPreviousDay(selectedDate)
                        },
                        onQuickAddTask = { showAddTaskDialog = true }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            TaskItemRow(
                                task = task,
                                onToggle = { viewModel.toggleTaskCompletion(task) },
                                onEdit = { taskToEdit = task },
                                onDelete = { taskToDelete = task }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Task Dialog
    if (showAddTaskDialog) {
        TaskAddEditDialog(
            isEdit = false,
            taskToEdit = null,
            selectedDate = selectedDate,
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, desc, reminder, enableReminder ->
                viewModel.addTask(title, desc, selectedDate, reminder, enableReminder)
                showAddTaskDialog = false
            }
        )
    }

    // Edit Task Dialog
    if (taskToEdit != null) {
        TaskAddEditDialog(
            isEdit = true,
            taskToEdit = taskToEdit,
            selectedDate = selectedDate,
            onDismiss = { taskToEdit = null },
            onConfirm = { title, desc, reminder, enableReminder ->
                taskToEdit?.let {
                    viewModel.updateTask(
                        it.copy(
                            title = title,
                            description = desc,
                            reminderTime = reminder,
                            isReminderEnabled = enableReminder
                        )
                    )
                }
                taskToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete Task", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${taskToDelete?.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        taskToDelete?.let { viewModel.deleteTask(it) }
                        taskToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DateTimelinePill(
    dateStr: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val parsedDate = remember(dateStr) { sdf.parse(dateStr) ?: Date() }
    val dayNumber = remember(parsedDate) { SimpleDateFormat("dd", Locale.getDefault()).format(parsedDate) }
    val dayName = remember(parsedDate) { SimpleDateFormat("EEE", Locale.getDefault()).format(parsedDate) }
    val isToday = remember(dateStr) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        todayStr == dateStr
    }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else if (isToday) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else if (isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .width(62.dp)
            .clickable(onClick = onClick)
            .testTag("date_pill_$dateStr"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayName.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = if (isSelected) 0.8f else 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dayNumber,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            if (isToday) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

@Composable
fun TaskProgressSection(completed: Int, total: Int) {
    val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "TaskProgressAnimated"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("tasks_progress_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Completion Status",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (total > 0) "$completed of $total completed" else "No tasks scheduled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
fun TaskItemRow(
    task: DailyTask,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cardAlpha = if (task.isCompleted) 0.6f else 1.0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox with click listener
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onToggle)
                    .testTag("task_checkbox_${task.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (task.isCompleted) "Completed" else "Incomplete",
                    tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(26.dp)
                )
            }

            // Title & Description
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.description.isNotEmpty()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (task.isCompleted) 0.4f else 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Reminder Pill
                if (task.isReminderEnabled && !task.reminderTime.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Reminder",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Reminder: ${task.reminderTime}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Actions (Edit/Delete)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("task_edit_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Task",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("task_delete_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Task",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyTasksState(
    selectedDate: String,
    onCopyFromYesterday: () -> Unit,
    onQuickAddTask: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val parsedDate = remember(selectedDate) { sdf.parse(selectedDate) ?: Date() }
    val isToday = remember(selectedDate) {
        val todayStr = sdf.format(Date())
        todayStr == selectedDate
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlaylistAddCheck,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tasks schedule for ${if (isToday) "today" else "this date"}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Each day starts fresh automatically, giving you a clean slate!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onQuickAddTask,
            modifier = Modifier.testTag("empty_add_task_button")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create First Task")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Delightful Copy Button (Clone incomplete tasks from yesterday)
        OutlinedButton(
            onClick = onCopyFromYesterday,
            modifier = Modifier.testTag("copy_yesterday_tasks_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Copy unfinished tasks from yesterday")
        }
    }
}

@Composable
fun TaskAddEditDialog(
    isEdit: Boolean,
    taskToEdit: DailyTask?,
    selectedDate: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, reminderTime: String?, isReminderEnabled: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
    var description by remember { mutableStateOf(taskToEdit?.description ?: "") }
    var isReminderEnabled by remember { mutableStateOf(taskToEdit?.isReminderEnabled ?: false) }
    
    // Easy text-based reminder setup that is safe, beautiful, and robust
    var selectedHour by remember { mutableStateOf(taskToEdit?.reminderTime?.split(":")?.getOrNull(0) ?: "09") }
    var selectedMinute by remember { mutableStateOf(taskToEdit?.reminderTime?.split(":")?.getOrNull(1) ?: "00") }

    var titleError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isEdit) "Edit Daily Task" else "Add Daily Task",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )

                // Date label
                Text(
                    text = "Scheduled for: $selectedDate",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = false
                    },
                    label = { Text("Task Title *") },
                    placeholder = { Text("e.g. Visit SHOP01, Log sales") },
                    singleLine = true,
                    isError = titleError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                if (titleError) {
                    Text(
                        text = "Title is required!",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Task Details (Optional)") },
                    placeholder = { Text("e.g. Check for returns, collect cash") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_desc_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Reminder Toggles
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Set Reminder Alarm",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = isReminderEnabled,
                            onCheckedChange = { isReminderEnabled = it },
                            modifier = Modifier.testTag("task_reminder_switch")
                        )
                    }

                    AnimatedVisibility(visible = isReminderEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Select Time (24h format)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Hour selection box
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = selectedHour,
                                        onValueChange = {
                                            val filtered = it.take(2).filter { char -> char.isDigit() }
                                            val intVal = filtered.toIntOrNull() ?: 0
                                            if (intVal in 0..23) {
                                                selectedHour = filtered
                                            }
                                        },
                                        label = { Text("Hour") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.testTag("task_reminder_hour"),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                                Text(" : ", fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 8.dp))
                                // Minute selection box
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = selectedMinute,
                                        onValueChange = {
                                            val filtered = it.take(2).filter { char -> char.isDigit() }
                                            val intVal = filtered.toIntOrNull() ?: 0
                                            if (intVal in 0..59) {
                                                selectedMinute = filtered
                                            }
                                        },
                                        label = { Text("Min") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.testTag("task_reminder_min"),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = true
                            } else {
                                val padHour = selectedHour.padStart(2, '0')
                                val padMin = selectedMinute.padStart(2, '0')
                                onConfirm(title.trim(), description.trim(), "$padHour:$padMin", isReminderEnabled)
                            }
                        },
                        modifier = Modifier.testTag("task_dialog_confirm_button")
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
