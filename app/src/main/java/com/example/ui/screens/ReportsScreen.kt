package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppViewModel
import com.example.utils.Exporter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class PriceBreakdown(
    val sellingPrice: Double,
    val packetsSold: Int,
    val revenue: Double,
    val profit: Double
)

data class ProductPerformance(
    val productName: String,
    val breakdowns: List<PriceBreakdown>,
    val totalPacketsSold: Int,
    val totalRevenue: Double,
    val totalProfit: Double,
    val maxPrice: Double,
    val minPrice: Double
)

data class MonthlyData(
    val monthYear: String,
    val totalPackets: Int,
    val totalSales: Double,
    val totalProfit: Double,
    val transactionCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: AppViewModel,
    onOpenChat: () -> Unit,
    onOpenTimetable: () -> Unit,
    onBack: () -> Unit = {},
    showBackButton: Boolean = false
) {
    val context = LocalContext.current
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val shops by viewModel.shops.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()

    var activeReportTab by remember { mutableStateOf("Summary") } // Summary, Profit, Shops, Products, Pending
    val listState = rememberLazyListState()
    val tabs = listOf("Summary", "Profit", "Shops", "Products", "Pending", "Growth")

    // --- Product Performance Filters State ---
    var filterProduct by remember { mutableStateOf("All") }
    var filterPrice by remember { mutableStateOf("All") }
    var filterLocation by remember { mutableStateOf("All") }
    var filterShop by remember { mutableStateOf("All") }
    var filterDateOption by remember { mutableStateOf("All") } // All, Today, Yesterday, Last 7 Days, Last 30 Days, Custom Range
    var filterStartDate by remember { mutableStateOf<Long?>(null) }
    var filterEndDate by remember { mutableStateOf<Long?>(null) }
    var productSortBy by remember { mutableStateOf("Highest Packets Sold") }
    var filtersExpanded by remember { mutableStateOf(false) }
    var expandedProducts by remember { mutableStateOf(setOf<String>()) }
    
    // --- Chart State ---
    var chartType by remember { mutableStateOf("Bar") } // Bar, Line
    var chartMetric by remember { mutableStateOf("Packets") } // Packets, Sales, Profit, Transactions
    var selectedMonthData by remember { mutableStateOf<MonthlyData?>(null) }

    // --- Date Calculators ---
    val startOfDay = { timestamp: Long ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    val endOfDay = { timestamp: Long ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        cal.timeInMillis
    }

    val now = System.currentTimeMillis()
    val startOfToday = startOfDay(now)
    val endOfToday = endOfDay(now)

    val calYest = Calendar.getInstance()
    calYest.add(Calendar.DAY_OF_YEAR, -1)
    val startOfYesterday = startOfDay(calYest.timeInMillis)
    val endOfYesterday = endOfDay(calYest.timeInMillis)

    val cal7 = Calendar.getInstance()
    cal7.add(Calendar.DAY_OF_YEAR, -6)
    val startOfLast7 = startOfDay(cal7.timeInMillis)

    val cal30 = Calendar.getInstance()
    cal30.add(Calendar.DAY_OF_YEAR, -29)
    val startOfLast30 = startOfDay(cal30.timeInMillis)

    // --- Filter Dropdown Options ---
    val productOptions = remember(sales) {
        listOf("All") + sales.map { it.productName }.distinct().sorted()
    }

    val priceOptions = remember(sales) {
        listOf("All") + sales.map { it.ratePerPacket }.distinct().sorted().map { "₹${it.toInt()}" }
    }

    val locationOptions = remember(sales, locations) {
        listOf("All") + sales.map { it.locationNumber }.distinct().map { locNum ->
            val name = locations.firstOrNull { it.locationNumber == locNum }?.locationName ?: locNum
            "$name ($locNum)"
        }.sorted()
    }

    val shopOptions = remember(sales) {
        listOf("All") + sales.map { "${it.shopName} (${it.shopNumber})" }.distinct().sorted()
    }

    // --- Filter Processing ---
    val selectedLocNum = if (filterLocation == "All") {
        "All"
    } else {
        val match = Regex("\\(([^)]+)\\)").find(filterLocation)
        match?.groupValues?.get(1) ?: filterLocation
    }

    val selectedShopNum = if (filterShop == "All") {
        "All"
    } else {
        val match = Regex("\\(([^)]+)\\)").find(filterShop)
        match?.groupValues?.get(1) ?: filterShop
    }

    val targetPriceDouble = if (filterPrice == "All") null else filterPrice.replace("₹", "").toDoubleOrNull()

    val filteredSalesForProducts = remember(
        sales, filterProduct, filterPrice, filterLocation, filterShop, filterDateOption, filterStartDate, filterEndDate
    ) {
        sales.filter { sale ->
            val matchProduct = filterProduct == "All" || sale.productName == filterProduct
            val matchPrice = targetPriceDouble == null || sale.ratePerPacket == targetPriceDouble
            val matchLocation = selectedLocNum == "All" || sale.locationNumber == selectedLocNum
            val matchShop = selectedShopNum == "All" || sale.shopNumber == selectedShopNum
            
            val matchDate = when (filterDateOption) {
                "Today" -> sale.entryDate in startOfToday..endOfToday
                "Yesterday" -> sale.entryDate in startOfYesterday..endOfYesterday
                "Last 7 Days" -> sale.entryDate >= startOfLast7
                "Last 30 Days" -> sale.entryDate >= startOfLast30
                "Custom Range" -> {
                    val start = filterStartDate?.let { startOfDay(it) } ?: 0L
                    val end = filterEndDate?.let { endOfDay(it) } ?: Long.MAX_VALUE
                    sale.entryDate in start..end
                }
                else -> true
            }
            
            matchProduct && matchPrice && matchLocation && matchShop && matchDate
        }
    }

    val monthlySalesData = remember(filteredSalesForProducts) {
        filteredSalesForProducts.groupBy {
            SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(it.entryDate))
        }.map { (monthYear, entries) ->
            MonthlyData(
                monthYear = monthYear,
                totalPackets = entries.sumOf { it.packetsSold },
                totalSales = entries.sumOf { it.totalAmount },
                totalProfit = entries.sumOf { it.totalProfit },
                transactionCount = entries.size
            )
        }.sortedBy {
            try {
                SimpleDateFormat("MMM yyyy", Locale.getDefault()).parse(it.monthYear)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    }

    val productPerformances = remember(filteredSalesForProducts) {
        filteredSalesForProducts.groupBy { it.productName }.map { (prodName, entries) ->
            val breakdowns = entries.groupBy { it.ratePerPacket }.map { (price, priceEntries) ->
                val pkts = priceEntries.sumOf { it.packetsSold }
                val rev = pkts * price
                val prof = priceEntries.sumOf { it.totalProfit }
                PriceBreakdown(
                    sellingPrice = price,
                    packetsSold = pkts,
                    revenue = rev,
                    profit = prof
                )
            }.sortedByDescending { it.sellingPrice }

            val totalPkts = breakdowns.sumOf { it.packetsSold }
            val totalRev = breakdowns.sumOf { it.revenue }
            val totalProf = breakdowns.sumOf { it.profit }
            val maxPrice = breakdowns.maxOfOrNull { it.sellingPrice } ?: 0.0
            val minPrice = breakdowns.minOfOrNull { it.sellingPrice } ?: 0.0

            ProductPerformance(
                productName = prodName,
                breakdowns = breakdowns,
                totalPacketsSold = totalPkts,
                totalRevenue = totalRev,
                totalProfit = totalProf,
                maxPrice = maxPrice,
                minPrice = minPrice
            )
        }
    }

    val sortedProducts = remember(productPerformances, productSortBy) {
        when (productSortBy) {
            "Highest Packets Sold" -> productPerformances.sortedByDescending { it.totalPacketsSold }
            "Lowest Packets Sold" -> productPerformances.sortedBy { it.totalPacketsSold }
            "Highest Revenue" -> productPerformances.sortedByDescending { it.totalRevenue }
            "Highest Profit" -> productPerformances.sortedByDescending { it.totalProfit }
            "Highest Selling Price" -> productPerformances.sortedByDescending { it.maxPrice }
            "Lowest Selling Price" -> productPerformances.sortedBy { it.minPrice }
            else -> productPerformances.sortedByDescending { it.totalPacketsSold }
        }
    }

    // --- Calculations ---
    val totalRevenue = sales.sumOf { it.totalAmount }
    val totalProfit = sales.sumOf { it.totalProfit }
    val totalVolume = sales.sumOf { it.packetsSold }

    // Grouping for reports
    val locationWise = sales.groupBy { it.locationNumber }.mapValues { (_, entries) ->
        Pair(entries.sumOf { it.totalAmount }, entries.sumOf { it.totalProfit })
    }
    
    val shopWise = sales.groupBy { it.shopNumber }.mapValues { (shopNum, entries) ->
        val storeName = shops.firstOrNull { it.shopNumber == shopNum }?.storeName ?: shopNum
        Triple(storeName, entries.sumOf { it.totalAmount }, entries.sumOf { it.totalProfit })
    }

    val productWise = sales.groupBy { it.productName }.mapValues { (_, entries) ->
        Pair(entries.sumOf { it.packetsSold }, entries.sumOf { it.totalProfit })
    }

    val pendingCollectionsList = sales.filter { it.status == "Pending" || it.status == "Partially Paid" }
    val pendingAmount = pendingCollectionsList.sumOf { it.totalAmount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Reports", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onOpenTimetable,
                            modifier = Modifier.testTag("open_timetable_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Weekly Timetable",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenChat,
                        modifier = Modifier.testTag("open_ai_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "AI Assistant",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            Exporter.exportSales(context, sales)
                            Toast.makeText(context, "Full Sales Ledger exported", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("export_reports_all_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export Full Ledger")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- Tabs Scrollable Row ---
            LaunchedEffect(activeReportTab) {
                listState.scrollToItem(0)
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(tabs) { tab ->
                    FilterChip(
                        selected = activeReportTab == tab,
                        onClick = { activeReportTab = tab },
                        label = { Text(tab) }
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                when (activeReportTab) {
                    "Summary" -> {
                        item {
                            Text("Snack Distribution KPIs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ReportStatCard(label = "Total Volume", value = "$totalVolume Packs", color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.weight(1f))
                                ReportStatCard(label = "Total Revenue", value = "₹${"%.2f".format(totalRevenue)}", color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f))
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Route-Wise Distribution Analysis", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (locationWise.isEmpty()) {
                                        Text("Log sales to visualize route-wise performance", fontSize = 12.sp, color = Color.Gray)
                                    } else {
                                        // Custom Canvas Bar Chart for locations
                                        val locList = locationWise.toList().take(5)
                                        val maxVal = locList.maxOf { it.second.second }.coerceAtLeast(1.0)
                                        
                                        // Combined Bar Chart and Legends
                                        Row(
                                            modifier = Modifier.fillMaxWidth().height(180.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            locList.forEach { (locCode, values) ->
                                                val locName = locations.firstOrNull { it.locationNumber == locCode }?.locationName ?: locCode
                                                val profit = values.second
                                                val barHeight = (profit / maxVal * 100.0).coerceAtLeast(10.0)
                                                
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    // Bar
                                                    Box(
                                                        modifier = Modifier
                                                            .width(32.dp)
                                                            .height(barHeight.dp)
                                                            .background(Color(0xFFFF9100), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                    )
                                                    // Name
                                                    Text(
                                                        text = locName,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    // Profit
                                                    Text(
                                                        text = "₹${profit.toInt()}",
                                                        fontSize = 9.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("Monthly Distribution Timeline", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterDropdown(
                                            label = "Type",
                                            selected = chartType,
                                            options = listOf("Bar", "Line"),
                                            onSelected = { chartType = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterDropdown(
                                            label = "Metric",
                                            selected = chartMetric,
                                            options = listOf("Packets", "Sales", "Profit", "Transactions"),
                                            onSelected = { chartMetric = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    MonthlyChart(
                                        data = monthlySalesData,
                                        chartType = chartType,
                                        chartMetric = chartMetric,
                                        onMonthSelected = { selectedMonthData = it }
                                    )
                                    selectedMonthData?.let {
                                        Text("Selected: ${it.monthYear} - ${chartMetric}: ${when(chartMetric){"Packets" -> it.totalPackets; "Sales" -> "₹${it.totalSales}"; "Profit" -> "₹${it.totalProfit}"; else -> it.transactionCount}}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    "Profit" -> {
                        item {
                            Text("Profit Margin & Performance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Accumulated Profit: ₹${"%.2f".format(totalProfit)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    Text("Average margin per packet: ₹${"%.2f".format(if (totalVolume > 0) totalProfit / totalVolume else 0.0)}", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                        item {
                            Text("Top Routes by Net Profit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        items(locationWise.toList().sortedByDescending { it.second.second }) { (locCode, values) ->
                            val locName = locations.firstOrNull { it.locationNumber == locCode }?.locationName ?: locCode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(locName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("₹${"%.2f".format(values.second)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 13.sp)
                            }
                        }
                    }

                    "Shops" -> {
                        item {
                            Text("Store Master Performance Rankings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        val sortedShops = shopWise.toList().sortedByDescending { it.second.third }
                        if (sortedShops.isEmpty()) {
                            item { Text("No store log data available", fontSize = 12.sp, color = Color.Gray) }
                        } else {
                            items(sortedShops) { (shopNum, details) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(details.first, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Shop Code: $shopNum", fontSize = 11.sp, color = Color.Gray)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            val ratingVal = shops.firstOrNull { it.shopNumber == shopNum }?.rating ?: 1.0f
                                            Text("${"%.1f".format(ratingVal)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("₹${"%.2f".format(details.third)} Profit", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("₹${"%.2f".format(details.second)} Rev", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }

                    "Products" -> {
                        item {
                            Text("Snack Variant Performance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        // --- Filters Toggle & Panel ---
                        item {
                            val isAnyFilterApplied = filterProduct != "All" || filterPrice != "All" || filterLocation != "All" || filterShop != "All" || filterDateOption != "All"
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("product_filters_card"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isAnyFilterApplied) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FilterList,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = if (isAnyFilterApplied) "Filters Active" else "Filters & Sorting",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isAnyFilterApplied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        IconButton(
                                            onClick = { filtersExpanded = !filtersExpanded },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = "Toggle Filters",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    if (filtersExpanded) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilterDropdown(
                                                    label = "Product",
                                                    selected = filterProduct,
                                                    options = productOptions,
                                                    onSelected = { filterProduct = it },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                FilterDropdown(
                                                    label = "Selling Price",
                                                    selected = filterPrice,
                                                    options = priceOptions,
                                                    onSelected = { filterPrice = it },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilterDropdown(
                                                    label = "Location Route",
                                                    selected = filterLocation,
                                                    options = locationOptions,
                                                    onSelected = { filterLocation = it },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                FilterDropdown(
                                                    label = "Shop / Customer",
                                                    selected = filterShop,
                                                    options = shopOptions,
                                                    onSelected = { filterShop = it },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilterDropdown(
                                                    label = "Date / Timeline",
                                                    selected = filterDateOption,
                                                    options = listOf("All", "Today", "Yesterday", "Last 7 Days", "Last 30 Days", "Custom Range"),
                                                    onSelected = { filterDateOption = it },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                FilterDropdown(
                                                    label = "Sort Performance By",
                                                    selected = productSortBy,
                                                    options = listOf(
                                                        "Highest Packets Sold",
                                                        "Lowest Packets Sold",
                                                        "Highest Revenue",
                                                        "Highest Profit",
                                                        "Highest Selling Price",
                                                        "Lowest Selling Price"
                                                    ),
                                                    onSelected = { productSortBy = it },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            // Custom Range Picker Callbacks
                                            if (filterDateOption == "Custom Range") {
                                                val showDatePicker = { onDateSelected: (Long) -> Unit ->
                                                    val calendar = Calendar.getInstance()
                                                    android.app.DatePickerDialog(
                                                        context,
                                                        { _, year, month, dayOfMonth ->
                                                            val cal = Calendar.getInstance()
                                                            cal.set(Calendar.YEAR, year)
                                                            cal.set(Calendar.MONTH, month)
                                                            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                            onDateSelected(cal.timeInMillis)
                                                        },
                                                        calendar.get(Calendar.YEAR),
                                                        calendar.get(Calendar.MONTH),
                                                        calendar.get(Calendar.DAY_OF_MONTH)
                                                    ).show()
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { showDatePicker { date -> filterStartDate = date } },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                                                            Text("Start Date", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                                            Text(
                                                                text = filterStartDate?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "Select Start Date",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                    }

                                                    OutlinedButton(
                                                        onClick = { showDatePicker { date -> filterEndDate = date } },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                                                            Text("End Date", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                                            Text(
                                                                text = filterEndDate?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "Select End Date",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (isAnyFilterApplied) {
                                                TextButton(
                                                    onClick = {
                                                        filterProduct = "All"
                                                        filterPrice = "All"
                                                        filterLocation = "All"
                                                        filterShop = "All"
                                                        filterDateOption = "All"
                                                        filterStartDate = null
                                                        filterEndDate = null
                                                    },
                                                    modifier = Modifier.align(Alignment.End)
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Reset Filters", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // --- Excel Export Button ---
                        item {
                            Button(
                                onClick = {
                                    val exportItems = sortedProducts.flatMap { prod ->
                                        prod.breakdowns.map { breakdown ->
                                            Exporter.ProductPerformanceExportItem(
                                                productName = prod.productName,
                                                sellingPrice = breakdown.sellingPrice,
                                                packetsSold = breakdown.packetsSold,
                                                revenue = breakdown.revenue,
                                                estimatedProfit = breakdown.profit
                                            )
                                        }
                                    }
                                    if (exportItems.isEmpty()) {
                                        Toast.makeText(context, "No performance data to export", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Exporter.exportProductPerformance(context, exportItems)
                                        Toast.makeText(context, "Product Performance report exported", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("export_product_perf_excel_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Product Performance to Excel")
                            }
                        }

                        // --- Product Performance List ---
                        if (sortedProducts.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            "No matching product logs found",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            "Try resetting your active filters.",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        } else {
                            items(sortedProducts) { prod ->
                                val isExpanded = expandedProducts.contains(prod.productName)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedProducts = if (isExpanded) {
                                                expandedProducts - prod.productName
                                            } else {
                                                expandedProducts + prod.productName
                                            }
                                        }
                                        .testTag("product_perf_card_${prod.productName}"),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Collapsed View / Card Header
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = prod.productName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${prod.totalPacketsSold} total packets sold",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Expanded View
                                        if (isExpanded) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                                Spacer(modifier = Modifier.height(12.dp))
                                                
                                                prod.breakdowns.forEach { breakdown ->
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                        )
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(12.dp),
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "Selling Price ₹${breakdown.sellingPrice.toInt()}",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 13.sp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text("Packets Sold :", fontSize = 12.sp, color = Color.Gray)
                                                                Text("${breakdown.packetsSold}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                            }
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text("Revenue :", fontSize = 12.sp, color = Color.Gray)
                                                                Text("₹${"%.2f".format(breakdown.revenue)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                            }
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text("Estimated Profit :", fontSize = 12.sp, color = Color.Gray)
                                                                Text("₹${"%.2f".format(breakdown.profit)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32))
                                                            }
                                                        }
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.height(8.dp))
                                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                                Spacer(modifier = Modifier.height(12.dp))
                                                
                                                // Product Summary
                                                Text(
                                                    text = "Product Summary",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Total Packets Sold :", fontSize = 12.sp, color = Color.Gray)
                                                        Text("${prod.totalPacketsSold}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Total Revenue :", fontSize = 12.sp, color = Color.Gray)
                                                        Text("₹${"%.2f".format(prod.totalRevenue)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Total Estimated Profit :", fontSize = 12.sp, color = Color.Gray)
                                                        Text("₹${"%.2f".format(prod.totalProfit)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Growth" -> {
                        item {
                            val data by viewModel.monthlyGrowth.collectAsStateWithLifecycle()
                            if (data != null) {
                                val d = data!!
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("Monthly Growth Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    GrowthMetricCard("Packets", d.currentMonth.packets.toString(), d.previousMonth.packets.toString(), d.packetsGrowthPercent)
                                    GrowthMetricCard("Sales", "₹${"%.2f".format(d.currentMonth.sales)}", "₹${"%.2f".format(d.previousMonth.sales)}", d.salesGrowthPercent)
                                    GrowthMetricCard("Profit", "₹${"%.2f".format(d.currentMonth.profit)}", "₹${"%.2f".format(d.previousMonth.profit)}", d.profitGrowthPercent)

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Comparison Chart", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    SimpleComparisonChart(d)
                                }
                            } else {
                                Text("No growth data available")
                            }
                        }
                    }
                    "Pending" -> {
                        item {
                            Text("Pending Collection Pipeline", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Total Pending Collections", fontSize = 12.sp)
                                        Text("₹${"%.2f".format(pendingAmount)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                        if (pendingCollectionsList.isEmpty()) {
                            item { Text("All collections are paid perfectly! Great job!", fontSize = 13.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold) }
                        } else {
                            items(pendingCollectionsList) { sale ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(sale.shopName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${sale.entryDateFormatted} • ${sale.productName}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("₹${"%.2f".format(sale.totalAmount)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(sale.status, fontSize = 11.sp, color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold)
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

@Composable
fun ReportStatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(85.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MonthlyChart(
    data: List<MonthlyData>,
    chartType: String,
    chartMetric: String,
    onMonthSelected: (MonthlyData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text("No data for the selected period", color = Color.Gray)
        }
        return
    }

    // Display Range
    Text(
        text = "Showing Data: ${data.first().monthYear} → ${data.last().monthYear}",
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        textAlign = TextAlign.Center
    )

    val maxVal = when (chartMetric) {
        "Packets" -> data.maxOf { it.totalPackets }.toDouble().coerceAtLeast(1.0)
        "Sales" -> data.maxOf { it.totalSales }.coerceAtLeast(1.0)
        "Profit" -> data.maxOf { it.totalProfit }.coerceAtLeast(1.0)
        else -> data.maxOf { it.transactionCount }.toDouble().coerceAtLeast(1.0)
    }

    LazyRow(
        modifier = modifier.fillMaxWidth().height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(data) { item ->
            val value = when (chartMetric) {
                "Packets" -> item.totalPackets.toDouble()
                "Sales" -> item.totalSales
                "Profit" -> item.totalProfit
                else -> item.transactionCount.toDouble()
            }
            val barHeight = ((value / maxVal) * 100.0).coerceAtLeast(10.0)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(40.dp).clickable { onMonthSelected(item) }
            ) {
                // Chart Element
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (chartType == "Bar") {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(barHeight.dp)
                                .background(Color(0xFF6200EE), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(0xFF6200EE), CircleShape)
                        )
                    }
                }
                
                // Month Label
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.monthYear.substringBefore(" "),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FilterDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                    Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = selected,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 12.sp) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun GrowthMetricCard(title: String, current: String, previous: String, growthPercent: Double) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Curr: $current", style = MaterialTheme.typography.bodyMedium)
                Text("Prev: $previous", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "${if (growthPercent >= 0) "+" else ""}${String.format("%.1f", growthPercent)}% ${if (growthPercent >= 0) "Increase" else "Decrease"}",
                color = if (growthPercent >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun SimpleComparisonChart(data: AppViewModel.MonthlyGrowthData) {
    val values = listOf(
        Pair("Prev Packets", data.previousMonth.packets.toFloat()),
        Pair("Curr Packets", data.currentMonth.packets.toFloat()),
        Pair("Prev Sales", data.previousMonth.sales.toFloat()),
        Pair("Curr Sales", data.currentMonth.sales.toFloat())
    )
    
    val maxVal = values.maxOfOrNull { it.second } ?: 1f
    
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp)) {
        val barWidth = size.width / (values.size * 2)
        values.forEachIndexed { index, pair ->
            val barHeight = (pair.second / maxVal) * size.height
            drawRect(
                color = if (index % 2 == 0) Color.Gray else Color.Blue,
                topLeft = Offset(index * (barWidth * 2) + barWidth / 2, size.height - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

