package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.LocationMaster
import com.example.ui.AppViewModel
import com.example.utils.Exporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    viewModel: AppViewModel,
    onNavigateToTab: (String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenTimetable: () -> Unit
) {
    val context = LocalContext.current
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importLocationsFromExcel(context, uri)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Number") } // Number, Name
    var sortAscending by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedLocationForEdit by remember { mutableStateOf<LocationMaster?>(null) }

    // Dialog state fields
    var locNumField by remember { mutableStateOf("") }
    var locNameField by remember { mutableStateOf("") }
    var locNumError by remember { mutableStateOf<String?>(null) }
    var locNameError by remember { mutableStateOf<String?>(null) }

    // --- Search & Filtering Logic ---
    LaunchedEffect(searchQuery, sortBy, sortAscending) {
        listState.scrollToItem(0)
    }

    val filteredLocations = remember(locations, searchQuery, sortBy, sortAscending) {
        var list = locations.filter {
            it.locationNumber.contains(searchQuery, ignoreCase = true) ||
                    it.locationName.contains(searchQuery, ignoreCase = true)
        }
        list = if (sortBy == "Number") {
            list.sortedBy { it.locationNumber }
        } else {
            list.sortedBy { it.locationName }
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location Master", fontWeight = FontWeight.Bold) },
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
                    IconButton(
                        onClick = { importLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") },
                        modifier = Modifier.testTag("import_locations_button")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Import Excel")
                    }
                    IconButton(
                        onClick = { Exporter.exportLocations(context, locations) },
                        modifier = Modifier.testTag("export_locations_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export Excel")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedLocationForEdit = null
                    locNumField = ""
                    locNameField = ""
                    locNumError = null
                    locNameError = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_location_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Location")
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
            // --- Search Bar & Sort ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by Number or Name...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("location_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // --- Sort Pills ---
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Sort by:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                FilterChip(
                    selected = sortBy == "Number",
                    onClick = { sortBy = "Number" },
                    label = { Text("Location Code") }
                )
                FilterChip(
                    selected = sortBy == "Name",
                    onClick = { sortBy = "Name" },
                    label = { Text("Location Name") }
                )
            }

            // --- List of Locations ---
            if (filteredLocations.isEmpty()) {
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
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (searchQuery.isEmpty()) "No Locations Available" else "No matching results found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Tap + to add a location number and name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredLocations, key = { it.locationNumber }) { loc ->
                        LocationCard(
                            location = loc,
                            onEdit = {
                                selectedLocationForEdit = loc
                                locNumField = loc.locationNumber
                                locNameField = loc.locationName
                                locNumError = null
                                locNameError = null
                                showAddEditDialog = true
                            },
                            onDelete = {
                                viewModel.deleteLocation(loc)
                                Toast.makeText(context, "Location deleted", Toast.LENGTH_SHORT).show()
                            },
                            onViewShops = {
                                viewModel.setShopLocationFilter(loc.locationNumber)
                                onNavigateToTab("Shops")
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Add / Edit Dialog ---
    if (showAddEditDialog) {
        val isEdit = selectedLocationForEdit != null
        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = { Text(if (isEdit) "Edit Location" else "Add New Location", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = locNumField,
                        onValueChange = {
                            locNumField = it.trim()
                            locNumError = null
                        },
                        label = { Text("Location Number / Code") },
                        placeholder = { Text("e.g. LOC001") },
                        isError = locNumError != null,
                        supportingText = locNumError?.let { { Text(it) } },
                        enabled = !isEdit, // Cannot edit Location Number directly as it is PK
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = locNameField,
                        onValueChange = {
                            locNameField = it
                            locNameError = null
                        },
                        label = { Text("Location Name") },
                        placeholder = { Text("e.g. MG Road, Bengaluru") },
                        isError = locNameError != null,
                        supportingText = locNameError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        var isValid = true
                        if (locNumField.isEmpty()) {
                            locNumError = "Location Number is required"
                            isValid = false
                        } else if (!isEdit) {
                            // Check duplicate PK
                            val exists = locations.any { it.locationNumber.equals(locNumField, ignoreCase = true) }
                            if (exists) {
                                locNumError = "This Location Number already exists"
                                isValid = false
                            }
                        }

                        if (locNameField.trim().isEmpty()) {
                            locNameError = "Location Name is required"
                            isValid = false
                        }

                        if (isValid) {
                            val location = LocationMaster(
                                locationNumber = locNumField,
                                locationName = locNameField.trim()
                            )
                            if (isEdit) {
                                viewModel.updateLocation(location)
                                Toast.makeText(context, "Location updated successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addLocation(location)
                                Toast.makeText(context, "Location added successfully", Toast.LENGTH_SHORT).show()
                            }
                            showAddEditDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_location_button")
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
            title = { Text("Importing Locations") },
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
    if (importSummary != null && importSummary!!.type == com.example.utils.Exporter.ImportType.LOCATIONS) {
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
                                Exporter.shareFile(context, summary.errorReportFile, "Location Import Error Report")
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
fun LocationCard(
    location: LocationMaster,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewShops: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text(
                            text = location.locationName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Code: ${location.locationNumber}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row {
                    IconButton(onClick = onViewShops) {
                        Icon(Icons.Default.Storefront, contentDescription = "View Shops", tint = MaterialTheme.colorScheme.tertiary)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
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
                        text = "Are you sure you want to delete this location? It may affect stores linked to it.",
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
