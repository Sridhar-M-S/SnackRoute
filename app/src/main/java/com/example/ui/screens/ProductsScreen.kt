package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ProductMaster
import com.example.data.ProductPrice
import com.example.ui.AppViewModel
import com.example.utils.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: AppViewModel,
    onOpenChat: () -> Unit,
    onOpenTimetable: () -> Unit,
    onBack: () -> Unit = {},
    showBackButton: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importProductsFromExcel(context, uri)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var sortBy by remember { mutableStateOf("Name") } // Name, Price, Profit
    var sortAscending by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    
    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedProductForEdit by remember { mutableStateOf<ProductMaster?>(null) }
    
    // Dialog state fields
    var productName by remember { mutableStateOf("") }
    var productCategory by remember { mutableStateOf("Popcorn") }
    var productStatus by remember { mutableStateOf("Active") }
    var priceConfigurations by remember { mutableStateOf(listOf<com.example.data.ProductPrice>()) }
    
    // Dialog price input fields
    var newSellingPrice by remember { mutableStateOf("") }
    var newProfitPerPacket by remember { mutableStateOf("") }
    var editingPriceId by remember { mutableStateOf<Int?>(null) }
    
    // Validation errors
    var nameError by remember { mutableStateOf<String?>(null) }
    var priceError by remember { mutableStateOf<String?>(null) }
    var profitError by remember { mutableStateOf<String?>(null) }
    
    // Category suggestions
    val categories = listOf("Popcorn", "Chips", "Savouries", "Puffs", "Drinks", "Healthy Snack")
    
    // --- Search & Filters ---
    LaunchedEffect(searchQuery, selectedCategoryFilter, sortBy, sortAscending) {
        listState.scrollToItem(0)
    }
    
    val filteredProducts = remember(products, searchQuery, selectedCategoryFilter, sortBy, sortAscending) {
        var list = products.filter { prod ->
            val matchSearch = prod.productName.contains(searchQuery, ignoreCase = true) ||
                    prod.productCategory.contains(searchQuery, ignoreCase = true)
            val matchFilter = selectedCategoryFilter == null || prod.productCategory == selectedCategoryFilter
            matchSearch && matchFilter
        }
        
        list = when (sortBy) {
            else -> if (sortAscending) list.sortedBy { it.productName } else list.sortedByDescending { it.productName }
        }
        list
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Master", fontWeight = FontWeight.Bold) },
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
                        onClick = { importLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") },
                        modifier = Modifier.testTag("import_products_button")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Import Excel")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val allPrices = viewModel.getAllPrices()
                                Exporter.exportProducts(context, products, allPrices)
                            }
                        },
                        modifier = Modifier.testTag("export_products_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export CSV")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedProductForEdit = null
                    productName = ""
                    productCategory = "Popcorn"
                    productStatus = "Active"
                    nameError = null
                    priceError = null
                    profitError = null
                    priceConfigurations = emptyList()
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
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
            // --- Search input ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search snacks, categories...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            
            // --- Category Filters Row ---
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryFilter == null,
                        onClick = { selectedCategoryFilter = null },
                        label = { Text("All Categories") }
                    )
                }
                categories.forEach { cat ->
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }
            
            // --- Sort Pills ---
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Sort:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                FilterChip(
                    selected = sortBy == "Name",
                    onClick = {
                        if (sortBy == "Name") sortAscending = !sortAscending
                        else { sortBy = "Name"; sortAscending = true }
                    },
                    label = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Name")
                            Icon(if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )
                FilterChip(
                    selected = sortBy == "Price",
                    onClick = {
                        if (sortBy == "Price") sortAscending = !sortAscending
                        else { sortBy = "Price"; sortAscending = false }
                    },
                    label = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Price")
                            Icon(if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )
                FilterChip(
                    selected = sortBy == "Profit",
                    onClick = {
                        if (sortBy == "Profit") sortAscending = !sortAscending
                        else { sortBy = "Profit"; sortAscending = false }
                    },
                    label = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Profit")
                            Icon(if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }
            
            // --- List of Products ---
            if (filteredProducts.isEmpty()) {
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
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No Products Available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredProducts, key = { it.id }) { prod ->
                        ProductCard(
                            product = prod,
                            onEdit = {
                                selectedProductForEdit = prod
                                productName = prod.productName
                                productCategory = prod.productCategory
                                // sellingPrice = prod.sellingPrice.toString()
                                // profitPerPacket = prod.profitPerPacket.toString()
                                productStatus = prod.status
                                nameError = null
                                priceError = null
                                profitError = null
                                scope.launch {
                                    viewModel.getPricesForProduct(prod.id).collect { prices ->
                                        priceConfigurations = prices
                                    }
                                }
                                showAddEditDialog = true
                            },
                            onDelete = {
                                viewModel.deleteProduct(prod)
                                Toast.makeText(context, "Product deleted successfully", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
    
    // --- Add / Edit Dialog ---
    if (showAddEditDialog) {
        val isEdit = selectedProductForEdit != null
        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = { Text(if (isEdit) "Edit Snack Configuration" else "Add New Snack", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = productName,
                        onValueChange = { productName = it; nameError = null },
                        label = { Text("Snack Product Name*") },
                        placeholder = { Text("e.g. Cheese Popcorn") },
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // Category Selector
                    var catExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = productCategory,
                            onValueChange = {},
                            label = { Text("Category*") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { catExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { catExpanded = true }
                        )
                        DropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        productCategory = cat
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Prices & Profits
                    Text("Price Configurations", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    
                    priceConfigurations.forEach { price ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("₹${price.sellingPrice} / Profit: ₹${price.profitPerPacket}")
                            Row {
                                IconButton(onClick = { 
                                    newSellingPrice = price.sellingPrice.toString()
                                    newProfitPerPacket = price.profitPerPacket.toString()
                                    editingPriceId = price.priceId
                                }) { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                IconButton(onClick = { priceConfigurations = priceConfigurations.filter { it.priceId != price.priceId } }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newSellingPrice,
                            onValueChange = { newSellingPrice = it },
                            label = { Text("Selling Price (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = newProfitPerPacket,
                            onValueChange = { newProfitPerPacket = it },
                            label = { Text("Profit / Packet (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Button(
                        onClick = {
                            val sp = newSellingPrice.toDoubleOrNull() ?: 0.0
                            val pr = newProfitPerPacket.toDoubleOrNull() ?: 0.0
                            if (sp > 0) {
                                if (editingPriceId != null) {
                                    priceConfigurations = priceConfigurations.map { if (it.priceId == editingPriceId) it.copy(sellingPrice = sp, profitPerPacket = pr) else it }
                                    editingPriceId = null
                                } else {
                                    priceConfigurations = priceConfigurations + com.example.data.ProductPrice(productId = selectedProductForEdit?.id ?: 0, sellingPrice = sp, profitPerPacket = pr)
                                }
                                newSellingPrice = ""
                                newProfitPerPacket = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (editingPriceId != null) "Update Price" else "Add Price")
                    }
                    
                    // Status selection
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Status:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { productStatus = "Active" }) {
                            RadioButton(selected = productStatus == "Active", onClick = { productStatus = "Active" })
                            Text("Active", fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { productStatus = "Inactive" }) {
                            RadioButton(selected = productStatus == "Inactive", onClick = { productStatus = "Inactive" })
                            Text("Inactive", fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        var isValid = true
                        if (productName.trim().isEmpty()) {
                            nameError = "Snack Name is required"
                            isValid = false
                        } else {
                            val exists = products.any {
                                it.productName.equals(productName, ignoreCase = true) &&
                                        it.id != (selectedProductForEdit?.id ?: -1)
                            }
                            if (exists) {
                                nameError = "Product already exists"
                                isValid = false
                            }
                        }
                        
                        if (priceConfigurations.isEmpty()) {
                            priceError = "Add at least one price configuration"
                            isValid = false
                        } else if (priceConfigurations.any { it.sellingPrice <= 0 || it.profitPerPacket < 0 }) {
                            priceError = "All price configurations must have valid selling price and profit"
                            isValid = false
                        }
                        
                        if (isValid) {
                            val product = ProductMaster(
                                id = selectedProductForEdit?.id ?: 0,
                                productName = productName.trim(),
                                productCategory = productCategory,
                                status = productStatus
                            )
                            
                            scope.launch {
                                if (selectedProductForEdit != null) {
                                    viewModel.updateProduct(product)
                                    // Handle price updates/additions/deletions - simplified for now: delete all and re-add
                                    viewModel.deletePricesForProduct(product.id)
                                    priceConfigurations.forEach { viewModel.addPrice(it.copy(productId = product.id)) }
                                    Toast.makeText(context, "Product configuration updated", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addProductWithPrices(product, priceConfigurations)
                                    Toast.makeText(context, "Product configured successfully", Toast.LENGTH_SHORT).show()
                                }
                                showAddEditDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_product_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Excel Import Progress Dialog ---
    if (isImporting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Importing Products") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Reading spreadsheet and validating products...")
                }
            }
        )
    }

    // --- Excel Import Summary Dialog ---
    if (importSummary != null && importSummary!!.type == com.example.utils.Exporter.ImportType.PRODUCTS) {
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
                    SummaryRow(label = "Updated Records:", value = "${summary.updatedRecordsCount}", color = MaterialTheme.colorScheme.secondary)
                    SummaryRow(label = "Skipped Rows (Total):", value = "${summary.skippedRows}", color = if (summary.skippedRows > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Skipped Details:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    
                    SummaryRow(label = "• Duplicate Records:", value = "${summary.duplicateRecordsCount}")
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
                                Exporter.shareFile(context, summary.errorReportFile, "Product Import Error Report")
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
fun ProductCard(
    product: ProductMaster,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Column {
                        Text(
                            text = product.productName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${product.productCategory} • ${product.status}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (product.status == "Active") Color(0xFF2E7D32) else Color.Gray
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text("See Price Configurations to view pricing.")
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
                        text = "Are you sure you want to delete this product? It will delete historic sales entries linked to it.",
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
fun ProductMetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
