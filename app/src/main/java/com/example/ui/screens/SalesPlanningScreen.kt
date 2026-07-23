package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    onBack: () -> Unit
) {
    val dueReminders by viewModel.dueReminders.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val productPrices by viewModel.allPrices.collectAsStateWithLifecycle()
    val allRemarks by viewModel.allRemarks.collectAsStateWithLifecycle()
    val inAppNotifications by viewModel.inAppNotifications.collectAsStateWithLifecycle()

    // Map location number to location name for easy lookup
    val locationMap = remember(locations) {
        locations.associate { it.locationNumber to it.locationName }
    }

    // Keep track of which shop details are expanded
    var expandedShops by remember { mutableStateOf(setOf<String>()) }

    // Collapsible sections
    var isInAppExpanded by remember { mutableStateOf(true) }
    var isTodayExpanded by remember { mutableStateOf(true) }
    var isMissedExpanded by remember { mutableStateOf(true) }

    // Collapsible location sub-sections
    var collapsedTodayLocations by remember { mutableStateOf(setOf<String>()) }
    var collapsedMissedLocations by remember { mutableStateOf(setOf<String>()) }

    // Partition reminders
    val todayReminders = remember(dueReminders) {
        dueReminders.filter { it.daysSince == it.interval }
    }
    val missedReminders = remember(dueReminders) {
        dueReminders.filter { it.daysSince > it.interval }
    }

    // Group by location
    val todayRemindersByLocation = remember(todayReminders) {
        todayReminders.groupBy { it.shop.locationNumber }
    }
    val missedRemindersByLocation = remember(missedReminders) {
        missedReminders.groupBy { it.shop.locationNumber }
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
                                        onMarkCompleted = { viewModel.markReminderCompleted(reminder.shop.shopNumber) }
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
                            val isLocCollapsed = collapsedMissedLocations.contains(locNum)
                            item(key = "missed_loc_hdr_$locNum") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            collapsedMissedLocations = if (isLocCollapsed) {
                                                collapsedMissedLocations - locNum
                                            } else {
                                                collapsedMissedLocations + locNum
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
                                        onMarkCompleted = { viewModel.markReminderCompleted(reminder.shop.shopNumber) }
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

    val summaryItems = remember(reminders, products, productPrices, locationMap) {
        reminders.flatMap { reminder ->
            reminder.recommendedProducts.map { (prodName, recommendedQty) ->
                val matchingProduct = products.find { it.productName == prodName }
                val variety = matchingProduct?.productCategory ?: "Standard"
                val latestSalePrice = reminder.lastSaleProducts.find { it.productName == prodName }?.sellingPrice
                val matchingPrice = productPrices.find { it.productId == matchingProduct?.id }?.sellingPrice ?: 0.0
                val sellingPrice = latestSalePrice ?: matchingPrice
                val locationName = locationMap[reminder.shop.locationNumber] ?: "Location ${reminder.shop.locationNumber}"
                
                Triple(locationName, variety, sellingPrice) to recommendedQty
            }
        }
        .groupBy { it.first }
        .map { (key, entries) ->
            val totalPackets = entries.sumOf { it.second }
            Triple(key.first, key.second, key.third) to totalPackets
        }
        .sortedWith(compareBy({ it.first.first }, { it.first.second }, { it.first.third }))
    }

    if (summaryItems.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .testTag(testTag)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Handyman,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Stock Preparation Summary",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                val summaryByLocation = summaryItems.groupBy { it.first.first }
                summaryByLocation.forEach { (locName, entries) ->
                    Text(
                        text = locName,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    entries.forEach { entry ->
                        val (_, variety, price) = entry.first
                        val totalPackets = entry.second
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• $variety ₹${String.format(Locale.getDefault(), "%.2f", price)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                            )
                            Text(
                                text = "$totalPackets packets",
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
    onMarkCompleted: () -> Unit
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

                Spacer(modifier = Modifier.width(8.dp))

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
