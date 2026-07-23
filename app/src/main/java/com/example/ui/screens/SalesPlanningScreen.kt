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
import androidx.compose.runtime.saveable.rememberSaveable
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

    // Group overdue reminder shops by location number
    val groupedReminders = remember(dueReminders) {
        dueReminders.groupBy { it.shop.locationNumber }
    }

    // Keep track of which location groups are expanded
    var expandedLocations by remember { mutableStateOf(setOf<String>()) }
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
                                    "Prepare packet stocks below based on past sales averages for overdue shops.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Locations list
                items(groupedReminders.keys.toList()) { locNo ->
                    val locName = locationMap[locNo] ?: "Location $locNo"
                    val remindersInLoc = groupedReminders[locNo] ?: emptyList()
                    val isLocExpanded = expandedLocations.contains(locNo)

                    // Calculate total packets across all shops in this location
                    val totalPacketsInLocation = remindersInLoc.sumOf { rem ->
                        rem.recommendedProducts.values.sum()
                    }

                    // Product-wise summary for this location
                    val locationProductSummary = mutableMapOf<String, Int>()
                    remindersInLoc.forEach { rem ->
                        rem.recommendedProducts.forEach { (prodName, qty) ->
                            locationProductSummary[prodName] = (locationProductSummary[prodName] ?: 0) + qty
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column {
                            // Location Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedLocations = if (isLocExpanded) {
                                            expandedLocations - locNo
                                        } else {
                                            expandedLocations + locNo
                                        }
                                    }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = locName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${remindersInLoc.size} shops overdue • Total $totalPacketsInLocation packets recommended",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isLocExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle Location",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Location Summary Info & Shops under Location
                            if (isLocExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Stock preparation box for this location
                                    if (locationProductSummary.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Luggage,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    "Preparation Checklist: $locName",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            locationProductSummary.forEach { (prod, qty) ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        prod,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        "$qty packets",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Render each shop in this location
                                    remindersInLoc.forEach { reminder ->
                                        val shop = reminder.shop
                                        val isShopExpanded = expandedShops.contains(shop.shopNumber)
                                        val shopSalesRemark = allRemarks.firstOrNull { 
                                            it.shopNumber == shop.shopNumber && it.status == "Pending" 
                                        }?.remark ?: shop.notes

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .animateContentSize(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Column {
                                                // Shop item row
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            expandedShops = if (isShopExpanded) {
                                                                expandedShops - shop.shopNumber
                                                            } else {
                                                                expandedShops + shop.shopNumber
                                                            }
                                                        }
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = shop.storeName,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                        Text(
                                                            text = "Last sale: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(reminder.lastSaleDate))} (${reminder.daysSince} days ago)",
                                                            fontSize = 11.sp,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${reminder.recommendedProducts.values.sum()} pkts",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Icon(
                                                            imageVector = if (isShopExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                            contentDescription = "Toggle Shop",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }

                                                if (isShopExpanded) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp)
                                                            .padding(bottom = 12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        if (!shopSalesRemark.isNullOrBlank()) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color.Yellow.copy(alpha = 0.15f))
                                                                    .padding(8.dp),
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Feedback,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFFE5A93C),
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Text(
                                                                    text = "Remark: $shopSalesRemark",
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                            }
                                                        }

                                                        // Recommended products list details
                                                        Text(
                                                            "Recommended Stock Options:",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )

                                                        if (reminder.recommendedProducts.isEmpty()) {
                                                            Text(
                                                                "No previous sales data available to calculate recommendations.",
                                                                fontSize = 11.sp,
                                                                color = Color.Gray
                                                            )
                                                        } else {
                                                            reminder.recommendedProducts.forEach { (prodName, recommendedQty) ->
                                                                // Find matching product master and product price
                                                                val matchingProduct = products.find { it.productName == prodName }
                                                                val matchingPrice = matchingProduct?.let { p ->
                                                                    productPrices.find { it.productId == p.id }
                                                                }

                                                                val priceString = matchingPrice?.let { "₹${it.sellingPrice}" } ?: "N/A"
                                                                val category = matchingProduct?.productCategory ?: "Standard"

                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Column {
                                                                        Text(
                                                                            prodName,
                                                                            fontWeight = FontWeight.SemiBold,
                                                                            fontSize = 12.sp
                                                                        )
                                                                        Text(
                                                                            "Variety: $category • Price: $priceString",
                                                                            fontSize = 10.sp,
                                                                            color = Color.Gray
                                                                        )
                                                                    }
                                                                    Text(
                                                                        text = "$recommendedQty packets",
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 12.sp,
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(4.dp))

                                                        // Dismiss button
                                                        Button(
                                                            onClick = { viewModel.markReminderCompleted(shop.shopNumber) },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = MaterialTheme.colorScheme.primary
                                                            ),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .testTag("dismiss_reminder_${shop.shopNumber}"),
                                                            shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Mark as Visited / Completed", fontSize = 12.sp)
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
        }
    }
}
