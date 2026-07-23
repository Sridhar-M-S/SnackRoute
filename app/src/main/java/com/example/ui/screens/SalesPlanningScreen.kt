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

    // Map location number to location name for easy lookup
    val locationMap = remember(locations) {
        locations.associate { it.locationNumber to it.locationName }
    }

    // Keep track of which shop details are expanded
    var expandedShops by remember { mutableStateOf(setOf<String>()) }

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
        if (dueReminders.isEmpty()) {
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
                        "There are no shops due for a sales reminder.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            // Group overdue/upcoming reminder shops by daily notification date/day
            val groupedByDay = remember(dueReminders) {
                dueReminders.groupBy { reminder ->
                    val diff = reminder.daysDifference
                    if (diff <= 0) 0 else diff
                }.toSortedMap()
            }

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
                                    "Prepare stock based on past sales averages, organized by reminder dates.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Render each Daily Notification Group
                groupedByDay.forEach { (dayKey, remindersInGroup) ->
                    val groupTitle = when (dayKey) {
                        0 -> "Today's Reminders"
                        1 -> "Tomorrow's Reminders"
                        2 -> "Next Day's Reminders"
                        else -> {
                            val calendar = Calendar.getInstance()
                            calendar.add(Calendar.DAY_OF_YEAR, dayKey)
                            SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(calendar.time)
                        }
                    }

                    // Consolidated Manufacturing Summary for this day group
                    val summaryItems = remindersInGroup.flatMap { reminder ->
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

                    // 1. Group Header Item
                    item(key = "header_$dayKey") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth().testTag("group_header_$dayKey")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = groupTitle,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "${remindersInGroup.size} shops scheduled",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                Button(
                                    onClick = { 
                                        val shopNos = remindersInGroup.map { it.shop.shopNumber }
                                        viewModel.markRemindersCompleted(shopNos)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        contentColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("mark_group_done_$dayKey")
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

                    // 2. Group Manufacturing Summary Item
                    if (summaryItems.isNotEmpty()) {
                        item(key = "summary_$dayKey") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("mfg_summary_$dayKey")
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
                                    
                                    // Group and render entries beautifully by location
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

                    // 3. Reminder Cards list for this group
                    items(remindersInGroup, key = { "rem_${dayKey}_${it.shop.shopNumber}" }) { reminder ->
                        val shop = reminder.shop
                        val isShopExpanded = expandedShops.contains(shop.shopNumber)
                        val locName = locationMap[shop.locationNumber] ?: "Location ${shop.locationNumber}"

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
                                            .clickable {
                                                expandedShops = if (isShopExpanded) {
                                                    expandedShops - shop.shopNumber
                                                } else {
                                                    expandedShops + shop.shopNumber
                                                }
                                            },
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
                                        onClick = { viewModel.markReminderCompleted(shop.shopNumber) },
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
                                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

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
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
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
                }
            }
        }
    }
}
