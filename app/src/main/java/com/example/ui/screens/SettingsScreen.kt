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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onOpenChat: () -> Unit,
    onOpenTimetable: () -> Unit
) {
    val context = LocalContext.current

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
                    // Google Drive Row
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
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text("Google Drive", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Sync database and backups to Google Drive", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        AssistChip(
                            onClick = { Toast.makeText(context, "Google Drive sync is being prepared. Standard local backup and restore is fully functional!", Toast.LENGTH_SHORT).show() },
                            label = { Text("Upcoming") },
                            enabled = false,
                            modifier = Modifier.testTag("google_drive_chip")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // OneDrive Row
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
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text("OneDrive", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Sync database and backups to Microsoft OneDrive", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        AssistChip(
                            onClick = { Toast.makeText(context, "OneDrive sync is being prepared. Standard local backup and restore is fully functional!", Toast.LENGTH_SHORT).show() },
                            label = { Text("Upcoming") },
                            enabled = false,
                            modifier = Modifier.testTag("onedrive_chip")
                        )
                    }
                }
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
