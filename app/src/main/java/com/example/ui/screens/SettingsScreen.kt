package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import androidx.compose.foundation.text.selection.SelectionContainer
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.ui.text.style.TextAlign
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.io.File

private fun getAppSignatureSHA1(context: Context): String {
    try {
        val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        
        val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        
        if (signatures != null && signatures.isNotEmpty()) {
            val md = MessageDigest.getInstance("SHA-1")
            val publicKey = md.digest(signatures[0].toByteArray())
            val hexString = StringBuilder()
            for (i in publicKey.indices) {
                val appendString = java.lang.Integer.toHexString(0xFF and publicKey[i].toInt()).uppercase(Locale.US)
                if (appendString.length == 1) hexString.append("0")
                hexString.append(appendString)
                if (i < publicKey.size - 1) hexString.append(":")
            }
            return hexString.toString()
        }
    } catch (e: Exception) {
        android.util.Log.e("SignatureError", "Error getting app signature SHA1", e)
    }
    return "Error fetching signature"
}

private fun parseGoogleSignInError(e: Throwable, context: Context): String {
    val stackTraceString = android.util.Log.getStackTraceString(e)
    android.util.Log.e("GoogleSignInError", "Complete Google Sign-In Error details:\n$stackTraceString")
    
    val baseAppId = context.packageName
    val debugSha1 = getAppSignatureSHA1(context)
    val oauthClientId = com.example.BuildConfig.OAUTH_CLIENT_ID
    
    if (e is ApiException) {
        val statusCode = e.statusCode
        return when (statusCode) {
            10 -> { // CommonStatusCodes.DEVELOPER_ERROR
                "DEVELOPER_ERROR (10):\n" +
                "The application is misconfigured. This is most commonly caused by a mismatch between the SHA-1 fingerprint of the signing key and the package name registered in the Google Cloud Console / Firebase Console.\n\n" +
                "DURABLY VERIFIED RUNTIME METRICS:\n" +
                "• Your Actual Package Name: $baseAppId\n" +
                "• Your Actual SHA-1 Certificate: $debugSha1\n" +
                "• Your Configured Web Client ID: $oauthClientId\n\n" +
                "Make sure these match your Firebase Console/Google Cloud OAuth configuration exactly."
            }
            12500 -> { // GoogleSignInStatusCodes.SIGN_IN_FAILED
                "SIGN_IN_FAILED (12500):\n" +
                "Google Sign-In failed. Possible causes:\n" +
                "- Google Drive API is not enabled in your Google Cloud Console for this project.\n" +
                "- There is a network issue.\n" +
                "- The OAuth Web Client ID configuration is invalid.\n\n" +
                "DURABLY VERIFIED RUNTIME METRICS:\n" +
                "• Your Actual Package Name: $baseAppId\n" +
                "• Your Actual SHA-1 Certificate: $debugSha1"
            }
            12501 -> { // GoogleSignInStatusCodes.SIGN_IN_CANCELLED
                "SIGN_IN_CANCELLED (12501):\n" +
                "The sign-in flow was cancelled by the user."
            }
            12502 -> { // GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS
                "SIGN_IN_IN_PROGRESS (12502):\n" +
                "Sign-In is already in progress. Please wait."
            }
            7 -> { // CommonStatusCodes.NETWORK_ERROR
                "NETWORK_ERROR (7):\n" +
                "A network error occurred. Please check your internet connection."
            }
            5 -> { // CommonStatusCodes.INVALID_ACCOUNT
                "INVALID_ACCOUNT (5):\n" +
                "The Google Account selected is invalid."
            }
            8 -> { // CommonStatusCodes.INTERNAL_ERROR
                "INTERNAL_ERROR (8):\n" +
                "An internal Google Play Services error occurred."
            }
            else -> "Google Sign-In failed (Status Code $statusCode): ${e.localizedMessage}\n\nFull stack trace printed to Logcat."
        }
    }
    
    return "Google Sign-In failed: ${e.localizedMessage}\n\nType: ${e.javaClass.simpleName}\n\nFull stack trace printed to Logcat."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onOpenChat: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenCostEngine: () -> Unit,
    onOpenRemarks: () -> Unit = {}
) {
    val context = LocalContext.current
    val userApiKey by viewModel.userGeminiApiKey.collectAsState()
    var apiKeyInput by remember(userApiKey) { mutableStateOf(userApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }

    // --- Google Drive Sync States ---
    val googleAccountState by viewModel.googleSignInAccount.collectAsState()
    val syncStatusState by viewModel.syncStatus.collectAsState()
    val lastSyncedTimeState by viewModel.lastSyncedTime.collectAsState()
    val lastSyncErrorState by viewModel.lastSyncError.collectAsState()
    val isAutoSyncEnabledState by viewModel.isAutoSyncEnabled.collectAsState()
    val syncProgressState by viewModel.syncProgress.collectAsState()
    val syncProgressTextState by viewModel.syncProgressText.collectAsState()
    val isOfflineQueueActiveState by viewModel.isOfflineQueueActive.collectAsState()

    var showGDriveRestoreConfirm by remember { mutableStateOf(false) }
    var gdriveErrorDetails by remember { mutableStateOf<String?>(null) }

    val gso = remember {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
        
        val oauthClientId = com.example.BuildConfig.OAUTH_CLIENT_ID
        if (oauthClientId.isNotEmpty()) {
            builder.requestIdToken(oauthClientId)
        }
        builder.build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.setGoogleAccount(account)
            gdriveErrorDetails = null
            Toast.makeText(context, "Successfully connected to Google Drive!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            val parsedError = parseGoogleSignInError(e, context)
            gdriveErrorDetails = parsedError
            Toast.makeText(context, "Google Connection Failed (Details on Screen)", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Tools", fontWeight = FontWeight.Bold) },
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
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Theme Settings ---
            Text("General Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text("Dark Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Enforce eye-safe dark gray palette", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = onToggleDarkMode,
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }
            }

            // --- Dynamic Cost & Profit Engine Settings (Steps 17, 18, 20) ---
            val isDynamicProfitEnabled by viewModel.isDynamicProfitEnabled.collectAsState()
            Text("Cost & Profit Engine", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("cost_engine_settings_card"),
                shape = RoundedCornerShape(12.dp)
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text("Dynamic Cost Engine", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Calculate snack margins dynamically from raw ingredient purchases.", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Switch(
                            checked = isDynamicProfitEnabled,
                            onCheckedChange = { viewModel.setDynamicProfitEnabled(it) },
                            modifier = Modifier.testTag("dynamic_profit_settings_toggle")
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Configure ingredients, unit conversions, covers, stickers, cups, petrol, electricity, and custom recipe snapshots.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Button(
                            onClick = onOpenCostEngine,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("btn_settings_open_cost_engine")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Configure", fontSize = 12.sp)
                        }
                    }
                }
            }

            // --- Sales Reminder Settings ---
            val isReminderEnabled by viewModel.isReminderEnabled.collectAsState()
            val defaultReminderInterval by viewModel.defaultReminderInterval.collectAsState()
            val reminderTime by viewModel.reminderTime.collectAsState()
            val shops by viewModel.shops.collectAsState()
            var isShopsOverrideExpanded by remember { mutableStateOf(false) }
            var showTimePickerDialog by remember { mutableStateOf(false) }

            Text("Sales Reminder Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("sales_reminder_settings_card"),
                shape = RoundedCornerShape(12.dp)
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text("Enable Reminder Notifications", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Get notified when a shop is overdue for a visit", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Switch(
                            checked = isReminderEnabled,
                            onCheckedChange = { viewModel.updateReminderEnabled(it) },
                            modifier = Modifier.testTag("sales_reminder_toggle")
                        )
                    }

                    if (isReminderEnabled) {
                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                        Text("Default Reminder Interval", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        var intervalInput by remember(defaultReminderInterval) {
                            mutableStateOf(defaultReminderInterval.toString())
                        }
                        OutlinedTextField(
                            value = intervalInput,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                    intervalInput = newValue
                                    newValue.toIntOrNull()?.let {
                                        viewModel.updateDefaultReminderInterval(it)
                                    }
                                }
                            },
                            placeholder = { Text("7") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("default_reminder_interval_input"),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )

                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notification Time", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Time to trigger daily checklist generation", fontSize = 11.sp, color = Color.Gray)
                            }
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .clickable { showTimePickerDialog = true }
                            ) {
                                OutlinedTextField(
                                    value = reminderTime,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("reminder_time_field"),
                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }

                        if (showTimePickerDialog) {
                            val initialHour = reminderTime.substringBefore(":", "20").toIntOrNull() ?: 20
                            val initialMinute = reminderTime.substringAfter(":", "00").toIntOrNull() ?: 0
                            val timePickerState = rememberTimePickerState(
                                initialHour = initialHour,
                                initialMinute = initialMinute,
                                is24Hour = true
                            )
                            
                            AlertDialog(
                                onDismissRequest = { showTimePickerDialog = false },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val formattedTime = String.format(java.util.Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                                            viewModel.updateReminderTime(formattedTime)
                                            showTimePickerDialog = false
                                        },
                                        modifier = Modifier.testTag("time_picker_confirm")
                                    ) {
                                        Text("OK")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showTimePickerDialog = false },
                                        modifier = Modifier.testTag("time_picker_dismiss")
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                title = { Text("Select Notification Time") },
                                text = {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TimePicker(state = timePickerState)
                                    }
                                },
                                modifier = Modifier.testTag("time_picker_dialog")
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isShopsOverrideExpanded = !isShopsOverrideExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Custom Shop Intervals", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Set unique reminder overrides per shop", fontSize = 11.sp, color = Color.Gray)
                            }
                            Icon(
                                imageVector = if (isShopsOverrideExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expand Overrides",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        AnimatedVisibility(visible = isShopsOverrideExpanded) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var shopSearchQuery by remember { mutableStateOf("") }
                                var selectedShopForOverride by remember { mutableStateOf<com.example.data.ShopMaster?>(null) }

                                // Update selectedShopForOverride if database updates
                                LaunchedEffect(shops, selectedShopForOverride) {
                                    selectedShopForOverride?.let { selected ->
                                        val updated = shops.find { it.shopNumber == selected.shopNumber }
                                        if (updated != selected) {
                                            selectedShopForOverride = updated
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = shopSearchQuery,
                                    onValueChange = { shopSearchQuery = it },
                                    label = { Text("Search Shops...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (shopSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { shopSearchQuery = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("shop_override_search_bar")
                                )

                                if (shopSearchQuery.isNotEmpty()) {
                                    val matchingShops = remember(shopSearchQuery, shops) {
                                        shops.filter {
                                            it.storeName.contains(shopSearchQuery, ignoreCase = true) ||
                                            it.shopNumber.contains(shopSearchQuery, ignoreCase = true)
                                        }.take(10) // Limit to top 10 for performance
                                    }

                                    if (matchingShops.isEmpty()) {
                                        Text("No matching shops found", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                                    } else {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text("Search Results (Tap to edit):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                matchingShops.forEach { shop ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { 
                                                                selectedShopForOverride = shop 
                                                                shopSearchQuery = ""
                                                            }
                                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            Text(shop.storeName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                            val intervalText = shop.customReminderInterval?.let { "${it}d" } ?: "Default interval"
                                                            Text("ID: ${shop.shopNumber} • Interval: $intervalText", fontSize = 11.sp, color = Color.Gray)
                                                        }
                                                        Icon(Icons.Default.Edit, contentDescription = "Edit override", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Display custom interval editor for selected shop
                                selectedShopForOverride?.let { shop ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().testTag("custom_interval_editor_card"),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Shop Interval Editor", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                                IconButton(
                                                    onClick = { selectedShopForOverride = null },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Close editor", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            
                                            ShopOverrideRow(
                                                shop = shop,
                                                onUpdateInterval = { newInterval ->
                                                    viewModel.updateShop(shop.shopNumber, shop.copy(customReminderInterval = newInterval))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Store Remarks Card ---
            Text("Store Observations & Remarks", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("manage_remarks_settings_card"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Comment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Store Remarks & Observations", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("View, sort, filter, and reply to customer feedback, follow-up remarks, or observations from store visits.", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Button(
                        onClick = onOpenRemarks,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag("btn_settings_open_remarks"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Remarks Manager")
                    }
                }
            }

            // --- Gemini API Key Card ---
            Card(
                modifier = Modifier.fillMaxWidth().testTag("gemini_api_card"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text("Gemini AI API Key", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Required for AI Assistant when downloaded from GitHub", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Gemini API Key", fontSize = 12.sp) },
                        placeholder = { Text("AIzaSy...", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isKeyVisible) "Hide API Key" else "Show API Key"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input"),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                context.startActivity(intent)
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.testTag("get_api_key_link")
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Get free API Key", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                viewModel.saveGeminiApiKey(apiKeyInput.trim())
                                Toast.makeText(context, "API Key saved successfully!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("save_api_key_button"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Save Key", fontSize = 12.sp)
                        }
                    }
                }
            }

            // --- Database Tools ---
            Text("Backup & Restore (Full Application Data)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Backup Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Full App Backup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Creates a safe ZIP backup package containing database, settings, and all shop images", fontSize = 11.sp, color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                val backupDir = File(context.filesDir, "backups")
                                if (!backupDir.exists()) backupDir.mkdirs()
                                val backupFile = File(backupDir, "snackroute_full_backup.zip")
                                val success = viewModel.backupData(context)
                                if (success && backupFile.exists()) {
                                    Toast.makeText(context, "Full backup created successfully!", Toast.LENGTH_SHORT).show()
                                    // Export/Share the Backup File immediately
                                    com.example.utils.Exporter.shareFile(context, backupFile, "Share SnackRoute Backup ZIP")
                                } else {
                                    Toast.makeText(context, "Backup failed. Verify storage space.", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.testTag("backup_db_button")
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Backup", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Restore Action (Local)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Restore Local Backup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Overwrites active database, images, and preferences with last saved local checkpoint", fontSize = 11.sp, color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                val success = viewModel.restoreData(context)
                                if (success) {
                                    Toast.makeText(context, "Full restore complete! Restarting application...", Toast.LENGTH_LONG).show()
                                    // Programmatic restart
                                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    if (intent != null) {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        context.startActivity(intent)
                                    }
                                    android.os.Process.killProcess(android.os.Process.myPid())
                                } else {
                                    Toast.makeText(context, "No backup file found to restore from.", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("restore_db_button")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Pick Backup File from Device Action
                    val filePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            val success = viewModel.restoreFromUri(context, uri)
                            if (success) {
                                Toast.makeText(context, "Full Backup restored successfully! Restarting application...", Toast.LENGTH_LONG).show()
                                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    context.startActivity(intent)
                                }
                                android.os.Process.killProcess(android.os.Process.myPid())
                            } else {
                                Toast.makeText(context, "Failed to restore backup. Ensure it is a valid ZIP backup package.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Restore from Backup File", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Select and import a .zip backup package from your device", fontSize = 11.sp, color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                filePickerLauncher.launch("application/zip")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.testTag("restore_from_file_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Choose File", fontSize = 12.sp)
                        }
                    }
                }
            }

            // --- Cloud Backup Section ---
            Text("Cloud Backup & Sync", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("cloud_backup_card"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Google Drive integration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text("Google Drive", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (googleAccountState != null) {
                                    Text("Connected as ${googleAccountState?.email}", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                } else {
                                    Text("Sync database and backups to Google Drive", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                        
                        if (googleAccountState == null) {
                            Button(
                                onClick = {
                                    val signInIntent = googleSignInClient.signInIntent
                                    launcher.launch(signInIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("google_drive_signin_btn")
                            ) {
                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Connect", fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    viewModel.signOutGoogle(context) {
                                        Toast.makeText(context, "Google Drive disconnected.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("google_drive_signout_btn")
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Disconnect", fontSize = 12.sp)
                            }
                        }
                    }

                    if (googleAccountState != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Settings section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Sync", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Automatically backup after add, edit, delete, import, or restore", fontSize = 11.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = isAutoSyncEnabledState,
                                onCheckedChange = { viewModel.setAutoSyncEnabled(it) },
                                modifier = Modifier.testTag("auto_sync_switch")
                            )
                        }

                        // Sync Status Line
                        val formattedLastSync = remember(lastSyncedTimeState) {
                            if (lastSyncedTimeState == 0L) {
                                "Never"
                            } else {
                                val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault())
                                sdf.format(java.util.Date(lastSyncedTimeState))
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Sync Status:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    when (syncStatusState) {
                                        "InProgress" -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text("Sync in Progress...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                        "Success" -> {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text("Success", fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
                                        }
                                        "Failed" -> {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text("Failed", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Default.CloudQueue,
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text("Connected", fontSize = 12.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }

                            if (syncStatusState == "InProgress" && syncProgressState >= 0) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LinearProgressIndicator(
                                        progress = { syncProgressState / 100f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                    Text(
                                        text = "$syncProgressTextState (${syncProgressState}%)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Text("Last Backup Sync: $formattedLastSync", fontSize = 11.sp, color = Color.Gray)

                            if (lastSyncErrorState.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(8.dp)
                                ) {
                                    Text("Error details:", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    Text(lastSyncErrorState, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }

                        if (isOfflineQueueActiveState) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFF3CD), shape = RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFF856404), modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Offline Mode: Drive sync is queued and will automatically retry when internet connection is restored.",
                                    color = Color(0xFF856404),
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        // Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.triggerDriveSync() },
                                modifier = Modifier.weight(1f).testTag("sync_now_button"),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                enabled = syncStatusState != "InProgress"
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Now", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { showGDriveRestoreConfirm = true },
                                modifier = Modifier.weight(1f).testTag("restore_from_gdrive_button"),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                enabled = syncStatusState != "InProgress"
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore Backup", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (showGDriveRestoreConfirm) {
                AlertDialog(
                    onDismissRequest = { showGDriveRestoreConfirm = false },
                    title = { Text("Restore backup from Google Drive?") },
                    text = { Text("WARNING: This will completely replace your current local data, including shops, sales, products, images, XP/level, and settings with the cloud backup. This action cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showGDriveRestoreConfirm = false
                                viewModel.restoreFromGoogleDrive { success, message ->
                                    if (success) {
                                        Toast.makeText(context, "Cloud restore complete! Restored all shops, sales, and settings.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Restore failed: $message", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Restore Now")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showGDriveRestoreConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (gdriveErrorDetails != null) {
                AlertDialog(
                    onDismissRequest = { gdriveErrorDetails = null },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text("Connection Configuration Error", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(gdriveErrorDetails!!, fontSize = 13.sp, lineHeight = 18.sp)
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                Text(
                                    "Additional Context:\n" +
                                    "- Application ID: com.aistudio.snackroutepro.lpxmkw\n" +
                                    "- Workspace File: /app/google-services.json\n" +
                                    "- Keystore: debug.keystore",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Google Sign-In Configuration Details", gdriveErrorDetails)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Details copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Copy Details")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { gdriveErrorDetails = null }) {
                            Text("Dismiss")
                        }
                    }
                )
            }

            // --- Data Management ---
            var showDeleteDialog by remember { mutableStateOf<String?>(null) }
            Text("Data Management", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("data_management_card"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DeleteActionItem(
                        title = "Delete All Locations",
                        subtitle = "Permanently delete all Location Master records",
                        testTag = "delete_all_locations_row",
                        buttonTag = "delete_all_locations_button",
                        onClick = { showDeleteDialog = "Locations" }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    DeleteActionItem(
                        title = "Delete All Shops",
                        subtitle = "Permanently delete all Shop Master records",
                        testTag = "delete_all_shops_row",
                        buttonTag = "delete_all_shops_button",
                        onClick = { showDeleteDialog = "Shops" }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    DeleteActionItem(
                        title = "Delete All Products",
                        subtitle = "Permanently delete all product master and price configurations",
                        testTag = "delete_all_products_row",
                        buttonTag = "delete_all_products_button",
                        onClick = { showDeleteDialog = "Products" }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    DeleteActionItem(
                        title = "Delete All Sales",
                        subtitle = "Permanently delete all sales ledger entries",
                        testTag = "delete_all_sales_row",
                        buttonTag = "delete_all_sales_button",
                        onClick = { showDeleteDialog = "Sales" }
                    )
                }
            }
            
            if (showDeleteDialog != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = null },
                    modifier = Modifier.testTag("delete_warning_dialog"),
                    title = { Text("Warning") },
                    text = {
                        Text(
                            "This action will permanently delete all records from the selected module.\nThis action cannot be undone."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val moduleToDelete = showDeleteDialog
                                if (moduleToDelete != null) {
                                    when(moduleToDelete) {
                                        "Sales" -> viewModel.deleteAllSales()
                                        "Shops" -> viewModel.deleteAllShops()
                                        "Products" -> viewModel.deleteAllProducts()
                                        "Locations" -> viewModel.deleteAllLocations()
                                    }
                                    Toast.makeText(context, "All $moduleToDelete deleted successfully", Toast.LENGTH_SHORT).show()
                                }
                                showDeleteDialog = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("confirm_delete_button")
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteDialog = null },
                            modifier = Modifier.testTag("cancel_delete_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // --- System Diagnostics & Debug Panel ---
            Text("Diagnostics", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("diagnostics_debug_card"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("System Debug Panel", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("View captured error logs, inspect exception stack traces, clear records, and export reports to Excel.", fontSize = 11.sp, color = Color.Gray)
                        }
                        Button(
                            onClick = onOpenDebug,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.testTag("open_debug_panel_button")
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Debug Panel", fontSize = 12.sp)
                        }
                    }
                }
            }

            // --- App Information ---
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().testTag("app_info_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("SnackRoute Pro", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    Text("v1.0.0 (Offline-First Build)", fontSize = 11.sp, color = Color.Gray)
                    Text("Designed for Professional Distributors", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun DeleteActionItem(
    title: String,
    subtitle: String,
    testTag: String,
    buttonTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier
                .testTag(buttonTag)
                .heightIn(min = 36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ShopOverrideRow(
    shop: com.example.data.ShopMaster,
    onUpdateInterval: (Int?) -> Unit
) {
    var customVal by remember(shop.customReminderInterval) { 
        mutableStateOf(shop.customReminderInterval?.toString() ?: "") 
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(shop.storeName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("ID: ${shop.shopNumber} • Location: ${shop.locationNumber}", fontSize = 11.sp, color = Color.Gray)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customVal,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() }) {
                        customVal = newValue
                        val intVal = newValue.toIntOrNull()
                        if (intVal != null) {
                            onUpdateInterval(intVal)
                        } else if (newValue.isEmpty()) {
                            onUpdateInterval(null)
                        }
                    }
                },
                label = { Text("Days", fontSize = 10.sp) },
                singleLine = true,
                modifier = Modifier.width(75.dp).testTag("shop_override_${shop.shopNumber}"),
                placeholder = { Text("Default") }
            )
            if (shop.customReminderInterval != null) {
                IconButton(
                    onClick = {
                        customVal = ""
                        onUpdateInterval(null)
                    }
                ) {
                    Icon(Icons.Default.Clear, "Reset Override", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
