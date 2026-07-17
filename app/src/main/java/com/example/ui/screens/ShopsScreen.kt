package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.LocationMaster
import com.example.data.ShopMaster
import com.example.ui.AppViewModel
import com.example.utils.Exporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopsScreen(
    viewModel: AppViewModel,
    onNavigateToTab: (String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenTimetable: () -> Unit
) {
    val context = LocalContext.current
    val shops by viewModel.shops.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val nextShopNum by viewModel.nextShopNumber.collectAsStateWithLifecycle()
    val resolvedUrls by viewModel.resolvedUrls.collectAsStateWithLifecycle()
    val nearestQueryCoords by viewModel.nearestQueryCoords.collectAsStateWithLifecycle()
    val isResolvingQuery by viewModel.isResolvingQuery.collectAsStateWithLifecycle()

    val dailyTarget by viewModel.dailyTarget.collectAsStateWithLifecycle()
    val todayPackets by viewModel.todayPackets.collectAsStateWithLifecycle()
    val todaySales by viewModel.todaySales.collectAsStateWithLifecycle()
    val todayProfit by viewModel.todayProfit.collectAsStateWithLifecycle()
    val allBadges by viewModel.allBadges.collectAsStateWithLifecycle()
    val unlockedBadges by viewModel.unlockedBadges.collectAsStateWithLifecycle()

    fun extractCoordinates(text: String): Pair<Double, Double>? {
        if (text.isBlank()) return null
        val urlToParse = resolvedUrls[text] ?: text
        val decoded = try {
            java.net.URLDecoder.decode(urlToParse, "UTF-8")
        } catch (e: Exception) {
            urlToParse
        }

        // 1. Try to find @lat,lng format
        val atPattern = Regex("@(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)")
        atPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        // 2. Try to find parameter pattern e.g. q=lat,lng or query=lat,lng
        val paramPattern = Regex("(?:[?&](?:q|query|daddr|saddr|ll|cbll)=)(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)")
        // 2.5 Try to find place, dir or search path pattern: e.g. /place/lat,lng or /dir/lat,lng
        val pathPattern = Regex("/(?:place|dir|search)/(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)")
        pathPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }
        paramPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        // 3. Try DMS format: e.g. 12°58'17.8"N 77°35'40.4"E
        fun parseDMS(deg: String, min: String, sec: String, dir: String): Double? {
            val d = deg.toDoubleOrNull() ?: return null
            val m = min.toDoubleOrNull() ?: 0.0
            val s = sec.toDoubleOrNull() ?: 0.0
            var decimal = d + (m / 60.0) + (s / 3600.0)
            if (dir.equals("S", ignoreCase = true) || dir.equals("W", ignoreCase = true)) {
                decimal = -decimal
            }
            return decimal
        }

        val dmsRegex = Regex("(\\d+)[°\\s]+(\\d+)[\\'\\s]+(\\d+(?:\\.\\d+)?)\"?\\s*([NSEWnsew])")
        val dmsMatches = dmsRegex.findAll(decoded).toList()
        if (dmsMatches.size >= 2) {
            val lat = parseDMS(dmsMatches[0].groupValues[1], dmsMatches[0].groupValues[2], dmsMatches[0].groupValues[3], dmsMatches[0].groupValues[4])
            val lng = parseDMS(dmsMatches[1].groupValues[1], dmsMatches[1].groupValues[2], dmsMatches[1].groupValues[3], dmsMatches[1].groupValues[4])
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        // 4. Try generic decimal pair: e.g. "12.971598, 77.594562"
        val genericPattern = Regex("(-?\\d{1,3}\\.\\d+)[\\s,]+(-?\\d{1,3}\\.\\d+)")
        genericPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        return null
    }

    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()

    val excelImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importShopsFromExcel(context, uri)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val selectedLocationFilter by viewModel.shopLocationFilter.collectAsStateWithLifecycle()
    var sortBy by remember { mutableStateOf("Name") } // Name, Number, Rating, Date
    var sortAscending by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    var activeSubTab by remember { mutableStateOf("Directory") } // "Directory" or "Nearest Search"
    var nearestQuery by remember { mutableStateOf("") }

    LaunchedEffect(nearestQuery) {
        viewModel.resolveNearestQueryCoords(context, nearestQuery)
    }

    var showAddEditScreen by remember { mutableStateOf(false) }
    var selectedShopForEdit by remember { mutableStateOf<ShopMaster?>(null) }
    var selectedShopForDetail by remember { mutableStateOf<ShopMaster?>(null) }

    // Form fields
    var formShopNumber by remember { mutableStateOf("") }
    var storeName by remember { mutableStateOf("") }
    var selectedLocationCode by remember { mutableStateOf("") }
    var storeImageUri by remember { mutableStateOf<String?>(null) }
    var rating by remember { mutableStateOf(5f) }
    var startingDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var googleMapLink by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Form validation states
    var shopNumberError by remember { mutableStateOf<String?>(null) }
    var storeNameError by remember { mutableStateOf<String?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var mobileError by remember { mutableStateOf<String?>(null) }

    // Image selector launcher
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = viewModel.saveImageToStorage(uri)
            if (savedPath != null) {
                storeImageUri = savedPath
            } else {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Search & Filtering ---
    LaunchedEffect(searchQuery, selectedLocationFilter, sortBy, sortAscending) {
        listState.scrollToItem(0)
    }

    val filteredShops = remember(shops, searchQuery, selectedLocationFilter, sortBy, sortAscending, sales) {
        var list = shops.filter { shop ->
            val matchSearch = shop.storeName.contains(searchQuery, ignoreCase = true) ||
                    shop.shopNumber.contains(searchQuery, ignoreCase = true) ||
                    shop.locationNumber.contains(searchQuery, ignoreCase = true) ||
                    (shop.mobileNumber ?: "").contains(searchQuery)
            
            val matchFilter = selectedLocationFilter == null || shop.locationNumber == selectedLocationFilter
            matchSearch && matchFilter
        }

        val shopAnalyticsMap = list.associate { shop ->
            val salesForShop = sales.filter { it.shopNumber == shop.shopNumber }
            shop.shopNumber to com.example.utils.RatingCalculator.calculateAnalytics(salesForShop)
        }

        list = when (sortBy) {
            "Highest Rating" -> list.sortedByDescending { shopAnalyticsMap[it.shopNumber]?.currentRating ?: 0f }
            "Lowest Rating" -> list.sortedBy { shopAnalyticsMap[it.shopNumber]?.currentRating ?: 5f }
            "Most Regular Customer" -> list.sortedByDescending { shopAnalyticsMap[it.shopNumber]?.totalSalesTransactions ?: 0 }
            "Highest Revenue" -> list.sortedByDescending { shopAnalyticsMap[it.shopNumber]?.totalRevenue ?: 0.0 }
            "Highest Profit" -> list.sortedByDescending { shopAnalyticsMap[it.shopNumber]?.totalProfit ?: 0.0 }
            "Highest Packets Purchased" -> list.sortedByDescending { shopAnalyticsMap[it.shopNumber]?.totalPacketsPurchased ?: 0 }
            "Health Score (High → Low)" -> {
                val healthSelector: (ShopMaster) -> Int = { shop ->
                    val salesForShop = sales.filter { it.shopNumber == shop.shopNumber }
                    viewModel.calculateHealthScore(shop, salesForShop)
                }
                list.sortedByDescending(healthSelector)
            }
            "Health Score (Low → High)" -> {
                val healthSelector: (ShopMaster) -> Int = { shop ->
                    val salesForShop = sales.filter { it.shopNumber == shop.shopNumber }
                    viewModel.calculateHealthScore(shop, salesForShop)
                }
                list.sortedBy(healthSelector)
            }
            "Most Recent Purchase" -> list.sortedWith(compareByDescending<ShopMaster> { shopAnalyticsMap[it.shopNumber]?.lastPurchaseDate }.thenBy { it.shopNumber })
            "Number" -> {
                val numSelector: (ShopMaster) -> Double = { s ->
                    s.shopNumber.filter { it.isDigit() }.toDoubleOrNull() ?: Double.MAX_VALUE
                }
                if (sortAscending) list.sortedBy(numSelector) else list.sortedByDescending(numSelector)
            }
            "Rating" -> if (sortAscending) list.sortedBy { it.rating } else list.sortedByDescending { it.rating }
            "Date" -> if (sortAscending) list.sortedBy { it.startingDate } else list.sortedByDescending { it.startingDate }
            else -> if (sortAscending) list.sortedBy { it.storeName } else list.sortedByDescending { it.storeName }
        }
        list
    }

    if (!showAddEditScreen) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Shop Master", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
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
                        // Import Excel Button
                        IconButton(
                            onClick = {
                                excelImportLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            },
                            modifier = Modifier.testTag("import_shops_button")
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = "Import Excel")
                        }
                        
                        // Export Excel Button
                        IconButton(
                            onClick = { Exporter.exportShops(context, shops) },
                            modifier = Modifier.testTag("export_shops_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Export Excel")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        if (locations.isEmpty()) {
                            Toast.makeText(context, "Please create at least one location first!", Toast.LENGTH_LONG).show()
                        } else {
                            selectedShopForEdit = null
                            formShopNumber = nextShopNum
                            storeName = ""
                            selectedLocationCode = locations.first().locationNumber
                            storeImageUri = null
                            rating = 5f
                            startingDateMillis = System.currentTimeMillis()
                            googleMapLink = ""
                            mobileNumber = ""
                            notes = ""
                            shopNumberError = null
                            storeNameError = null
                            locationError = null
                            mobileError = null
                            showAddEditScreen = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_shop_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Shop")
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- Sub Tabs (Store Directory / Nearest Shops) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ElevatedAssistChip(
                        onClick = { activeSubTab = "Directory" },
                        label = { Text("Store Directory", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.elevatedAssistChipColors(
                            containerColor = if (activeSubTab == "Directory") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = if (activeSubTab == "Directory") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f).testTag("tab_store_directory")
                    )
                    ElevatedAssistChip(
                        onClick = { activeSubTab = "Nearest Search" },
                        label = { Text("Find Nearest", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.elevatedAssistChipColors(
                            containerColor = if (activeSubTab == "Nearest Search") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = if (activeSubTab == "Nearest Search") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f).testTag("tab_find_nearest")
                    )
                }

                if (activeSubTab == "Directory") {
                    // --- Search input ---
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by Shop Name, ID, Mobile...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_search_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // --- Active Filter Indicator ---
                    if (selectedLocationFilter != null) {
                        val loc = locations.firstOrNull { it.locationNumber == selectedLocationFilter }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Filtered by: ${loc?.locationNumber ?: ""} - ${loc?.locationName ?: ""}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(onClick = { viewModel.setShopLocationFilter(null) }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear Filter")
                                }
                            }
                        }
                    }

                    // --- Filters (Locations Selector & Sorting) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Location Filter dropdown
                        var filterExpanded by remember { mutableStateOf(false) }
                        Box {
                            FilterChip(
                                selected = selectedLocationFilter != null,
                                onClick = { filterExpanded = true },
                                label = { Text(selectedLocationFilter ?: "All Locations") },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                            )
                            DropdownMenu(
                                expanded = filterExpanded,
                                onDismissRequest = { filterExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Locations") },
                                    onClick = {
                                        viewModel.setShopLocationFilter(null)
                                        filterExpanded = false
                                    }
                                )
                                locations.forEach { loc ->
                                    DropdownMenuItem(
                                        text = { Text(loc.locationName) },
                                        onClick = {
                                            viewModel.setShopLocationFilter(loc.locationNumber)
                                            filterExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Sort menu
                        var sortExpanded by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                FilterChip(
                                    selected = true,
                                    onClick = { sortExpanded = true },
                                    label = { Text("Sort: $sortBy") },
                                    trailingIcon = { Icon(Icons.Default.Sort, contentDescription = null) }
                                )
                                DropdownMenu(
                                    expanded = sortExpanded,
                                    onDismissRequest = { sortExpanded = false }
                                ) {
                                    DropdownMenuItem(text = { Text("Name") }, onClick = { sortBy = "Name"; sortExpanded = false })
                                    DropdownMenuItem(text = { Text("Shop Number") }, onClick = { sortBy = "Number"; sortExpanded = false })
                                    DropdownMenuItem(text = { Text("Highest Rating") }, onClick = { sortBy = "Highest Rating"; sortExpanded = false })
                                    DropdownMenuItem(text = { Text("Lowest Rating") }, onClick = { sortBy = "Lowest Rating"; sortExpanded = false })
                                    DropdownMenuItem(text = { Text("Most Regular Customer") }, onClick = { sortBy = "Most Regular Customer"; sortExpanded = false })
                                    DropdownMenuItem(text = { Text("Highest Revenue") }, onClick = { sortBy = "Highest Revenue"; sortExpanded = false })
                                    DropdownMenuItem(text = { Text("Highest Profit") }, onClick = { sortBy = "Highest Profit"; sortExpanded = false })
                                    DropdownMenuItem(text = { Text("Highest Packets Purchased") }, onClick = { sortBy = "Highest Packets Purchased"; sortExpanded = false })
                                    DropdownMenuItem(text = { Text("Health Score (High → Low)") }, onClick = { sortBy = "Health Score (High → Low)"; sortExpanded = false })
                                    DropdownMenuItem(text = { Text("Health Score (Low → High)") }, onClick = { sortBy = "Health Score (Low → High)"; sortExpanded = false })
                                    DropdownMenuItem(text = { Text("Most Recent Purchase") }, onClick = { sortBy = "Most Recent Purchase"; sortExpanded = false })
                                    DropdownMenuItem(text = { Text("Date Added") }, onClick = { sortBy = "Date"; sortExpanded = false })
                                }
                            }
                            IconButton(onClick = { sortAscending = !sortAscending }) {
                                Icon(if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, contentDescription = "Toggle Sort Order")
                            }
                        }
                    }

                    // --- Shops List ---
                    if (filteredShops.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = if (searchQuery.isEmpty() && selectedLocationFilter == null) "No Shops Logged" else "No matching stores found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredShops, key = { it.shopNumber }) { shop ->
                                val locName = locations.firstOrNull { it.locationNumber == shop.locationNumber }?.locationName ?: shop.locationNumber
                                val score = viewModel.calculateHealthScore(shop, sales)
                                ShopCard(
                                    shop = shop,
                                    locationName = locName,
                                    healthScore = score,
                                    healthCategory = viewModel.getHealthCategory(score),
                                    onClick = { selectedShopForDetail = shop },
                                    onGoToSales = {
                                        viewModel.setSalesFilterShopNumber(shop.shopNumber)
                                        viewModel.setSalesSearchQuery(shop.storeName)
                                        onNavigateToTab("Sales")
                                    },
                                    onRecordSale = {
                                        viewModel.setPrefilledSaleData(shop.shopNumber, shop.storeName, locName)
                                        onNavigateToTab("Sales")
                                    },
                                    onEdit = {
                                        selectedShopForEdit = shop
                                        formShopNumber = shop.shopNumber
                                        storeName = shop.storeName
                                        selectedLocationCode = shop.locationNumber
                                        storeImageUri = shop.storeImage
                                        rating = shop.rating
                                        startingDateMillis = shop.startingDate
                                        googleMapLink = shop.googleMapLink ?: ""
                                        mobileNumber = shop.mobileNumber ?: ""
                                        notes = shop.notes ?: ""
                                        shopNumberError = null
                                        storeNameError = null
                                        locationError = null
                                        mobileError = null
                                        showAddEditScreen = true
                                    },
                                    onDelete = {
                                        viewModel.deleteShop(shop)
                                        Toast.makeText(context, "Shop deleted", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // --- Nearest Shops Search ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = nearestQuery,
                            onValueChange = { nearestQuery = it },
                            placeholder = { Text("Enter Location or Paste Link...") },
                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                            trailingIcon = {
                                if (nearestQuery.isNotEmpty()) {
                                    IconButton(onClick = { nearestQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nearest_search_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.resolveNearestQueryCoords(context, nearestQuery)
                            },
                            modifier = Modifier.testTag("nearest_search_button"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Search")
                        }
                    }

                    if (isResolvingQuery) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    if (nearestQuery.isNotEmpty() && !isResolvingQuery) {
                        val isLocationResolved = remember(nearestQuery, nearestQueryCoords, locations, shops) {
                            var coords = nearestQueryCoords
                            if (coords == null) {
                                coords = extractCoordinates(nearestQuery)
                            }
                            if (coords == null) {
                                val queryLower = nearestQuery.lowercase()
                                val matchedOffline = listOf("bangalore", "bengaluru", "indiranagar", "koramangala", "whitefield", "jayanagar", "m.g. road", "mg road", "malleshwaram", "hsr layout", "hebbal", "electronic city", "marathahalli", "btm layout", "rajajinagar", "banashankari", "yeshwanthpur", "bellandur", "yelahanka", "bannerghatta", "mysore", "mysuru", "mangalore", "mangaluru", "hubli", "belgaum", "dharwad", "delhi", "new delhi", "mumbai", "bombay", "chennai", "madras", "kolkata", "calcutta", "hyderabad", "pune", "ahmedabad", "jaipur", "kochi", "cochin").any { queryLower.contains(it) }
                                if (matchedOffline) {
                                    coords = Pair(0.0, 0.0)
                                }
                            }
                            if (coords == null) {
                                val matchedLoc = locations.any {
                                    it.locationNumber.equals(nearestQuery, ignoreCase = true) ||
                                    it.locationName.contains(nearestQuery, ignoreCase = true)
                                }
                                if (matchedLoc) {
                                    coords = Pair(0.0, 0.0)
                                }
                            }
                            if (coords == null) {
                                val matchedShop = shops.any {
                                    it.storeName.contains(nearestQuery, ignoreCase = true) ||
                                    it.shopNumber.equals(nearestQuery, ignoreCase = true) ||
                                    (it.notes ?: "").contains(nearestQuery, ignoreCase = true)
                                }
                                if (matchedShop) {
                                    coords = Pair(0.0, 0.0)
                                }
                            }
                            coords != null
                        }

                        if (!isLocationResolved) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Location not recognized. Showing closest shops to Central Bangalore.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp, 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Resolved",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Location coordinates successfully resolved & verified.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    val nearestShopsResult = remember(nearestQuery, nearestQueryCoords, shops, locations, sales, resolvedUrls) {
                        if (nearestQuery.isBlank()) {
                            emptyList<Pair<ShopMaster, Double>>()
                        } else {
                            val query = nearestQuery.trim()

                            // Standard Haversine distance in km
                            fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
                                val r = 6371.0
                                val dLat = Math.toRadians(lat2 - lat1)
                                val dLon = Math.toRadians(lon2 - lon1)
                                val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                                        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                                        Math.sin(dLon / 2) * Math.sin(dLon / 2)
                                val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                                return r * c
                            }

                            // 1. Read and validate coordinates for every shop.
                            // Do not include Shops with invalid coordinates in the nearest shop calculation.
                            val shopsWithValidCoords = shops.mapNotNull { shop ->
                                val coords = if (shop.latitude != null && shop.longitude != null) {
                                    Pair(shop.latitude, shop.longitude)
                                } else if (!shop.googleMapLink.isNullOrEmpty()) {
                                    extractCoordinates(shop.googleMapLink)
                                } else {
                                    null
                                }
                                
                                if (coords != null && coords.first in -90.0..90.0 && coords.second in -180.0..180.0 && !(coords.first == 0.0 && coords.second == 0.0)) {
                                    Pair(shop, coords)
                                } else {
                                    null
                                }
                            }

                            // 2. Resolve target coordinates.
                            // If the user entered coordinates directly, use them directly without any text matching.
                            var targetCoords: Pair<Double, Double>? = extractCoordinates(query)

                            if (targetCoords == null) {
                                // Fallback to state flow resolved coordinates (e.g. from shortened URL resolve or ViewModel geocoder)
                                targetCoords = nearestQueryCoords
                            }

                            // If still null, try offline text geocoding or matching (only if NOT explicit coordinates query)
                            if (targetCoords == null) {
                                val queryLower = query.lowercase()
                                val offlineMatch = listOf(
                                    "bangalore" to Pair(12.971598, 77.594562),
                                    "bengaluru" to Pair(12.971598, 77.594562),
                                    "indiranagar" to Pair(12.9640, 77.6385),
                                    "koramangala" to Pair(12.9352, 77.6244),
                                    "whitefield" to Pair(12.9698, 77.7500),
                                    "jayanagar" to Pair(12.9307, 77.5832),
                                    "m.g. road" to Pair(12.9738, 77.6119),
                                    "mg road" to Pair(12.9738, 77.6119),
                                    "malleshwaram" to Pair(13.0031, 77.5643),
                                    "hsr layout" to Pair(12.9116, 77.6388),
                                    "hebbal" to Pair(13.0359, 77.5970),
                                    "electronic city" to Pair(12.8452, 77.6602),
                                    "marathahalli" to Pair(12.9569, 77.7011),
                                    "btm layout" to Pair(12.9166, 77.6101),
                                    "rajajinagar" to Pair(12.9901, 77.5525),
                                    "banashankari" to Pair(12.9254, 77.5468),
                                    "yeshwanthpur" to Pair(13.0232, 77.5529),
                                    "bellandur" to Pair(12.9304, 77.6784),
                                    "yelahanka" to Pair(13.1007, 77.5963),
                                    "bannerghatta" to Pair(12.8063, 77.5772),
                                    "mysore" to Pair(12.2958, 76.6394),
                                    "mysuru" to Pair(12.2958, 76.6394),
                                    "mangalore" to Pair(12.9141, 74.8560),
                                    "mangaluru" to Pair(12.9141, 74.8560),
                                    "hubli" to Pair(15.3647, 75.1240),
                                    "belgaum" to Pair(15.8497, 74.4977),
                                    "dharwad" to Pair(15.4589, 75.0078),
                                    "delhi" to Pair(28.6139, 77.2090),
                                    "new delhi" to Pair(28.6139, 77.2090),
                                    "mumbai" to Pair(19.0760, 72.8777),
                                    "bombay" to Pair(19.0760, 72.8777),
                                    "chennai" to Pair(13.0827, 80.2707),
                                    "madras" to Pair(13.0827, 80.2707),
                                    "kolkata" to Pair(22.5726, 88.3639),
                                    "calcutta" to Pair(22.5726, 88.3639),
                                    "hyderabad" to Pair(17.3850, 78.4867),
                                    "pune" to Pair(18.5204, 73.8567),
                                    "ahmedabad" to Pair(23.0225, 72.5714),
                                    "jaipur" to Pair(26.9124, 75.7873),
                                    "kochi" to Pair(9.9312, 76.2673),
                                    "cochin" to Pair(9.9312, 76.2673)
                                )
                                offlineMatch.firstOrNull { queryLower.contains(it.first) }?.let {
                                    targetCoords = it.second
                                }
                            }

                            // If still not coordinates, try location master match
                            if (targetCoords == null) {
                                val matchedLocation = locations.firstOrNull {
                                    it.locationNumber.equals(query, ignoreCase = true) ||
                                    it.locationName.contains(query, ignoreCase = true)
                                }
                                if (matchedLocation != null) {
                                    // Use center of location based only on shops with valid coords inside this location
                                    val shopsInLoc = shopsWithValidCoords.filter { it.first.locationNumber == matchedLocation.locationNumber }
                                    if (shopsInLoc.isNotEmpty()) {
                                        targetCoords = Pair(
                                            shopsInLoc.map { it.second.first }.average(),
                                            shopsInLoc.map { it.second.second }.average()
                                        )
                                    }
                                }
                            }

                            // If still not found, try matching shop name/number
                            if (targetCoords == null) {
                                val matchedShop = shopsWithValidCoords.firstOrNull {
                                    it.first.storeName.contains(query, ignoreCase = true) ||
                                    it.first.shopNumber.equals(query, ignoreCase = true) ||
                                    (it.first.notes ?: "").contains(query, ignoreCase = true)
                                }
                                if (matchedShop != null) {
                                    targetCoords = matchedShop.second
                                }
                            }

                            // 3. Distance calculation and sorting
                            val anchorCoordinate = shopsWithValidCoords.firstOrNull()?.second ?: Pair(12.971598, 77.594562)
                            val finalTargetCoords = targetCoords ?: anchorCoordinate
                            val (tLat, tLng) = finalTargetCoords

                            shopsWithValidCoords.map { (shop, shopCoords) ->
                                val dist = calculateDistance(tLat, tLng, shopCoords.first, shopCoords.second)
                                Pair(shop, dist)
                            }.sortedBy { it.second }.take(5)
                        }
                    }

                    if (nearestQuery.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NearMe,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(72.dp)
                                )
                                Text(
                                    text = "Nearest Shops Finder",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Enter a manual location (e.g. LOC001, Avenue Road) or paste a Google Maps navigation link to find the 5 closest shops, sorted by shortest distance.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.widthIn(max = 320.dp)
                                )
                            }
                        }
                    } else {
                        if (nearestShopsResult.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No nearest shops found",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                item {
                                    Text(
                                        text = "Nearest 5 Shops",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                itemsIndexed(nearestShopsResult) { index, (shop, distance) ->
                                    val locName = locations.firstOrNull { it.locationNumber == shop.locationNumber }?.locationName ?: shop.locationNumber
                                    NearestShopCard(
                                        shop = shop,
                                        locationName = locName,
                                        distance = distance,
                                        index = index + 1,
                                        onClick = { selectedShopForDetail = shop },
                                        onNavigate = {
                                            if (!shop.googleMapLink.isNullOrEmpty()) {
                                                val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(shop.googleMapLink))
                                                context.startActivity(mapIntent)
                                            }
                                        },
                                        onCreateSale = {
                                            viewModel.setPrefilledSaleData(shop.shopNumber, shop.storeName, locName)
                                            onNavigateToTab("Sales")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // --- Add / Edit Form Screen ---
        val isEdit = selectedShopForEdit != null
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEdit) "Edit Store Master" else "Add New Store", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { showAddEditScreen = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        // Image Picker Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { imageLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!storeImageUri.isNullOrEmpty() && File(storeImageUri!!).exists()) {
                                Image(
                                    painter = rememberAsyncImagePainter(File(storeImageUri!!)),
                                    contentDescription = "Store Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Change Image", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("Add Store Image", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    item {
                        // Shop ID & Store Name
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = formShopNumber,
                                onValueChange = { formShopNumber = it; shopNumberError = null },
                                label = { Text("Shop Number") },
                                enabled = true,
                                isError = shopNumberError != null,
                                supportingText = shopNumberError?.let { { Text(it) } },
                                modifier = Modifier.width(135.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = storeName,
                                onValueChange = { storeName = it; storeNameError = null },
                                label = { Text("Store Name*") },
                                placeholder = { Text("e.g. Balaji Sweets") },
                                isError = storeNameError != null,
                                supportingText = storeNameError?.let { { Text(it) } },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }

                    item {
                        // Location dropdown
                        var locationExpanded by remember { mutableStateOf(false) }
                        val activeLocName = locations.firstOrNull { it.locationNumber == selectedLocationCode }?.locationName ?: "Select Location"
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = activeLocName,
                                onValueChange = {},
                                label = { Text("Route Location Master*") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { locationExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { locationExpanded = true }
                            )
                            DropdownMenu(
                                expanded = locationExpanded,
                                onDismissRequest = { locationExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                locations.forEach { loc ->
                                    DropdownMenuItem(
                                        text = { Text("${loc.locationNumber} - ${loc.locationName}") },
                                        onClick = {
                                            selectedLocationCode = loc.locationNumber
                                            locationExpanded = false
                                            locationError = null
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        // Rating (Info only)
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("auto_rating_info_card"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column {
                                    Text(
                                        "Dynamic Store Rating Enabled",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Ratings (1.0 - 5.0) are calculated automatically based on purchase recency, frequency, packets purchased, revenue, and profit. No manual updates required.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        // Date picker starting date
                        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        OutlinedTextField(
                            value = dateFormat.format(Date(startingDateMillis)),
                            onValueChange = {},
                            label = { Text("Starting Date*") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val calendar = Calendar.getInstance().apply { timeInMillis = startingDateMillis }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            calendar.set(y, m, d)
                                            startingDateMillis = calendar.timeInMillis
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Select Date")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        // Mobile number
                        OutlinedTextField(
                            value = mobileNumber,
                            onValueChange = {
                                mobileNumber = it
                                mobileError = null
                            },
                            label = { Text("Mobile Number (Optional)") },
                            placeholder = { Text("e.g. 9876543210") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            isError = mobileError != null,
                            supportingText = mobileError?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    item {
                        // Google Map link
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = googleMapLink,
                                onValueChange = { googleMapLink = it },
                                label = { Text("Google Map Link (Optional)") },
                                placeholder = { Text("e.g. https://maps.app.goo.gl/...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            val coordsWarning = remember(googleMapLink) {
                                if (googleMapLink.isBlank()) {
                                    "⚠️ Warning: No Google Map link or coordinates provided. This store will not appear in coordinate-based Nearest Shop searches."
                                } else {
                                    val coords = extractCoordinates(googleMapLink)
                                    if (coords == null) {
                                        if (googleMapLink.contains("goo.gl") || googleMapLink.contains("maps.app.goo.gl")) {
                                            "ℹ️ Info: Shortened link detected. Coordinates will be resolved dynamically after saving."
                                        } else {
                                            "⚠️ Warning: Could not parse coordinates from this link. Use format: Lat,Lng (e.g. 11.795344,77.813469) or a valid Google Map URL."
                                        }
                                    } else {
                                        val (lat, lng) = coords
                                        if (lat !in -90.0..90.0 || lng !in -180.0..180.0 || (lat == 0.0 && lng == 0.0)) {
                                            "⚠️ Warning: Parsed coordinates ($lat, $lng) are invalid. Please double-check them."
                                        } else {
                                            null
                                        }
                                    }
                                }
                            }
                            if (coordsWarning != null) {
                                val isError = coordsWarning.startsWith("⚠️")
                                Text(
                                    text = coordsWarning,
                                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    item {
                        // Notes
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Special Notes / Details") },
                            placeholder = { Text("e.g. Best visiting times, custom prices...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                }

                // Save buttons
                Button(
                    onClick = {
                        var isValid = true
                        val cleanedShopNum = formShopNumber.trim()
                        if (cleanedShopNum.isEmpty()) {
                            shopNumberError = "Shop Number is required"
                            isValid = false
                        } else {
                            val duplicateExists = if (isEdit) {
                                shops.any { it.shopNumber.equals(cleanedShopNum, ignoreCase = true) && !it.shopNumber.equals(selectedShopForEdit!!.shopNumber, ignoreCase = true) }
                            } else {
                                shops.any { it.shopNumber.equals(cleanedShopNum, ignoreCase = true) }
                            }

                            if (duplicateExists) {
                                shopNumberError = "Shop Number already exists. Please enter a different Shop Number."
                                isValid = false
                            }
                        }

                        if (storeName.trim().isEmpty()) {
                            storeNameError = "Store Name is required"
                            isValid = false
                        }
                        if (selectedLocationCode.isEmpty()) {
                            locationError = "Please select a location Route"
                            isValid = false
                        }
                        if (mobileNumber.isNotEmpty() && mobileNumber.length < 10) {
                            mobileError = "Invalid phone number (must be at least 10 digits)"
                            isValid = false
                        }

                        if (isValid) {
                            val targetRating = if (isEdit) (selectedShopForEdit?.rating ?: 1.0f) else 1.0f
                            val scoreValue = (targetRating * 20).toInt()
                            val shop = ShopMaster(
                                shopNumber = cleanedShopNum,
                                locationNumber = selectedLocationCode,
                                storeName = storeName.trim(),
                                storeImage = storeImageUri,
                                rating = targetRating,
                                score = scoreValue,
                                startingDate = startingDateMillis,
                                googleMapLink = googleMapLink.trim().ifEmpty { null },
                                mobileNumber = mobileNumber.trim().ifEmpty { null },
                                notes = notes.trim().ifEmpty { null }
                            )

                            if (isEdit) {
                                viewModel.updateShop(selectedShopForEdit!!.shopNumber, shop)
                                Toast.makeText(context, "Store Master updated", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addShop(shop)
                                Toast.makeText(context, "Store added successfully", Toast.LENGTH_SHORT).show()
                            }
                            showAddEditScreen = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_shop_button")
                ) {
                    Text("Save Store Record", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    // --- Detail Bottom Sheet / Dialog ---
    if (selectedShopForDetail != null) {
        val detail = shops.firstOrNull { it.shopNumber == selectedShopForDetail?.shopNumber } ?: selectedShopForDetail!!
        val locName = locations.firstOrNull { it.locationNumber == detail.locationNumber }?.locationName ?: detail.locationNumber
        
        val resolvingMap by viewModel.isResolvingCoordinates.collectAsStateWithLifecycle()
        val errorMap by viewModel.coordinateResolutionError.collectAsStateWithLifecycle()

        val isResolving = resolvingMap[detail.shopNumber] ?: false
        val resolutionError = errorMap[detail.shopNumber]

        LaunchedEffect(detail.shopNumber) {
            if (detail.latitude == null || detail.longitude == null || detail.coordinateStatus == null) {
                viewModel.resolveAndSaveCoordinatesForShop(context, detail)
            }
        }

        AlertDialog(
            onDismissRequest = { selectedShopForDetail = null },
            title = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(detail.storeName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(detail.shopNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!detail.storeImage.isNullOrEmpty() && File(detail.storeImage).exists()) {
                        Image(
                            painter = rememberAsyncImagePainter(File(detail.storeImage)),
                            contentDescription = "Store Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }

                    DetailField(label = "Route Location", value = "$locName (${detail.locationNumber})")
                    DetailField(label = "Starting Date", value = detail.startingDateFormatted)
                    
                    val salesForShop = sales.filter { it.shopNumber == detail.shopNumber }
                    val analytics = com.example.utils.RatingCalculator.calculateAnalytics(salesForShop)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dynamic Rating:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row {
                            (1..5).forEach { star ->
                                val isFilled = star <= analytics.currentRating
                                val isHalf = !isFilled && (star - 1) < analytics.currentRating
                                Icon(
                                    imageVector = if (isFilled) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = null,
                                    tint = if (isFilled) Color(0xFFFFD700) else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "${analytics.currentRating} (${analytics.ratingDescription})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("shop_detail_analytics_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Store Performance Analytics", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Sales Transactions", fontSize = 11.sp, color = Color.Gray)
                                Text("${analytics.totalSalesTransactions}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Packets Purchased", fontSize = 11.sp, color = Color.Gray)
                                Text("${analytics.totalPacketsPurchased} packs", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Revenue", fontSize = 11.sp, color = Color.Gray)
                                Text("₹${"%.2f".format(analytics.totalRevenue)}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF2E7D32))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Profit", fontSize = 11.sp, color = Color.Gray)
                                Text("₹${"%.2f".format(analytics.totalProfit)}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF2E7D32))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Avg Purchases / Month", fontSize = 11.sp, color = Color.Gray)
                                Text("${"%.1f".format(analytics.averagePurchasesPerMonth)}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("First Purchase Date", fontSize = 11.sp, color = Color.Gray)
                                Text(analytics.firstPurchaseDateFormatted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Last Purchase Date", fontSize = 11.sp, color = Color.Gray)
                                Text(analytics.lastPurchaseDateFormatted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Days Since Last Purchase", fontSize = 11.sp, color = Color.Gray)
                                val daysStr = if (analytics.totalSalesTransactions > 0) "${analytics.daysSinceLastPurchase} days" else "N/A"
                                Text(daysStr, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (analytics.daysSinceLastPurchase > 30) Color.Red else Color.Unspecified)
                            }
                        }
                    }

                    if (!detail.mobileNumber.isNullOrEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DetailField(label = "Mobile Number", value = detail.mobileNumber)
                            IconButton(
                                onClick = {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${detail.mobileNumber}"))
                                    context.startActivity(dialIntent)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call Store", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("shop_detail_location_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f))
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
                                Text(
                                    text = "Location Information",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                if (isResolving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else if (!detail.googleMapLink.isNullOrEmpty()) {
                                    IconButton(
                                        onClick = {
                                            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(detail.googleMapLink))
                                            context.startActivity(mapIntent)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Navigation,
                                            contentDescription = "Navigate to Google Maps",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            DetailField(label = "Google Maps Link", value = detail.googleMapLink ?: "N/A")

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    DetailField(label = "Latitude", value = detail.latitude?.toString() ?: "N/A")
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    DetailField(label = "Longitude", value = detail.longitude?.toString() ?: "N/A")
                                }
                            }

                            val coordinatesString = if (detail.latitude != null && detail.longitude != null) {
                                "${detail.latitude}, ${detail.longitude}"
                            } else {
                                null
                            }

                            if (coordinatesString != null) {
                                val clipboardManager = LocalClipboardManager.current
                                Surface(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(coordinatesString))
                                        Toast.makeText(context, "Coordinates copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("copy_coordinates_button")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Coordinates (Lat, Lng) - Tap to Copy",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = coordinatesString,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy coordinates",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    Column {
                                        Text("Coordinate Status", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        val status = detail.coordinateStatus ?: "Pending"
                                        val statusColor = when (status) {
                                            "Valid" -> Color(0xFF2E7D32)
                                            "Invalid" -> MaterialTheme.colorScheme.error
                                            else -> Color(0xFFE65100)
                                        }
                                        Text(status, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = statusColor)
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    DetailField(label = "Last Updated", value = detail.lastCoordinateUpdateFormatted ?: "N/A")
                                }
                            }

                            val isInvalid = detail.coordinateStatus == "Invalid"
                            if (isInvalid || resolutionError != null) {
                                val errorMsg = resolutionError ?: "Unable to retrieve location coordinates from the provided Google Maps link."
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "⚠️ $errorMsg",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (!detail.notes.isNullOrEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Notes:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                detail.notes,
                                fontSize = 12.sp,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            viewModel.setSalesFilterShopNumber(detail.shopNumber)
                            viewModel.setSalesSearchQuery(detail.storeName)
                            onNavigateToTab("Sales")
                            selectedShopForDetail = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("go_to_sales_detail_button")
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sales")
                    }
                    if (!detail.googleMapLink.isNullOrEmpty()) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(detail.googleMapLink))
                                    context.startActivity(mapIntent)
                                } catch (e: Exception) {
                                    // fallback
                                }
                            },
                            modifier = Modifier.testTag("go_to_map_detail_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Navigate to Google Maps",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Map", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.setPrefilledSaleData(detail.shopNumber, detail.storeName, locName)
                            onNavigateToTab("Sales")
                            selectedShopForDetail = null
                        }
                    ) {
                        Text("Create Daily Sale")
                    }
                    TextButton(onClick = { selectedShopForDetail = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // --- Excel Import Progress Dialog ---
    if (isImporting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Importing Shops") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Reading spreadsheet and validating records...")
                }
            }
        )
    }

    // --- Excel Import Summary Dialog ---
    if (importSummary != null && importSummary!!.type == com.example.utils.Exporter.ImportType.SHOPS) {
        val summary = importSummary!!
        AlertDialog(
            onDismissRequest = { viewModel.clearImportSummary() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (summary.skippedRows > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (summary.skippedRows > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text("Import Summary", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text("The spreadsheet import has completed. Here are the details:")
                    
                    HorizontalDivider()
                    
                    SummaryRow(label = "Total Candidate Rows:", value = "${summary.totalRows}")
                    SummaryRow(label = "Successfully Imported:", value = "${summary.successfullyImported}", color = MaterialTheme.colorScheme.primary)
                    SummaryRow(label = "Skipped Rows (Total):", value = "${summary.skippedRows}", color = if (summary.skippedRows > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Skipped Details:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    
                    when (summary.type) {
                        com.example.utils.Exporter.ImportType.SHOPS -> {
                            SummaryRow(label = "• Duplicate Records:", value = "${summary.duplicateRecordsCount}")
                            SummaryRow(label = "• Invalid Location Numbers:", value = "${summary.invalidLocationNumbersCount}")
                            SummaryRow(label = "• Missing Images:", value = "${summary.missingImagesCount}")
                            SummaryRow(label = "• Failed / Invalid Rows:", value = "${summary.failedRowsCount}")
                            if (summary.invalidCoordinatesCount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "⚠️ Warning: ${summary.invalidCoordinatesCount} imported shops have missing or invalid GPS coordinates. They will be excluded from Nearest Shop searches.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        com.example.utils.Exporter.ImportType.LOCATIONS -> {
                            SummaryRow(label = "• Duplicate Records:", value = "${summary.duplicateRecordsCount}")
                            SummaryRow(label = "• Failed / Invalid Rows:", value = "${summary.failedRowsCount}")
                        }
                        com.example.utils.Exporter.ImportType.SALES -> {
                            SummaryRow(label = "• Invalid Shop Numbers:", value = "${summary.invalidShopNumbersCount}")
                            SummaryRow(label = "• Invalid Products:", value = "${summary.invalidProductsCount}")
                            SummaryRow(label = "• Failed / Invalid Rows:", value = "${summary.failedRowsCount}")
                        }
                        else -> {}
                    }
                    
                    if (summary.type == com.example.utils.Exporter.ImportType.SHOPS && summary.totalImagesFound > 0) {
                        HorizontalDivider()
                        Text("Image Import Details:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        SummaryRow(label = "• Total Images Found:", value = "${summary.totalImagesFound}")
                        SummaryRow(label = "• Images Imported Successfully:", value = "${summary.imagesImportedSuccessfully}", color = MaterialTheme.colorScheme.primary)
                        SummaryRow(label = "• Images Failed:", value = "${summary.imagesFailed}", color = if (summary.imagesFailed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        
                        if (summary.imageImportReasons.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                                    .verticalScroll(scrollState),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                summary.imageImportReasons.forEach { reason ->
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (reason.contains("successfully", ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    
                    if (summary.errorReportFile != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Some rows could not be imported. Download/Share the Error Report to review and correct them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (summary.errorReportFile != null) {
                        Button(
                            onClick = {
                                val reportTitle = when (summary.type) {
                                    com.example.utils.Exporter.ImportType.SHOPS -> "Shop Import Error Report"
                                    com.example.utils.Exporter.ImportType.LOCATIONS -> "Location Import Error Report"
                                    com.example.utils.Exporter.ImportType.SALES -> "Sales Import Error Report"
                                    else -> "Import Error Report"
                                }
                                Exporter.shareFile(context, summary.errorReportFile, reportTitle)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download Report", fontSize = 11.sp)
                        }
                    }
                    
                    Button(
                        onClick = { viewModel.clearImportSummary() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

private @Composable
fun SummaryRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun DetailField(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ShopCard(
    shop: ShopMaster,
    locationName: String,
    healthScore: Int,
    healthCategory: String,
    onClick: () -> Unit,
    onGoToSales: () -> Unit,
    onRecordSale: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Image or Icon
                if (!shop.storeImage.isNullOrEmpty() && File(shop.storeImage).exists()) {
                    Image(
                        painter = rememberAsyncImagePainter(File(shop.storeImage)),
                        contentDescription = "Store image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Shop details
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = shop.storeName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val isCoordsInvalid = shop.latitude == null || shop.longitude == null || (shop.latitude == 0.0 && shop.longitude == 0.0) || shop.latitude !in -90.0..90.0 || shop.longitude !in -180.0..180.0
                        if (isCoordsInvalid) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "⚠️ No GPS",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = "ID: ${shop.shopNumber} • Route: $locationName",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                        Text(
                            text = "${shop.rating} (${shop.score}/100)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Health Score
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$healthScore%",
                        style = MaterialTheme.typography.titleMedium,
                        color = when (healthCategory) {
                            "Excellent" -> Color(0xFF4CAF50)
                            "Good" -> Color(0xFF8BC34A)
                            "Average" -> Color(0xFFFFC107)
                            else -> Color(0xFFF44336)
                        }
                    )
                    Text(
                        text = healthCategory,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Edit/Delete actions
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasMapLink = !shop.googleMapLink.isNullOrEmpty()
                val hasCoords = shop.latitude != null && shop.longitude != null && shop.latitude != 0.0 && shop.longitude != 0.0
                if (hasMapLink || hasCoords) {
                    val context = LocalContext.current
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = if (hasMapLink) {
                                    Intent(Intent.ACTION_VIEW, Uri.parse(shop.googleMapLink))
                                } else {
                                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:${shop.latitude},${shop.longitude}?q=${Uri.encode(shop.storeName)}"))
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // fallback
                            }
                        },
                        modifier = Modifier.testTag("go_to_map_button_${shop.shopNumber}"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Navigate to Google Maps",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Map", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                OutlinedButton(
                    onClick = onGoToSales,
                    modifier = Modifier.testTag("go_to_sales_button_${shop.shopNumber}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "Navigate to Sales",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sales", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRecordSale,
                    modifier = Modifier.testTag("record_sale_button_${shop.shopNumber}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Record Sale",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Record Sale", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            AnimatedVisibility(visible = showDeleteConfirm) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Delete store master record? This is irreversible and will delete historic logs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                onDelete()
                                showDeleteConfirm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Delete", fontSize = 12.sp, color = MaterialTheme.colorScheme.onError)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NearestShopCard(
    shop: ShopMaster,
    locationName: String,
    distance: Double,
    index: Int,
    onClick: () -> Unit,
    onNavigate: () -> Unit,
    onCreateSale: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("nearest_shop_card_${shop.shopNumber}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Rank badge, Store Info, and Distance Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Rank Badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$index",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Shop Name & Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shop.storeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${shop.shopNumber} • $locationName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Distance Badge
                val distanceText = if (distance >= 10.0) {
                    "%.1f km".format(distance)
                } else if (distance > 0.0) {
                    "%.2f km".format(distance)
                } else {
                    "Exact Match"
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = distanceText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Image if available
            if (!shop.storeImage.isNullOrEmpty() && File(shop.storeImage).exists()) {
                Image(
                    painter = rememberAsyncImagePainter(File(shop.storeImage)),
                    contentDescription = "Store Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // Rating & Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stars
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val displayRating = shop.rating
                    (1..5).forEach { star ->
                        Icon(
                            imageVector = if (star <= displayRating) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = if (star <= displayRating) Color(0xFFFFD700) else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "%.1f".format(displayRating),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Quick Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!shop.googleMapLink.isNullOrEmpty()) {
                        OutlinedButton(
                            onClick = onNavigate,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Map", fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = onCreateSale,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sale", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
