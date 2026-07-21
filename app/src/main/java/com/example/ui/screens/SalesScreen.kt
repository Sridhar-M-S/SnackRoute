package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.SalesEntry
import com.example.ui.AppViewModel
import com.example.utils.Exporter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    viewModel: AppViewModel,
    onOpenChat: () -> Unit,
    onOpenTimetable: () -> Unit,
    onBackToParent: () -> Unit = {},
    showBackButton: Boolean = false
) {
    val context = LocalContext.current
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.setSalesFilterShopNumber(null)
            viewModel.setSalesSearchQuery("")
            viewModel.setPrefilledSaleData(null, null, null)
        }
    }
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val shops by viewModel.shops.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importSalesFromExcel(context, uri)
        }
    }

    val searchQuery by viewModel.salesSearchQuery.collectAsStateWithLifecycle()
    var showAddEditScreen by remember { mutableStateOf(false) }
    var selectedSalesForEdit by remember { mutableStateOf<SalesEntry?>(null) }
    
    BackHandler(enabled = showAddEditScreen) {
        showAddEditScreen = false
        selectedSalesForEdit = null
    }
    var isShopLocked by remember { mutableStateOf(false) }

    // --- Search & Multiple Filters State ---
    var filterExpanded by remember { mutableStateOf(false) }
    val filterShopNumber by viewModel.salesFilterShopNumber.collectAsStateWithLifecycle()
    var filterLocationNumber by remember { mutableStateOf<String?>(null) }
    var filterProductName by remember { mutableStateOf<String?>(null) }
    var filterStatus by remember { mutableStateOf<String?>(null) }
    var filterStartDate by remember { mutableStateOf<Long?>(null) }
    var filterEndDate by remember { mutableStateOf<Long?>(null) }
    var sortBy by remember { mutableStateOf("Date") } // Date, Amount, Profit
    var sortAscending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Form fields
    var entryDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedShopNumber by remember { mutableStateOf("") }
    var payStatus by remember { mutableStateOf("Paid") } // Paid, Pending, Partially Paid
    var remarks by remember { mutableStateOf("") }
    var saleItems by remember { mutableStateOf<List<SaleItemState>>(emptyList()) }

    // Validation
    var shopError by remember { mutableStateOf<String?>(null) }

    // --- Filter logic ---
    LaunchedEffect(
        searchQuery, filterShopNumber, filterLocationNumber,
        filterProductName, filterStatus, filterStartDate, filterEndDate, sortBy, sortAscending
    ) {
        listState.scrollToItem(0)
    }

    val filteredSales = remember(
        sales, searchQuery, filterShopNumber, filterLocationNumber,
        filterProductName, filterStatus, filterStartDate, filterEndDate, sortBy, sortAscending
    ) {
        var list = sales.filter { sale ->
            // Search filter
            val matchSearch = searchQuery.isEmpty() ||
                    sale.shopName.contains(searchQuery, ignoreCase = true) ||
                    sale.shopNumber.equals(searchQuery, ignoreCase = true) ||
                    sale.shopNumber.contains(searchQuery, ignoreCase = true) ||
                    sale.productName.contains(searchQuery, ignoreCase = true) ||
                    sale.locationNumber.contains(searchQuery, ignoreCase = true)

            // Shop filter
            val matchShop = filterShopNumber == null || sale.shopNumber == filterShopNumber
            // Location filter
            val matchLocation = filterLocationNumber == null || sale.locationNumber == filterLocationNumber
            // Product filter
            val matchProduct = filterProductName == null || sale.productName == filterProductName
            // Status filter
            val matchStatus = filterStatus == null || sale.status == filterStatus

            // Date Range filter
            val matchDate = (filterStartDate == null || sale.entryDate >= filterStartDate!!) &&
                    (filterEndDate == null || sale.entryDate <= filterEndDate!! + 86400000L) // Include whole end day

            matchSearch && matchShop && matchLocation && matchProduct && matchStatus && matchDate
        }

        // Sorting
        list = when (sortBy) {
            "Amount" -> if (sortAscending) list.sortedBy { it.totalAmount } else list.sortedByDescending { it.totalAmount }
            "Profit" -> if (sortAscending) list.sortedBy { it.totalProfit } else list.sortedByDescending { it.totalProfit }
            else -> if (sortAscending) list.sortedBy { it.entryDate } else list.sortedByDescending { it.entryDate }
        }
        list
    }

    // --- Excel Import Summary Dialog ---
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val prefilledSaleData by viewModel.prefilledSaleData.collectAsStateWithLifecycle()

    LaunchedEffect(prefilledSaleData) {
        if (prefilledSaleData != null) {
            val (shopNumber, _, _) = prefilledSaleData!!
            
            // Set form fields
            entryDateMillis = System.currentTimeMillis()
            selectedShopNumber = shopNumber
            
            val defaultProdName = products.filter { it.status == "Active" }.firstOrNull()?.productName 
                ?: products.firstOrNull()?.productName 
                ?: ""
            
            saleItems = listOf(
                SaleItemState(
                    productName = defaultProdName
                )
            )
            payStatus = "Paid"
            remarks = ""
            shopError = null
            
            isShopLocked = true
            showAddEditScreen = true
            
            // Clear prefilled data to avoid re-triggering
            viewModel.setPrefilledSaleData(null, null, null)
        }
    }

    if (importSummary != null) {
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
                    
                    SummaryRow(label = "• Invalid Shop Numbers:", value = "${summary.invalidShopNumbersCount}")
                    SummaryRow(label = "• Invalid Products:", value = "${summary.invalidProductsCount}")
                    SummaryRow(label = "• Invalid Dates:", value = "${summary.invalidDatesCount}")
                    SummaryRow(label = "• Failed / Invalid Rows:", value = "${summary.failedRowsCount}")
                    
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
                                Exporter.shareFile(context, summary.errorReportFile, "Sales Import Error Report")
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

    if (!showAddEditScreen) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Sales Records", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = onBackToParent) {
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
                            onClick = { filterExpanded = !filterExpanded },
                            modifier = Modifier.testTag("sales_filter_toggle")
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Toggle Filters")
                        }
                        IconButton(
                            onClick = { importLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") },
                            modifier = Modifier.testTag("import_sales_button")
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = "Import Excel")
                        }
                        IconButton(
                            onClick = { Exporter.exportSales(context, filteredSales) },
                            modifier = Modifier.testTag("export_sales_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Export Excel")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        if (shops.isEmpty()) {
                            Toast.makeText(context, "Please create at least one store first!", Toast.LENGTH_LONG).show()
                        } else if (products.isEmpty()) {
                            Toast.makeText(context, "Please configure products first!", Toast.LENGTH_LONG).show()
                        } else {
                            selectedSalesForEdit = null
                            isShopLocked = false
                            entryDateMillis = System.currentTimeMillis()
                            selectedShopNumber = shops.first().shopNumber
                            
                            val defaultProdName = products.filter { it.status == "Active" }.firstOrNull()?.productName 
                                ?: products.firstOrNull()?.productName 
                                ?: ""
                            
                            saleItems = listOf(
                                SaleItemState(
                                    productName = defaultProdName
                                )
                            )
                            payStatus = "Paid"
                            remarks = ""
                            shopError = null
                            showAddEditScreen = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_sales_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Sales")
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // --- Search Bar ---
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSalesSearchQuery(it) },
                    placeholder = { Text("Search shop, product, route...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sales_search_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // --- Multi Filters Expandable Card ---
                AnimatedVisibility(visible = filterExpanded) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Multiple Filters", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Shop Filter
                                var shopFilterExp by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    Button(
                                        onClick = { shopFilterExp = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = shops.firstOrNull { it.shopNumber == filterShopNumber }?.storeName ?: "All Stores",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    DropdownMenu(expanded = shopFilterExp, onDismissRequest = { shopFilterExp = false }) {
                                        DropdownMenuItem(text = { Text("All Stores") }, onClick = { viewModel.setSalesFilterShopNumber(null); shopFilterExp = false })
                                        shops.forEach { s ->
                                            DropdownMenuItem(text = { Text(s.storeName) }, onClick = { viewModel.setSalesFilterShopNumber(s.shopNumber); shopFilterExp = false })
                                        }
                                    }
                                }

                                // Location Filter
                                var locFilterExp by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    Button(
                                        onClick = { locFilterExp = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = locations.firstOrNull { it.locationNumber == filterLocationNumber }?.locationName ?: "All Routes",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    DropdownMenu(expanded = locFilterExp, onDismissRequest = { locFilterExp = false }) {
                                        DropdownMenuItem(text = { Text("All Routes") }, onClick = { filterLocationNumber = null; locFilterExp = false })
                                        locations.forEach { l ->
                                            DropdownMenuItem(text = { Text(l.locationName) }, onClick = { filterLocationNumber = l.locationNumber; locFilterExp = false })
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Product Filter
                                var prodFilterExp by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    Button(
                                        onClick = { prodFilterExp = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = filterProductName ?: "All Products",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    DropdownMenu(expanded = prodFilterExp, onDismissRequest = { prodFilterExp = false }) {
                                        DropdownMenuItem(text = { Text("All Products") }, onClick = { filterProductName = null; prodFilterExp = false })
                                        products.forEach { p ->
                                            DropdownMenuItem(text = { Text(p.productName) }, onClick = { filterProductName = p.productName; prodFilterExp = false })
                                        }
                                    }
                                }

                                // Status Filter
                                var statusFilterExp by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    Button(
                                        onClick = { statusFilterExp = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = filterStatus ?: "All Statuses",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    DropdownMenu(expanded = statusFilterExp, onDismissRequest = { statusFilterExp = false }) {
                                        DropdownMenuItem(text = { Text("All Statuses") }, onClick = { filterStatus = null; statusFilterExp = false })
                                        listOf("Paid", "Pending", "Partially Paid").forEach { st ->
                                            DropdownMenuItem(text = { Text(st) }, onClick = { filterStatus = st; statusFilterExp = false })
                                        }
                                    }
                                }
                            }

                            // Date Range selectors
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                                
                                Button(
                                    onClick = {
                                        val calendar = Calendar.getInstance()
                                        DatePickerDialog(context, { _, y, m, d ->
                                            calendar.set(y, m, d)
                                            filterStartDate = calendar.timeInMillis
                                        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = filterStartDate?.let { "From: ${dateFormat.format(Date(it))}" } ?: "Start Date",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Button(
                                    onClick = {
                                        val calendar = Calendar.getInstance()
                                        DatePickerDialog(context, { _, y, m, d ->
                                            calendar.set(y, m, d)
                                            filterEndDate = calendar.timeInMillis
                                        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = filterEndDate?.let { "To: ${dateFormat.format(Date(it))}" } ?: "End Date",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(onClick = {
                                    viewModel.setSalesFilterShopNumber(null)
                                    viewModel.setSalesSearchQuery("")
                                    filterLocationNumber = null
                                    filterProductName = null
                                    filterStatus = null
                                    filterStartDate = null
                                    filterEndDate = null
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear filters", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // --- Sort Dropdown Header ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Logs count: ${filteredSales.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )

                    var sortMenuExp by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { sortMenuExp = true }
                            ) {
                                Text("Sort: $sortBy", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = sortMenuExp, onDismissRequest = { sortMenuExp = false }) {
                                DropdownMenuItem(text = { Text("Date") }, onClick = { sortBy = "Date"; sortMenuExp = false })
                                DropdownMenuItem(text = { Text("Amount") }, onClick = { sortBy = "Amount"; sortMenuExp = false })
                                DropdownMenuItem(text = { Text("Profit") }, onClick = { sortBy = "Profit"; sortMenuExp = false })
                            }
                        }
                        IconButton(onClick = { sortAscending = !sortAscending }) {
                            Icon(if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, contentDescription = "Toggle Sort Order", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // --- List ---
                if (filteredSales.isEmpty()) {
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
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                            Text("No Sales Logged", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredSales, key = { it.id }) { sale ->
                            SalesCard(
                                sale = sale,
                                onEdit = {
                                    selectedSalesForEdit = sale
                                    entryDateMillis = sale.entryDate
                                    selectedShopNumber = sale.shopNumber
                                    payStatus = sale.status
                                    remarks = sale.remarks ?: ""
                                    shopError = null
                                    isShopLocked = false

                                    val sessionSales = if (!sale.sessionId.isNullOrEmpty()) {
                                        sales.filter { it.sessionId == sale.sessionId }
                                    } else {
                                        listOf(sale)
                                    }

                                    saleItems = sessionSales.map { s ->
                                        SaleItemState(
                                            id = java.util.UUID.randomUUID().toString(),
                                            dbId = s.id,
                                            productName = s.productName,
                                            ratePerPacketStr = s.ratePerPacket.toString(),
                                            packetsGivenStr = s.packetsGiven.toString(),
                                            packetsReturnedStr = s.packetsReturned.toString(),
                                            customProfitStr = s.profitPerPacket.toString(),
                                            isCustomRate = false
                                        )
                                    }
                                    showAddEditScreen = true
                                },
                                onDelete = {
                                    viewModel.deleteSales(sale)
                                    Toast.makeText(context, "Log deleted", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // --- Add / Edit Sales Screen ---
        val isEdit = selectedSalesForEdit != null

        // Derive objects live for calculations
        val currentShopObj = shops.firstOrNull { it.shopNumber == selectedShopNumber }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEdit) "Edit Sales Record" else "Log Daily Sales", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            showAddEditScreen = false
                            selectedSalesForEdit = null
                        }) {
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        // Date picker
                        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = dateFormat.format(Date(entryDateMillis)),
                                onValueChange = {},
                                label = { Text("Log Entry Date*") },
                                readOnly = true,
                                trailingIcon = {
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Select Date")
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable {
                                        val calendar = Calendar.getInstance().apply { timeInMillis = entryDateMillis }
                                        DatePickerDialog(
                                            context,
                                            { _, y, m, d ->
                                                calendar.set(y, m, d)
                                                entryDateMillis = calendar.timeInMillis
                                            },
                                            calendar.get(Calendar.YEAR),
                                            calendar.get(Calendar.MONTH),
                                            calendar.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                            )
                        }
                    }

                    item {
                        // Shop Selector Dropdown
                        var shopExpanded by remember { mutableStateOf(false) }
                        var shopSearchQuery by remember { mutableStateOf("") }
                        val activeShopName = currentShopObj?.storeName ?: "Select Store"

                        val sortedShops = remember(shops) {
                            shops.sortedBy { it.storeName.lowercase() }
                        }

                        val filteredShops = remember(sortedShops, shopSearchQuery) {
                            if (shopSearchQuery.isBlank()) {
                                sortedShops
                            } else {
                                sortedShops.filter {
                                    it.storeName.contains(shopSearchQuery, ignoreCase = true) ||
                                    it.shopNumber.contains(shopSearchQuery, ignoreCase = true)
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = if (selectedShopNumber.isEmpty()) "Select Store" else "$activeShopName (${selectedShopNumber})",
                                onValueChange = {},
                                label = { Text("Shop Master Store*") },
                                readOnly = true,
                                trailingIcon = {
                                    if (isShopLocked) {
                                        IconButton(onClick = { isShopLocked = false }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Change Shop")
                                        }
                                    } else {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                isError = shopError != null,
                                supportingText = shopError?.let { { Text(it) } },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (!isShopLocked) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { 
                                            shopSearchQuery = ""
                                            shopExpanded = true 
                                        }
                                )
                            }
                            DropdownMenu(
                                expanded = shopExpanded,
                                onDismissRequest = { 
                                    shopExpanded = false 
                                    shopSearchQuery = ""
                                },
                                properties = PopupProperties(focusable = true),
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .heightIn(max = 400.dp)
                            ) {
                                // Search field inside dropdown
                                OutlinedTextField(
                                    value = shopSearchQuery,
                                    onValueChange = { shopSearchQuery = it },
                                    label = { Text("Search shop by name or number...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (shopSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { shopSearchQuery = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("shop_dropdown_search_input"),
                                    singleLine = true
                                )
                                
                                HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
                                
                                if (filteredShops.isEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "No shops found",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        onClick = {},
                                        enabled = false
                                    )
                                } else {
                                    filteredShops.forEach { s ->
                                        val isSelected = s.shopNumber == selectedShopNumber
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${s.storeName} (${s.shopNumber})",
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                selectedShopNumber = s.shopNumber
                                                shopExpanded = false
                                                shopError = null
                                                shopSearchQuery = ""
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                    else Color.Transparent
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        val shopLocCode = currentShopObj?.locationNumber
                        val shopLocName = locations.firstOrNull { it.locationNumber == shopLocCode }?.locationName ?: shopLocCode ?: "Not Assigned"
                        OutlinedTextField(
                            value = shopLocName,
                            onValueChange = {},
                            label = { Text("Associated Route / Location") },
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Iterate and render each product row in saleItems list
                    items(saleItems.size) { index ->
                        val item = saleItems[index]
                        SaleItemRow(
                            item = item,
                            products = products,
                            viewModel = viewModel,
                            saleDate = entryDateMillis,
                            onItemChange = { updatedItem ->
                                saleItems = saleItems.toMutableList().apply {
                                    this[index] = updatedItem
                                }
                            },
                            onRemove = {
                                if (saleItems.size > 1) {
                                    saleItems = saleItems.toMutableList().apply {
                                        removeAt(index)
                                    }
                                }
                            },
                            showRemoveButton = saleItems.size > 1
                        )
                    }

                    item {
                        // "Add Item (+)" Button
                        OutlinedButton(
                            onClick = {
                                val defaultProdName = products.filter { it.status == "Active" }.firstOrNull()?.productName 
                                    ?: products.firstOrNull()?.productName 
                                    ?: ""
                                saleItems = saleItems + SaleItemState(
                                    productName = defaultProdName
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Item (+)", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    item {
                        // Calculations Summary Card
                        val totalProducts = saleItems.size
                        var totalPacketsGiven = 0
                        var totalPacketsReturned = 0
                        var totalPacketsSold = 0
                        var totalEstimatedProfit = 0.0
                        var grandTotalAmount = 0.0
                        
                        saleItems.forEach { itm ->
                            val given = itm.packetsGivenStr.toIntOrNull() ?: 0
                            val returned = itm.packetsReturnedStr.toIntOrNull() ?: 0
                            val sold = maxOf(0, given - returned)
                            val rate = itm.ratePerPacketStr.toDoubleOrNull() ?: 0.0
                            val profitPerUnit = itm.customProfitStr.toDoubleOrNull() ?: 0.0
                            
                            totalPacketsGiven += given
                            totalPacketsReturned += returned
                            totalPacketsSold += sold
                            totalEstimatedProfit += (sold * profitPerUnit)
                            grandTotalAmount += (sold * rate)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Grand Summary",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Total Products:", fontSize = 12.sp, color = Color.Gray)
                                    Text("$totalProducts products", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Total Packets Given:", fontSize = 12.sp, color = Color.Gray)
                                    Text("$totalPacketsGiven", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Total Packets Returned:", fontSize = 12.sp, color = Color.Gray)
                                    Text("$totalPacketsReturned", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Total Packets Sold:", fontSize = 12.sp, color = Color.Gray)
                                    Text("$totalPacketsSold", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Total Estimated Profit:", fontSize = 12.sp, color = Color.Gray)
                                    Text("₹${"%.2f".format(totalEstimatedProfit)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2E7D32))
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Grand Total Amount:", fontSize = 12.sp, color = Color.Gray)
                                    Text("₹${"%.2f".format(grandTotalAmount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    item {
                        // Paid Status Radio Group
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Payment Status*", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { payStatus = "Paid" }) {
                                        RadioButton(selected = payStatus == "Paid", onClick = { payStatus = "Paid" })
                                        Text("Paid", fontSize = 13.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { payStatus = "Partially Paid" }) {
                                        RadioButton(selected = payStatus == "Partially Paid", onClick = { payStatus = "Partially Paid" })
                                        Text("Partial", fontSize = 13.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { payStatus = "Pending" }) {
                                        RadioButton(selected = payStatus == "Pending", onClick = { payStatus = "Pending" })
                                        Text("Pending", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        // Remarks
                        OutlinedTextField(
                            value = remarks,
                            onValueChange = { remarks = it },
                            label = { Text("Remarks (Optional)") },
                            placeholder = { Text("e.g. Next collection Monday, cash paid...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Save button
                Button(
                    onClick = {
                        var isAllValid = true
                        
                        if (selectedShopNumber.isEmpty()) {
                            shopError = "Please select a store"
                            isAllValid = false
                        } else {
                            shopError = null
                        }

                        // Validate each sale row
                        val validatedItems = saleItems.map { itm ->
                            var prodErr: String? = null
                            var rateErr: String? = null
                            var packErr: String? = null

                            if (itm.productName.isEmpty()) {
                                prodErr = "Product is required"
                                isAllValid = false
                            }

                            val givenVal = itm.packetsGivenStr.toIntOrNull()
                            val returnedVal = itm.packetsReturnedStr.toIntOrNull() ?: 0
                            if (givenVal == null || givenVal < 0) {
                                packErr = "Valid packets count is required"
                                isAllValid = false
                            } else if (returnedVal > givenVal) {
                                packErr = "Returned cannot exceed Given packets"
                                isAllValid = false
                            }

                            val rateVal = itm.ratePerPacketStr.toDoubleOrNull()
                            if (rateVal == null || rateVal < 0) {
                                rateErr = "Valid rate is required"
                                isAllValid = false
                            }

                            itm.copy(
                                productError = prodErr,
                                packetsError = packErr,
                                rateError = rateErr
                            )
                        }

                        saleItems = validatedItems

                        if (isAllValid) {
                            // Generate or reuse Sales Session ID
                            val sessionId = selectedSalesForEdit?.sessionId 
                                ?: ("SESS_" + java.util.UUID.randomUUID().toString().substring(0, 8).uppercase())

                            val salesToInsert = saleItems.map { itm ->
                                val givenVal = itm.packetsGivenStr.toInt()
                                val returnedVal = itm.packetsReturnedStr.toIntOrNull() ?: 0
                                val finalSold = givenVal - returnedVal
                                val rateVal = itm.ratePerPacketStr.toDouble()
                                val finalTotal = finalSold * rateVal
                                val finalProfitPerUnit = itm.customProfitStr.toDoubleOrNull() ?: 0.0
                                val finalProfitTotal = finalSold * finalProfitPerUnit

                                SalesEntry(
                                    id = itm.dbId,
                                    entryDate = entryDateMillis,
                                    shopNumber = selectedShopNumber,
                                    shopName = currentShopObj?.storeName ?: "",
                                    locationNumber = currentShopObj?.locationNumber ?: "",
                                    productName = itm.productName,
                                    packetsGiven = givenVal,
                                    packetsReturned = returnedVal,
                                    packetsSold = finalSold,
                                    ratePerPacket = rateVal,
                                    totalAmount = finalTotal,
                                    profitPerPacket = finalProfitPerUnit,
                                    totalProfit = finalProfitTotal,
                                    status = payStatus,
                                    remarks = remarks.trim().ifEmpty { null },
                                    sessionId = sessionId
                                )
                            }

                            // If we are in edit mode, delete the previous entries of the session (and legacy non-session ID too)
                            val legacyIdToDelete = if (selectedSalesForEdit != null && selectedSalesForEdit?.sessionId.isNullOrEmpty()) {
                                selectedSalesForEdit?.id
                            } else {
                                null
                            }
                            
                            val oldSessionId = selectedSalesForEdit?.sessionId

                            viewModel.saveSalesSession(salesToInsert, oldSessionId, legacyIdToDelete)
                            
                            if (isEdit) {
                                Toast.makeText(context, "Sales log updated", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Sales logged successfully", Toast.LENGTH_SHORT).show()
                            }
                            showAddEditScreen = false
                            selectedSalesForEdit = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_sales_button")
                ) {
                    Text("Save Sales Entry", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    // --- Excel Import Progress Dialog ---
    if (isImporting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Importing Sales Logs") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Reading spreadsheet and validating sales entries...")
                }
            }
        )
    }

    // --- Excel Import Summary Dialog ---
    if (importSummary != null && importSummary!!.type == com.example.utils.Exporter.ImportType.SALES) {
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
                    SummaryRow(label = "Updated Records:", value = "${summary.updatedRecordsCount}")
                    SummaryRow(label = "Skipped Rows (Total):", value = "${summary.skippedRows}", color = if (summary.skippedRows > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Skipped Details:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    
                    SummaryRow(label = "• Duplicate Records:", value = "${summary.duplicateRecordsCount}")
                    SummaryRow(label = "• Invalid Shop Numbers:", value = "${summary.invalidShopNumbersCount}")
                    SummaryRow(label = "• Invalid Products:", value = "${summary.invalidProductsCount}")
                    SummaryRow(label = "• Failed / Invalid Rows:", value = "${summary.failedRowsCount}")
                    
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
                                Exporter.shareFile(context, summary.errorReportFile, "Sales Import Error Report")
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
fun SalesCard(
    sale: SalesEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val formattedDate = remember(sale.entryDate) { sale.entryDateFormatted }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sale.shopName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$formattedDate • Route: ${sale.locationNumber}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Transaction Details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Product", fontSize = 11.sp, color = Color.Gray)
                    Text(sale.productName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Given/Ret", fontSize = 11.sp, color = Color.Gray)
                    Text("${sale.packetsGiven} / ${sale.packetsReturned}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sold (Packets)", fontSize = 11.sp, color = Color.Gray)
                    Text("${sale.packetsSold} sold", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pricing details & Pay Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text("Total Amount", fontSize = 11.sp, color = Color.Gray)
                            Text("₹${"%.2f".format(sale.totalAmount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column {
                            Text("Est. Profit", fontSize = 11.sp, color = Color.Gray)
                            Text("₹${"%.2f".format(sale.totalProfit)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                        }
                    }
                }

                val badgeColor = when (sale.status) {
                    "Paid" -> Color(0xFFE8F5E9)
                    "Partially Paid" -> Color(0xFFFFF3E0)
                    else -> Color(0xFFFFEBEE)
                }
                val textColor = when (sale.status) {
                    "Paid" -> Color(0xFF2E7D32)
                    "Partially Paid" -> Color(0xFFE65100)
                    else -> Color(0xFFC62828)
                }
                Box(
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = sale.status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }

            if (!sale.remarks.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Remarks: ${sale.remarks}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
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
                        text = "Are you sure you want to delete this sales log entry? Calculations will adjust instantly.",
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

data class SaleItemState(
    val id: String = java.util.UUID.randomUUID().toString(),
    val dbId: Int = 0,
    val productName: String = "",
    val ratePerPacketStr: String = "",
    val packetsGivenStr: String = "",
    val packetsReturnedStr: String = "",
    val customProfitStr: String = "",
    val isCustomRate: Boolean = false,
    val productError: String? = null,
    val rateError: String? = null,
    val packetsError: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleItemRow(
    item: SaleItemState,
    products: List<com.example.data.ProductMaster>,
    viewModel: AppViewModel,
    saleDate: Long,
    onItemChange: (SaleItemState) -> Unit,
    onRemove: () -> Unit,
    showRemoveButton: Boolean
) {
    val currentProductObj = remember(item.productName, products) {
        products.firstOrNull { it.productName == item.productName }
    }
    
    val isDynamicProfitEnabled by viewModel.isDynamicProfitEnabled.collectAsStateWithLifecycle()
    val calculations by viewModel.allCostCalculations.collectAsStateWithLifecycle()
    
    var availablePrices by remember { mutableStateOf<List<com.example.data.ProductPrice>>(emptyList()) }
    
    LaunchedEffect(saleDate, isDynamicProfitEnabled, item.ratePerPacketStr, item.productName, availablePrices, calculations) {
        if (!item.isCustomRate && item.ratePerPacketStr.isNotEmpty() && item.productName.isNotEmpty() && availablePrices.isNotEmpty()) {
            val matchingPrice = availablePrices.find { it.sellingPrice.toString() == item.ratePerPacketStr }
            if (matchingPrice != null) {
                val profitToUse = if (isDynamicProfitEnabled) {
                    val saleDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(saleDate))
                    calculations
                        .filter { it.productPriceId == matchingPrice.priceId && it.calculationDate <= saleDateStr }
                        .maxByOrNull { it.calculationDate }
                        ?.profitSnapshot
                        ?: matchingPrice.profitPerPacket
                } else {
                    matchingPrice.profitPerPacket
                }
                
                val formattedProfit = String.format(java.util.Locale.US, "%.2f", profitToUse)
                if (item.customProfitStr != formattedProfit) {
                    onItemChange(item.copy(customProfitStr = formattedProfit))
                }
            }
        }
    }
    
    LaunchedEffect(currentProductObj) {
        if (currentProductObj != null) {
            viewModel.getPricesForProduct(currentProductObj.id).collect { prices ->
                availablePrices = prices
                
                // If this is an existing DB row, check if its rate matches one of the prices.
                // If not, set isCustomRate to true so the custom price/profit fields are visible.
                if (item.ratePerPacketStr.isNotEmpty() && item.dbId != 0 && !item.isCustomRate) {
                    val matchingPrice = prices.any { it.sellingPrice.toString() == item.ratePerPacketStr }
                    if (!matchingPrice) {
                        onItemChange(item.copy(isCustomRate = true))
                    }
                }
            }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Sale Item",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (showRemoveButton) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove item",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            // Product Selector Dropdown
            var prodExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = item.productName.ifEmpty { "Select Product" },
                    onValueChange = {},
                    label = { Text("Snack Product*") },
                    readOnly = true,
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    },
                    isError = item.productError != null,
                    supportingText = item.productError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { prodExpanded = true }
                )
                DropdownMenu(
                    expanded = prodExpanded,
                    onDismissRequest = { prodExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    products.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.productName) },
                            onClick = {
                                prodExpanded = false
                                onItemChange(item.copy(
                                    productName = p.productName,
                                    ratePerPacketStr = "",
                                    customProfitStr = "",
                                    isCustomRate = false,
                                    productError = null,
                                    rateError = null
                                ))
                            }
                        )
                    }
                }
            }
            
            // Packets Given and Returned Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = item.packetsGivenStr,
                    onValueChange = {
                        onItemChange(item.copy(
                            packetsGivenStr = it,
                            packetsError = null
                        ))
                    },
                    label = { Text("Packets Given*") },
                    placeholder = { Text("e.g. 50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = item.packetsError != null,
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = item.packetsReturnedStr,
                    onValueChange = {
                        onItemChange(item.copy(
                            packetsReturnedStr = it,
                            packetsError = null
                        ))
                    },
                    label = { Text("Packets Returned") },
                    placeholder = { Text("e.g. 2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            
            if (item.packetsError != null) {
                Text(
                    text = item.packetsError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // Rate per packet selection (dropdown + custom inputs)
            var rateMenuExpanded by remember { mutableStateOf(false) }
            val isCustomRate = item.isCustomRate

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = rateMenuExpanded,
                    onExpandedChange = { rateMenuExpanded = !rateMenuExpanded }
                ) {
                    val selectedRateText = if (isCustomRate) {
                        "Custom Price"
                    } else {
                        availablePrices.find { it.sellingPrice.toString() == item.ratePerPacketStr }?.let { "₹${it.sellingPrice}" } ?: "Select Rate"
                    }
                    OutlinedTextField(
                        value = selectedRateText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rate Per Packet*") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rateMenuExpanded) },
                        isError = item.rateError != null,
                        supportingText = item.rateError?.let { { Text(it) } },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = rateMenuExpanded,
                        onDismissRequest = { rateMenuExpanded = false }
                    ) {
                        availablePrices.forEach { price ->
                            val dynamicProfitOpt = if (isDynamicProfitEnabled) {
                                val saleDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(saleDate))
                                calculations
                                    .filter { it.productPriceId == price.priceId && it.calculationDate <= saleDateStr }
                                    .maxByOrNull { it.calculationDate }
                                    ?.profitSnapshot
                            } else null
                            val profitToUse = dynamicProfitOpt ?: price.profitPerPacket
                            val labelSuffix = if (dynamicProfitOpt != null) " (Dynamic Profit: ₹${String.format("%.2f", dynamicProfitOpt)})" else " (Profit: ₹${price.profitPerPacket})"
                            
                            DropdownMenuItem(
                                text = { Text("₹${price.sellingPrice}$labelSuffix") },
                                onClick = {
                                    onItemChange(item.copy(
                                        ratePerPacketStr = price.sellingPrice.toString(),
                                        customProfitStr = String.format(Locale.US, "%.2f", profitToUse),
                                        isCustomRate = false,
                                        rateError = null
                                    ))
                                    rateMenuExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Custom Price") },
                            onClick = {
                                onItemChange(item.copy(
                                    ratePerPacketStr = "",
                                    customProfitStr = "",
                                    isCustomRate = true,
                                    rateError = null
                                ))
                                rateMenuExpanded = false
                            }
                        )
                    }
                }

                if (isCustomRate) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = item.ratePerPacketStr,
                            onValueChange = {
                                onItemChange(item.copy(
                                    ratePerPacketStr = it,
                                    rateError = null
                                ))
                            },
                            label = { Text("Selling Price*") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = item.customProfitStr,
                            onValueChange = {
                                onItemChange(item.copy(customProfitStr = it))
                            },
                            label = { Text("Profit*") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }
            
            // Row Level Calculations
            val givenCount = item.packetsGivenStr.toIntOrNull() ?: 0
            val returnedCount = item.packetsReturnedStr.toIntOrNull() ?: 0
            val soldCalculated = maxOf(0, givenCount - returnedCount)
            
            val liveRate = item.ratePerPacketStr.toDoubleOrNull() ?: 0.0
            val totalAmountCalculated = soldCalculated * liveRate
            
            val liveProfitPerPacket = item.customProfitStr.toDoubleOrNull() ?: 0.0
            val totalProfitCalculated = soldCalculated * liveProfitPerPacket
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sold: $soldCalculated | Amt: ₹${"%.2f".format(totalAmountCalculated)} | Profit: ₹${"%.2f".format(totalProfitCalculated)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
