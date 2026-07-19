package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.AppViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- Unit System Helpers (Step 3) ---
object UnitConverter {
    val WeightUnits = listOf("mg", "g", "kg")
    val VolumeUnits = listOf("ml", "L")
    val QuantityUnits = listOf("Piece", "Packet", "Roll", "Box", "Bundle")
    val AllUnits = WeightUnits + VolumeUnits + QuantityUnits

    fun getUnitType(unit: String): String {
        return when (unit.lowercase(Locale.getDefault())) {
            "mg", "g", "kg" -> "Weight"
            "ml", "l" -> "Volume"
            else -> "Quantity"
        }
    }

    fun convertToGramOrMl(quantity: Double, unit: String): Double {
        return when (unit.lowercase(Locale.getDefault())) {
            "mg" -> quantity / 1000.0
            "g" -> quantity
            "kg" -> quantity * 1000.0
            "ml" -> quantity
            "l" -> quantity * 1000.0
            else -> quantity
        }
    }

    fun calculateCostPerUsageUnit(
        purchasePrice: Double,
        purchaseQty: Double,
        purchaseUnit: String,
        usageUnit: String,
        sealCost: Double = 0.0,
        printingCost: Double = 0.0,
        largeCoverDistribution: Int = 1
    ): Double {
        if (purchaseQty <= 0) return 0.0
        
        val basePurchasePrice = purchasePrice + sealCost + printingCost
        val unitTypePurchase = getUnitType(purchaseUnit)
        val unitTypeUsage = getUnitType(usageUnit)

        if (unitTypePurchase != unitTypeUsage) {
            val costPerPurchaseUnit = basePurchasePrice / purchaseQty
            return costPerPurchaseUnit / largeCoverDistribution.coerceAtLeast(1).toDouble()
        }

        return when (unitTypePurchase) {
            "Weight" -> {
                val purchaseQtyInGrams = convertToGramOrMl(purchaseQty, purchaseUnit)
                val costPerGram = basePurchasePrice / purchaseQtyInGrams
                when (usageUnit.lowercase(Locale.getDefault())) {
                    "mg" -> costPerGram / 1000.0
                    "g" -> costPerGram
                    "kg" -> costPerGram * 1000.0
                    else -> costPerGram
                }
            }
            "Volume" -> {
                val purchaseQtyInMl = convertToGramOrMl(purchaseQty, purchaseUnit)
                val costPerMl = basePurchasePrice / purchaseQtyInMl
                when (usageUnit.lowercase(Locale.getDefault())) {
                    "ml" -> costPerMl
                    "l" -> costPerMl * 1000.0
                    else -> costPerMl
                }
            }
            else -> {
                val costPerPurchaseUnit = basePurchasePrice / purchaseQty
                costPerPurchaseUnit / largeCoverDistribution.coerceAtLeast(1).toDouble()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostEngineScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Preferences & Settings Toggle (Step 17)
    val isDynamicProfitEnabled by viewModel.isDynamicProfitEnabled.collectAsState()

    // Master flows from ViewModel
    val products by viewModel.products.collectAsState()
    val ingredients by viewModel.allIngredients.collectAsState()
    val purchases by viewModel.allIngredientPurchases.collectAsState()
    val calculations by viewModel.allCostCalculations.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Calculate Cost, 1: Ingredient Master

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dynamic Cost Engine", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("cost_engine_back")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (isDynamicProfitEnabled) "Dynamic ON" else "Dynamic OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDynamicProfitEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isDynamicProfitEnabled,
                            onCheckedChange = { viewModel.setDynamicProfitEnabled(it) },
                            modifier = Modifier.scale(0.8f).testTag("dynamic_profit_toggle")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Calculate Cost", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_calculate_cost")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Ingredient Master", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_ingredient_master")
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (activeTab == 0) {
                    CalculateCostTabContent(
                        viewModel = viewModel,
                        products = products,
                        ingredients = ingredients,
                        purchases = purchases,
                        calculations = calculations,
                        isDynamicProfitEnabled = isDynamicProfitEnabled,
                        onToggleDynamic = { viewModel.setDynamicProfitEnabled(it) }
                    )
                } else {
                    IngredientMasterTabContent(
                        viewModel = viewModel,
                        ingredients = ingredients,
                        purchases = purchases
                    )
                }
            }
        }
    }
}

