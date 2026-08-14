package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ProductCostCalculation
import com.example.data.ProductCostIngredient
import com.example.ui.AppViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

data class IngredientUsage(
    val ingredientId: Int,
    val name: String,
    val category: String? = null,
    val defaultUnitType: String,
    val quantityUsed: Double,
    val unitUsed: String,
    val purchasePrice: Double,
    val purchaseQuantity: Double,
    val purchaseUnit: String,
    val isNoUnit: Boolean,
    val finalCost: Double
)

data class ExpenseUsage(
    val name: String,
    val quantityUsed: Double,
    val unitUsed: String,
    val purchasePrice: Double,
    val purchaseQuantity: Double,
    val purchaseUnit: String,
    val isNoUnit: Boolean,
    val finalCost: Double
)

fun serializeIngredientsList(list: List<IngredientUsage>): String {
    val array = JSONArray()
    for (item in list) {
        val obj = JSONObject()
        obj.put("ingredientId", item.ingredientId)
        obj.put("name", item.name)
        obj.put("category", item.category ?: "")
        obj.put("defaultUnitType", item.defaultUnitType)
        obj.put("quantityUsed", item.quantityUsed)
        obj.put("unitUsed", item.unitUsed)
        obj.put("purchasePrice", item.purchasePrice)
        obj.put("purchaseQuantity", item.purchaseQuantity)
        obj.put("purchaseUnit", item.purchaseUnit)
        obj.put("isNoUnit", item.isNoUnit)
        obj.put("finalCost", item.finalCost)
        array.put(obj)
    }
    return array.toString()
}

