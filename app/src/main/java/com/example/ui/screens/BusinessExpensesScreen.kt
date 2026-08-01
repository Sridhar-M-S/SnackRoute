package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.BusinessExpense
import com.example.ui.AppViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

val DEFAULT_EXPENSE_CATEGORIES = listOf(
    "Raw Materials", "Ingredients", "Packaging", "Stickers", "Oil", "Masala",
    "Transport", "Fuel", "Vehicle Maintenance", "Electricity", "Rent",
    "Salary / Wages", "Shop Expenses", "Marketing", "Equipment Purchase",
    "Repairs", "Miscellaneous"
)

val PAYMENT_METHODS = listOf("Cash", "UPI", "Bank", "Credit")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessExpensesScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val expenses by viewModel.allExpensesList.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importExpensesFromExcel(context, uri)
        }
    }

    var activeTab by remember { mutableStateOf("History") } // History, Category Analysis, Monthly Reports
    val tabs = listOf("History", "Category Analysis", "Monthly Reports")

    var showAddEditDialog by remember { mutableStateOf<BusinessExpense?>(null) } // if not null, show Dialog
    var showAddDialog by remember { mutableStateOf(false) }

    // Search and Filters
    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("All") }
    var filterPaymentMethod by remember { mutableStateOf("All") }

    // Date Range Filter
    var filterStartDate by remember { mutableStateOf<Long?>(null) }
    var filterEndDate by remember { mutableStateOf<Long?>(null) }
    var showRangePicker by remember { mutableStateOf(false) }

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

    val getStartOfWeek = { timestamp: Long ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        startOfDay(cal.timeInMillis)
    }

    val getStartOfMonth = { timestamp: Long ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.DAY_OF_MONTH, 1)
        startOfDay(cal.timeInMillis)
    }

    val getStartOfYear = { timestamp: Long ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.DAY_OF_YEAR, 1)
        startOfDay(cal.timeInMillis)
    }

    // Calculators
    val now = System.currentTimeMillis()
    val todayStart = startOfDay(now)
    val todayEnd = endOfDay(now)
    val weekStart = getStartOfWeek(now)
    val monthStart = getStartOfMonth(now)
    val yearStart = getStartOfYear(now)

    val todayExpenseSum = remember(expenses) {
        expenses.filter { it.expenseDate in todayStart..todayEnd }.sumOf { it.amount }
    }
    val weekExpenseSum = remember(expenses) {
        expenses.filter { it.expenseDate >= weekStart }.sumOf { it.amount }
    }
    val monthExpenseSum = remember(expenses) {
        expenses.filter { it.expenseDate >= monthStart }.sumOf { it.amount }
    }
    val yearExpenseSum = remember(expenses) {
        expenses.filter { it.expenseDate >= yearStart }.sumOf { it.amount }
    }

    val customRangeExpenseSum = remember(expenses, filterStartDate, filterEndDate) {
        if (filterStartDate != null && filterEndDate != null) {
            val start = startOfDay(filterStartDate!!)
            val end = endOfDay(filterEndDate!!)
            expenses.filter { it.expenseDate in start..end }.sumOf { it.amount }
        } else {
            0.0
        }
    }

    // Filtered Expenses for List
    val filteredExpenses = remember(expenses, searchQuery, filterCategory, filterPaymentMethod, filterStartDate, filterEndDate) {
        expenses.filter { expense ->
            val matchSearch = expense.description.contains(searchQuery, ignoreCase = true) ||
                    (expense.notes ?: "").contains(searchQuery, ignoreCase = true)
            val matchCategory = filterCategory == "All" || expense.category == filterCategory
            val matchPayment = filterPaymentMethod == "All" || expense.paymentMethod == filterPaymentMethod
            
            val matchDate = if (filterStartDate != null && filterEndDate != null) {
                val s = startOfDay(filterStartDate!!)
                val e = endOfDay(filterEndDate!!)
                expense.expenseDate in s..e
            } else {
                true
            }

            matchSearch && matchCategory && matchPayment && matchDate
        }.sortedByDescending { expense -> expense.expenseDate }
    }

    // Category Analysis Calculations
    val categoryAnalysis = remember(expenses, filterStartDate, filterEndDate) {
        val filteredForAnalysis = if (filterStartDate != null && filterEndDate != null) {
            val start = startOfDay(filterStartDate!!)
            val end = endOfDay(filterEndDate!!)
            expenses.filter { it.expenseDate in start..end }
        } else {
            expenses
        }
        val total = filteredForAnalysis.sumOf { it.amount }.coerceAtLeast(1.0)
        val grouped = filteredForAnalysis.groupBy { it.category }
        
        val analysisList = grouped.map { (cat, list) ->
            val amount = list.sumOf { it.amount }
            val pct = (amount / total) * 100
            CategoryStat(category = cat, totalAmount = amount, percentage = pct)
        }.sortedByDescending { it.totalAmount }

        val highest = analysisList.firstOrNull()
        val lowest = analysisList.lastOrNull()

        CategoryAnalysisResult(
            stats = analysisList,
            highest = highest,
            lowest = lowest,
            totalExpense = total
        )
    }

    // Monthly Comparison Calculations
    val monthlyComparison = remember(expenses) {
        val groupedByMonth = expenses.groupBy {
            val cal = Calendar.getInstance()
            cal.timeInMillis = it.expenseDate
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
        }
        groupedByMonth.map { (month, list) ->
            val total = list.sumOf { it.amount }
            MonthlyExpenseStat(monthYear = month, totalAmount = total, timestamp = list.firstOrNull()?.expenseDate ?: 0L)
        }.sortedByDescending { it.timestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business Expenses", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_expenses_back")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { importLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") },
                        modifier = Modifier.testTag("btn_expenses_import")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Import Excel")
                    }
                    IconButton(
                        onClick = { viewModel.exportExpensesToExcel(context) },
                        modifier = Modifier.testTag("btn_expenses_export")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export Excel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (activeTab == "History") {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_expense")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Expense")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tabs Scrollable Row
            TabRow(
                selectedTabIndex = tabs.indexOf(activeTab),
                modifier = Modifier.fillMaxWidth().testTag("expenses_tab_row")
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = { Text(tab, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("tab_$tab")
                    )
                }
            }

            AnimatedContent(
                targetState = activeTab,
                label = "expenses_content_switch",
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { targetTab ->
                when (targetTab) {
                    "History" -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Summary Mini Dashboard
                            item {
                                ExpenseSummaryDashboardCard(
                                    today = todayExpenseSum,
                                    week = weekExpenseSum,
                                    month = monthExpenseSum,
                                    year = yearExpenseSum,
                                    customRange = customRangeExpenseSum,
                                    hasCustomRange = filterStartDate != null && filterEndDate != null,
                                    onSelectCustomRange = { showRangePicker = true },
                                    onClearCustomRange = {
                                        filterStartDate = null
                                        filterEndDate = null
                                    }
                                )
                            }

                            // Search and Quick Filters
                            item {
                                SearchAndFiltersSection(
                                    searchQuery = searchQuery,
                                    onSearchChange = { searchQuery = it },
                                    selectedCategory = filterCategory,
                                    onCategorySelect = { filterCategory = it },
                                    selectedPayment = filterPaymentMethod,
                                    onPaymentSelect = { filterPaymentMethod = it }
                                )
                            }

                            // History Header
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Expense Ledger (${filteredExpenses.size})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    if (filteredExpenses.isNotEmpty()) {
                                        Text(
                                            "Total: ₹${"%,.2f".format(filteredExpenses.sumOf { it.amount })}",
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            if (filteredExpenses.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Receipt,
                                                contentDescription = null,
                                                modifier = Modifier.size(64.dp),
                                                tint = Color.Gray.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                "No business expenses logged",
                                                color = Color.Gray,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(filteredExpenses, key = { it.id }) { expense ->
                                    ExpenseHistoryItemCard(
                                        expense = expense,
                                        onEdit = { showAddEditDialog = expense },
                                        onDelete = { viewModel.deleteExpense(expense) }
                                    )
                                }
                            }
                        }
                    }

                    "Category Analysis" -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Date Range Header (If selected)
                            item {
                                DateRangeHeaderSection(
                                    filterStartDate = filterStartDate,
                                    filterEndDate = filterEndDate,
                                    onSelectCustomRange = { showRangePicker = true },
                                    onClearCustomRange = {
                                        filterStartDate = null
                                        filterEndDate = null
                                    }
                                )
                            }

                            if (categoryAnalysis.stats.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No category insights available.",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            } else {
                                // Highest and Lowest highlights
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        categoryAnalysis.highest?.let { hi ->
                                            HighlightStatCard(
                                                title = "Highest Spending",
                                                category = hi.category,
                                                amount = hi.totalAmount,
                                                pct = hi.percentage,
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                textColor = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        categoryAnalysis.lowest?.let { lo ->
                                            HighlightStatCard(
                                                title = "Lowest Spending",
                                                category = lo.category,
                                                amount = lo.totalAmount,
                                                pct = lo.percentage,
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                // Categories Progress bar list
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().testTag("category_breakdown_card"),
                                        shape = RoundedCornerShape(16.dp),
                                        border = CardDefaults.outlinedCardBorder()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                "Category Wise Breakdown",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(bottom = 12.dp)
                                            )

                                            categoryAnalysis.stats.forEach { stat ->
                                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            stat.category,
                                                            fontWeight = FontWeight.SemiBold,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        Text(
                                                            "₹${"%,.2f".format(stat.totalAmount)} (${"%.1f".format(stat.percentage)}%)",
                                                            fontWeight = FontWeight.Bold,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    LinearProgressIndicator(
                                                        progress = (stat.percentage / 100).toFloat(),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(8.dp)
                                                            .clip(RoundedCornerShape(4.dp)),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Monthly Reports" -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Text(
                                    "Monthly Expenditure History",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            if (monthlyComparison.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No monthly comparisons available.",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            } else {
                                items(monthlyComparison) { monthStat ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().testTag("monthly_stat_card_${monthStat.monthYear.lowercase().replace(" ", "_")}"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.CalendarToday,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = monthStat.monthYear,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }
                                            Text(
                                                text = "₹${"%,.2f".format(monthStat.totalAmount)}",
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.bodyLarge,
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
        }
    }

    // RANGE PICKER DIALOG
    if (showRangePicker) {
        CustomRangePickerDialog(
            initialStart = filterStartDate,
            initialEnd = filterEndDate,
            onDismiss = { showRangePicker = false },
            onConfirm = { start, end ->
                filterStartDate = start
                filterEndDate = end
                showRangePicker = false
            }
        )
    }

    // ADD EXPENSE DIALOG
    if (showAddDialog) {
        AddEditExpenseDialog(
            expense = null,
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onConfirm = { expense, addAnother ->
                viewModel.insertExpense(expense)
                if (!addAnother) {
                    showAddDialog = false
                }
            }
        )
    }

    // EDIT EXPENSE DIALOG
    if (showAddEditDialog != null) {
        AddEditExpenseDialog(
            expense = showAddEditDialog,
            viewModel = viewModel,
            onDismiss = { showAddEditDialog = null },
            onConfirm = { expense, _ ->
                viewModel.updateExpense(expense)
                showAddEditDialog = null
            }
        )
    }

    // --- Importing Loading Dialog ---
    if (isImporting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Importing Expenses") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Reading spreadsheet and importing business expenses...")
                }
            }
        )
    }

    // --- Excel Import Summary Dialog ---
    if (importSummary != null && importSummary!!.type == com.example.utils.Exporter.ImportType.BUSINESS_EXPENSES) {
        val summary = importSummary!!
        AlertDialog(
            onDismissRequest = { viewModel.clearImportSummary() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (summary.failedRowsCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (summary.failedRowsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Rows Scanned:", fontWeight = FontWeight.Medium)
                        Text("${summary.totalRows}")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Successfully Imported:", fontWeight = FontWeight.Medium)
                        Text("${summary.successfullyImported}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Failed Rows:", fontWeight = FontWeight.Medium)
                        Text("${summary.failedRowsCount}", color = if (summary.failedRowsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                    
                    if (summary.errorReportFile != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Some rows could not be imported due to formatting or validation errors. Download the error report to review.",
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
                                com.example.utils.Exporter.shareFile(context, summary.errorReportFile, "Expenses Import Error Report")
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
                        modifier = Modifier.weight(1f).testTag("btn_close_import_summary")
                    ) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
fun ExpenseSummaryDashboardCard(
    today: Double,
    week: Double,
    month: Double,
    year: Double,
    customRange: Double,
    hasCustomRange: Boolean,
    onSelectCustomRange: () -> Unit,
    onClearCustomRange: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("expense_summary_dashboard_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Expense Summary Dashboard",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Grid of 4 main periods
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    MiniExpenseMetricBlock("Today", today)
                    Spacer(modifier = Modifier.height(10.dp))
                    MiniExpenseMetricBlock("This Month", month)
                }
                Column(modifier = Modifier.weight(1f)) {
                    MiniExpenseMetricBlock("This Week", week)
                    Spacer(modifier = Modifier.height(10.dp))
                    MiniExpenseMetricBlock("This Year", year)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom Range Block
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Custom Date Range",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (hasCustomRange) {
                            Text(
                                "₹${"%,.2f".format(customRange)}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Text(
                                "No range selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    if (hasCustomRange) {
                        IconButton(onClick = onClearCustomRange) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Range", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(
                            onClick = onSelectCustomRange,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniExpenseMetricBlock(label: String, value: Double) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "₹${"%,.2f".format(value)}",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchAndFiltersSection(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    selectedPayment: String,
    onPaymentSelect: (String) -> Unit
) {
    var expandedCategory by remember { mutableStateOf(false) }
    var expandedPayment by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search description or notes...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("expenses_search_input"),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category filter dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expandedCategory = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (selectedCategory == "All") "Category: All" else selectedCategory,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false },
                    modifier = Modifier.heightIn(max = 250.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("All Categories") },
                        onClick = {
                            onCategorySelect("All")
                            expandedCategory = false
                        }
                    )
                    DEFAULT_EXPENSE_CATEGORIES.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                onCategorySelect(category)
                                expandedCategory = false
                            }
                        )
                    }
                }
            }

            // Payment method dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expandedPayment = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (selectedPayment == "All") "Payment: All" else selectedPayment,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = expandedPayment,
                    onDismissRequest = { expandedPayment = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Payments") },
                        onClick = {
                            onPaymentSelect("All")
                            expandedPayment = false
                        }
                    )
                    PAYMENT_METHODS.forEach { method ->
                        DropdownMenuItem(
                            text = { Text(method) },
                            onClick = {
                                onPaymentSelect(method)
                                expandedPayment = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DateRangeHeaderSection(
    filterStartDate: Long?,
    filterEndDate: Long?,
    onSelectCustomRange: () -> Unit,
    onClearCustomRange: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                if (filterStartDate != null && filterEndDate != null) {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val startStr = sdf.format(Date(filterStartDate))
                    val endStr = sdf.format(Date(filterEndDate))
                    Text(
                        "$startStr - $endStr",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "Showing All-Time Insights",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (filterStartDate != null && filterEndDate != null) {
                IconButton(onClick = onClearCustomRange) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear Filters", tint = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onSelectCustomRange) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Filter Dates", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ExpenseHistoryItemCard(
    expense: BusinessExpense,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("expense_card_${expense.id}")
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            expense.category,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        expense.description,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "₹${"%,.2f".format(expense.amount)}",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        expense.expenseDateFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Notes
                    if (!expense.notes.isNullOrEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Notes, contentDescription = null, size14Modifier(), tint = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Notes: ${expense.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Payment Method
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Payment, contentDescription = null, size14Modifier(), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Payment Method: ${expense.paymentMethod}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }

                    // Attachment Preview
                    if (!expense.attachmentUri.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Attachment:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        val file = File(expense.attachmentUri)
                        if (file.exists()) {
                            Image(
                                painter = rememberAsyncImagePainter(file),
                                contentDescription = "Receipt Attachment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            )
                        } else {
                            Text(
                                "Image not found or missing permission",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.testTag("btn_edit_expense_${expense.id}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("btn_delete_expense_${expense.id}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Expense") },
            text = { Text("Are you sure you want to permanently delete this expense record?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun HighlightStatCard(
    title: String,
    category: String,
    amount: Double,
    pct: Double,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                category,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "₹${"%,.2f".format(amount)} (${"%.1f".format(pct)}%)",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun size14Modifier() = Modifier.size(14.dp)

// --- CUSTOM RANGE DIALOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRangePickerDialog(
    initialStart: Long?,
    initialEnd: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart,
        initialSelectedEndDateMillis = initialEnd
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
        title = { Text("Select Date Range", fontWeight = FontWeight.Bold) },
        text = {
            DateRangePicker(
                state = state,
                title = { Text("Select range of dates") },
                modifier = Modifier.fillMaxWidth().height(400.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis
                    if (s != null && e != null) {
                        onConfirm(s, e)
                    }
                },
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- ADD/EDIT DIALOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditExpenseDialog(
    expense: BusinessExpense?,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onConfirm: (BusinessExpense, Boolean) -> Unit
) {
    val context = LocalContext.current

    var selectedDate by remember { mutableStateOf(expense?.expenseDate ?: System.currentTimeMillis()) }
    var selectedCategory by remember { mutableStateOf(expense?.category ?: DEFAULT_EXPENSE_CATEGORIES.first()) }
    var description by remember { mutableStateOf(expense?.description ?: "") }
    var amountStr by remember { mutableStateOf(expense?.amount?.toString() ?: "") }
    var notes by remember { mutableStateOf(expense?.notes ?: "") }
    var selectedPaymentMethod by remember { mutableStateOf(expense?.paymentMethod ?: PAYMENT_METHODS.first()) }
    var attachmentPath by remember { mutableStateOf(expense?.attachmentUri) }

    var expandedCat by remember { mutableStateOf(false) }
    var expandedPayment by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Errors
    var descError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = viewModel.saveImageToStorage(uri)
            if (savedPath != null) {
                attachmentPath = savedPath
                Toast.makeText(context, "Attachment Saved Successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to save attachment", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (expense == null) "Add Expense" else "Edit Expense",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date picker
                item {
                    val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDate))
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth().testTag("btn_select_date")
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Date: $formattedDate")
                    }
                }

                // Category selector
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Expense Category") },
                            trailingIcon = {
                                IconButton(onClick = { expandedCat = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable { expandedCat = true }.testTag("expense_category_picker"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        DropdownMenu(
                            expanded = expandedCat,
                            onDismissRequest = { expandedCat = false },
                            modifier = Modifier.heightIn(max = 250.dp)
                        ) {
                            DEFAULT_EXPENSE_CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        selectedCategory = cat
                                        expandedCat = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Description
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            description = it
                            descError = if (it.trim().isEmpty()) "Description is required" else null
                        },
                        label = { Text("Expense Description / Name") },
                        isError = descError != null,
                        supportingText = descError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("expense_description_input"),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Amount
                item {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = {
                            amountStr = it
                            val value = it.toDoubleOrNull()
                            amountError = when {
                                value == null -> "Please enter a valid amount"
                                value <= 0 -> "Amount must be greater than zero"
                                else -> null
                            }
                        },
                        label = { Text("Amount (₹)") },
                        isError = amountError != null,
                        supportingText = amountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("expense_amount_input"),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Payment Method
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedPaymentMethod,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Payment Method") },
                            trailingIcon = {
                                IconButton(onClick = { expandedPayment = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable { expandedPayment = true }.testTag("expense_payment_method_picker"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        DropdownMenu(
                            expanded = expandedPayment,
                            onDismissRequest = { expandedPayment = false }
                        ) {
                            PAYMENT_METHODS.forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method) },
                                    onClick = {
                                        selectedPaymentMethod = method
                                        expandedPayment = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Notes
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("expense_notes_input"),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Attachment (Optional image/bill)
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Attachment (Optional Bill/Receipt)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { imageLauncher.launch("image/*") },
                                modifier = Modifier.testTag("btn_select_attachment")
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick Image")
                            }

                            if (!attachmentPath.isNullOrEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(File(attachmentPath!!)),
                                        contentDescription = "Selected Receipt",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = { attachmentPath = null },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(20.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .padding(2.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (expense == null) {
                    OutlinedButton(
                        onClick = {
                            val d = description.trim()
                            val a = amountStr.toDoubleOrNull()
                            
                            var isValid = true
                            if (d.isEmpty()) {
                                descError = "Description is required"
                                isValid = false
                            }
                            if (a == null || a <= 0) {
                                amountError = "Please enter a valid amount greater than zero"
                                isValid = false
                            }

                            if (isValid) {
                                val updatedExpense = BusinessExpense(
                                    id = 0,
                                    expenseDate = selectedDate,
                                    category = selectedCategory,
                                    description = d,
                                    amount = a!!,
                                    notes = if (notes.trim().isEmpty()) null else notes,
                                    paymentMethod = selectedPaymentMethod,
                                    attachmentUri = attachmentPath
                                )
                                onConfirm(updatedExpense, true)
                                // Reset fields for next entry
                                description = ""
                                amountStr = ""
                                notes = ""
                                attachmentPath = null
                                descError = null
                                amountError = null
                            }
                        },
                        modifier = Modifier.testTag("btn_save_and_add_another_expense")
                    ) {
                        Text("Save & Add Another")
                    }
                }
                Button(
                    onClick = {
                        val d = description.trim()
                        val a = amountStr.toDoubleOrNull()
                        
                        var isValid = true
                        if (d.isEmpty()) {
                            descError = "Description is required"
                            isValid = false
                        }
                        if (a == null || a <= 0) {
                            amountError = "Please enter a valid amount greater than zero"
                            isValid = false
                        }

                        if (isValid) {
                            val updatedExpense = BusinessExpense(
                                id = expense?.id ?: 0,
                                expenseDate = selectedDate,
                                category = selectedCategory,
                                description = d,
                                amount = a!!,
                                notes = if (notes.trim().isEmpty()) null else notes,
                                paymentMethod = selectedPaymentMethod,
                                attachmentUri = attachmentPath
                            )
                            onConfirm(updatedExpense, false)
                        }
                    },
                    modifier = Modifier.testTag("btn_save_expense")
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = pickerState.selectedDateMillis
                        if (millis != null) {
                            selectedDate = millis
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// Auxiliary data structures
data class CategoryStat(val category: String, val totalAmount: Double, val percentage: Double)
data class CategoryAnalysisResult(val stats: List<CategoryStat>, val highest: CategoryStat?, val lowest: CategoryStat?, val totalExpense: Double)
data class MonthlyExpenseStat(val monthYear: String, val totalAmount: Double, val timestamp: Long)
