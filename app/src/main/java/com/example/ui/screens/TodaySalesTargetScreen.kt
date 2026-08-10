package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ProductMaster
import com.example.data.ProductPrice
import com.example.data.SalesTargetItem
import com.example.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodaySalesTargetScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsStateWithLifecycle()
    val allPrices by viewModel.allPrices.collectAsStateWithLifecycle()
    val todayTargetsFromDb by viewModel.todaySalesTargets.collectAsStateWithLifecycle()

    val todayDateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val todayFormattedDisplay = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }

    var localTargetItems by remember { mutableStateOf<List<SalesTargetItem>>(emptyList()) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(todayTargetsFromDb) {
        if (!isInitialized || localTargetItems.isEmpty() && todayTargetsFromDb.isNotEmpty()) {
            localTargetItems = todayTargetsFromDb
            isInitialized = true
        }
    }

    val activeProducts = remember(products) { products.filter { it.status == "Active" } }
    val categories = remember(activeProducts) { activeProducts.map { it.productCategory }.distinct().sorted() }

    var selectedCategory by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<ProductMaster?>(null) }
    var selectedPrice by remember { mutableStateOf<ProductPrice?>(null) }
    var inputPacketsText by remember { mutableStateOf("50") }

    var editingItem by remember { mutableStateOf<SalesTargetItem?>(null) }
    var editPacketsText by remember { mutableStateOf("") }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var productDropdownExpanded by remember { mutableStateOf(false) }
    var priceDropdownExpanded by remember { mutableStateOf(false) }

    val categoryProducts = remember(activeProducts, selectedCategory) {
        if (selectedCategory.isEmpty()) activeProducts
        else activeProducts.filter { it.productCategory == selectedCategory }
    }

    val productPrices = remember(allPrices, selectedProduct) {
        if (selectedProduct == null) emptyList()
        else allPrices.filter { it.productId == selectedProduct!!.id && it.status == "Active" }
    }

    // Auto select first price when product changes
    LaunchedEffect(selectedProduct) {
        if (selectedProduct != null) {
            val prices = allPrices.filter { it.productId == selectedProduct!!.id && it.status == "Active" }
            selectedPrice = prices.firstOrNull()
        } else {
            selectedPrice = null
        }
    }

    val packetsInputInt = inputPacketsText.toIntOrNull() ?: 0
    val currentPriceVal = selectedPrice?.sellingPrice ?: 0.0
    val newItemCalculatedAmount = currentPriceVal * packetsInputInt

    val totalTargetPackets = localTargetItems.sumOf { it.targetPackets }
    val totalTargetAmount = localTargetItems.sumOf { it.targetAmount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Today's Sales Target",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = todayFormattedDisplay,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("today_target_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Total Target Amount",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${"%,.2f".format(totalTargetAmount)}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("running_total_target_amount")
                        )
                        Text(
                            text = "$totalTargetPackets Total Packets",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.saveTodaySalesTargets(todayDateStr, localTargetItems)
                            Toast.makeText(
                                context,
                                "Today's sales target has been saved successfully. Complete your target today. Best of luck!",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("btn_save_today_sales_target"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Target", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // --- Section 1: Add/Select Target Item ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.TrackChanges,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Add Product Sales Target",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Select from existing categories, products, and prices in your catalog.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1. Category Selector
                        ExposedDropdownMenuBox(
                            expanded = categoryDropdownExpanded,
                            onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = if (selectedCategory.isEmpty()) "All Categories" else selectedCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Product Category") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("dropdown_target_category"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Categories") },
                                    onClick = {
                                        selectedCategory = ""
                                        selectedProduct = null
                                        categoryDropdownExpanded = false
                                    }
                                )
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = {
                                            selectedCategory = cat
                                            selectedProduct = null
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 2. Product Selector
                        ExposedDropdownMenuBox(
                            expanded = productDropdownExpanded,
                            onExpandedChange = { productDropdownExpanded = !productDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedProduct?.productName ?: "Select Product",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Product") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("dropdown_target_product"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = productDropdownExpanded,
                                onDismissRequest = { productDropdownExpanded = false }
                            ) {
                                if (categoryProducts.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No products available") },
                                        onClick = { productDropdownExpanded = false },
                                        enabled = false
                                    )
                                } else {
                                    categoryProducts.forEach { prod ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(prod.productName, fontWeight = FontWeight.Bold)
                                                    Text(prod.productCategory, fontSize = 11.sp, color = Color.Gray)
                                                }
                                            },
                                            onClick = {
                                                selectedProduct = prod
                                                productDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Selling Price Selector
                        if (selectedProduct != null) {
                            if (productPrices.size > 1) {
                                ExposedDropdownMenuBox(
                                    expanded = priceDropdownExpanded,
                                    onExpandedChange = { priceDropdownExpanded = !priceDropdownExpanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = selectedPrice?.let { "₹${"%,.2f".format(it.sellingPrice)} per pkt" } ?: "Select Price",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Selling Price") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priceDropdownExpanded) },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                            .testTag("dropdown_target_price"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = priceDropdownExpanded,
                                        onDismissRequest = { priceDropdownExpanded = false }
                                    ) {
                                        productPrices.forEach { pr ->
                                            DropdownMenuItem(
                                                text = { Text("Selling Price: ₹${"%,.2f".format(pr.sellingPrice)}") },
                                                onClick = {
                                                    selectedPrice = pr
                                                    priceDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Selling Price", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = "₹${"%,.2f".format(currentPriceVal)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Target Packets Input & Stepper
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val current = inputPacketsText.toIntOrNull() ?: 0
                                    if (current > 5) {
                                        inputPacketsText = (current - 5).toString()
                                    } else if (current > 1) {
                                        inputPacketsText = (current - 1).toString()
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                                    .testTag("btn_decrease_packets")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease Packets")
                            }

                            OutlinedTextField(
                                value = inputPacketsText,
                                onValueChange = { inputPacketsText = it.filter { char -> char.isDigit() } },
                                label = { Text("Target Packets") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_target_packets"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            IconButton(
                                onClick = {
                                    val current = inputPacketsText.toIntOrNull() ?: 0
                                    inputPacketsText = (current + 10).toString()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                                    .testTag("btn_increase_packets")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase Packets")
                            }
                        }

                        // 5. Item Calculated Summary
                        if (selectedProduct != null && packetsInputInt > 0) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Calculated Item Amount", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "₹${"%,.2f".format(newItemCalculatedAmount)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // 6. Add Item Button
                        Button(
                            onClick = {
                                val prod = selectedProduct ?: return@Button
                                val packets = inputPacketsText.toIntOrNull() ?: return@Button
                                if (packets <= 0) return@Button

                                val price = currentPriceVal
                                val amount = price * packets

                                val existingIndex = localTargetItems.indexOfFirst {
                                    it.productName == prod.productName && Math.abs(it.sellingPrice - price) < 0.01
                                }

                                if (existingIndex >= 0) {
                                    val existing = localTargetItems[existingIndex]
                                    val updatedPackets = existing.targetPackets + packets
                                    val updatedAmount = price * updatedPackets
                                    val updatedList = localTargetItems.toMutableList()
                                    updatedList[existingIndex] = existing.copy(
                                        targetPackets = updatedPackets,
                                        targetAmount = updatedAmount
                                    )
                                    localTargetItems = updatedList
                                } else {
                                    val newItem = SalesTargetItem(
                                        targetDate = todayDateStr,
                                        productName = prod.productName,
                                        productCategory = prod.productCategory,
                                        sellingPrice = price,
                                        targetPackets = packets,
                                        targetAmount = amount
                                    )
                                    localTargetItems = localTargetItems + newItem
                                }

                                // Reset selection for adding next item
                                selectedProduct = null
                                selectedPrice = null
                                inputPacketsText = "50"
                            },
                            enabled = selectedProduct != null && packetsInputInt > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_add_product_target"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Product to Target Plan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- Section 2: Planned Sales Targets List for Today ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Target Items (${localTargetItems.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (localTargetItems.isNotEmpty()) {
                        TextButton(
                            onClick = { localTargetItems = emptyList() },
                            modifier = Modifier.testTag("btn_clear_all_targets")
                        ) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (localTargetItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Assignment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = "No sales target has been set for today.",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("empty_target_message")
                            )
                            Text(
                                text = "Select products above to build today's sales target plan.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(localTargetItems, key = { "${it.id}_${it.productName}_${it.sellingPrice}" }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("target_item_card_${item.productName}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = item.productCategory,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.productName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Selling Price: ₹${"%,.2f".format(item.sellingPrice)} / packet",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            editingItem = item
                                            editPacketsText = item.targetPackets.toString()
                                        },
                                        modifier = Modifier.testTag("btn_edit_target_item_${item.productName}")
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit Item",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            localTargetItems = localTargetItems.filter { it != item }
                                        },
                                        modifier = Modifier.testTag("btn_delete_target_item_${item.productName}")
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Item",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (item.targetPackets > 1) {
                                                val newPackets = item.targetPackets - 1
                                                val newAmount = item.sellingPrice * newPackets
                                                localTargetItems = localTargetItems.map {
                                                    if (it == item) it.copy(targetPackets = newPackets, targetAmount = newAmount)
                                                    else it
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                            .testTag("btn_item_minus_${item.productName}")
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                    }

                                    Text(
                                        text = "${item.targetPackets} Pkts",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )

                                    IconButton(
                                        onClick = {
                                            val newPackets = item.targetPackets + 1
                                            val newAmount = item.sellingPrice * newPackets
                                            localTargetItems = localTargetItems.map {
                                                if (it == item) it.copy(targetPackets = newPackets, targetAmount = newAmount)
                                                else it
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                            .testTag("btn_item_plus_${item.productName}")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Target Amount", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "₹${"%,.2f".format(item.targetAmount)}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingItem != null) {
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Edit Target Packets") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Product: ${editingItem!!.productName}", fontWeight = FontWeight.Bold)
                    Text("Selling Price: ₹${"%,.2f".format(editingItem!!.sellingPrice)} / packet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editPacketsText,
                        onValueChange = { editPacketsText = it },
                        label = { Text("Target Packets") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_edit_target_packets")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newCount = editPacketsText.toIntOrNull()
                        if (newCount != null && newCount >= 0) {
                            val newAmount = editingItem!!.sellingPrice * newCount
                            localTargetItems = localTargetItems.map {
                                if (it == editingItem) it.copy(targetPackets = newCount, targetAmount = newAmount)
                                else it
                            }
                            editingItem = null
                        } else {
                            Toast.makeText(context, "Please enter a valid packet count", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_edit_target")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingItem = null },
                    modifier = Modifier.testTag("btn_cancel_edit_target")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