// ============================================
// TAB 0: CALCULATE COST CONTENT
// ============================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculateCostTabContent(
    viewModel: AppViewModel,
    products: List<ProductMaster>,
    ingredients: List<Ingredient>,
    purchases: List<IngredientPurchase>,
    calculations: List<CostCalculation>,
    isDynamicProfitEnabled: Boolean,
    onToggleDynamic: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Dynamic Selectors State (Step 1)
    var selectedCategory by remember { mutableStateOf("") }
    var selectedVarietyProductId by remember { mutableStateOf<Int?>(null) }
    var selectedPriceId by remember { mutableStateOf<Int?>(null) }

    var allPricesForSelectedProduct by remember { mutableStateOf<List<ProductPrice>>(emptyList()) }
    LaunchedEffect(selectedVarietyProductId) {
        val prodId = selectedVarietyProductId
        if (prodId != null) {
            viewModel.getPricesForProduct(prodId).collect { prices ->
                allPricesForSelectedProduct = prices
            }
        } else {
            allPricesForSelectedProduct = emptyList()
        }
    }

    // Dropdowns Expanded States
    var catExpanded by remember { mutableStateOf(false) }
    var varExpanded by remember { mutableStateOf(false) }
    var priceExpanded by remember { mutableStateOf(false) }

    // Derive Categories
    val categories = remember(products) {
        products.map { it.productCategory }.distinct().sorted()
    }

    // Derive Varieties for Selected Category
    val filteredProducts = remember(products, selectedCategory) {
        if (selectedCategory.isEmpty()) emptyList()
        else products.filter { it.productCategory == selectedCategory }
    }

    // Derive Prices for Selected Product Variety
    val filteredPrices = allPricesForSelectedProduct

    val selectedProductObj = remember(products, selectedVarietyProductId) {
        products.find { it.id == selectedVarietyProductId }
    }

    val selectedPriceObj = remember(allPricesForSelectedProduct, selectedPriceId) {
        allPricesForSelectedProduct.find { it.priceId == selectedPriceId }
    }

    // Recipe State mapping: ingredientId -> UsageQuantity & UsageUnit
    var checkedIngredients by remember { mutableStateOf(setOf<Int>()) }
    var ingredientUsages by remember { mutableStateOf(mapOf<Int, Double>()) }
    var ingredientUnits by remember { mutableStateOf(mapOf<Int, String>()) }

    // Historical calculations for selected price variant
    val variantCalculations = remember(calculations, selectedPriceId) {
        if (selectedPriceId == null) emptyList()
        else calculations.filter { it.productPriceId == selectedPriceId }.sortedByDescending { it.version }
    }

    val activeCalculation = variantCalculations.firstOrNull()

    // History Dialog State
    var showHistoryDialog by remember { mutableStateOf(false) }
    var historicalItemsMap by remember { mutableStateOf<Map<Int, List<CostCalculationItem>>>(emptyMap()) }

    // Fetch snapshot items for historical versions when needed
    LaunchedEffect(variantCalculations) {
        variantCalculations.forEach { calc ->
            if (!historicalItemsMap.containsKey(calc.calculationId)) {
                viewModel.getCalculationItems(calc.calculationId).first().let { items ->
                    historicalItemsMap = historicalItemsMap + (calc.calculationId to items)
                }
            }
        }
    }

    // Automatically pre-load recipe usages from active calculation if it exists
    LaunchedEffect(selectedPriceId, activeCalculation) {
        if (activeCalculation != null) {
            viewModel.getCalculationItems(activeCalculation.calculationId).first().let { items ->
                val checked = mutableSetOf<Int>()
                val usages = mutableMapOf<Int, Double>()
                val units = mutableMapOf<Int, String>()
                items.forEach { item ->
                    checked.add(item.ingredientId)
                    usages[item.ingredientId] = item.usageQuantity
                    units[item.ingredientId] = item.usageUnit
                }
                checkedIngredients = checked
                ingredientUsages = usages
                ingredientUnits = units
            }
        } else {
            // Reset
            checkedIngredients = emptySet()
            ingredientUsages = emptyMap()
            ingredientUnits = emptyMap()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- STEP 1: PRODUCT SELECTION CARD ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("product_selection_card"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Step 1: Select Product Details",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // 1. Snack Category Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCategory.ifEmpty { "Select Snack Category" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Snack Category") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth().clickable { catExpanded = true }.testTag("selector_category")
                        )
                        DropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            if (categories.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No categories found. Create a product first.") },
                                    onClick = { catExpanded = false }
                                )
                            }
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        selectedCategory = cat
                                        selectedVarietyProductId = null
                                        selectedPriceId = null
                                        catExpanded = false
                                    },
                                    modifier = Modifier.testTag("cat_option_$cat")
                                )
                            }
                        }
                    }

                    // 2. Product Variety Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedVarietyProductId?.let { id -> products.find { it.id == id }?.productName } ?: "Select Variety",
                            onValueChange = {},
                            readOnly = true,
                            enabled = selectedCategory.isNotEmpty(),
                            label = { Text("Product Variety") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth().clickable { if (selectedCategory.isNotEmpty()) varExpanded = true }.testTag("selector_variety")
                        )
                        DropdownMenu(
                            expanded = varExpanded,
                            onDismissRequest = { varExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            if (filteredProducts.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No varieties found") },
                                    onClick = { varExpanded = false }
                                )
                            }
                            filteredProducts.forEach { prod ->
                                DropdownMenuItem(
                                    text = { Text(prod.productName) },
                                    onClick = {
                                        selectedVarietyProductId = prod.id
                                        selectedPriceId = null
                                        varExpanded = false
                                    },
                                    modifier = Modifier.testTag("var_option_${prod.productName}")
                                )
                            }
                        }
                    }

                    // 3. Selling Price Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedPriceObj?.let { "₹${it.sellingPrice} (Current Profit: ₹${it.profitPerPacket})" } ?: "Select Price Variant",
                            onValueChange = {},
                            readOnly = true,
                            enabled = selectedVarietyProductId != null,
                            label = { Text("Selling Price Variant") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth().clickable { if (selectedVarietyProductId != null) priceExpanded = true }.testTag("selector_price")
                        )
                        DropdownMenu(
                            expanded = priceExpanded,
                            onDismissRequest = { priceExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            if (filteredPrices.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No price variants found") },
                                    onClick = { priceExpanded = false }
                                )
                            }
                            filteredPrices.forEach { price ->
                                DropdownMenuItem(
                                    text = { Text("₹${price.sellingPrice} (Profit: ₹${price.profitPerPacket})") },
                                    onClick = {
                                        selectedPriceId = price.priceId
                                        priceExpanded = false
                                    },
                                    modifier = Modifier.testTag("price_option_${price.sellingPrice.toInt()}")
                                )
                            }
                        }
                    }

                    // Enable Dynamic profit toggle inside
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Use Dynamic Profit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Calculates profit dynamically using actual ingredients cost", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = isDynamicProfitEnabled,
                            onCheckedChange = onToggleDynamic,
                            modifier = Modifier.testTag("dynamic_switch_card")
                        )
                    }
                }
            }
        }

        // Return if product is not selected
        if (selectedPriceObj == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Please select Category, Variety, and Selling Price to view or create dynamic cost calculations.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            return@LazyColumn
        }

        // Active version indicator
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (activeCalculation != null) "Current Cost Version: v${activeCalculation.version}" else "No Cost Version Saved",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (activeCalculation != null) {
                            Text("Saved on: ${activeCalculation.calculationDate}", fontSize = 12.sp, color = Color.Gray)
                            Text("Production Cost: ₹${String.format("%.2f", activeCalculation.totalProductionCost)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Creating initial version", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    if (variantCalculations.isNotEmpty()) {
                        Button(
                            onClick = { showHistoryDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.testTag("btn_view_history")
                        ) {
                            Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("History (${variantCalculations.size})", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- INGREDIENT USAGES SHEET (Step 5) ---
        item {
            Text(
                "Step 2: Define Ingredient Usages (Per Packet)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (ingredients.none { it.status == "Active" }) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No active ingredients found in Master.", color = Color.Gray)
                        Button(
                            onClick = { /* Will change tab */ },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Add Ingredients in Master")
                        }
                    }
                }
            }
        } else {
            val activeIngredients = ingredients.filter { it.status == "Active" }
            items(activeIngredients) { ingredient ->
                val isChecked = checkedIngredients.contains(ingredient.id)
                val ingredientPurchases = purchases.filter { it.ingredientId == ingredient.id }
                val latestPurchase = ingredientPurchases.firstOrNull() // Purchases sorted by date desc

                // Usage quantity & unit local controls
                val usageQty = ingredientUsages[ingredient.id] ?: 0.0
                val usageUnit = ingredientUnits[ingredient.id] ?: when (UnitConverter.getUnitType(latestPurchase?.unit ?: "g")) {
                    "Weight" -> "g"
                    "Volume" -> "ml"
                    else -> latestPurchase?.unit ?: "Piece"
                }

                // Compute exact usage cost
                val costPerUsageUnit = latestPurchase?.let { p ->
                    UnitConverter.calculateCostPerUsageUnit(
                        purchasePrice = p.purchasePrice,
                        purchaseQty = p.purchaseQuantity,
                        purchaseUnit = p.unit,
                        usageUnit = usageUnit,
                        sealCost = p.sealCost,
                        printingCost = p.printingCost,
                        largeCoverDistribution = p.largeCoverDistribution
                    )
                } ?: 0.0

                val calculatedUsageCost = costPerUsageUnit * usageQty

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("ingredient_card_${ingredient.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChecked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isChecked) 2.dp else 0.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    checkedIngredients = if (checked) {
                                        checkedIngredients + ingredient.id
                                    } else {
                                        checkedIngredients - ingredient.id
                                    }
                                },
                                modifier = Modifier.testTag("checkbox_ingredient_${ingredient.id}")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ingredient.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isChecked) MaterialTheme.colorScheme.onSurface else Color.Gray
                                )
                                if (ingredient.variety.isNotEmpty()) {
                                    Text(
                                        text = "Variety: ${ingredient.variety} | Category: ${ingredient.category}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            if (isChecked) {
                                Text(
                                    text = "₹${String.format("%.2f", calculatedUsageCost)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("usage_cost_${ingredient.id}")
                                )
                            }
                        }

                        if (isChecked) {
                            Divider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = if (usageQty == 0.0) "" else usageQty.toString(),
                                    onValueChange = { input ->
                                        val qty = input.toDoubleOrNull() ?: 0.0
                                        ingredientUsages = ingredientUsages + (ingredient.id to qty)
                                    },
                                    label = { Text("Usage Amount") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).testTag("input_usage_qty_${ingredient.id}"),
                                    singleLine = true
                                )

                                // Unit selector dropdown (Step 3)
                                var unitExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = usageUnit,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Unit") },
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                                        modifier = Modifier.fillMaxWidth().clickable { unitExpanded = true }.testTag("dropdown_unit_${ingredient.id}")
                                    )
                                    DropdownMenu(
                                        expanded = unitExpanded,
                                        onDismissRequest = { unitExpanded = false }
                                    ) {
                                        val supportedUnits = when (UnitConverter.getUnitType(latestPurchase?.unit ?: "Piece")) {
                                            "Weight" -> UnitConverter.WeightUnits
                                            "Volume" -> UnitConverter.VolumeUnits
                                            else -> listOf(latestPurchase?.unit ?: "Piece") + UnitConverter.QuantityUnits
                                        }.distinct()

                                        supportedUnits.forEach { u ->
                                            DropdownMenuItem(
                                                text = { Text(u) },
                                                onClick = {
                                                    ingredientUnits = ingredientUnits + (ingredient.id to u)
                                                    unitExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Math breakdown info
                            if (latestPurchase != null) {
                                val costPerPurchaseBaseUnit = latestPurchase.purchasePrice / latestPurchase.purchaseQuantity
                                val packagingExtrasText = if (latestPurchase.sealCost > 0 || latestPurchase.printingCost > 0) {
                                    " (incl. ₹${latestPurchase.sealCost} seal, ₹${latestPurchase.printingCost} print)"
                                } else ""
                                val dividedText = if (latestPurchase.largeCoverDistribution > 1) {
                                    " / divided by ${latestPurchase.largeCoverDistribution} packets"
                                } else ""

                                Text(
                                    text = "Latest Purchase: ${latestPurchase.purchaseQuantity} ${latestPurchase.unit} for ₹${latestPurchase.purchasePrice}$packagingExtrasText$dividedText. Effective Cost: ₹${String.format("%.4f", costPerUsageUnit)} per $usageUnit",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            } else {
                                Text(
                                    text = "⚠️ No purchases added for this ingredient yet! Cost calculated as ₹0.00.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- DYNAMIC FORMULA & SAVE (Step 14, 15, 16) ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Calculate final cost
            var totalProductionCost = 0.0
            val formulasList = mutableListOf<String>()

            checkedIngredients.forEach { id ->
                val ingredientObj = ingredients.find { it.id == id } ?: return@forEach
                val purchaseObj = purchases.find { it.ingredientId == id }
                val qty = ingredientUsages[id] ?: 0.0
                val unit = ingredientUnits[id] ?: purchaseObj?.unit ?: "g"
                
                val costPerUsageUnit = purchaseObj?.let { p ->
                    UnitConverter.calculateCostPerUsageUnit(
                        purchasePrice = p.purchasePrice,
                        purchaseQty = p.purchaseQuantity,
                        purchaseUnit = p.unit,
                        usageUnit = unit,
                        sealCost = p.sealCost,
                        printingCost = p.printingCost,
                        largeCoverDistribution = p.largeCoverDistribution
                    )
                } ?: 0.0
                val itemCost = costPerUsageUnit * qty
                totalProductionCost += itemCost
                if (itemCost > 0) {
                    formulasList.add("${ingredientObj.name} (₹${String.format("%.2f", itemCost)})")
                }
            }

            val sellingPrice = selectedPriceObj.sellingPrice
            val dynamicProfit = sellingPrice - totalProductionCost

            Card(
                modifier = Modifier.fillMaxWidth().testTag("summary_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Step 3: Formula & Profit Summary",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

                    // Formula Breakdown Text (Step 14)
                    Text(
                        text = if (formulasList.isNotEmpty()) {
                            "Formula: " + formulasList.joinToString(" + ")
                        } else {
                            "Formula: No ingredients selected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Production Cost:", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "₹${String.format("%.2f", totalProductionCost)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("total_production_cost")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Selling Price:", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("₹${sellingPrice}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Resulting Profit:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "₹${String.format("%.2f", dynamicProfit)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = if (dynamicProfit >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("resulting_profit")
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            val nextVersion = (activeCalculation?.version ?: 0) + 1
                            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            
                            val calculation = CostCalculation(
                                productPriceId = selectedPriceObj.priceId,
                                version = nextVersion,
                                calculationDate = todayStr,
                                totalProductionCost = totalProductionCost,
                                sellingPriceSnapshot = sellingPrice,
                                profitSnapshot = dynamicProfit,
                                remarks = "Saved cost version v$nextVersion"
                            )

                            val itemsList = checkedIngredients.map { id ->
                                val ing = ingredients.find { it.id == id }!!
                                val p = purchases.find { it.ingredientId == id }
                                val qty = ingredientUsages[id] ?: 0.0
                                val u = ingredientUnits[id] ?: p?.unit ?: "g"
                                
                                val costPerUsageUnit = p?.let {
                                    UnitConverter.calculateCostPerUsageUnit(
                                        purchasePrice = p.purchasePrice,
                                        purchaseQty = p.purchaseQuantity,
                                        purchaseUnit = p.unit,
                                        usageUnit = u,
                                        sealCost = p.sealCost,
                                        printingCost = p.printingCost,
                                        largeCoverDistribution = p.largeCoverDistribution
                                    )
                                } ?: 0.0

                                CostCalculationItem(
                                    costCalculationId = 0, // Assigned inside transaction
                                    ingredientId = id,
                                    ingredientName = ing.name,
                                    ingredientVariety = ing.variety,
                                    usageQuantity = qty,
                                    usageUnit = u,
                                    costPerUnitSnapshot = costPerUsageUnit,
                                    calculatedCost = costPerUsageUnit * qty,
                                    purchaseUnitSnapshot = p?.unit ?: u
                                )
                            }

                            viewModel.saveCostCalculation(calculation, itemsList)
                            Toast.makeText(context, "Locked & Saved Cost Version v$nextVersion!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_save_calculation"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lock & Save Cost Version")
                    }
                }
            }
        }
    }

    // --- HISTORY DIALOG (Step 15, 16) ---
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Cost Calculation History") },
            text = {
                Box(modifier = Modifier.sizeIn(maxHeight = 450.dp)) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(variantCalculations) { calc ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Version v${calc.version}", fontWeight = FontWeight.Bold)
                                        Text(calc.calculationDate, fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Production Cost: ₹${String.format("%.2f", calc.totalProductionCost)}", fontSize = 13.sp)
                                    Text("Selling Price: ₹${calc.sellingPriceSnapshot} | Profit: ₹${String.format("%.2f", calc.profitSnapshot)}", fontSize = 13.sp)
                                    
                                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                                    Text("Ingredients Snapshot Used:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    
                                    val snapshotItems = historicalItemsMap[calc.calculationId] ?: emptyList()
                                    if (snapshotItems.isEmpty()) {
                                        Text("No ingredients snapshotted", fontSize = 11.sp, color = Color.Gray)
                                    } else {
                                        snapshotItems.forEach { snap ->
                                            Text(
                                                text = "• ${snap.ingredientName} (${snap.ingredientVariety.ifEmpty { "Generic" }}): ${snap.usageQuantity} ${snap.usageUnit} @ ₹${String.format("%.4f", snap.costPerUnitSnapshot)} = ₹${String.format("%.2f", snap.calculatedCost)}",
                                                fontSize = 11.sp,
                                                color = Color.DarkGray
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteCalculation(calc)
                                                Toast.makeText(context, "Calculation deleted", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// ============================================
// TAB 1: INGREDIENT MASTER CONTENT (Step 2)
// ============================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientMasterTabContent(
    viewModel: AppViewModel,
    ingredients: List<Ingredient>,
    purchases: List<IngredientPurchase>
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("All") }

    // Dialog trigger states
    var showAddEditIngredientDialog by remember { mutableStateOf(false) }
    var selectedIngredientForEdit by remember { mutableStateOf<Ingredient?>(null) }

    var showAddPurchaseDialog by remember { mutableStateOf(false) }
    var selectedIngredientForPurchase by remember { mutableStateOf<Ingredient?>(null) }

    // Filter ingredients list (Step 19: Search, Filter)
    val filteredIngredients = remember(ingredients, searchQuery, categoryFilter) {
        ingredients.filter { ing ->
            val matchesSearch = ing.name.contains(searchQuery, ignoreCase = true) || 
                                ing.variety.contains(searchQuery, ignoreCase = true)
            val matchesCategory = categoryFilter == "All" || ing.category == categoryFilter
            matchesSearch && matchesCategory
        }
    }

    // Dynamic Categories derived
    val ingredientCategories = remember(ingredients) {
        listOf("All") + ingredients.map { it.category }.distinct().sorted()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedIngredientForEdit = null
                    showAddEditIngredientDialog = true
                },
                modifier = Modifier.testTag("fab_add_ingredient")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Ingredient")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search & Filter (Step 19)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search ingredients...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.weight(1.5f).testTag("ingredient_search_bar"),
                    singleLine = true
                )

                var catFilterExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = categoryFilter,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth().clickable { catFilterExpanded = true }.testTag("ingredient_filter_dropdown")
                    )
                    DropdownMenu(
                        expanded = catFilterExpanded,
                        onDismissRequest = { catFilterExpanded = false }
                    ) {
                        ingredientCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    categoryFilter = cat
                                    catFilterExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Ingredients List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredIngredients.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                "No ingredients found. Create a new ingredient using the '+' button.",
                                modifier = Modifier.padding(24.dp),
                                color = Color.Gray
                            )
                        }
                    }
                }

                items(filteredIngredients) { ingredient ->
                    val ingredientPurchases = purchases.filter { it.ingredientId == ingredient.id }
                    val latestPurchase = ingredientPurchases.firstOrNull()

                    var isExpanded by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("ingredient_master_card_${ingredient.id}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ingredient.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    if (ingredient.variety.isNotEmpty()) {
                                        Text("Variety: ${ingredient.variety}", fontSize = 13.sp, color = Color.Gray)
                                    }
                                    Text("Category: ${ingredient.category} | Status: ${ingredient.status}", fontSize = 12.sp, color = Color.Gray)
                                }
                                
                                // Expand toggle
                                Row {
                                    IconButton(onClick = { isExpanded = !isExpanded }) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Expand Purchases"
                                        )
                                    }
                                    
                                    // More options Menu (Step 19: Edit, Delete, Duplicate)
                                    var actionMenuExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { actionMenuExpanded = true }) {
                                            Icon(Icons.Default.MoreVert, null)
                                        }
                                        DropdownMenu(
                                            expanded = actionMenuExpanded,
                                            onDismissRequest = { actionMenuExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit") },
                                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                onClick = {
                                                    selectedIngredientForEdit = ingredient
                                                    showAddEditIngredientDialog = true
                                                    actionMenuExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Duplicate") },
                                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                                onClick = {
                                                    viewModel.addIngredient(
                                                        name = ingredient.name + " Copy",
                                                        variety = ingredient.variety,
                                                        category = ingredient.category
                                                    )
                                                    Toast.makeText(context, "Duplicated ingredient", Toast.LENGTH_SHORT).show()
                                                    actionMenuExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    viewModel.deleteIngredient(ingredient)
                                                    Toast.makeText(context, "Ingredient deleted", Toast.LENGTH_SHORT).show()
                                                    actionMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 8.dp))

                            // Show quick summary of cost
                            if (latestPurchase != null) {
                                val costPerUnit = latestPurchase.purchasePrice / latestPurchase.purchaseQuantity
                                Text(
                                    text = "Latest Purchase Price: ₹${latestPurchase.purchasePrice} / ${latestPurchase.purchaseQuantity} ${latestPurchase.unit} (₹${String.format("%.2f", costPerUnit)} per ${latestPurchase.unit})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text("No purchases logged. Click expanded menu to add.", fontSize = 12.sp, color = Color.Gray)
                            }

                            // EXPANDED PURCHASES LIST (Step 4, 19)
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Purchase History", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Button(
                                            onClick = {
                                                selectedIngredientForPurchase = ingredient
                                                showAddPurchaseDialog = true
                                            },
                                            modifier = Modifier.testTag("btn_add_purchase_${ingredient.id}")
                                        ) {
                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add Purchase", fontSize = 11.sp)
                                        }
                                    }

                                    if (ingredientPurchases.isEmpty()) {
                                        Text("No purchases logged yet.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ingredientPurchases.forEach { p ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("Price: ₹${p.purchasePrice} for ${p.purchaseQuantity} ${p.unit}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                            Text(p.purchaseDate, fontSize = 10.sp, color = Color.Gray)
                                                        }
                                                        val effectiveCostPerUnit = p.purchasePrice / p.purchaseQuantity
                                                        Text("Effective Cost: ₹${String.format("%.4f", effectiveCostPerUnit)} per ${p.unit}", fontSize = 11.sp)
                                                        
                                                        // Show cover specific details if available
                                                        if (p.sealCost > 0 || p.printingCost > 0 || p.largeCoverDistribution > 1) {
                                                            var detailsStr = ""
                                                            if (p.sealCost > 0) detailsStr += "Seal Cost: ₹${p.sealCost} | "
                                                            if (p.printingCost > 0) detailsStr += "Print Cost: ₹${p.printingCost} | "
                                                            if (p.largeCoverDistribution > 1) detailsStr += "Large Divisor: ${p.largeCoverDistribution}"
                                                            Text(detailsStr.trimEnd(' ', '|'), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                        }

                                                        if (!p.supplier.isNullOrEmpty()) {
                                                            Text("Supplier: ${p.supplier}", fontSize = 10.sp, color = Color.Gray)
                                                        }
                                                        if (!p.remarks.isNullOrEmpty()) {
                                                            Text("Remarks: ${p.remarks}", fontSize = 10.sp, color = Color.Gray)
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.End
                                                        ) {
                                                            IconButton(
                                                                onClick = {
                                                                    viewModel.deletePurchase(p)
                                                                    Toast.makeText(context, "Purchase deleted", Toast.LENGTH_SHORT).show()
                                                                },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
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

    // --- ADD/EDIT INGREDIENT DIALOG (Step 2, 19) ---
    if (showAddEditIngredientDialog) {
        var name by remember { mutableStateOf(selectedIngredientForEdit?.name ?: "") }
        var variety by remember { mutableStateOf(selectedIngredientForEdit?.variety ?: "") }
        var category by remember { mutableStateOf(selectedIngredientForEdit?.category ?: "") }

        val categoryOptions = listOf("Popcorn", "Chips", "Mixture", "Corn", "Seasoning", "Oil", "Cover", "Sticker", "Cup", "Petrol", "Electricity", "Other")

        AlertDialog(
            onDismissRequest = { showAddEditIngredientDialog = false },
            title = { Text(if (selectedIngredientForEdit == null) "Add New Ingredient" else "Edit Ingredient") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Ingredient Product Name") },
                        modifier = Modifier.fillMaxWidth().testTag("input_ingredient_name"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = variety,
                        onValueChange = { variety = it },
                        label = { Text("Variety") },
                        modifier = Modifier.fillMaxWidth().testTag("input_ingredient_variety"),
                        singleLine = true
                    )

                    var catDropdownExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = category.ifEmpty { "Select Category" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth().clickable { catDropdownExpanded = true }.testTag("input_ingredient_category_dropdown")
                        )
                        DropdownMenu(
                            expanded = catDropdownExpanded,
                            onDismissRequest = { catDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categoryOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        category = opt
                                        catDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Also allow typing a custom category if they want
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Or Type Custom Category") },
                        modifier = Modifier.fillMaxWidth().testTag("input_ingredient_category_text"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.trim().isEmpty() || category.trim().isEmpty()) {
                            Toast.makeText(context, "Please fill in Name and Category", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (selectedIngredientForEdit == null) {
                            viewModel.addIngredient(name.trim(), variety.trim(), category.trim())
                            Toast.makeText(context, "Ingredient added successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.updateIngredient(
                                selectedIngredientForEdit!!.copy(
                                    name = name.trim(),
                                    variety = variety.trim(),
                                    category = category.trim()
                                )
                            )
                            Toast.makeText(context, "Ingredient updated successfully", Toast.LENGTH_SHORT).show()
                        }
                        showAddEditIngredientDialog = false
                    },
                    modifier = Modifier.testTag("btn_confirm_ingredient")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditIngredientDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- ADD PURCHASE DIALOG (Step 4, 6) ---
    if (showAddPurchaseDialog && selectedIngredientForPurchase != null) {
        val ingredient = selectedIngredientForPurchase!!
        
        var qtyStr by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf(if (ingredient.category.lowercase(Locale.getDefault()) == "oil") "L" else "kg") }
        var priceStr by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
        var supplier by remember { mutableStateOf("") }
        var remarks by remember { mutableStateOf("") }

        // Step 6 packaging variables
        var sealCostStr by remember { mutableStateOf("") }
        var printingCostStr by remember { mutableStateOf("") }
        var largeCoverDistributionStr by remember { mutableStateOf("1") }

        val isCoverCategory = ingredient.category.lowercase(Locale.getDefault()).contains("cover") || 
                              ingredient.name.lowercase(Locale.getDefault()).contains("cover")

        AlertDialog(
            onDismissRequest = { showAddPurchaseDialog = false },
            title = { Text("Log Purchase for ${ingredient.name}") },
            text = {
                Box(modifier = Modifier.sizeIn(maxHeight = 450.dp)) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            OutlinedTextField(
                                value = qtyStr,
                                onValueChange = { qtyStr = it },
                                label = { Text("Purchase Quantity") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("input_purchase_qty"),
                                singleLine = true
                            )
                        }

                        // Unit Selector (Step 3)
                        item {
                            var unitDropdownExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = unit,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Unit") },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                                    modifier = Modifier.fillMaxWidth().clickable { unitDropdownExpanded = true }.testTag("input_purchase_unit")
                                )
                                DropdownMenu(
                                    expanded = unitDropdownExpanded,
                                    onDismissRequest = { unitDropdownExpanded = false }
                                ) {
                                    UnitConverter.AllUnits.forEach { u ->
                                        DropdownMenuItem(
                                            text = { Text(u) },
                                            onClick = {
                                                unit = u
                                                unitDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = priceStr,
                                onValueChange = { priceStr = it },
                                label = { Text("Purchase Price (₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("input_purchase_price"),
                                singleLine = true
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = date,
                                onValueChange = { date = it },
                                label = { Text("Purchase Date (yyyy-MM-dd)") },
                                modifier = Modifier.fillMaxWidth().testTag("input_purchase_date"),
                                singleLine = true
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = supplier,
                                onValueChange = { supplier = it },
                                label = { Text("Supplier (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = remarks,
                                onValueChange = { remarks = it },
                                label = { Text("Remarks (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        // STEP 6: Extra Cover packaging fields (only shown for cover ingredients)
                        if (isCoverCategory) {
                            item {
                                Text("Packaging Custom Cost Options:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            item {
                                OutlinedTextField(
                                    value = sealCostStr,
                                    onValueChange = { sealCostStr = it },
                                    label = { Text("Total Seal Cost (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("input_seal_cost"),
                                    singleLine = true
                                )
                            }
                            item {
                                OutlinedTextField(
                                    value = printingCostStr,
                                    onValueChange = { printingCostStr = it },
                                    label = { Text("Total Printing Cost (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("input_printing_cost"),
                                    singleLine = true
                                )
                            }
                            item {
                                OutlinedTextField(
                                    value = largeCoverDistributionStr,
                                    onValueChange = { largeCoverDistributionStr = it },
                                    label = { Text("Large Cover Distribution (e.g. contains 10 packets)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("input_large_divisor"),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = qtyStr.toDoubleOrNull() ?: 0.0
                        val price = priceStr.toDoubleOrNull() ?: 0.0
                        if (qty <= 0 || price <= 0) {
                            Toast.makeText(context, "Please enter valid Quantity and Price", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val sealCost = sealCostStr.toDoubleOrNull() ?: 0.0
                        val printingCost = printingCostStr.toDoubleOrNull() ?: 0.0
                        val largeDivisor = largeCoverDistributionStr.toIntOrNull() ?: 1

                        viewModel.addPurchase(
                            ingredientId = ingredient.id,
                            quantity = qty,
                            unit = unit,
                            price = price,
                            date = date.trim(),
                            supplier = supplier.trim().ifEmpty { null },
                            remarks = remarks.trim().ifEmpty { null },
                            sealCost = sealCost,
                            printingCost = printingCost,
                            largeCoverDistribution = largeDivisor
                        )

                        Toast.makeText(context, "Purchase logged!", Toast.LENGTH_SHORT).show()
                        showAddPurchaseDialog = false
                    },
                    modifier = Modifier.testTag("btn_confirm_purchase")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPurchaseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
