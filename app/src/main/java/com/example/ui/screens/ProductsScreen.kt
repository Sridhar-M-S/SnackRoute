package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.ui.AppViewModel
import com.example.utils.Exporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var sortBy by remember { mutableStateOf("Name") } // Name, Price, Profit

    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedProductForEdit by remember { mutableStateOf<ProductMaster?>(null) }

    // Dialog state fields
    var productName by remember { mutableStateOf("") }
    var productCategory by remember { mutableStateOf("Popcorn") }
    var sellingPrice by remember { mutableStateOf("") }
    var profitPerPacket by remember { mutableStateOf("") }
    var productStatus by remember { mutableStateOf("Active") }

    // Validation errors
    var nameError by remember { mutableStateOf<String?>(null) }
    var priceError by remember { mutableStateOf<String?>(null) }
    var profitError by remember { mutableStateOf<String?>(null) }

    // Category suggestions
    val categories = listOf("Popcorn", "Chips", "Savouries", "Puffs", "Drinks", "Healthy Snack")

    // --- Add Default Snack Seed Data if Database is Empty ---
    LaunchedEffect(products) {
        if (products.isEmpty()) {
            val defaults = listOf(
                ProductMaster(productName = "Cheese Popcorn", productCategory = "Popcorn", sellingPrice = 10.0, profitPerPacket = 4.0),
                ProductMaster(productName = "Caramel Popcorn", productCategory = "Popcorn", sellingPrice = 15.0, profitPerPacket = 6.0),
                ProductMaster(productName = "Masala Popcorn", productCategory = "Popcorn", sellingPrice = 10.0, profitPerPacket = 4.0),
                ProductMaster(productName = "Butter Popcorn", productCategory = "Popcorn", sellingPrice = 10.0, profitPerPacket = 4.0),
                ProductMaster(productName = "Banana Chips", productCategory = "Chips", sellingPrice = 20.0, profitPerPacket = 8.0),
                ProductMaster(productName = "Murukku", productCategory = "Savouries", sellingPrice = 15.0, profitPerPacket = 6.0),
                ProductMaster(productName = "Mixture", productCategory = "Savouries", sellingPrice = 20.0, profitPerPacket = 8.0),
                ProductMaster(productName = "Corn Rings", productCategory = "Savouries", sellingPrice = 10.0, profitPerPacket = 4.0),
                ProductMaster(productName = "Potato Chips", productCategory = "Chips", sellingPrice = 10.0, profitPerPacket = 4.0),
                ProductMaster(productName = "Nachos", productCategory = "Chips", sellingPrice = 25.0, profitPerPacket = 10.0)
            )
            defaults.forEach { viewModel.addProduct(it) }
        }
    }

    // --- Search & Filters ---
    val filteredProducts = remember(products, searchQuery, selectedCategoryFilter, sortBy) {
        var list = products.filter { prod ->
            val matchSearch = prod.productName.contains(searchQuery, ignoreCase = true) ||
                    prod.productCategory.contains(searchQuery, ignoreCase = true)
            val matchFilter = selectedCategoryFilter == null || prod.productCategory == selectedCategoryFilter
            matchSearch && matchFilter
        }

        list = when (sortBy) {
            "Price" -> list.sortedByDescending { it.sellingPrice }
            "Profit" -> list.sortedByDescending { it.profitPerPacket }
            else -> list.sortedBy { it.productName }
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Master", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = { Exporter.exportProducts(context, products) },
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
                    sellingPrice = ""
                    profitPerPacket = ""
                    productStatus = "Active"
                    nameError = null
                    priceError = null
                    profitError = null
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
                    onClick = { sortBy = "Name" },
                    label = { Text("A - Z Name") }
                )
                FilterChip(
                    selected = sortBy == "Price",
                    onClick = { sortBy = "Price" },
                    label = { Text("Highest Price") }
                )
                FilterChip(
                    selected = sortBy == "Profit",
                    onClick = { sortBy = "Profit" },
                    label = { Text("Highest Profit") }
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
                                sellingPrice = prod.sellingPrice.toString()
                                profitPerPacket = prod.profitPerPacket.toString()
                                productStatus = prod.status
                                nameError = null
                                priceError = null
                                profitError = null
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

                    // Prices & Profits (Numeric inputs)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = sellingPrice,
                            onValueChange = {
                                sellingPrice = it
                                priceError = null
                            },
                            label = { Text("Selling Price (₹)*") },
                            placeholder = { Text("e.g. 15.00") },
                            isError = priceError != null,
                            supportingText = priceError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = profitPerPacket,
                            onValueChange = {
                                profitPerPacket = it
                                profitError = null
                            },
                            label = { Text("Profit / Packet (₹)*") },
                            placeholder = { Text("e.g. 5.50") },
                            isError = profitError != null,
                            supportingText = profitError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
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

                        val sellPriceVal = sellingPrice.toDoubleOrNull()
                        if (sellPriceVal == null || sellPriceVal <= 0.0) {
                            priceError = "Enter valid price"
                            isValid = false
                        }

                        val profitVal = profitPerPacket.toDoubleOrNull()
                        if (profitVal == null || profitVal < 0.0) {
                            profitError = "Enter valid profit"
                            isValid = false
                        } else if (sellPriceVal != null && profitVal > sellPriceVal) {
                            profitError = "Profit cannot exceed selling price"
                            isValid = false
                        }

                        if (isValid) {
                            val product = ProductMaster(
                                id = selectedProductForEdit?.id ?: 0,
                                productName = productName.trim(),
                                productCategory = productCategory,
                                sellingPrice = sellPriceVal!!,
                                profitPerPacket = profitVal!!,
                                status = productStatus
                            )

                            if (isEdit) {
                                viewModel.updateProduct(product)
                                Toast.makeText(context, "Product configuration updated", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addProduct(product)
                                Toast.makeText(context, "Product configured successfully", Toast.LENGTH_SHORT).show()
                            }
                            showAddEditDialog = false
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
                ProductMetricItem(label = "Selling Price", value = "₹${"%.2f".format(product.sellingPrice)}")
                ProductMetricItem(label = "Profit / Packet", value = "₹${"%.2f".format(product.profitPerPacket)}")
                ProductMetricItem(label = "Profit Ratio", value = "${"%.1f".format((product.profitPerPacket / product.sellingPrice) * 100)}%")
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
