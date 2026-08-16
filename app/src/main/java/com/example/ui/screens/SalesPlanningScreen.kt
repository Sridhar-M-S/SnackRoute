package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppViewModel
import com.example.ui.ReminderItem
import com.example.ui.InAppNotification
import com.example.data.ProductMaster
import com.example.data.ProductPrice
import com.example.data.ShopRemark
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesPlanningScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onNavigateToTab: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dueReminders by viewModel.dueReminders.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val productPrices by viewModel.allPrices.collectAsStateWithLifecycle()
    val allRemarks by viewModel.allRemarks.collectAsStateWithLifecycle()
    val inAppNotifications by viewModel.inAppNotifications.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()

    // Map location number to location name for easy lookup
    val locationMap = remember(locations) {
        locations.associate { it.locationNumber to it.locationName }
    }

    // Keep track of which shop details are expanded
    var expandedShops by remember { mutableStateOf(setOf<String>()) }

    // Collapsible sections
    var isInAppExpanded by remember { mutableStateOf(true) }
    var isTodayExpanded by remember { mutableStateOf(true) }
    var isMissedExpanded by remember { mutableStateOf(false) }

    // Collapsible location sub-sections
    var collapsedTodayLocations by remember { mutableStateOf(setOf<String>()) }
    var expandedMissedLocations by remember { mutableStateOf(setOf<String>()) }

    // Multi-Filter State
    var filterExpanded by remember { mutableStateOf(true) }
    var filterStartDate by remember { mutableStateOf<Long?>(null) }
    var filterEndDate by remember { mutableStateOf<Long?>(null) }
    var filterDateBy by remember { mutableStateOf("Last Sale Date") } // "Last Sale Date" or "Due Date"
    var filterLocationNumber by remember { mutableStateOf<String?>(null) }
    var filterPaymentStatus by remember { mutableStateOf<String?>(null) } // "Paid", "Pending", "Partially Paid"

    fun startOfDay(timeMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun endOfDay(timeMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMs
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val filteredDueReminders = remember(dueReminders, sales, filterStartDate, filterEndDate, filterDateBy, filterLocationNumber, filterPaymentStatus) {
        dueReminders.filter { reminder ->
            val matchLocation = filterLocationNumber == null || reminder.shop.locationNumber == filterLocationNumber
            
            val matchDate = if (filterStartDate == null && filterEndDate == null) {
                true
            } else {
                val start = filterStartDate?.let { startOfDay(it) } ?: 0L
                val end = filterEndDate?.let { endOfDay(it) } ?: Long.MAX_VALUE
                val targetDate = if (filterDateBy == "Due Date") {
                    reminder.lastSaleDate + (reminder.interval * 86400000L)
                } else {
                    reminder.lastSaleDate
                }
                targetDate in start..end
            }

            val matchPaymentStatus = if (filterPaymentStatus == null) {
                true
            } else {
                val shopSales = sales.filter { it.shopNumber == reminder.shop.shopNumber }
                val latestSale = shopSales.maxByOrNull { it.entryDate }
                if (shopSales.isEmpty()) {
                    filterPaymentStatus.equals("Pending", ignoreCase = true)
                } else {
                    latestSale != null && latestSale.status.equals(filterPaymentStatus, ignoreCase = true)
                }
            }

            matchLocation && matchDate && matchPaymentStatus
        }
    }

    // Partition reminders using filtered reminders
    val todayReminders = remember(filteredDueReminders) {
        filteredDueReminders.filter { it.daysSince == it.interval }
    }
    val missedReminders = remember(filteredDueReminders) {
        filteredDueReminders.filter { it.daysSince > it.interval }
    }

    // Group by location
    val todayRemindersByLocation = remember(todayReminders) {
        todayReminders.groupBy { it.shop.locationNumber }
    }
    val missedRemindersByLocation = remember(missedReminders, locations) {
        val grouped = missedReminders.groupBy { it.shop.locationNumber }
        val locationNumbersOrder = locations.map { it.locationNumber }
        grouped.mapValues { (_, list) ->
            list.sortedByDescending { it.lastSaleDate + it.interval * 86400000L }
        }.toList().sortedBy { (locNum, _) ->
            val index = locationNumbersOrder.indexOf(locNum)
            if (index != -1) index else Int.MAX_VALUE
        }.toMap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sales Planning", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("sales_planning_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (dueReminders.isEmpty() && inAppNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "No reminders",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "All Shops Up to Date!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "There are no active reminders or notifications.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info header card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    "Daily Preparation Guide",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Prepare stock based on past sales averages, organized by locations.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Multi-Filter Card Item
                item(key = "sales_planning_filter_card_item") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sales_planning_filter_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val activeFilterCount = (if (filterStartDate != null || filterEndDate != null) 1 else 0) +
                                    (if (filterLocationNumber != null) 1 else 0) +
                                    (if (filterPaymentStatus != null) 1 else 0)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { filterExpanded = !filterExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Filter Preparation & Reminders",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (activeFilterCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("$activeFilterCount", color = Color.White, modifier = Modifier.padding(2.dp))
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (activeFilterCount > 0) {
                                        TextButton(
                                            onClick = {
                                                filterStartDate = null
                                                filterEndDate = null
                                                filterLocationNumber = null
                                                filterPaymentStatus = null
                                            },
                                            modifier = Modifier.testTag("clear_sales_planning_filters")
                                        ) {
                                            Text("Clear All", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Icon(
                                        imageVector = if (filterExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            AnimatedVisibility(visible = filterExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    HorizontalDivider()

                                    // 1. Date Filter Mode Selection
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Filter Date By:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FilterChip(
                                            selected = filterDateBy == "Last Sale Date",
                                            onClick = { filterDateBy = "Last Sale Date" },
                                            label = { Text("Last Sale Date", fontSize = 11.sp) },
                                            modifier = Modifier.testTag("filter_by_last_sale_chip")
                                        )
                                        FilterChip(
                                            selected = filterDateBy == "Due Date",
                                            onClick = { filterDateBy = "Due Date" },
                                            label = { Text("Due Date", fontSize = 11.sp) },
                                            modifier = Modifier.testTag("filter_by_due_date_chip")
                                        )
                                    }

                                    // Quick Date Presets
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SuggestionChip(
                                            onClick = {
                                                val now = System.currentTimeMillis()
                                                filterStartDate = startOfDay(now)
                                                filterEndDate = endOfDay(now)
                                            },
                                            label = { Text("Today", fontSize = 11.sp) }
                                        )
                                        SuggestionChip(
                                            onClick = {
                                                val cal = Calendar.getInstance()
                                                cal.add(Calendar.DAY_OF_YEAR, -1)
                                                filterStartDate = startOfDay(cal.timeInMillis)
                                                filterEndDate = endOfDay(cal.timeInMillis)
                                            },
                                            label = { Text("Yesterday", fontSize = 11.sp) }
                                        )
                                        SuggestionChip(
                                            onClick = {
                                                val now = System.currentTimeMillis()
                                                val cal = Calendar.getInstance()
                                                cal.add(Calendar.DAY_OF_YEAR, -6)
                                                filterStartDate = startOfDay(cal.timeInMillis)
                                                filterEndDate = endOfDay(now)
                                            },
                                            label = { Text("Last 7 Days", fontSize = 11.sp) }
                                        )
                                        SuggestionChip(
                                            onClick = {
                                                val now = System.currentTimeMillis()
                                                val cal = Calendar.getInstance()
                                                cal.add(Calendar.DAY_OF_YEAR, -29)
                                                filterStartDate = startOfDay(cal.timeInMillis)
                                                filterEndDate = endOfDay(now)
                                            },
                                            label = { Text("Last 30 Days", fontSize = 11.sp) }
                                        )
                                        SuggestionChip(
                                            onClick = {
                                                val cal = Calendar.getInstance()
                                                cal.set(Calendar.DAY_OF_MONTH, 1)
                                                val start = startOfDay(cal.timeInMillis)
                                                val calEnd = Calendar.getInstance()
                                                calEnd.set(Calendar.DAY_OF_MONTH, calEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
                                                val end = endOfDay(calEnd.timeInMillis)
                                                filterStartDate = start
                                                filterEndDate = end
                                            },
                                            label = { Text("This Month", fontSize = 11.sp) }
                                        )
                                        if (filterStartDate != null || filterEndDate != null) {
                                            SuggestionChip(
                                                onClick = {
                                                    filterStartDate = null
                                                    filterEndDate = null
                                                },
                                                label = { Text("Clear Date", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                                            )
                                        }
                                    }

                                    // Date Range Pickers (From Date and To Date)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val cal = Calendar.getInstance()
                                                filterStartDate?.let { cal.timeInMillis = it }
                                                android.app.DatePickerDialog(
                                                    context,
                                                    { _, year, month, dayOfMonth ->
                                                        val c = Calendar.getInstance()
                                                        c.set(year, month, dayOfMonth, 0, 0, 0)
                                                        c.set(Calendar.MILLISECOND, 0)
                                                        filterStartDate = c.timeInMillis
                                                    },
                                                    cal.get(Calendar.YEAR),
                                                    cal.get(Calendar.MONTH),
                                                    cal.get(Calendar.DAY_OF_MONTH)
                                                ).show()
                                            },
                                            modifier = Modifier.weight(1f).testTag("filter_from_date_button"),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = filterStartDate?.let { "From: ${dateFormat.format(Date(it))}" } ?: "From Date",
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val cal = Calendar.getInstance()
                                                filterEndDate?.let { cal.timeInMillis = it }
                                                android.app.DatePickerDialog(
                                                    context,
                                                    { _, year, month, dayOfMonth ->
                                                        val c = Calendar.getInstance()
                                                        c.set(year, month, dayOfMonth, 23, 59, 59)
                                                        c.set(Calendar.MILLISECOND, 999)
                                                        filterEndDate = c.timeInMillis
                                                    },
                                                    cal.get(Calendar.YEAR),
                                                    cal.get(Calendar.MONTH),
                                                    cal.get(Calendar.DAY_OF_MONTH)
                                                ).show()
                                            },
                                            modifier = Modifier.weight(1f).testTag("filter_to_date_button"),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = filterEndDate?.let { "To: ${dateFormat.format(Date(it))}" } ?: "To Date",
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // 2. Location & Payment Status Filters
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Location Dropdown
                                        var locMenuExpanded by remember { mutableStateOf(false) }
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedButton(
                                                onClick = { locMenuExpanded = true },
                                                modifier = Modifier.fillMaxWidth().testTag("filter_location_button"),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                val currentLocName = locations.find { it.locationNumber == filterLocationNumber }?.locationName
                                                Text(
                                                    text = currentLocName ?: "All Locations",
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = locMenuExpanded,
                                                onDismissRequest = { locMenuExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("All Locations", fontWeight = if (filterLocationNumber == null) FontWeight.Bold else FontWeight.Normal) },
                                                    onClick = {
                                                        filterLocationNumber = null
                                                        locMenuExpanded = false
                                                    }
                                                )
                                                locations.forEach { loc ->
                                                    DropdownMenuItem(
                                                        text = { Text("${loc.locationNumber} - ${loc.locationName}", fontWeight = if (filterLocationNumber == loc.locationNumber) FontWeight.Bold else FontWeight.Normal) },
                                                        onClick = {
                                                            filterLocationNumber = loc.locationNumber
                                                            locMenuExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // Payment Status Dropdown
                                        var statusMenuExpanded by remember { mutableStateOf(false) }
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedButton(
                                                onClick = { statusMenuExpanded = true },
                                                modifier = Modifier.fillMaxWidth().testTag("filter_payment_status_button"),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = filterPaymentStatus ?: "All Statuses",
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = statusMenuExpanded,
                                                onDismissRequest = { statusMenuExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("All Statuses", fontWeight = if (filterPaymentStatus == null) FontWeight.Bold else FontWeight.Normal) },
                                                    onClick = {
                                                        filterPaymentStatus = null
                                                        statusMenuExpanded = false
                                                    }
                                                )
                                                listOf("Paid", "Pending", "Partially Paid").forEach { statusOption ->
                                                    DropdownMenuItem(
                                                        text = { Text(statusOption, fontWeight = if (filterPaymentStatus == statusOption) FontWeight.Bold else FontWeight.Normal) },
                                                        onClick = {
                                                            filterPaymentStatus = statusOption
                                                            statusMenuExpanded = false
                                                        }
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

                // Filter Active Banner / No matching reminders banner
                val activeFiltersPresent = (filterStartDate != null || filterEndDate != null || filterLocationNumber != null || filterPaymentStatus != null)
                if (activeFiltersPresent && filteredDueReminders.isEmpty() && dueReminders.isNotEmpty()) {
                    item(key = "no_filtered_results_card") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("no_filtered_results_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterListOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "No Reminders Found Matching Filters",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "No shops match the selected date range ($filterDateBy) or filters. Try adjusting your dates or clearing filters.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                OutlinedButton(
                                    onClick = {
                                        filterStartDate = null
                                        filterEndDate = null
                                        filterLocationNumber = null
                                        filterPaymentStatus = null
                                    },
                                    modifier = Modifier.testTag("reset_filters_button"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear All Filters", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // In-App Notifications Center Section
                if (inAppNotifications.isNotEmpty()) {
                    item(key = "in_app_notifs_header") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isInAppExpanded = !isInAppExpanded },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isInAppExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "In-App Notifications",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    val unreadCount = inAppNotifications.count { !it.isRead }
                                    if (unreadCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                                            Text("$unreadCount", color = Color.White, modifier = Modifier.padding(2.dp))
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.clearAllInAppNotifications() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ClearAll,
                                        contentDescription = "Clear All",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isInAppExpanded) {
                        items(inAppNotifications, key = { "notif_${it.id}" }) { notification ->
                            InAppNotificationCard(
                                notification = notification,
                                onMarkRead = { viewModel.markInAppNotificationRead(notification.id) },
                                onDelete = { viewModel.deleteInAppNotification(notification.id) }
                            )
                        }
                    }
                }

                // Today's Reminders Section
                item(key = "today_rem_header") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTodayExpanded = !isTodayExpanded },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isTodayExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                    contentDescription = null
                                )
                                Text(
                                    text = "Today's Reminders",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 16.sp
                                )
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("${todayReminders.size}", color = Color.White, modifier = Modifier.padding(4.dp))
                                }
                            }
                            if (todayReminders.isNotEmpty()) {
                                Button(
                                    onClick = { viewModel.markTodayRemindersCompleted(todayReminders) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        contentColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("mark_today_done")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Done", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                if (isTodayExpanded) {
                    if (todayReminders.isEmpty()) {
                        item(key = "today_empty") {
                            Text(
                                text = "No active reminders scheduled for today.",
                                modifier = Modifier.padding(16.dp),
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        item(key = "today_mfg_summary") {
                            ManufacturingSummaryView(
                                reminders = todayReminders,
                                products = products,
                                productPrices = productPrices,
                                locationMap = locationMap,
                                testTag = "today_mfg_summary_view"
                            )
                        }

                        todayRemindersByLocation.forEach { (locNum, remindersInLoc) ->
                            val locName = locationMap[locNum] ?: "Location $locNum"
                            val isLocCollapsed = collapsedTodayLocations.contains(locNum)
                            item(key = "today_loc_hdr_$locNum") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            collapsedTodayLocations = if (isLocCollapsed) {
                                                collapsedTodayLocations - locNum
                                            } else {
                                                collapsedTodayLocations + locNum
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isLocCollapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = locName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                            Text("${remindersInLoc.size}", modifier = Modifier.padding(2.dp))
                                        }
                                    }
                                }
                            }

                            if (!isLocCollapsed) {
                                items(remindersInLoc, key = { "today_rem_${it.shop.shopNumber}" }) { reminder ->
                                    ReminderCard(
                                        reminder = reminder,
                                        isShopExpanded = expandedShops.contains(reminder.shop.shopNumber),
                                        onToggleExpand = {
                                            expandedShops = if (expandedShops.contains(reminder.shop.shopNumber)) {
                                                expandedShops - reminder.shop.shopNumber
                                            } else {
                                                expandedShops + reminder.shop.shopNumber
                                            }
                                        },
                                        locName = locName,
                                        allRemarks = allRemarks,
                                        onMarkCompleted = { viewModel.markReminderCompleted(reminder.shop.shopNumber) },
                                        onOpenInShopMaster = {
                                            viewModel.setPrefilledShopSearchQuery(reminder.shop.storeName)
                                            onNavigateToTab("Shops")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Missed Reminders Section
                item(key = "missed_rem_header") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isMissedExpanded = !isMissedExpanded },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isMissedExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Missed Reminders",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 16.sp
                                )
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text("${missedReminders.size}", color = Color.White, modifier = Modifier.padding(4.dp))
                                }
                            }
                            if (missedReminders.isNotEmpty()) {
                                Button(
                                    onClick = { viewModel.markMissedRemindersCompleted(missedReminders) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                        contentColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    modifier = Modifier.testTag("mark_missed_done")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Done", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                if (isMissedExpanded) {
                    if (missedReminders.isEmpty()) {
                        item(key = "missed_empty") {
                            Text(
                                text = "No missed reminders.",
                                modifier = Modifier.padding(16.dp),
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        item(key = "missed_mfg_summary") {
                            ManufacturingSummaryView(
                                reminders = missedReminders,
                                products = products,
                                productPrices = productPrices,
                                locationMap = locationMap,
                                testTag = "missed_mfg_summary_view"
                            )
                        }

                        missedRemindersByLocation.forEach { (locNum, remindersInLoc) ->
                            val locName = locationMap[locNum] ?: "Location $locNum"
                            val isLocExpanded = expandedMissedLocations.contains(locNum)
                            item(key = "missed_loc_hdr_$locNum") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedMissedLocations = if (isLocExpanded) {
                                                expandedMissedLocations - locNum
                                            } else {
                                                expandedMissedLocations + locNum
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isLocExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = locName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                            Text("${remindersInLoc.size}", modifier = Modifier.padding(2.dp))
                                        }
                                    }
                                }
                            }

                            if (isLocExpanded) {
                                items(remindersInLoc, key = { "missed_rem_${it.shop.shopNumber}" }) { reminder ->
                                    ReminderCard(
                                        reminder = reminder,
                                        isShopExpanded = expandedShops.contains(reminder.shop.shopNumber),
                                        onToggleExpand = {
                                            expandedShops = if (expandedShops.contains(reminder.shop.shopNumber)) {
                                                expandedShops - reminder.shop.shopNumber
                                            } else {
                                                expandedShops + reminder.shop.shopNumber
                                            }
                                        },
                                        locName = locName,
                                        allRemarks = allRemarks,
                                        onMarkCompleted = { viewModel.markReminderCompleted(reminder.shop.shopNumber) },
                                        onOpenInShopMaster = {
                                            viewModel.setPrefilledShopSearchQuery(reminder.shop.storeName)
                                            onNavigateToTab("Shops")
                                        }
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

@Composable
fun InAppNotificationCard(
    notification: InAppNotification,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(notification.timestamp))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("in_app_notif_card_${notification.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMarkRead,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (notification.isRead) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Mark Read",
                    tint = if (notification.isRead) Color.Gray else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateStr,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.message,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ManufacturingSummaryView(
    reminders: List<ReminderItem>,
    products: List<ProductMaster>,
    productPrices: List<ProductPrice>,
    locationMap: Map<String, String>,
    testTag: String
) {
    if (reminders.isEmpty()) return

    data class ItemSummary(
        val locationName: String,
        val productName: String,
        val category: String,
        val price: Double,
        val totalPackets: Int,
        val shopCount: Int
    )

    val summaryItems = remember(reminders, products, productPrices, locationMap) {
        val flatList = reminders.flatMap { reminder ->
            val locationName = locationMap[reminder.shop.locationNumber] ?: "Location ${reminder.shop.locationNumber}"
            
            // Prefer recommendedProducts (average sales quantity per product)
            if (reminder.recommendedProducts.isNotEmpty()) {
                reminder.recommendedProducts.map { (prodName, recommendedQty) ->
                    val matchingProduct = products.find { it.productName.equals(prodName, ignoreCase = true) }
                    val category = matchingProduct?.productCategory ?: "Standard"
                    val latestSalePrice = reminder.lastSaleProducts.find { it.productName.equals(prodName, ignoreCase = true) }?.sellingPrice
                    val matchingPrice = productPrices.find { it.productId == matchingProduct?.id }?.sellingPrice ?: 0.0
                    val sellingPrice = latestSalePrice ?: matchingPrice
                    
                    Triple(locationName, prodName, sellingPrice) to Pair(recommendedQty, category)
                }
            } else {
                // Fallback to last sale products if recommended products is empty
                reminder.lastSaleProducts.map { lsp ->
                    val matchingProduct = products.find { it.productName.equals(lsp.productName, ignoreCase = true) }
                    val category = if (lsp.productVariety.isNotBlank()) lsp.productVariety else (matchingProduct?.productCategory ?: "Standard")
                    Triple(locationName, lsp.productName, lsp.sellingPrice) to Pair(lsp.packetsSupplied, category)
                }
            }
        }

        flatList
            .groupBy { it.first }
            .map { (key, entries) ->
                val (locName, prodName, price) = key
                val totalPackets = entries.sumOf { it.second.first }
                val category = entries.firstOrNull()?.second?.second ?: "Standard"
                val shopCount = entries.size
                ItemSummary(
                    locationName = locName,
                    productName = prodName,
                    category = category,
                    price = price,
                    totalPackets = totalPackets,
                    shopCount = shopCount
                )
            }
            .sortedWith(compareBy({ it.locationName }, { it.productName }, { it.price }))
    }

    val overallTotalPackets = remember(summaryItems) {
        summaryItems.sumOf { it.totalPackets }
    }

    if (summaryItems.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .testTag(testTag)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header with total badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Handyman,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Stock Preparation Summary",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 14.sp
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Total: $overallTotalPackets pkts",
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "Breakdown by Location, Item Name, and Selling Price",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                val summaryByLocation = summaryItems.groupBy { it.locationName }
                summaryByLocation.forEach { (locName, entries) ->
                    val locationTotal = entries.sumOf { it.totalPackets }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = locName,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Subtotal: $locationTotal pkts",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }

                    entries.forEach { entry ->
                        val priceLabel = if (entry.price > 0.0) " ₹${String.format(Locale.getDefault(), "%.2f", entry.price)}" else ""
                        val categoryBadge = if (entry.category.isNotBlank() && !entry.category.equals(entry.productName, ignoreCase = true) && !entry.category.equals("Standard", ignoreCase = true)) " (${entry.category})" else ""
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "• ${entry.productName}$categoryBadge$priceLabel",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                if (entry.shopCount > 1) {
                                    Text(
                                        text = "across ${entry.shopCount} shops",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.65f),
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${entry.totalPackets} packets",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun ReminderCard(
    reminder: ReminderItem,
    isShopExpanded: Boolean,
    onToggleExpand: () -> Unit,
    locName: String,
    allRemarks: List<ShopRemark>,
    onMarkCompleted: () -> Unit,
    onOpenInShopMaster: () -> Unit = {}
) {
    val shop = reminder.shop
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("reminder_card_${shop.shopNumber}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Shop and Location
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleExpand() },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shop.storeName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = locName,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = if (isShopExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Details",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Shop Master Shortcut Icon Button
                IconButton(
                    onClick = onOpenInShopMaster,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("open_shop_master_${shop.shopNumber}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Open ${shop.storeName} in Shop Master",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Done button for individual dismissal
                IconButton(
                    onClick = onMarkCompleted,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("done_button_${shop.shopNumber}")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Mark Shop Done",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Last Sale Date & Overdue Days
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Last Sale: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(reminder.lastSaleDate))}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${reminder.daysSince} days ago",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (reminder.daysSince >= reminder.interval) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = isShopExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    // Section Title: Last Sale Details
                    Text(
                        text = "Last Sale Details:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (reminder.lastSaleProducts.isEmpty()) {
                        Text(
                            text = "No prior sale items details available.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    } else {
                        reminder.lastSaleProducts.forEach { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.productName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "${item.packetsSupplied} pkts",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Variety: ${item.productVariety}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = "Price: ₹${String.format(Locale.getDefault(), "%.2f", item.sellingPrice)}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                if (!item.remarks.isNullOrBlank()) {
                                    Text(
                                        text = "Item Remark: ${item.remarks}",
                                        fontSize = 11.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Section Title: Recommended Stock to Prepare
                    Text(
                        text = "Expected packets to prepare:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (reminder.recommendedProducts.isEmpty()) {
                        Text(
                            text = "No sales history to compute averages.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    } else {
                        reminder.recommendedProducts.forEach { (prodName, avgQty) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• $prodName",
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "$avgQty pkts recommended",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Remarks section
                    val shopSalesRemark = allRemarks.firstOrNull { 
                        it.shopNumber == shop.shopNumber && it.status == "Pending" 
                    }?.remark ?: shop.notes

                    if (!shopSalesRemark.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Yellow.copy(alpha = 0.15f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Feedback,
                                contentDescription = null,
                                tint = Color(0xFFE5A93C),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Shop Remark: $shopSalesRemark",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
