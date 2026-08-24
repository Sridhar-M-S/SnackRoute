package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PaymentInvoice
import com.example.data.SalesEntry
import com.example.data.ShopMaster
import com.example.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentInvoicesScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    initialShopNumber: String? = null
) {
    val context = LocalContext.current
    val allInvoices by viewModel.allInvoices.collectAsStateWithLifecycle()
    val allShops by viewModel.shops.collectAsStateWithLifecycle()
    val allSales by viewModel.sales.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // ALL, UNPAID, PARTIALLY PAID, PAID

    var showCreateEditDialog by remember { mutableStateOf(false) }
    var invoiceToEdit by remember { mutableStateOf<PaymentInvoice?>(null) }
    var viewingInvoice by remember { mutableStateOf<PaymentInvoice?>(null) }
    var invoiceToDelete by remember { mutableStateOf<PaymentInvoice?>(null) }

    // If navigated with a preselected shop
    var preselectedShopNumber by remember { mutableStateOf(initialShopNumber) }

    val filteredInvoices = remember(allInvoices, searchQuery, selectedStatusFilter) {
        allInvoices.filter { invoice ->
            val matchesSearch = searchQuery.isBlank() ||
                    invoice.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                    invoice.shopName.contains(searchQuery, ignoreCase = true) ||
                    invoice.shopNumber.contains(searchQuery, ignoreCase = true) ||
                    invoice.locationNumber.contains(searchQuery, ignoreCase = true)

            val matchesStatus = when (selectedStatusFilter) {
                "UNPAID" -> invoice.status.equals("UNPAID", ignoreCase = true)
                "PARTIALLY PAID" -> invoice.status.equals("PARTIALLY PAID", ignoreCase = true)
                "PAID" -> invoice.status.equals("PAID", ignoreCase = true)
                else -> true
            }

            matchesSearch && matchesStatus
        }
    }

    val totalInvoicesCount = allInvoices.size
    val totalInvoicedAmount = remember(allInvoices) { allInvoices.sumOf { it.totalAmount } }
    val totalPaidAmount = remember(allInvoices) { allInvoices.sumOf { it.paidAmount } }
    val totalBalanceAmount = remember(allInvoices) { allInvoices.sumOf { it.balanceAmount } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Payment Invoices",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$totalInvoicesCount total invoices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("invoice_screen_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.exportPaymentInvoicesToExcel(context) },
                        modifier = Modifier.testTag("export_invoices_excel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Invoices to Excel",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    invoiceToEdit = null
                    showCreateEditDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = "Create Invoice") },
                text = { Text("New Invoice") },
                modifier = Modifier.testTag("create_new_invoice_fab")
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp)
        ) {
            // --- Summary Cards Row ---
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invoices_summary_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Payment Summary",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryMetricItem(
                                label = "Total Invoiced",
                                value = "₹${"%.2f".format(totalInvoicedAmount)}",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricItem(
                                label = "Total Paid",
                                value = "₹${"%.2f".format(totalPaidAmount)}",
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricItem(
                                label = "Balance Due",
                                value = "₹${"%.2f".format(totalBalanceAmount)}",
                                color = Color(0xFFC62828),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // --- Search & Filter Row ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invoice_search_input"),
                        placeholder = { Text("Search by Invoice No, Shop, Location...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Status Filters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ALL", "UNPAID", "PARTIALLY PAID", "PAID").forEach { statusOption ->
                            val isSelected = selectedStatusFilter == statusOption
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedStatusFilter = statusOption },
                                label = {
                                    Text(
                                        text = statusOption,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag("invoice_filter_${statusOption.lowercase().replace(" ", "_")}")
                            )
                        }
                    }
                }
            }

            // --- Invoices List ---
            if (filteredInvoices.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isNotBlank() || selectedStatusFilter != "ALL") "No matching invoices found" else "No payment invoices created yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create an invoice by selecting a shop and attaching its existing sales records.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    invoiceToEdit = null
                                    showCreateEditDialog = true
                                },
                                modifier = Modifier.testTag("create_invoice_empty_state_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create First Invoice")
                            }
                        }
                    }
                }
            } else {
                items(filteredInvoices, key = { it.id }) { invoice ->
                    InvoiceListItemCard(
                        invoice = invoice,
                        onView = { viewingInvoice = invoice },
                        onEdit = {
                            invoiceToEdit = invoice
                            showCreateEditDialog = true
                        },
                        onShare = {
                            viewModel.sharePaymentInvoice(context, invoice, allSales)
                        },
                        onDelete = {
                            invoiceToDelete = invoice
                        }
                    )
                }
            }
        }
    }

    // --- Create / Edit Dialog ---
    if (showCreateEditDialog) {
        CreateEditInvoiceDialog(
            viewModel = viewModel,
            invoiceToEdit = invoiceToEdit,
            initialShopNumber = preselectedShopNumber,
            allShops = allShops,
            allSales = allSales,
            allInvoices = allInvoices,
            onDismiss = {
                showCreateEditDialog = false
                invoiceToEdit = null
            },
            onSaved = {
                showCreateEditDialog = false
                invoiceToEdit = null
            }
        )
    }

    // --- Invoice Detail & Slip View Dialog ---
    if (viewingInvoice != null) {
        InvoiceDetailDialog(
            invoice = viewingInvoice!!,
            allSales = allSales,
            onDismiss = { viewingInvoice = null },
            onShare = {
                viewModel.sharePaymentInvoice(context, viewingInvoice!!, allSales)
            },
            onEdit = {
                val inv = viewingInvoice
                viewingInvoice = null
                invoiceToEdit = inv
                showCreateEditDialog = true
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    if (invoiceToDelete != null) {
        AlertDialog(
            onDismissRequest = { invoiceToDelete = null },
            icon = {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Invoice ${invoiceToDelete!!.invoiceNumber}?") },
            text = {
                Text(
                    "Are you sure you want to delete this invoice for ${invoiceToDelete!!.shopName}? The associated sales records will become available for future invoices again."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = invoiceToDelete!!
                        invoiceToDelete = null
                        viewModel.deletePaymentInvoice(toDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_invoice_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { invoiceToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SummaryMetricItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InvoiceListItemCard(
    invoice: PaymentInvoice,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView() }
            .testTag("invoice_item_${invoice.invoiceNumber}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Invoice Number + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = invoice.invoiceNumber,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = invoice.invoiceDateFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                InvoiceStatusBadge(status = invoice.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Shop Details Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = invoice.shopName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "(${invoice.shopNumber})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Location: ${invoice.locationNumber} • ${invoice.salesEntryIds.size} sales items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Amounts Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Amount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${"%.2f".format(invoice.totalAmount)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Paid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${"%.2f".format(invoice.paidAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E7D32)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${"%.2f".format(invoice.balanceAmount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (invoice.balanceAmount > 0) Color(0xFFC62828) else Color(0xFF2E7D32)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("share_invoice_${invoice.invoiceNumber}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Invoice",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("edit_invoice_${invoice.invoiceNumber}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Invoice",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_invoice_${invoice.invoiceNumber}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Invoice",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onView,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("view_invoice_${invoice.invoiceNumber}")
                ) {
                    Text("View Slip", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun InvoiceStatusBadge(status: String) {
    val (backgroundColor, textColor, icon) = when (status.uppercase()) {
        "PAID" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.CheckCircle)
        "PARTIALLY PAID" -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Default.HourglassTop)
        else -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.ErrorOutline)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = status.uppercase(),
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateEditInvoiceDialog(
    viewModel: AppViewModel,
    invoiceToEdit: PaymentInvoice?,
    initialShopNumber: String?,
    allShops: List<ShopMaster>,
    allSales: List<SalesEntry>,
    allInvoices: List<PaymentInvoice>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val isEditMode = invoiceToEdit != null

    // Shop Selection
    var selectedShop by remember {
        mutableStateOf(
            if (isEditMode) {
                allShops.find { it.shopNumber == invoiceToEdit!!.shopNumber }
            } else if (!initialShopNumber.isNullOrBlank()) {
                allShops.find { it.shopNumber == initialShopNumber }
            } else {
                null
            }
        )
    }
    var shopDropdownExpanded by remember { mutableStateOf(false) }
    var shopSearchText by remember { mutableStateOf("") }

    // Date
    var invoiceDateTimestamp by remember {
        mutableStateOf(invoiceToEdit?.invoiceDate ?: System.currentTimeMillis())
    }
    val formattedInvoiceDate = remember(invoiceDateTimestamp) {
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(invoiceDateTimestamp))
    }

    // Selected Sales IDs
    val selectedSalesIds = remember {
        mutableStateListOf<Int>().apply {
            if (isEditMode) {
                addAll(invoiceToEdit!!.salesEntryIds)
            }
        }
    }

    // Amount Paid & Notes
    var paidAmountText by remember {
        mutableStateOf(
            if (isEditMode && invoiceToEdit!!.paidAmount > 0) invoiceToEdit.paidAmount.toString() else "0"
        )
    }
    var notesText by remember {
        mutableStateOf(invoiceToEdit?.notes ?: "")
    }

    // Invoiced Sales filter for DUPLICATE PREVENTION:
    // Sales already attached to other active invoices
    val alreadyInvoicedSalesIds = remember(allInvoices, invoiceToEdit) {
        viewModel.getInvoicedSalesIds(excludeInvoiceId = invoiceToEdit?.id)
    }

    // Sales available for current selected shop
    val availableSalesForShop = remember(selectedShop, allSales) {
        if (selectedShop == null) emptyList()
        else allSales.filter { it.shopNumber == selectedShop!!.shopNumber }
    }

    // Live calculation of total from selected sales
    val calculatedTotalAmount = remember(selectedSalesIds.toList(), availableSalesForShop) {
        availableSalesForShop
            .filter { it.id in selectedSalesIds }
            .sumOf { it.totalAmount }
    }

    val paidAmountDouble = paidAmountText.toDoubleOrNull() ?: 0.0
    val calculatedBalance = (calculatedTotalAmount - paidAmountDouble).coerceAtLeast(0.0)
    val calculatedStatus = viewModel.calculateInvoiceStatus(calculatedTotalAmount, paidAmountDouble)

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .testTag("create_edit_invoice_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEditMode) "Edit Invoice" else "New Payment Invoice",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (isEditMode) {
                            Text(
                                text = invoiceToEdit!!.invoiceNumber,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Step 1: Select Shop
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "1. Select Shop *",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        ExposedDropdownMenuBox(
                            expanded = shopDropdownExpanded,
                            onExpandedChange = { shopDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedShop?.let { "${it.storeName} (${it.shopNumber})" } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Choose shop from Shop Master") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = shopDropdownExpanded) },
                                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("invoice_shop_selector"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = shopDropdownExpanded,
                                onDismissRequest = { shopDropdownExpanded = false }
                            ) {
                                OutlinedTextField(
                                    value = shopSearchText,
                                    onValueChange = { shopSearchText = it },
                                    placeholder = { Text("Search shop...") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    singleLine = true
                                )
                                val filteredShopList = allShops.filter {
                                    shopSearchText.isBlank() ||
                                            it.storeName.contains(shopSearchText, ignoreCase = true) ||
                                            it.shopNumber.contains(shopSearchText, ignoreCase = true) ||
                                            it.locationNumber.contains(shopSearchText, ignoreCase = true)
                                }
                                if (filteredShopList.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No shops found") },
                                        onClick = {}
                                    )
                                } else {
                                    filteredShopList.forEach { shop ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(shop.storeName, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        "ID: ${shop.shopNumber} • Loc: ${shop.locationNumber}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                if (selectedShop?.shopNumber != shop.shopNumber) {
                                                    selectedShop = shop
                                                    selectedSalesIds.clear()
                                                }
                                                shopDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Auto-populated shop details
                        if (selectedShop != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Shop: ${selectedShop!!.storeName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Location: ${selectedShop!!.locationNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Step 2: Invoice Date
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "2. Invoice Date",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedCard(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = invoiceDateTimestamp }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val selectedCal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, month)
                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                        }
                                        invoiceDateTimestamp = selectedCal.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = formattedInvoiceDate,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "Change",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Step 3: Select Sales Records (with Duplicate Prevention)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "3. Select Sales Records *",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (availableSalesForShop.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            val validIds = availableSalesForShop
                                                .filter { it.id !in alreadyInvoicedSalesIds || it.id in selectedSalesIds }
                                                .map { it.id }
                                            selectedSalesIds.clear()
                                            selectedSalesIds.addAll(validIds)
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Select All", style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(
                                        onClick = { selectedSalesIds.clear() },
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        if (selectedShop == null) {
                            Text(
                                text = "Please select a shop above to view and attach sales records.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (availableSalesForShop.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No sales records found for this shop in Sales Master.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                availableSalesForShop.forEach { sale ->
                                    val isAlreadyInvoicedElsewhere = sale.id in alreadyInvoicedSalesIds && (!isEditMode || sale.id !in (invoiceToEdit?.salesEntryIds ?: emptyList()))
                                    val isChecked = sale.id in selectedSalesIds
                                    val rate = sale.customSellingPrice ?: sale.ratePerPacket

                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            else if (isAlreadyInvoicedElsewhere) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isAlreadyInvoicedElsewhere) {
                                                if (isChecked) {
                                                    selectedSalesIds.remove(sale.id)
                                                } else {
                                                    selectedSalesIds.add(sale.id)
                                                }
                                            }
                                            .testTag("invoice_sale_item_${sale.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    if (checked) selectedSalesIds.add(sale.id)
                                                    else selectedSalesIds.remove(sale.id)
                                                },
                                                enabled = !isAlreadyInvoicedElsewhere,
                                                modifier = Modifier.testTag("checkbox_sale_${sale.id}")
                                            )

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = sale.productName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "₹${"%.2f".format(sale.totalAmount)}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(2.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "Date: ${sale.entryDateFormatted} • Pkts: ${sale.packetsSold} @ ₹${"%.2f".format(rate)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                if (isAlreadyInvoicedElsewhere) {
                                                    Text(
                                                        text = "Already attached to another active invoice",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFFC62828),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Step 4: Amount Summary & Paid Entry
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "4. Amount Summary & Payment",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Invoice Amount:", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "₹${"%.2f".format(calculatedTotalAmount)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Payment Status:", style = MaterialTheme.typography.bodyMedium)
                                    InvoiceStatusBadge(status = calculatedStatus)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = paidAmountText,
                            onValueChange = { paidAmountText = it },
                            label = { Text("Amount Paid (₹) *") },
                            placeholder = { Text("0.00") },
                            leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("invoice_paid_amount_input")
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (calculatedBalance > 0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Balance Amount:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "₹${"%.2f".format(calculatedBalance)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (calculatedBalance > 0) Color(0xFFC62828) else Color(0xFF2E7D32)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            label = { Text("Remarks / Notes (Optional)") },
                            placeholder = { Text("Add any note or payment reference...") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("invoice_notes_input"),
                            maxLines = 3
                        )
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Bottom Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (selectedShop == null) {
                                errorMessage = "Please select a shop"
                                return@Button
                            }
                            if (selectedSalesIds.isEmpty()) {
                                errorMessage = "Please select at least one sales record"
                                return@Button
                            }

                            isSaving = true
                            errorMessage = null

                            viewModel.savePaymentInvoice(
                                id = invoiceToEdit?.id ?: 0,
                                invoiceNumber = invoiceToEdit?.invoiceNumber ?: "",
                                invoiceDate = invoiceDateTimestamp,
                                shopNumber = selectedShop!!.shopNumber,
                                shopName = selectedShop!!.storeName,
                                locationNumber = selectedShop!!.locationNumber,
                                selectedSalesIds = selectedSalesIds.toList(),
                                paidAmount = paidAmountDouble,
                                notes = notesText,
                                onComplete = { success, msg ->
                                    isSaving = false
                                    if (success) {
                                        onSaved()
                                    } else {
                                        errorMessage = msg
                                    }
                                }
                            )
                        },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("save_invoice_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (isEditMode) "Update Invoice" else "Create Invoice")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceDetailDialog(
    invoice: PaymentInvoice,
    allSales: List<SalesEntry>,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit
) {
    val invoiceSales = remember(invoice, allSales) {
        allSales.filter { it.id in invoice.salesEntryIds }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .testTag("invoice_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PAYMENT INVOICE",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Invoice: ${invoice.invoiceNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Top Info Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "INVOICE DATE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = invoice.invoiceDateFormatted,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "PAYMENT STATUS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            InvoiceStatusBadge(status = invoice.status)
                        }
                    }

                    // Shop Details
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "SHOP DETAILS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = invoice.shopName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = "Shop ID: ${invoice.shopNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Location: ${invoice.locationNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Sales Details List / Table
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "SALES DETAILS (${invoiceSales.size} items)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (invoiceSales.isEmpty()) {
                            Text(
                                text = "No itemized records available.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            invoiceSales.forEachIndexed { index, sale ->
                                val rate = sale.customSellingPrice ?: sale.ratePerPacket
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${index + 1}. ${sale.productName}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "₹${"%.2f".format(sale.totalAmount)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Date: ${sale.entryDateFormatted}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${sale.packetsSold} pkts @ ₹${"%.2f".format(rate)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Amount Summary
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "AMOUNT SUMMARY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Invoice Amount:", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "₹${"%.2f".format(invoice.totalAmount)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Amount Paid:", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "₹${"%.2f".format(invoice.paidAmount)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Balance Amount:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "₹${"%.2f".format(invoice.balanceAmount)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (invoice.balanceAmount > 0) Color(0xFFC62828) else Color(0xFF2E7D32)
                                )
                            }
                        }
                    }

                    if (!invoice.notes.isNullOrBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "NOTES",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = invoice.notes,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("dialog_edit_invoice_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit")
                    }

                    Button(
                        onClick = onShare,
                        modifier = Modifier.testTag("dialog_share_invoice_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Invoice")
                    }
                }
            }
        }
    }
}
