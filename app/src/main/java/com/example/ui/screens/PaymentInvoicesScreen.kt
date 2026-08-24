package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    val businessProfile by viewModel.businessProfile.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // ALL, UNPAID, PARTIALLY PAID, PAID

    var showCreateEditDialog by remember { mutableStateOf(false) }
    var showBusinessProfileDialog by remember { mutableStateOf(false) }
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
                        onClick = { showBusinessProfileDialog = true },
                        modifier = Modifier.testTag("btn_top_business_profile")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = "Company Profile & FSSAI",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
            // --- Business Profile Banner Card ---
            item {
                BusinessProfileHeaderCard(
                    profile = businessProfile,
                    onEditClick = { showBusinessProfileDialog = true }
                )
            }

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

    // --- Business Profile Dialog ---
    if (showBusinessProfileDialog) {
        BusinessProfileDialog(
            currentProfile = businessProfile,
            onDismiss = { showBusinessProfileDialog = false },
            onSave = { company, brand, addr, phone, fssai ->
                viewModel.saveBusinessProfile(company, brand, addr, phone, fssai)
                showBusinessProfileDialog = false
            }
        )
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
            businessProfile = businessProfile,
            onDismiss = { viewingInvoice = null },
            onShare = {
                viewModel.sharePaymentInvoice(context, viewingInvoice!!, allSales)
            },
            onEdit = {
                val inv = viewingInvoice
                viewingInvoice = null
                invoiceToEdit = inv
                showCreateEditDialog = true
            },
            onEditBusinessProfile = {
                showBusinessProfileDialog = true
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
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "Share PDF Invoice",
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
    val sdfDayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

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
    var isShopSelectionDialogOpen by remember { mutableStateOf(false) }

    // Invoice Date - Defaults to Today
    var invoiceDateTimestamp by remember {
        mutableStateOf(invoiceToEdit?.invoiceDate ?: System.currentTimeMillis())
    }
    val formattedInvoiceDate = remember(invoiceDateTimestamp) {
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(invoiceDateTimestamp))
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

    // Group available sales by date key (yyyy-MM-dd)
    val salesByDay = remember(availableSalesForShop) {
        availableSalesForShop.groupBy { sdfDayKey.format(Date(it.entryDate)) }
    }

    // Selected Sale Date Key (e.g., "2026-07-26")
    var selectedSaleDateKey by remember(selectedShop) {
        mutableStateOf(
            if (isEditMode && invoiceToEdit != null) {
                val firstSale = allSales.find { it.id in invoiceToEdit.salesEntryIds }
                if (firstSale != null) sdfDayKey.format(Date(firstSale.entryDate))
                else salesByDay.keys.maxOrNull()
            } else {
                // Default to latest sales date for this shop
                salesByDay.keys.maxOrNull()
            }
        )
    }

    // Selected Sales IDs for the invoice
    val selectedSalesIds = remember {
        mutableStateListOf<Int>().apply {
            if (isEditMode) {
                addAll(invoiceToEdit!!.salesEntryIds)
            } else if (selectedSaleDateKey != null) {
                val salesOnDay = salesByDay[selectedSaleDateKey] ?: emptyList()
                val validIds = salesOnDay
                    .filter { it.id !in alreadyInvoicedSalesIds }
                    .map { it.id }
                addAll(validIds)
            }
        }
    }

    // Amount Paid & Notes
    var paidAmountText by remember {
        mutableStateOf(
            if (isEditMode && invoiceToEdit!!.paidAmount > 0) {
                invoiceToEdit.paidAmount.toString()
            } else if (selectedSaleDateKey != null) {
                val salesOnDay = salesByDay[selectedSaleDateKey] ?: emptyList()
                val totalPaid = salesOnDay.filter { it.id in selectedSalesIds }.sumOf { it.actualPaidAmount }
                if (totalPaid > 0) "%.2f".format(totalPaid) else "0"
            } else {
                "0"
            }
        )
    }
    var notesText by remember {
        mutableStateOf(invoiceToEdit?.notes ?: "")
    }

    // Update sales selection and paid amount when sale date is picked
    fun onSelectSaleDate(dateKey: String) {
        selectedSaleDateKey = dateKey
        val salesOnDay = salesByDay[dateKey] ?: emptyList()
        val validIds = salesOnDay
            .filter { it.id !in alreadyInvoicedSalesIds || (isEditMode && it.id in (invoiceToEdit?.salesEntryIds ?: emptyList())) }
            .map { it.id }
        selectedSalesIds.clear()
        selectedSalesIds.addAll(validIds)

        // Automatically pre-fill the verified paid amount from the sales entries of that date
        val totalRecordedPaid = salesOnDay.filter { it.id in validIds }.sumOf { it.actualPaidAmount }
        paidAmountText = if (totalRecordedPaid > 0) "%.2f".format(totalRecordedPaid) else "0"
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

    if (isShopSelectionDialogOpen) {
        SearchableShopPickerDialog(
            shops = allShops,
            selectedShopNumber = selectedShop?.shopNumber,
            onShopSelected = { shop ->
                if (selectedShop?.shopNumber != shop.shopNumber) {
                    selectedShop = shop
                    selectedSalesIds.clear()
                    val shopSales = allSales.filter { it.shopNumber == shop.shopNumber }
                    val shopSalesByDay = shopSales.groupBy { sdfDayKey.format(Date(it.entryDate)) }
                    val latestDateKey = shopSalesByDay.keys.maxOrNull()
                    selectedSaleDateKey = latestDateKey
                    if (latestDateKey != null) {
                        val validIds = (shopSalesByDay[latestDateKey] ?: emptyList())
                            .filter { it.id !in alreadyInvoicedSalesIds }
                            .map { it.id }
                        selectedSalesIds.addAll(validIds)
                        val totalRecordedPaid = (shopSalesByDay[latestDateKey] ?: emptyList())
                            .filter { it.id in validIds }
                            .sumOf { it.actualPaidAmount }
                        paidAmountText = if (totalRecordedPaid > 0) "%.2f".format(totalRecordedPaid) else "0"
                    }
                }
                isShopSelectionDialogOpen = false
            },
            onDismiss = { isShopSelectionDialogOpen = false }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Invoice Date: $formattedInvoiceDate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
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
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Edit Date", fontSize = 11.sp)
                            }
                        }
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
                    // Step 1: Select Shop
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "1. Select Shop *",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (selectedShop == null) {
                            OutlinedCard(
                                onClick = { isShopSelectionDialogOpen = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("invoice_shop_selector"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                text = "Search and select shop",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Tap to search by name, ID or route",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("invoice_selected_shop_card"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Storefront,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = selectedShop!!.storeName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Shop ID: ${selectedShop!!.shopNumber}  •  Route: ${selectedShop!!.locationNumber}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { isShopSelectionDialogOpen = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("btn_change_selected_shop")
                                    ) {
                                        Text("Change", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Step 2: Select Sale Date (Monthly Sales Calendar)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "2. Select Sale Date *",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (selectedShop == null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Please select a shop first to view its sales dates on the calendar.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (availableSalesForShop.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.EventBusy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = "No sales records found for ${selectedShop!!.storeName} in Sales Master.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // Monthly Sales Calendar
                            ShopSalesMonthlyCalendar(
                                sales = availableSalesForShop,
                                selectedDateKey = selectedSaleDateKey,
                                onDateSelected = { dateKey, _ ->
                                    onSelectSaleDate(dateKey)
                                }
                            )
                        }
                    }

                    // Step 3: Verified Sales on Selected Date & Payment Status
                    if (selectedShop != null && selectedSaleDateKey != null) {
                        val salesOnSelectedDate = salesByDay[selectedSaleDateKey] ?: emptyList()
                        val formattedSelectedDay = try {
                            val parsedDate = sdfDayKey.parse(selectedSaleDateKey!!)
                            if (parsedDate != null) SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(parsedDate)
                            else selectedSaleDateKey!!
                        } catch (_: Exception) {
                            selectedSaleDateKey!!
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "3. Verified Sales on $formattedSelectedDay",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${salesOnSelectedDate.size} product sale${if (salesOnSelectedDate.size != 1) "s" else ""} found",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (salesOnSelectedDate.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = {
                                                val validIds = salesOnSelectedDate
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

                            if (salesOnSelectedDate.isEmpty()) {
                                Text(
                                    text = "No sales recorded on this date.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    salesOnSelectedDate.forEach { sale ->
                                        val isAlreadyInvoicedElsewhere = sale.id in alreadyInvoicedSalesIds && (!isEditMode || sale.id !in (invoiceToEdit?.salesEntryIds ?: emptyList()))
                                        val isChecked = sale.id in selectedSalesIds
                                        val rate = sale.customSellingPrice ?: sale.ratePerPacket

                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                else if (isAlreadyInvoicedElsewhere) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                            ),
                                            border = if (isChecked) CardDefaults.outlinedCardBorder() else null,
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
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
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
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(2.dp))

                                                    Text(
                                                        text = "Packets: ${sale.packetsSold} sold @ ₹${"%.2f".format(rate)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )

                                                    Spacer(modifier = Modifier.height(4.dp))

                                                    // Payment Status of this Sale Entry
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        SaleEntryStatusChip(sale = sale)

                                                        if (isAlreadyInvoicedElsewhere) {
                                                            Text(
                                                                text = "Already in another invoice",
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
                        }
                    }

                    // Step 4: Amount Summary & Invoice Generation
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "4. Payment Summary",
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
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Total Sale Amount:", style = MaterialTheme.typography.bodyMedium)
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
                                    Text("Invoice Status:", style = MaterialTheme.typography.bodyMedium)
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
                            placeholder = { Text("Add payment mode, reference or remarks...") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("invoice_notes_input"),
                            maxLines = 2
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

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
                                errorMessage = "Please select at least one sales record from the verified date"
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
                            Text(if (isEditMode) "Update Invoice" else "Generate Invoice")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleEntryStatusChip(sale: SalesEntry) {
    val (bg, textColor, label) = when (sale.status) {
        "Paid" -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            "Paid (₹${"%.2f".format(sale.actualPaidAmount)})"
        )
        "Partially Paid" -> Triple(
            Color(0xFFFFF3E0),
            Color(0xFFE65100),
            "Partial (Paid: ₹${"%.2f".format(sale.actualPaidAmount)} • Due: ₹${"%.2f".format(sale.pendingBalanceAmount)})"
        )
        else -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFC62828),
            "Pending Due (₹${"%.2f".format(sale.totalAmount)})"
        )
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(textColor, CircleShape)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun ShopSalesMonthlyCalendar(
    sales: List<SalesEntry>,
    selectedDateKey: String?,
    onDateSelected: (dateKey: String, dateMillis: Long) -> Unit
) {
    val sdfKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val monthTitleFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val chipDateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    // Group sales by yyyy-MM-dd
    val salesByDay = remember(sales) {
        sales.groupBy { sdfKey.format(Date(it.entryDate)) }
    }
    val uniqueDates = remember(salesByDay) {
        salesByDay.keys.sortedDescending()
    }

    // Default viewed month & year
    val initialCal = remember(selectedDateKey, uniqueDates) {
        val cal = Calendar.getInstance()
        val targetKey = selectedDateKey ?: uniqueDates.firstOrNull()
        if (targetKey != null) {
            try {
                val parsed = sdfKey.parse(targetKey)
                if (parsed != null) {
                    cal.time = parsed
                }
            } catch (_: Exception) {}
        }
        cal
    }

    var viewedYear by remember(selectedDateKey) { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
    var viewedMonth by remember(selectedDateKey) { mutableIntStateOf(initialCal.get(Calendar.MONTH)) }

    val viewedCal = remember(viewedYear, viewedMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, viewedYear)
            set(Calendar.MONTH, viewedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val daysInMonth = viewedCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = viewedCal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday
    val prevMonthPadding = firstDayOfWeek - Calendar.SUNDAY

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Quick Date Chips
            if (uniqueDates.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Quick Select Sale Dates (${uniqueDates.size} dates with sales):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uniqueDates) { dateKey ->
                            val isSelected = dateKey == selectedDateKey
                            val itemsOnDate = salesByDay[dateKey] ?: emptyList()
                            val totalAmountOnDate = itemsOnDate.sumOf { it.totalAmount }
                            val chipLabel = try {
                                val d = sdfKey.parse(dateKey)
                                if (d != null) chipDateFmt.format(d) else dateKey
                            } catch (_: Exception) {
                                dateKey
                            }

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    try {
                                        val d = sdfKey.parse(dateKey)
                                        if (d != null) {
                                            val c = Calendar.getInstance().apply { time = d }
                                            viewedYear = c.get(Calendar.YEAR)
                                            viewedMonth = c.get(Calendar.MONTH)
                                            onDateSelected(dateKey, d.time)
                                        }
                                    } catch (_: Exception) {
                                        onDateSelected(dateKey, System.currentTimeMillis())
                                    }
                                },
                                label = {
                                    Text(
                                        text = "$chipLabel (${itemsOnDate.size} • ₹${"%.0f".format(totalAmountOnDate)})",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            // Month Navigation Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (viewedMonth == 0) {
                            viewedMonth = 11
                            viewedYear -= 1
                        } else {
                            viewedMonth -= 1
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                }

                Text(
                    text = monthTitleFmt.format(viewedCal.time),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                IconButton(
                    onClick = {
                        if (viewedMonth == 11) {
                            viewedMonth = 0
                            viewedYear += 1
                        } else {
                            viewedMonth += 1
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                }
            }

            // Day of Week Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Calendar Days Grid (Rows of 7 days)
            val totalSlots = prevMonthPadding + daysInMonth
            val totalRows = (totalSlots + 6) / 7

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (row in 0 until totalRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        for (col in 0 until 7) {
                            val slotIndex = row * 7 + col
                            val dayNumber = slotIndex - prevMonthPadding + 1

                            if (dayNumber in 1..daysInMonth) {
                                val dayKey = String.format(Locale.US, "%04d-%02d-%02d", viewedYear, viewedMonth + 1, dayNumber)
                                val salesOnThisDay = salesByDay[dayKey] ?: emptyList()
                                val hasSales = salesOnThisDay.isNotEmpty()
                                val isSelected = dayKey == selectedDateKey

                                val dayCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, viewedYear)
                                    set(Calendar.MONTH, viewedMonth)
                                    set(Calendar.DAY_OF_MONTH, dayNumber)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                hasSales -> MaterialTheme.colorScheme.primaryContainer
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (hasSales && !isSelected) 1.dp else 0.dp,
                                            color = if (hasSales && !isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            onDateSelected(dayKey, dayCal.timeInMillis)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$dayNumber",
                                            fontSize = 12.sp,
                                            fontWeight = if (hasSales || isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                hasSales -> MaterialTheme.colorScheme.onPrimaryContainer
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        if (hasSales) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                        CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Empty padding slot
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }

            // Legend / Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(2.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    )
                    Text("Days with sales", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (selectedDateKey != null) {
                    val count = (salesByDay[selectedDateKey] ?: emptyList()).size
                    Text(
                        text = "Selected: $count sale item${if (count != 1) "s" else ""}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceDetailDialog(
    invoice: PaymentInvoice,
    allSales: List<SalesEntry>,
    businessProfile: AppViewModel.BusinessProfile,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onEditBusinessProfile: () -> Unit
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
                .fillMaxHeight(0.92f)
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Company & Branding Section
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (businessProfile.brandName.isNotBlank()) businessProfile.brandName.uppercase() else "SNACKROUTE",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = onEditBusinessProfile,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Business Details",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (businessProfile.companyName.isNotBlank()) {
                                Text(
                                    text = businessProfile.companyName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (businessProfile.address.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = businessProfile.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (businessProfile.phoneNumber.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Phone: ${businessProfile.phoneNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (businessProfile.fssaiNumber.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "FSSAI Verified",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "FSSAI Lic. No: ${businessProfile.fssaiNumber}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

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
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share PDF Invoice")
                    }
                }
            }
        }
    }
}

@Composable
fun BusinessProfileHeaderCard(
    profile: AppViewModel.BusinessProfile,
    onEditClick: () -> Unit
) {
    val isConfigured = profile.companyName.isNotBlank() || profile.brandName.isNotBlank() || profile.fssaiNumber.isNotBlank()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfigured) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isConfigured) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("business_profile_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Business / Company Profile",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                FilledTonalButton(
                    onClick = onEditClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("btn_edit_profile_header")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isConfigured) "Edit" else "Setup", fontSize = 12.sp)
                }
            }

            if (!isConfigured) {
                Text(
                    text = "Add your Company Name, Brand, Address, Phone & FSSAI License Number. These will automatically appear on all payment invoices, shareable slips, and unified Excel backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (profile.brandName.isNotBlank()) {
                    Text(
                        text = profile.brandName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                if (profile.companyName.isNotBlank()) {
                    Text(
                        text = profile.companyName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (profile.address.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = profile.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (profile.phoneNumber.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = profile.phoneNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (profile.fssaiNumber.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "FSSAI Lic No: ${profile.fssaiNumber}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BusinessProfileDialog(
    currentProfile: AppViewModel.BusinessProfile,
    onDismiss: () -> Unit,
    onSave: (companyName: String, brandName: String, address: String, phone: String, fssai: String) -> Unit
) {
    var companyName by remember(currentProfile) { mutableStateOf(currentProfile.companyName) }
    var brandName by remember(currentProfile) { mutableStateOf(currentProfile.brandName) }
    var address by remember(currentProfile) { mutableStateOf(currentProfile.address) }
    var phoneNumber by remember(currentProfile) { mutableStateOf(currentProfile.phoneNumber) }
    var fssaiNumber by remember(currentProfile) { mutableStateOf(currentProfile.fssaiNumber) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("business_profile_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Company & Brand Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "Enter your details once. They will automatically be included on all payment invoices, shareable receipts, and unified Excel backups (import & export).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Brand Name
                OutlinedTextField(
                    value = brandName,
                    onValueChange = { brandName = it },
                    label = { Text("Brand Name") },
                    placeholder = { Text("e.g. CrispyKing / SnackRoute") },
                    leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_brand_name"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Company Name
                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = { Text("Company / Firm Name") },
                    placeholder = { Text("e.g. Sri Lakshmi Foods Pvt Ltd") },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_company_name"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Phone Number
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("e.g. +91 9876543210") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_company_phone"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Address
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    placeholder = { Text("e.g. 12/4 Market Road, Salem, Tamil Nadu - 636001") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_company_address"),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )

                // FSSAI License Number
                OutlinedTextField(
                    value = fssaiNumber,
                    onValueChange = { fssaiNumber = it },
                    label = { Text("FSSAI License Number") },
                    placeholder = { Text("e.g. 12423004000123") },
                    leadingIcon = { Icon(Icons.Default.Verified, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_fssai_license"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                )

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
                            onSave(companyName, brandName, address, phoneNumber, fssaiNumber)
                        },
                        modifier = Modifier.testTag("btn_save_business_profile")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Details")
                    }
                }
            }
        }
    }
}

@Composable
fun SearchableShopPickerDialog(
    shops: List<ShopMaster>,
    selectedShopNumber: String?,
    onShopSelected: (ShopMaster) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Select Shop", fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search shop name, ID or route...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("dialog_search_shop_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                val filteredShops = remember(shops, searchQuery) {
                    if (searchQuery.isBlank()) {
                        shops
                    } else {
                        val q = searchQuery.trim().lowercase(Locale.getDefault())
                        shops.filter { s ->
                            s.storeName.lowercase(Locale.getDefault()).contains(q) ||
                            s.shopNumber.lowercase(Locale.getDefault()).contains(q) ||
                            s.locationNumber.lowercase(Locale.getDefault()).contains(q)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "Showing ${filteredShops.size} matching shop${if (filteredShops.size != 1) "s" else ""}"
                        } else {
                            "Total ${shops.size} shops (type to search)"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (filteredShops.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                "No shops match \"$searchQuery\"",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredShops, key = { it.shopNumber }) { shop ->
                            val isSelected = shop.shopNumber == selectedShopNumber
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onShopSelected(shop) }
                                    .testTag("shop_picker_item_${shop.shopNumber}"),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = if (isSelected) CardDefaults.outlinedCardBorder() else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Storefront,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = shop.storeName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "ID: ${shop.shopNumber}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "•",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Route / Loc: ${shop.locationNumber}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