fun deserializeIngredientsList(jsonStr: String?): List<IngredientUsage> {
    if (jsonStr.isNullOrEmpty()) return emptyList()
    val list = mutableListOf<IngredientUsage>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                IngredientUsage(
                    ingredientId = obj.optInt("ingredientId"),
                    name = obj.optString("name"),
                    category = obj.optString("category").ifEmpty { null },
                    defaultUnitType = obj.optString("defaultUnitType"),
                    quantityUsed = obj.optDouble("quantityUsed"),
                    unitUsed = obj.optString("unitUsed"),
                    purchasePrice = obj.optDouble("purchasePrice"),
                    purchaseQuantity = obj.optDouble("purchaseQuantity"),
                    purchaseUnit = obj.optString("purchaseUnit"),
                    isNoUnit = obj.optBoolean("isNoUnit"),
                    finalCost = obj.optDouble("finalCost")
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun serializeExpensesList(list: List<ExpenseUsage>): String {
    val array = JSONArray()
    for (item in list) {
        val obj = JSONObject()
        obj.put("name", item.name)
        obj.put("quantityUsed", item.quantityUsed)
        obj.put("unitUsed", item.unitUsed)
        obj.put("purchasePrice", item.purchasePrice)
        obj.put("purchaseQuantity", item.purchaseQuantity)
        obj.put("purchaseUnit", item.purchaseUnit)
        obj.put("isNoUnit", item.isNoUnit)
        obj.put("finalCost", item.finalCost)
        array.put(obj)
    }
    return array.toString()
}

fun deserializeExpensesList(jsonStr: String?): List<ExpenseUsage> {
    if (jsonStr.isNullOrEmpty()) return emptyList()
    val list = mutableListOf<ExpenseUsage>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                ExpenseUsage(
                    name = obj.optString("name"),
                    quantityUsed = obj.optDouble("quantityUsed"),
                    unitUsed = obj.optString("unitUsed"),
                    purchasePrice = obj.optDouble("purchasePrice"),
                    purchaseQuantity = obj.optDouble("purchaseQuantity"),
                    purchaseUnit = obj.optString("purchaseUnit"),
                    isNoUnit = obj.optBoolean("isNoUnit"),
                    finalCost = obj.optDouble("finalCost")
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

enum class FormMode {
    List,
    CalculationForm,
    IngredientForm
}

object UnitSystem {
    val Categories = mapOf(
        "Weight" to listOf("Kilogram (kg)", "Gram (g)"),
        "Volume" to listOf("Liter (L)", "Milliliter (ml)"),
        "Count" to listOf("Piece", "Pieces"),
        "Distance" to listOf("Kilometer (km)", "Meter (m)"),
        "Electricity" to listOf("Kilowatt Hour (kWh)"),
        "Fuel" to listOf("Liter (L)"),
        "No Unit" to listOf("No Unit")
    )

    fun getUnitCategory(defaultUnitType: String): String {
        val trimmed = defaultUnitType.trim()
        if (trimmed in listOf("Weight", "Volume", "Count", "Distance", "Electricity", "Fuel", "No Unit")) {
            return trimmed
        }
        for ((catName, unitsList) in Categories) {
            if (trimmed in unitsList) {
                return catName
            }
        }
        return "No Unit"
    }

    val AllUnits = listOf(
        "Kilogram (kg)", "Gram (g)",
        "Liter (L)", "Milliliter (ml)",
        "Piece", "Pieces",
        "Kilometer (km)", "Meter (m)",
        "Kilowatt Hour (kWh)", "No Unit"
    )

    fun normalize(unit: String): String {
        val u = unit.lowercase(Locale.getDefault())
        return when {
            u.contains("kilogram") || u.contains("kg") -> "kg"
            u.contains("gram") || u.contains("g") -> "g"
            u.contains("milliliter") || u.contains("ml") -> "ml"
            u.contains("liter") || u.contains("l") -> "l"
            u.contains("kilometer") || u.contains("km") -> "km"
            u.contains("meter") || u.contains("m") -> "m"
            u.contains("kilowatt hour") || u.contains("kwh") -> "kwh"
            u.contains("piece") -> "piece"
            u.contains("pieces") -> "pieces"
            else -> "nounit"
        }
    }

    fun convert(amount: Double, fromUnit: String, toUnit: String): Double {
        val from = normalize(fromUnit)
        val to = normalize(toUnit)
        if (from == to) return amount

        // Weight
        if (from == "kg" && to == "g") return amount * 1000.0
        if (from == "g" && to == "kg") return amount / 1000.0

        // Volume
        if (from == "l" && to == "ml") return amount * 1000.0
        if (from == "ml" && to == "l") return amount / 1000.0

        // Distance
        if (from == "km" && to == "m") return amount * 1000.0
        if (from == "m" && to == "km") return amount / 1000.0

        return amount
    }

    fun calculateCost(
        quantityUsed: Double,
        unitUsed: String,
        purchasePrice: Double,
        purchaseQuantity: Double,
        purchaseUnit: String,
        isNoUnit: Boolean
    ): Double {
        if (isNoUnit) return purchasePrice
        if (purchaseQuantity <= 0) return 0.0
        
        val converted = convert(quantityUsed, unitUsed, purchaseUnit)
        return converted * (purchasePrice / purchaseQuantity)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductCostCalculatorScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeMode by remember { mutableStateOf(FormMode.List) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Calculations, 1 = Ingredient Master

    // Database states
    val ingredients by viewModel.allProductCostIngredients.collectAsState()
    val calculations by viewModel.allProductCostCalculations.collectAsState()

    // Editing states
    var editingIngredient by remember { mutableStateOf<ProductCostIngredient?>(null) }
    var editingCalculation by remember { mutableStateOf<ProductCostCalculation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (activeMode) {
                            FormMode.List -> "Product Costing"
                            FormMode.IngredientForm -> if (editingIngredient == null) "New Ingredient" else "Edit Ingredient"
                            FormMode.CalculationForm -> if (editingCalculation == null) "New Product Recipe" else if (editingCalculation?.id == 0) "Duplicate Product Recipe" else "Edit Product Recipe"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeMode != FormMode.List) {
                                activeMode = FormMode.List
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("btn_back_product_cost")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (activeMode) {
                FormMode.List -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier.fillMaxWidth().testTag("product_cost_tabs")
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Calculations", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Ingredient Master", fontWeight = FontWeight.Bold) }
                            )
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (selectedTab == 0) {
                                CalculationsTab(
                                    calculations = calculations,
                                    onEdit = { calc ->
                                        editingCalculation = calc
                                        activeMode = FormMode.CalculationForm
                                    },
                                    onCopy = { calc ->
                                        val copied = calc.copy(
                                            id = 0,
                                            productName = "${calc.productName} (Copy)",
                                            date = System.currentTimeMillis()
                                        )
                                        editingCalculation = copied
                                        activeMode = FormMode.CalculationForm
                                    },
                                    onDelete = { calc ->
                                        viewModel.deleteProductCostCalculation(calc)
                                        Toast.makeText(context, "Calculation deleted", Toast.LENGTH_SHORT).show()
                                    },
                                    onCreateNew = {
                                        editingCalculation = null
                                        activeMode = FormMode.CalculationForm
                                    }
                                )
                            } else {
                                IngredientsTab(
                                    ingredients = ingredients,
                                    onEdit = { ing ->
                                        editingIngredient = ing
                                        activeMode = FormMode.IngredientForm
                                    },
                                    onDelete = { ing ->
                                        viewModel.deleteProductCostIngredient(ing)
                                        Toast.makeText(context, "Ingredient deleted", Toast.LENGTH_SHORT).show()
                                    },
                                    onCreateNew = {
                                        editingIngredient = null
                                        activeMode = FormMode.IngredientForm
                                    }
                                )
                            }
                        }
                    }
                }

                FormMode.IngredientForm -> {
                    IngredientFormScreen(
                        ingredient = editingIngredient,
                        onSave = { name, category, defaultUnit, notes, addAnother ->
                            if (editingIngredient == null) {
                                viewModel.insertProductCostIngredient(
                                    ProductCostIngredient(
                                        name = name,
                                        category = category,
                                        defaultUnitType = defaultUnit,
                                        notes = notes
                                    )
                                )
                                Toast.makeText(context, "Ingredient added", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updateProductCostIngredient(
                                    editingIngredient!!.copy(
                                        name = name,
                                        category = category,
                                        defaultUnitType = defaultUnit,
                                        notes = notes
                                    )
                                )
                                Toast.makeText(context, "Ingredient updated", Toast.LENGTH_SHORT).show()
                            }
                            if (!addAnother) {
                                activeMode = FormMode.List
                            }
                        },
                        onCancel = { activeMode = FormMode.List }
                    )
                }

                FormMode.CalculationForm -> {
                    CalculationFormScreen(
                        calculation = editingCalculation,
                        ingredientMaster = ingredients,
                        onSave = { calc ->
                            viewModel.insertProductCostCalculation(calc)
                            Toast.makeText(context, "Calculation saved successfully", Toast.LENGTH_SHORT).show()
                            activeMode = FormMode.List
                        },
                        onCancel = { activeMode = FormMode.List }
                    )
                }
            }
        }
    }
}

@Composable
fun CalculationsTab(
    calculations: List<ProductCostCalculation>,
    onEdit: (ProductCostCalculation) -> Unit,
    onCopy: (ProductCostCalculation) -> Unit,
    onDelete: (ProductCostCalculation) -> Unit,
    onCreateNew: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (calculations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No Saved Calculations",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Create a custom costing sheet with ingredients and other overhead expenses to calculate real-time profit margins.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onCreateNew,
                    modifier = Modifier.testTag("btn_create_calc_empty")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Product Calculation")
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(calculations, key = { it.id }) { calc ->
                        val dateString = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(calc.date))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calc_card_${calc.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(calc.productName, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Category: ${calc.category} • $dateString", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (calc.profitPerPacket >= 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${String.format("%.1f", calc.profitPercentage)}% Profit",
                                            color = if (calc.profitPerPacket >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Cost/Packet", fontSize = 11.sp, color = Color.Gray)
                                        Text("₹${String.format("%.2f", calc.costPerPacket)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Profit/Packet", fontSize = 11.sp, color = Color.Gray)
                                        Text("₹${String.format("%.2f", calc.profitPerPacket)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Total Profit", fontSize = 11.sp, color = Color.Gray)
                                        Text("₹${String.format("%.2f", calc.totalExpectedProfit)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onDelete(calc) },
                                        modifier = Modifier.testTag("btn_delete_calc_${calc.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = { onCopy(calc) },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("btn_copy_calc_${calc.id}")
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = { onEdit(calc) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("btn_edit_calc_${calc.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit / View", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                
                FloatingActionButton(
                    onClick = onCreateNew,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.End)
                        .testTag("btn_add_calculation_fab"),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Calculation")
                }
            }
        }
    }
}

@Composable
fun IngredientsTab(
    ingredients: List<ProductCostIngredient>,
    onEdit: (ProductCostIngredient) -> Unit,
    onDelete: (ProductCostIngredient) -> Unit,
    onCreateNew: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (ingredients.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Ingredient Master is Empty",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Add ingredients manually once and quickly select them in any product recipe calculations.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onCreateNew,
                    modifier = Modifier.testTag("btn_create_ing_empty")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add First Ingredient")
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ingredients, key = { it.id }) { ing ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ing_card_${ing.id}"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ing.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text("Unit Category: ${UnitSystem.getUnitCategory(ing.defaultUnitType)}", fontSize = 11.sp) }
                                        )
                                        if (!ing.category.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text(ing.category, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                    if (!ing.notes.isNullOrEmpty()) {
                                        Text(
                                            text = ing.notes,
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(top = 4.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onEdit(ing) },
                                        modifier = Modifier.testTag("btn_edit_ing_${ing.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.secondary)
                                    }
                                    IconButton(
                                        onClick = { onDelete(ing) },
                                        modifier = Modifier.testTag("btn_delete_ing_${ing.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = onCreateNew,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.End)
                        .testTag("btn_add_ingredient_fab"),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Ingredient")
                }
            }
        }
    }
}

@Composable
fun IngredientFormScreen(
    ingredient: ProductCostIngredient?,
    onSave: (name: String, category: String?, defaultUnit: String, notes: String?, addAnother: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(ingredient?.name ?: "") }
    var category by remember { mutableStateOf(ingredient?.category ?: "") }
    var defaultUnit by remember { mutableStateOf(ingredient?.let { UnitSystem.getUnitCategory(it.defaultUnitType) } ?: "Weight") }
    var notes by remember { mutableStateOf(ingredient?.notes ?: "") }

    var expandedUnit by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Ingredient Name *") },
            modifier = Modifier.fillMaxWidth().testTag("tf_ing_name")
        )

        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Category (optional, e.g. Flour, Sugar)") },
            modifier = Modifier.fillMaxWidth().testTag("tf_ing_category")
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = defaultUnit,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default Unit Category *") },
                trailingIcon = {
                    IconButton(onClick = { expandedUnit = true }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select category")
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("tf_ing_unit")
            )
            DropdownMenu(
                expanded = expandedUnit,
                onDismissRequest = { expandedUnit = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                val categoriesList = listOf("Weight", "Volume", "Count", "Distance", "Electricity", "Fuel", "No Unit")
                categoriesList.forEach { catName ->
                    DropdownMenuItem(
                        text = { Text(catName) },
                        onClick = {
                            defaultUnit = catName
                            expandedUnit = false
                        },
                        modifier = Modifier.testTag("unit_item_$catName")
                    )
                }
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth().testTag("tf_ing_notes"),
            minLines = 3
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).testTag("btn_cancel_ing")
            ) {
                Text("Cancel")
            }
            if (ingredient == null) {
                OutlinedButton(
                    onClick = {
                        if (name.trim().isEmpty()) {
                            return@OutlinedButton
                        }
                        onSave(name.trim(), category.trim().ifEmpty { null }, defaultUnit, notes.trim().ifEmpty { null }, true)
                        // Reset states for next input
                        name = ""
                        category = ""
                        defaultUnit = "Weight"
                        notes = ""
                    },
                    enabled = name.trim().isNotEmpty(),
                    modifier = Modifier.weight(1.5f).testTag("btn_save_and_add_another_ing")
                ) {
                    Text("Save & Add Another")
                }
            }
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        return@Button
                    }
                    onSave(name.trim(), category.trim().ifEmpty { null }, defaultUnit, notes.trim().ifEmpty { null }, false)
                },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.weight(1.5f).testTag("btn_save_ing")
            ) {
                Text("Save Ingredient")
            }
        }
    }
}

@Composable
fun CalculationFormScreen(
    calculation: ProductCostCalculation?,
    ingredientMaster: List<ProductCostIngredient>,
    onSave: (ProductCostCalculation) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val format = remember { DecimalFormat("#.##") }

    // Core attributes
    var productName by remember { mutableStateOf(calculation?.productName ?: "") }
    var category by remember { mutableStateOf(calculation?.category ?: "Bakery") }
    var sellingPriceStr by remember { mutableStateOf(calculation?.sellingPrice?.toString() ?: "") }
    var packetsProducedStr by remember { mutableStateOf(calculation?.packetsProduced?.toString() ?: "") }
    var labourCostStr by remember { mutableStateOf(calculation?.labourCost?.toString() ?: "") }

    // Sub-lists
    val ingredientsUsed = remember { mutableStateListOf<IngredientUsage>() }
    val expensesUsed = remember { mutableStateListOf<ExpenseUsage>() }

    // On-start restore logic
    LaunchedEffect(calculation) {
        if (calculation != null) {
            productName = calculation.productName
            category = calculation.category
            sellingPriceStr = if (calculation.sellingPrice > 0.0) {
                if (calculation.sellingPrice % 1.0 == 0.0) calculation.sellingPrice.toInt().toString()
                else calculation.sellingPrice.toString()
            } else ""
            packetsProducedStr = if (calculation.packetsProduced > 0) calculation.packetsProduced.toString() else "1"
            labourCostStr = if (calculation.labourCost > 0.0) {
                if (calculation.labourCost % 1.0 == 0.0) calculation.labourCost.toInt().toString()
                else calculation.labourCost.toString()
            } else ""

            try {
                val parsedIngs = deserializeIngredientsList(calculation.ingredientsJson)
                ingredientsUsed.clear()
                ingredientsUsed.addAll(parsedIngs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val parsedExps = deserializeExpensesList(calculation.otherExpensesJson)
                expensesUsed.clear()
                expensesUsed.addAll(parsedExps)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Modal forms states
    var showAddIngredientDialog by remember { mutableStateOf(false) }
    var editingIngredientIndex by remember { mutableStateOf<Int?>(null) }

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var editingExpenseIndex by remember { mutableStateOf<Int?>(null) }

    // Predefined Categories
    val categories = listOf("Bakery", "Popcorn", "Chips", "Drinks", "Other")
    var expandedCat by remember { mutableStateOf(false) }

    // Numerical conversions
    val sellingPrice = sellingPriceStr.toDoubleOrNull() ?: 0.0
    val packetsProduced = packetsProducedStr.toIntOrNull() ?: 1
    val labourCost = labourCostStr.toDoubleOrNull() ?: 0.0

    // Calculations logic
    val totalIngredientCost = ingredientsUsed.sumOf { it.finalCost }
    val totalOtherExpensesCost = expensesUsed.sumOf { it.finalCost }
    val totalProductionCost = totalIngredientCost + totalOtherExpensesCost + labourCost
    val costPerPacket = if (packetsProduced > 0) totalProductionCost / packetsProduced else 0.0
    val profitPerPacket = sellingPrice - costPerPacket
    val profitPercentage = if (sellingPrice > 0) (profitPerPacket / sellingPrice) * 100.0 else 0.0
    val totalExpectedProfit = profitPerPacket * packetsProduced

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("calculation_form_scrollable"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Card 1: Product info ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Product Details", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)

                    OutlinedTextField(
                        value = productName,
                        onValueChange = { productName = it },
                        label = { Text("Product Name *") },
                        modifier = Modifier.fillMaxWidth().testTag("tf_calc_product_name")
                    )

                    // Category Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category *") },
                            trailingIcon = {
                                IconButton(onClick = { expandedCat = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select category")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("tf_calc_category")
                        )
                        DropdownMenu(expanded = expandedCat, onDismissRequest = { expandedCat = false }) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        category = cat
                                        expandedCat = false
                                    },
                                    modifier = Modifier.testTag("cat_item_$cat")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sellingPriceStr,
                            onValueChange = { sellingPriceStr = it },
                            label = { Text("Selling Price") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("tf_calc_selling_price"),
                            prefix = { Text("₹") }
                        )

                        OutlinedTextField(
                            value = packetsProducedStr,
                            onValueChange = { packetsProducedStr = it },
                            label = { Text("Packets Produced") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("tf_calc_packets_produced")
                        )
                    }

                    OutlinedTextField(
                        value = labourCostStr,
                        onValueChange = { labourCostStr = it },
                        label = { Text("Flat Labour Cost for Batch") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("tf_calc_labour_cost"),
                        prefix = { Text("₹") }
                    )
                }
            }
        }

        // --- Card 2: Ingredients list ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ingredients used in batch", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                        TextButton(
                            onClick = { showAddIngredientDialog = true },
                            modifier = Modifier.testTag("btn_form_add_ing")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Ingredient")
                        }
                    }

                    if (ingredientsUsed.isEmpty()) {
                        Text(
                            "No ingredients added yet. Click 'Add Ingredient' to add raw materials.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        ingredientsUsed.forEachIndexed { index, item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        if (item.isNoUnit) {
                                            Text("No Unit conversion applied", fontSize = 11.sp, color = Color.Gray)
                                        } else {
                                            val costPerUnitCalculated = if (item.purchaseQuantity > 0) item.purchasePrice / item.purchaseQuantity else 0.0
                                            Text(
                                                text = "${item.quantityUsed} ${item.unitUsed} used (Purchased at ₹${format.format(item.purchasePrice)} per ${item.purchaseQuantity} ${item.purchaseUnit})",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "₹${format.format(item.finalCost)}",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        IconButton(onClick = { editingIngredientIndex = index }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { ingredientsUsed.removeAt(index) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Card 3: Other Expenses ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Overhead / Other Expenses", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                        TextButton(
                            onClick = { showAddExpenseDialog = true },
                            modifier = Modifier.testTag("btn_form_add_expense")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Expense")
                        }
                    }

                    if (expensesUsed.isEmpty()) {
                        Text(
                            "No overhead expenses added yet (e.g. gas, gas refills, packaging bags, labels, branding boxes, etc.)",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        expensesUsed.forEachIndexed { index, item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        if (item.isNoUnit) {
                                            Text("Fixed cost", fontSize = 11.sp, color = Color.Gray)
                                        } else {
                                            Text(
                                                text = "${item.quantityUsed} ${item.unitUsed} used (Purchased at ₹${format.format(item.purchasePrice)} per ${item.purchaseQuantity} ${item.purchaseUnit})",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "₹${format.format(item.finalCost)}",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        IconButton(onClick = { editingExpenseIndex = index }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { expensesUsed.removeAt(index) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Card 4: Dashboard Results summary ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Recipe Performance Dashboard", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Ingredient Cost:", fontSize = 13.sp)
                        Text("₹${format.format(totalIngredientCost)}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Overhead Expenses:", fontSize = 13.sp)
                        Text("₹${format.format(totalOtherExpensesCost)}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Labour cost batch flat:", fontSize = 13.sp)
                        Text("₹${format.format(labourCost)}", fontWeight = FontWeight.Bold)
                    }

                    Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Production Cost:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("₹${format.format(totalProductionCost)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Cost per Packet (Produced $packetsProduced):", fontSize = 13.sp)
                        Text("₹${format.format(costPerPacket)}", fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Selling Price per Packet:", fontSize = 13.sp)
                        Text("₹${format.format(sellingPrice)}", fontWeight = FontWeight.Bold)
                    }

                    Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Profit per Packet:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "₹${format.format(profitPerPacket)}",
                            fontWeight = FontWeight.Bold,
                            color = if (profitPerPacket >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 15.sp
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Profit Percentage:", fontSize = 13.sp)
                        Text(
                            text = "${format.format(profitPercentage)}%",
                            fontWeight = FontWeight.Bold,
                            color = if (profitPercentage >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Expected Total Batch Profit:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "₹${format.format(totalExpectedProfit)}",
                            fontWeight = FontWeight.Bold,
                            color = if (totalExpectedProfit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // --- Bottom Row of Actions ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).testTag("btn_cancel_calc")
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (productName.trim().isEmpty()) {
                            Toast.makeText(context, "Product name is required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val jsonIngredients = serializeIngredientsList(ingredientsUsed.toList())
                        val jsonExpenses = serializeExpensesList(expensesUsed.toList())

                        val newCalculation = ProductCostCalculation(
                            id = calculation?.id ?: 0,
                            productName = productName.trim(),
                            category = category,
                            sellingPrice = sellingPrice,
                            packetsProduced = packetsProduced,
                            labourCost = labourCost,
                            totalIngredientCost = totalIngredientCost,
                            totalOtherExpensesCost = totalOtherExpensesCost,
                            totalProductionCost = totalProductionCost,
                            costPerPacket = costPerPacket,
                            profitPerPacket = profitPerPacket,
                            profitPercentage = profitPercentage,
                            totalExpectedProfit = totalExpectedProfit,
                            date = System.currentTimeMillis(),
                            ingredientsJson = jsonIngredients,
                            otherExpensesJson = jsonExpenses
                        )
                        
                        onSave(newCalculation)
                    },
                    enabled = productName.trim().isNotEmpty(),
                    modifier = Modifier.weight(1.5f).testTag("btn_save_calc")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Calculation")
                }
            }
        }
    }

    // --- DIALOGS FOR FORMS ---
    if (showAddIngredientDialog) {
        AddIngredientUsageDialog(
            ingredientsList = ingredientMaster,
            onDismiss = { showAddIngredientDialog = false },
            onConfirm = { usage ->
                ingredientsUsed.add(usage)
                showAddIngredientDialog = false
            }
        )
    }

    if (editingIngredientIndex != null) {
        val index = editingIngredientIndex!!
        if (index in ingredientsUsed.indices) {
            AddIngredientUsageDialog(
                ingredientsList = ingredientMaster,
                initialUsage = ingredientsUsed[index],
                onDismiss = { editingIngredientIndex = null },
                onConfirm = { updatedUsage ->
                    ingredientsUsed[index] = updatedUsage
                    editingIngredientIndex = null
                }
            )
        } else {
            editingIngredientIndex = null
        }
    }
    
    if (showAddExpenseDialog) {
        AddExpenseUsageDialog(
            onDismiss = { showAddExpenseDialog = false },
            onConfirm = { usage ->
                expensesUsed.add(usage)
                showAddExpenseDialog = false
            }
        )
    }

    if (editingExpenseIndex != null) {
        val index = editingExpenseIndex!!
        if (index in expensesUsed.indices) {
            AddExpenseUsageDialog(
                initialUsage = expensesUsed[index],
                onDismiss = { editingExpenseIndex = null },
                onConfirm = { updatedUsage ->
                    expensesUsed[index] = updatedUsage
                    editingExpenseIndex = null
                }
            )
        } else {
            editingExpenseIndex = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddIngredientUsageDialog(
    ingredientsList: List<ProductCostIngredient>,
    initialUsage: IngredientUsage? = null,
    onDismiss: () -> Unit,
    onConfirm: (IngredientUsage) -> Unit
) {
    val context = LocalContext.current
    var selectedIng by remember { mutableStateOf<ProductCostIngredient?>(null) }
    var quantityStr by remember { mutableStateOf("") }
    var unitUsed by remember { mutableStateOf("") }

    // Purchase values
    var isNoUnit by remember { mutableStateOf(false) }
    var purchasePriceStr by remember { mutableStateOf("") }
    var purchaseQtyStr by remember { mutableStateOf("1") }
    var purchaseUnit by remember { mutableStateOf("") }

    var expandedIng by remember { mutableStateOf(false) }
    var expandedUnitUsed by remember { mutableStateOf(false) }
    var expandedPurchaseUnit by remember { mutableStateOf(false) }

    // Prepopulate if initialUsage is provided
    LaunchedEffect(initialUsage) {
        if (initialUsage != null) {
            val matched = ingredientsList.find { it.id == initialUsage.ingredientId }
            selectedIng = matched ?: ProductCostIngredient(
                id = initialUsage.ingredientId,
                name = initialUsage.name,
                category = initialUsage.category ?: "",
                defaultUnitType = initialUsage.defaultUnitType,
                notes = ""
            )
            quantityStr = if (initialUsage.isNoUnit) "" else initialUsage.quantityUsed.toString()
            unitUsed = initialUsage.unitUsed
            isNoUnit = initialUsage.isNoUnit
            purchasePriceStr = initialUsage.purchasePrice.toString()
            purchaseQtyStr = initialUsage.purchaseQuantity.toString()
            purchaseUnit = initialUsage.purchaseUnit
        }
    }

    // Initialize units when selected
    LaunchedEffect(selectedIng) {
        if (selectedIng != null) {
            val category = UnitSystem.getUnitCategory(selectedIng!!.defaultUnitType)
            val isEditingMatchingIng = initialUsage?.ingredientId == selectedIng!!.id
            if (isEditingMatchingIng) {
                isNoUnit = initialUsage!!.isNoUnit
                unitUsed = initialUsage.unitUsed
                purchaseUnit = initialUsage.purchaseUnit
            } else {
                isNoUnit = (category == "No Unit")
                val unitsList = UnitSystem.Categories[category] ?: emptyList()
                if (unitsList.isNotEmpty()) {
                    unitUsed = unitsList.first()
                    purchaseUnit = unitsList.first()
                } else {
                    unitUsed = "No Unit"
                    purchaseUnit = "No Unit"
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("dialog_add_ingredient"),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        if (initialUsage != null) "Edit Raw Ingredient" else "Add Raw Ingredient",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Ingredient Picker
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedIng?.name ?: "Select from Master...",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Ingredient *") },
                            trailingIcon = {
                                IconButton(onClick = { expandedIng = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("dialog_ing_picker")
                        )
                        DropdownMenu(
                            expanded = expandedIng,
                            onDismissRequest = { expandedIng = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            if (ingredientsList.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No ingredients! Go to tab 'Ingredient Master' first", color = Color.Red) },
                                    onClick = {}
                                )
                            } else {
                                ingredientsList.forEach { ing ->
                                    DropdownMenuItem(
                                        text = { Text(ing.name) },
                                        onClick = {
                                            selectedIng = ing
                                            expandedIng = false
                                        },
                                        modifier = Modifier.testTag("picker_ing_${ing.id}")
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedIng != null) {
                    val category = UnitSystem.getUnitCategory(selectedIng!!.defaultUnitType)
                    if (category != "No Unit") {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = isNoUnit,
                                    onCheckedChange = { isNoUnit = it },
                                    modifier = Modifier.testTag("dialog_no_unit_checkbox")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("No Unit / Fixed Cost", fontSize = 13.sp)
                            }
                        }
                    }

                    if (!isNoUnit) {
                        // Regular flow
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = quantityStr,
                                    onValueChange = { quantityStr = it },
                                    label = { Text("Quantity Used") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).testTag("dialog_tf_qty_used")
                                )

                                Box(modifier = Modifier.weight(1.2f)) {
                                    OutlinedTextField(
                                        value = unitUsed,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Unit Used") },
                                        trailingIcon = {
                                            IconButton(onClick = { expandedUnitUsed = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("dialog_tf_unit_used")
                                    )
                                    DropdownMenu(
                                        expanded = expandedUnitUsed,
                                        onDismissRequest = { expandedUnitUsed = false }
                                    ) {
                                        val availableUnits = UnitSystem.Categories[category] ?: emptyList()
                                        availableUnits.forEach { unitVal ->
                                            DropdownMenuItem(
                                                text = { Text(unitVal) },
                                                onClick = {
                                                    unitUsed = unitVal
                                                    expandedUnitUsed = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("Purchase Rate details (optional context):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        }

                        item {
                            OutlinedTextField(
                                value = purchasePriceStr,
                                onValueChange = { purchasePriceStr = it },
                                label = { Text("Purchase Price") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("dialog_tf_purchase_price"),
                                prefix = { Text("₹") }
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = purchaseQtyStr,
                                    onValueChange = { purchaseQtyStr = it },
                                    label = { Text("Purchase Qty") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).testTag("dialog_tf_purchase_qty")
                                )

                                Box(modifier = Modifier.weight(1.2f)) {
                                    OutlinedTextField(
                                        value = purchaseUnit,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Purchase Unit") },
                                        trailingIcon = {
                                            IconButton(onClick = { expandedPurchaseUnit = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("dialog_tf_purchase_unit")
                                    )
                                    DropdownMenu(
                                        expanded = expandedPurchaseUnit,
                                        onDismissRequest = { expandedPurchaseUnit = false }
                                    ) {
                                        val availableUnits = UnitSystem.Categories[category] ?: emptyList()
                                        availableUnits.forEach { unitVal ->
                                            DropdownMenuItem(
                                                text = { Text(unitVal) },
                                                onClick = {
                                                    purchaseUnit = unitVal
                                                    expandedPurchaseUnit = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // No unit flow, user directly enters final cost as purchase price
                        item {
                            val labelText = if (category == "No Unit") "Final Amount *" else "Direct Final Cost of Ingredient *"
                            OutlinedTextField(
                                value = purchasePriceStr,
                                onValueChange = { purchasePriceStr = it },
                                label = { Text(labelText) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("dialog_tf_direct_cost"),
                                prefix = { Text("₹") }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).testTag("dialog_btn_cancel")
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val ing = selectedIng ?: return@Button
                                val finalPrice = purchasePriceStr.toDoubleOrNull() ?: 0.0
                                if (isNoUnit) {
                                    onConfirm(
                                        IngredientUsage(
                                            ingredientId = ing.id,
                                            name = ing.name,
                                            category = ing.category,
                                            defaultUnitType = ing.defaultUnitType,
                                            quantityUsed = 1.0,
                                            unitUsed = "No Unit",
                                            purchasePrice = finalPrice,
                                            purchaseQuantity = 1.0,
                                            purchaseUnit = "No Unit",
                                            isNoUnit = true,
                                            finalCost = finalPrice
                                        )
                                    )
                                } else {
                                    val usedQty = quantityStr.toDoubleOrNull() ?: 0.0
                                    val pQty = purchaseQtyStr.toDoubleOrNull() ?: 1.0
                                    
                                    val calculatedCost = UnitSystem.calculateCost(
                                        quantityUsed = usedQty,
                                        unitUsed = unitUsed,
                                        purchasePrice = finalPrice,
                                        purchaseQuantity = pQty,
                                        purchaseUnit = purchaseUnit,
                                        isNoUnit = false
                                    )

                                    onConfirm(
                                        IngredientUsage(
                                            ingredientId = ing.id,
                                            name = ing.name,
                                            category = ing.category,
                                            defaultUnitType = ing.defaultUnitType,
                                            quantityUsed = usedQty,
                                            unitUsed = unitUsed,
                                            purchasePrice = finalPrice,
                                            purchaseQuantity = pQty,
                                            purchaseUnit = purchaseUnit,
                                            isNoUnit = false,
                                            finalCost = calculatedCost
                                        )
                                    )
                                }
                            },
                            enabled = selectedIng != null && (isNoUnit || quantityStr.trim().isNotEmpty()),
                            modifier = Modifier.weight(1.5f).testTag("dialog_btn_confirm")
                        ) {
                            Text(if (initialUsage != null) "Save Changes" else "Add to Recipe")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseUsageDialog(
    initialUsage: ExpenseUsage? = null,
    onDismiss: () -> Unit,
    onConfirm: (ExpenseUsage) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("") }
    var unitUsed by remember { mutableStateOf("") }

    // Purchase values
    var isNoUnit by remember { mutableStateOf(false) }
    var purchasePriceStr by remember { mutableStateOf("") }
    var purchaseQtyStr by remember { mutableStateOf("1") }
    var purchaseUnit by remember { mutableStateOf("") }

    var expandedUnitUsed by remember { mutableStateOf(false) }
    var expandedPurchaseUnit by remember { mutableStateOf(false) }

    LaunchedEffect(initialUsage) {
        if (initialUsage != null) {
            name = initialUsage.name
            quantityStr = if (initialUsage.isNoUnit) "" else initialUsage.quantityUsed.toString()
            unitUsed = initialUsage.unitUsed
            isNoUnit = initialUsage.isNoUnit
            purchasePriceStr = initialUsage.purchasePrice.toString()
            purchaseQtyStr = initialUsage.purchaseQuantity.toString()
            purchaseUnit = initialUsage.purchaseUnit
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("dialog_add_expense"),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        if (initialUsage != null) "Edit Overhead Expense" else "Add Overhead Expense",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Expense Name * (e.g. gas, stickers)") },
                        modifier = Modifier.fillMaxWidth().testTag("dialog_tf_exp_name")
                    )
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isNoUnit,
                            onCheckedChange = { isNoUnit = it },
                            modifier = Modifier.testTag("dialog_exp_no_unit_checkbox")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("No Unit / Fixed Cost", fontSize = 13.sp)
                    }
                }

                if (!isNoUnit) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = quantityStr,
                                onValueChange = { quantityStr = it },
                                label = { Text("Qty Used") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1.5f).testTag("dialog_tf_exp_qty")
                            )

                            Box(modifier = Modifier.weight(1.8f)) {
                                OutlinedTextField(
                                    value = unitUsed,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Unit Used") },
                                    trailingIcon = {
                                        IconButton(onClick = { expandedUnitUsed = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("dialog_tf_exp_unit_used")
                                )
                                DropdownMenu(
                                    expanded = expandedUnitUsed,
                                    onDismissRequest = { expandedUnitUsed = false }
                                ) {
                                    UnitSystem.AllUnits.forEach { unitVal ->
                                        DropdownMenuItem(
                                            text = { Text(unitVal) },
                                            onClick = {
                                                unitUsed = unitVal
                                                expandedUnitUsed = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = purchasePriceStr,
                            onValueChange = { purchasePriceStr = it },
                            label = { Text("Purchase Price") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("dialog_tf_exp_purchase_price"),
                            prefix = { Text("₹") }
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = purchaseQtyStr,
                                onValueChange = { purchaseQtyStr = it },
                                label = { Text("Purchase Qty") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1.5f).testTag("dialog_tf_exp_purchase_qty")
                            )

                            Box(modifier = Modifier.weight(1.8f)) {
                                OutlinedTextField(
                                    value = purchaseUnit,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Purchase Unit") },
                                    trailingIcon = {
                                        IconButton(onClick = { expandedPurchaseUnit = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("dialog_tf_exp_purchase_unit")
                                )
                                DropdownMenu(
                                    expanded = expandedPurchaseUnit,
                                    onDismissRequest = { expandedPurchaseUnit = false }
                                ) {
                                    UnitSystem.AllUnits.forEach { unitVal ->
                                        DropdownMenuItem(
                                            text = { Text(unitVal) },
                                            onClick = {
                                                purchaseUnit = unitVal
                                                expandedPurchaseUnit = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        OutlinedTextField(
                            value = purchasePriceStr,
                            onValueChange = { purchasePriceStr = it },
                            label = { Text("Direct Expense Cost *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("dialog_tf_exp_direct_cost"),
                            prefix = { Text("₹") }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).testTag("dialog_exp_btn_cancel")
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val expenseName = name.trim()
                                val finalPrice = purchasePriceStr.toDoubleOrNull() ?: 0.0
                                if (expenseName.isEmpty()) return@Button

                                if (isNoUnit) {
                                    onConfirm(
                                        ExpenseUsage(
                                            name = expenseName,
                                            quantityUsed = 1.0,
                                            unitUsed = "No Unit",
                                            purchasePrice = finalPrice,
                                            purchaseQuantity = 1.0,
                                            purchaseUnit = "No Unit",
                                            isNoUnit = true,
                                            finalCost = finalPrice
                                        )
                                    )
                                } else {
                                    val usedQty = quantityStr.toDoubleOrNull() ?: 0.0
                                    val pQty = purchaseQtyStr.toDoubleOrNull() ?: 1.0
                                    
                                    val calculatedCost = UnitSystem.calculateCost(
                                        quantityUsed = usedQty,
                                        unitUsed = unitUsed,
                                        purchasePrice = finalPrice,
                                        purchaseQuantity = pQty,
                                        purchaseUnit = purchaseUnit,
                                        isNoUnit = false
                                    )

                                    onConfirm(
                                        ExpenseUsage(
                                            name = expenseName,
                                            quantityUsed = usedQty,
                                            unitUsed = unitUsed,
                                            purchasePrice = finalPrice,
                                            purchaseQuantity = pQty,
                                            purchaseUnit = purchaseUnit,
                                            isNoUnit = false,
                                            finalCost = calculatedCost
                                        )
                                    )
                                }
                            },
                            enabled = name.trim().isNotEmpty() && (isNoUnit || quantityStr.trim().isNotEmpty()),
                            modifier = Modifier.weight(1.5f).testTag("dialog_exp_btn_confirm")
                        ) {
                            Text(if (initialUsage != null) "Save Changes" else "Add Expense")
                        }
                    }
                }
            }
        }
    }
}
