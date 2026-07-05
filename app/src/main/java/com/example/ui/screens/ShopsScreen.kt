package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.LocationMaster
import com.example.data.ShopMaster
import com.example.ui.AppViewModel
import com.example.utils.Exporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val shops by viewModel.shops.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val nextShopNum by viewModel.nextShopNumber.collectAsStateWithLifecycle()

    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()

    val excelImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importShopsFromExcel(context, uri)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedLocationFilter by remember { mutableStateOf<String?>(null) }
    var sortBy by remember { mutableStateOf("Name") } // Name, Number, Rating, Date

    var showAddEditScreen by remember { mutableStateOf(false) }
    var selectedShopForEdit by remember { mutableStateOf<ShopMaster?>(null) }
    var selectedShopForDetail by remember { mutableStateOf<ShopMaster?>(null) }

    // Form fields
    var storeName by remember { mutableStateOf("") }
    var selectedLocationCode by remember { mutableStateOf("") }
    var storeImageUri by remember { mutableStateOf<String?>(null) }
    var rating by remember { mutableStateOf(5f) }
    var startingDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var googleMapLink by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Form validation states
    var storeNameError by remember { mutableStateOf<String?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var mobileError by remember { mutableStateOf<String?>(null) }

    // Image selector launcher
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = viewModel.saveImageToStorage(uri)
            if (savedPath != null) {
                storeImageUri = savedPath
            } else {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Search & Filtering ---
    val filteredShops = remember(shops, searchQuery, selectedLocationFilter, sortBy) {
        var list = shops.filter { shop ->
            val matchSearch = shop.storeName.contains(searchQuery, ignoreCase = true) ||
                    shop.shopNumber.contains(searchQuery, ignoreCase = true) ||
                    shop.locationNumber.contains(searchQuery, ignoreCase = true) ||
                    (shop.mobileNumber ?: "").contains(searchQuery)
            
            val matchFilter = selectedLocationFilter == null || shop.locationNumber == selectedLocationFilter
            matchSearch && matchFilter
        }

        list = when (sortBy) {
            "Number" -> list.sortedBy { it.shopNumber }
            "Rating" -> list.sortedByDescending { it.rating }
            "Date" -> list.sortedByDescending { it.startingDate }
            else -> list.sortedBy { it.storeName }
        }
        list
    }

    if (!showAddEditScreen) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Shop Master", fontWeight = FontWeight.Bold) },
                    actions = {
                        // Import Excel Button
                        IconButton(
                            onClick = {
                                excelImportLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            },
                            modifier = Modifier.testTag("import_shops_button")
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = "Import Excel")
                        }
                        
                        // Export Excel Button
                        IconButton(
                            onClick = { Exporter.exportShops(context, shops) },
                            modifier = Modifier.testTag("export_shops_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Export Excel")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        if (locations.isEmpty()) {
                            Toast.makeText(context, "Please create at least one location first!", Toast.LENGTH_LONG).show()
                        } else {
                            selectedShopForEdit = null
                            storeName = ""
                            selectedLocationCode = locations.first().locationNumber
                            storeImageUri = null
                            rating = 5f
                            startingDateMillis = System.currentTimeMillis()
                            googleMapLink = ""
                            mobileNumber = ""
                            notes = ""
                            storeNameError = null
                            locationError = null
                            mobileError = null
                            showAddEditScreen = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_shop_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Shop")
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
                    placeholder = { Text("Search by Shop Name, ID, Mobile...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shop_search_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // --- Filters (Locations Selector & Sorting) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Location Filter dropdown
                    var filterExpanded by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = selectedLocationFilter != null,
                            onClick = { filterExpanded = true },
                            label = { Text(selectedLocationFilter ?: "All Locations") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                        )
                        DropdownMenu(
                            expanded = filterExpanded,
                            onDismissRequest = { filterExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Locations") },
                                onClick = {
                                    selectedLocationFilter = null
                                    filterExpanded = false
                                }
                            )
                            locations.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc.locationName) },
                                    onClick = {
                                        selectedLocationFilter = loc.locationNumber
                                        filterExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Sort menu
                    var sortExpanded by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { sortExpanded = true },
                            label = { Text("Sort: $sortBy") },
                            trailingIcon = { Icon(Icons.Default.Sort, contentDescription = null) }
                        )
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false }
                        ) {
                            DropdownMenuItem(text = { Text("Name") }, onClick = { sortBy = "Name"; sortExpanded = false })
                            DropdownMenuItem(text = { Text("Shop Number") }, onClick = { sortBy = "Number"; sortExpanded = false })
                            DropdownMenuItem(text = { Text("Rating") }, onClick = { sortBy = "Rating"; sortExpanded = false })
                            DropdownMenuItem(text = { Text("Date Added") }, onClick = { sortBy = "Date"; sortExpanded = false })
                        }
                    }
                }

                // --- Shops List ---
                if (filteredShops.isEmpty()) {
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
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = if (searchQuery.isEmpty() && selectedLocationFilter == null) "No Shops Logged" else "No matching stores found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredShops, key = { it.shopNumber }) { shop ->
                            val locName = locations.firstOrNull { it.locationNumber == shop.locationNumber }?.locationName ?: shop.locationNumber
                            ShopCard(
                                shop = shop,
                                locationName = locName,
                                onClick = { selectedShopForDetail = shop },
                                onEdit = {
                                    selectedShopForEdit = shop
                                    storeName = shop.storeName
                                    selectedLocationCode = shop.locationNumber
                                    storeImageUri = shop.storeImage
                                    rating = shop.rating
                                    startingDateMillis = shop.startingDate
                                    googleMapLink = shop.googleMapLink ?: ""
                                    mobileNumber = shop.mobileNumber ?: ""
                                    notes = shop.notes ?: ""
                                    storeNameError = null
                                    locationError = null
                                    mobileError = null
                                    showAddEditScreen = true
                                },
                                onDelete = {
                                    viewModel.deleteShop(shop)
                                    Toast.makeText(context, "Shop deleted", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // --- Add / Edit Form Screen ---
        val isEdit = selectedShopForEdit != null
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEdit) "Edit Store Master" else "Add New Store", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { showAddEditScreen = false }) {
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
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        // Image Picker Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { imageLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!storeImageUri.isNullOrEmpty() && File(storeImageUri!!).exists()) {
                                Image(
                                    painter = rememberAsyncImagePainter(File(storeImageUri!!)),
                                    contentDescription = "Store Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Change Image", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("Add Store Image", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    item {
                        // Shop ID & Store Name
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = if (isEdit) selectedShopForEdit!!.shopNumber else nextShopNum,
                                onValueChange = {},
                                label = { Text("Shop Number") },
                                enabled = false,
                                modifier = Modifier.width(110.dp)
                            )
                            OutlinedTextField(
                                value = storeName,
                                onValueChange = { storeName = it; storeNameError = null },
                                label = { Text("Store Name*") },
                                placeholder = { Text("e.g. Balaji Sweets") },
                                isError = storeNameError != null,
                                supportingText = storeNameError?.let { { Text(it) } },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }

                    item {
                        // Location dropdown
                        var locationExpanded by remember { mutableStateOf(false) }
                        val activeLocName = locations.firstOrNull { it.locationNumber == selectedLocationCode }?.locationName ?: "Select Location"
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = activeLocName,
                                onValueChange = {},
                                label = { Text("Route Location Master*") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { locationExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { locationExpanded = true }
                            )
                            DropdownMenu(
                                expanded = locationExpanded,
                                onDismissRequest = { locationExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                locations.forEach { loc ->
                                    DropdownMenuItem(
                                        text = { Text("${loc.locationNumber} - ${loc.locationName}") },
                                        onClick = {
                                            selectedLocationCode = loc.locationNumber
                                            locationExpanded = false
                                            locationError = null
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        // Rating (1-5 stars)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Store Rating & Score", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    (1..5).forEach { starIndex ->
                                        Icon(
                                            imageVector = if (starIndex <= rating) Icons.Default.Star else Icons.Outlined.StarBorder,
                                            contentDescription = "Rate $starIndex",
                                            tint = if (starIndex <= rating) Color(0xFFFFD700) else Color.Gray,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clickable { rating = starIndex.toFloat() }
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Score: ${(rating * 20).toInt()}/100",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    item {
                        // Date picker starting date
                        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        OutlinedTextField(
                            value = dateFormat.format(Date(startingDateMillis)),
                            onValueChange = {},
                            label = { Text("Starting Date*") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val calendar = Calendar.getInstance().apply { timeInMillis = startingDateMillis }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            calendar.set(y, m, d)
                                            startingDateMillis = calendar.timeInMillis
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Select Date")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        // Mobile number
                        OutlinedTextField(
                            value = mobileNumber,
                            onValueChange = {
                                mobileNumber = it
                                mobileError = null
                            },
                            label = { Text("Mobile Number (Optional)") },
                            placeholder = { Text("e.g. 9876543210") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            isError = mobileError != null,
                            supportingText = mobileError?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    item {
                        // Google Map link
                        OutlinedTextField(
                            value = googleMapLink,
                            onValueChange = { googleMapLink = it },
                            label = { Text("Google Map Link (Optional)") },
                            placeholder = { Text("e.g. https://maps.app.goo.gl/...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    item {
                        // Notes
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Special Notes / Details") },
                            placeholder = { Text("e.g. Best visiting times, custom prices...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                }

                // Save buttons
                Button(
                    onClick = {
                        var isValid = true
                        if (storeName.trim().isEmpty()) {
                            storeNameError = "Store Name is required"
                            isValid = false
                        }
                        if (selectedLocationCode.isEmpty()) {
                            locationError = "Please select a location Route"
                            isValid = false
                        }
                        if (mobileNumber.isNotEmpty() && mobileNumber.length < 10) {
                            mobileError = "Invalid phone number (must be at least 10 digits)"
                            isValid = false
                        }

                        if (isValid) {
                            val scoreValue = (rating * 20).toInt()
                            val shop = ShopMaster(
                                shopNumber = if (isEdit) selectedShopForEdit!!.shopNumber else nextShopNum,
                                locationNumber = selectedLocationCode,
                                storeName = storeName.trim(),
                                storeImage = storeImageUri,
                                rating = rating,
                                score = scoreValue,
                                startingDate = startingDateMillis,
                                googleMapLink = googleMapLink.trim().ifEmpty { null },
                                mobileNumber = mobileNumber.trim().ifEmpty { null },
                                notes = notes.trim().ifEmpty { null }
                            )

                            if (isEdit) {
                                viewModel.updateShop(shop)
                                Toast.makeText(context, "Store Master updated", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addShop(shop)
                                Toast.makeText(context, "Store added successfully", Toast.LENGTH_SHORT).show()
                            }
                            showAddEditScreen = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_shop_button")
                ) {
                    Text("Save Store Record", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    // --- Detail Bottom Sheet / Dialog ---
    if (selectedShopForDetail != null) {
        val detail = selectedShopForDetail!!
        val locName = locations.firstOrNull { it.locationNumber == detail.locationNumber }?.locationName ?: detail.locationNumber
        
        AlertDialog(
            onDismissRequest = { selectedShopForDetail = null },
            title = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(detail.storeName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(detail.shopNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!detail.storeImage.isNullOrEmpty() && File(detail.storeImage).exists()) {
                        Image(
                            painter = rememberAsyncImagePainter(File(detail.storeImage)),
                            contentDescription = "Store Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }

                    DetailField(label = "Route Location", value = "$locName (${detail.locationNumber})")
                    DetailField(label = "Starting Date", value = detail.startingDateFormatted)
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rating:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row {
                            (1..5).forEach { star ->
                                Icon(
                                    imageVector = if (star <= detail.rating) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = null,
                                    tint = if (star <= detail.rating) Color(0xFFFFD700) else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (!detail.mobileNumber.isNullOrEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DetailField(label = "Mobile Number", value = detail.mobileNumber)
                            IconButton(
                                onClick = {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${detail.mobileNumber}"))
                                    context.startActivity(dialIntent)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call Store", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (!detail.googleMapLink.isNullOrEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Maps Navigation Link",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(detail.googleMapLink))
                                    context.startActivity(mapIntent)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Navigate", fontSize = 12.sp)
                            }
                        }
                    }

                    if (!detail.notes.isNullOrEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Notes:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                detail.notes,
                                fontSize = 12.sp,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedShopForDetail = null }) {
                    Text("Close")
                }
            }
        )
    }

    // --- Excel Import Progress Dialog ---
    if (isImporting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Importing Shops") },
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
                    
                    when (summary.type) {
                        com.example.utils.Exporter.ImportType.SHOPS -> {
                            SummaryRow(label = "• Duplicate Records:", value = "${summary.duplicateRecordsCount}")
                            SummaryRow(label = "• Invalid Location Numbers:", value = "${summary.invalidLocationNumbersCount}")
                            SummaryRow(label = "• Missing Images:", value = "${summary.missingImagesCount}")
                            SummaryRow(label = "• Failed / Invalid Rows:", value = "${summary.failedRowsCount}")
                        }
                        com.example.utils.Exporter.ImportType.LOCATIONS -> {
                            SummaryRow(label = "• Duplicate Records:", value = "${summary.duplicateRecordsCount}")
                            SummaryRow(label = "• Failed / Invalid Rows:", value = "${summary.failedRowsCount}")
                        }
                        com.example.utils.Exporter.ImportType.SALES -> {
                            SummaryRow(label = "• Invalid Shop Numbers:", value = "${summary.invalidShopNumbersCount}")
                            SummaryRow(label = "• Invalid Products:", value = "${summary.invalidProductsCount}")
                            SummaryRow(label = "• Failed / Invalid Rows:", value = "${summary.failedRowsCount}")
                        }
                    }
                    
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
                                val reportTitle = when (summary.type) {
                                    com.example.utils.Exporter.ImportType.SHOPS -> "Shop Import Error Report"
                                    com.example.utils.Exporter.ImportType.LOCATIONS -> "Location Import Error Report"
                                    com.example.utils.Exporter.ImportType.SALES -> "Sales Import Error Report"
                                }
                                Exporter.shareFile(context, summary.errorReportFile, reportTitle)
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

@Composable
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
fun DetailField(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ShopCard(
    shop: ShopMaster,
    locationName: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Image or Icon
                if (!shop.storeImage.isNullOrEmpty() && File(shop.storeImage).exists()) {
                    Image(
                        painter = rememberAsyncImagePainter(File(shop.storeImage)),
                        contentDescription = "Store image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Shop details
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = shop.storeName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "ID: ${shop.shopNumber} • Route: $locationName",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                        Text(
                            text = "${shop.rating} (${shop.score}/100)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Edit/Delete actions
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
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
                        text = "Delete store master record? This is irreversible and will delete historic logs.",
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
