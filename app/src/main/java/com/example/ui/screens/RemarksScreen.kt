package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ShopRemark
import com.example.data.RemarkHistoryItem
import com.example.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemarksScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val allRemarks by viewModel.allRemarks.collectAsStateWithLifecycle()
    val shops by viewModel.shops.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedShopFilter by remember { mutableStateOf<String?>(null) }
    var selectedLocationFilter by remember { mutableStateOf<String?>(null) }
    var selectedStatusFilter by remember { mutableStateOf<String?>(null) }

    // Dialogue State for Add/Edit/Reply
    var isAddRemarkDialogOpen by remember { mutableStateOf(false) }
    var isEditRemarkDialogOpen by remember { mutableStateOf(false) }
    var isReplyDialogOpen by remember { mutableStateOf(false) }
    var selectedRemarkForAction by remember { mutableStateOf<ShopRemark?>(null) }

    // Dropdown States for filters
    var isShopDropdownExpanded by remember { mutableStateOf(false) }
    var isLocDropdownExpanded by remember { mutableStateOf(false) }
    var isStatusDropdownExpanded by remember { mutableStateOf(false) }

    // Form states for Add general remark
    var newRemarkShopNo by remember { mutableStateOf("") }
    var newRemarkText by remember { mutableStateOf("") }
    var isFormShopDropdownExpanded by remember { mutableStateOf(false) }

    // Form states for Edit remark
    var editRemarkText by remember { mutableStateOf("") }

    // Form states for Reply/Follow-up
    var replyText by remember { mutableStateOf("") }
    var replyType by remember { mutableStateOf("Reply") } // "Reply" or "Follow-up"

    // Filtered and searched remarks
    val filteredRemarks = remember(allRemarks, searchQuery, selectedShopFilter, selectedLocationFilter, selectedStatusFilter) {
        allRemarks.filter { remark ->
            val matchesSearch = searchQuery.isEmpty() ||
                    remark.remark.contains(searchQuery, ignoreCase = true) ||
                    remark.shopName.contains(searchQuery, ignoreCase = true) ||
                    remark.shopNumber.contains(searchQuery, ignoreCase = true) ||
                    remark.locationNumber.contains(searchQuery, ignoreCase = true)

            val matchesShop = selectedShopFilter == null || remark.shopNumber == selectedShopFilter
            val matchesLocation = selectedLocationFilter == null || remark.locationNumber == selectedLocationFilter
            val matchesStatus = selectedStatusFilter == null || remark.status == selectedStatusFilter

            matchesSearch && matchesShop && matchesLocation && matchesStatus
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Store Remarks Manager", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("remarks_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            selectedShopFilter = null
                            selectedLocationFilter = null
                            selectedStatusFilter = null
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Filters")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newRemarkShopNo = ""
                    newRemarkText = ""
                    isAddRemarkDialogOpen = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_remark_fab")
            ) {
                Icon(Icons.Default.AddComment, contentDescription = "Add General Remark")
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
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search remarks, shops, locations...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remarks_search_bar"),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                }
            )

            // Filtering Rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Shop Filter
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { isShopDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth().testTag("filter_shop_button"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (selectedShopFilter == null) "Shop" else shops.find { it.shopNumber == selectedShopFilter }?.storeName ?: "Selected",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = isShopDropdownExpanded,
                        onDismissRequest = { isShopDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Shops") },
                            onClick = {
                                selectedShopFilter = null
                                isShopDropdownExpanded = false
                            }
                        )
                        shops.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.storeName) },
                                onClick = {
                                    selectedShopFilter = s.shopNumber
                                    isShopDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Location Filter
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { isLocDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth().testTag("filter_loc_button"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (selectedLocationFilter == null) "Location" else locations.find { it.locationNumber == selectedLocationFilter }?.locationName ?: "Selected",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = isLocDropdownExpanded,
                        onDismissRequest = { isLocDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Locations") },
                            onClick = {
                                selectedLocationFilter = null
                                isLocDropdownExpanded = false
                            }
                        )
                        locations.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l.locationName) },
                                onClick = {
                                    selectedLocationFilter = l.locationNumber
                                    isLocDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Status Filter
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { isStatusDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth().testTag("filter_status_button"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = selectedStatusFilter ?: "Status",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = isStatusDropdownExpanded,
                        onDismissRequest = { isStatusDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Statuses") },
                            onClick = {
                                selectedStatusFilter = null
                                isStatusDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Pending") },
                            onClick = {
                                selectedStatusFilter = "Pending"
                                isStatusDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Done") },
                            onClick = {
                                selectedStatusFilter = "Done"
                                isStatusDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            if (filteredRemarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No remarks found matching your criteria.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredRemarks, key = { it.id }) { remark ->
                        var isExpanded by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                                .border(
                                    1.dp,
                                    if (remark.status == "Pending") MaterialTheme.colorScheme.error.copy(alpha = 0.3f) 
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (remark.status == "Pending") MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.05f)
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Header: Shop details and status badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            remark.shopName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "${remark.locationNumber} • ID: ${remark.shopNumber}",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    // Status Toggle chip
                                    SuggestionChip(
                                        onClick = {
                                            val nextStatus = if (remark.status == "Pending") "Done" else "Pending"
                                            viewModel.updateRemarkStatus(remark, nextStatus)
                                        },
                                        label = {
                                            Text(
                                                remark.status,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (remark.status == "Pending") MaterialTheme.colorScheme.errorContainer 
                                            else MaterialTheme.colorScheme.primaryContainer,
                                            labelColor = if (remark.status == "Pending") MaterialTheme.colorScheme.onErrorContainer 
                                            else MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.testTag("status_chip_${remark.id}")
                                    )
                                }

                                // Remark content
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Comment,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp).offset(y = 2.dp)
                                    )
                                    Column {
                                        Text(
                                            text = remark.remark,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = remark.dateFormatted,
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                // History expander / indicators
                                if (remark.history.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isExpanded = !isExpanded }
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${remark.history.size} replies / follow-up updates",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "View Replies",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp)
                                                .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                .background(Color.Gray.copy(alpha = 0.03f))
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            remark.history.forEach { historyItem ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Icon(
                                                        imageVector = if (historyItem.type == "Follow-up") Icons.Default.Update else Icons.Default.Reply,
                                                        contentDescription = null,
                                                        tint = if (historyItem.type == "Follow-up") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.size(14.dp).offset(y = 2.dp)
                                                    )
                                                    Column {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = historyItem.type,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp,
                                                                color = if (historyItem.type == "Follow-up") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                                                            )
                                                            Text(
                                                                text = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(historyItem.date)),
                                                                fontSize = 9.sp,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                        Text(
                                                            text = historyItem.note,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Divider(color = Color.Gray.copy(alpha = 0.15f))

                                // Action Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Reply / Follow-up button
                                    OutlinedButton(
                                        onClick = {
                                            selectedRemarkForAction = remark
                                            replyText = ""
                                            replyType = "Reply"
                                            isReplyDialogOpen = true
                                        },
                                        modifier = Modifier.weight(1f).testTag("btn_reply_${remark.id}"),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reply/Follow-up", fontSize = 11.sp)
                                    }

                                    // Edit Button
                                    IconButton(
                                        onClick = {
                                            selectedRemarkForAction = remark
                                            editRemarkText = remark.remark
                                            isEditRemarkDialogOpen = true
                                        },
                                        modifier = Modifier.testTag("btn_edit_${remark.id}").size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Remark", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }

                                    // Delete Button
                                    IconButton(
                                        onClick = { viewModel.deleteRemark(remark) },
                                        modifier = Modifier.testTag("btn_delete_${remark.id}").size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Remark", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. Add General Remark Dialog
    if (isAddRemarkDialogOpen) {
        AlertDialog(
            onDismissRequest = { isAddRemarkDialogOpen = false },
            title = { Text("Add General Store Remark", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Select Shop:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (newRemarkShopNo.isEmpty()) "Tap to select shop" else shops.find { it.shopNumber == newRemarkShopNo }?.storeName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().clickable { isFormShopDropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = isFormShopDropdownExpanded,
                            onDismissRequest = { isFormShopDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            shops.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.storeName) },
                                    onClick = {
                                        newRemarkShopNo = s.shopNumber
                                        isFormShopDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newRemarkText,
                        onValueChange = { newRemarkText = it },
                        label = { Text("Enter remark message") },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shop = shops.find { it.shopNumber == newRemarkShopNo }
                        if (shop != null && newRemarkText.isNotEmpty()) {
                            viewModel.addRemark(
                                shopNumber = shop.shopNumber,
                                shopName = shop.storeName,
                                locationNumber = shop.locationNumber,
                                remarkText = newRemarkText
                            )
                            isAddRemarkDialogOpen = false
                        }
                    },
                    enabled = newRemarkShopNo.isNotEmpty() && newRemarkText.isNotEmpty()
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddRemarkDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Edit Remark Dialog
    if (isEditRemarkDialogOpen && selectedRemarkForAction != null) {
        AlertDialog(
            onDismissRequest = { isEditRemarkDialogOpen = false },
            title = { Text("Edit Store Remark", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editRemarkText,
                    onValueChange = { editRemarkText = it },
                    label = { Text("Remark content") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedRemarkForAction?.let {
                            if (editRemarkText.isNotEmpty()) {
                                viewModel.editRemarkText(it, editRemarkText)
                                isEditRemarkDialogOpen = false
                            }
                        }
                    },
                    enabled = editRemarkText.isNotEmpty()
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditRemarkDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Reply / Follow-up Dialog
    if (isReplyDialogOpen && selectedRemarkForAction != null) {
        AlertDialog(
            onDismissRequest = { isReplyDialogOpen = false },
            title = { Text("Add Update to Remark", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = replyType == "Reply",
                                onClick = { replyType = "Reply" }
                            )
                            Text("Reply", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = replyType == "Follow-up",
                                onClick = { replyType = "Follow-up" }
                            )
                            Text("Follow-up note", fontSize = 13.sp)
                        }
                    }

                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("Update message content") },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedRemarkForAction?.let {
                            if (replyText.isNotEmpty()) {
                                viewModel.addRemarkReply(it, replyText, replyType)
                                isReplyDialogOpen = false
                            }
                        }
                    },
                    enabled = replyText.isNotEmpty()
                ) {
                    Text("Submit Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { isReplyDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
