package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ExportCategorySummary
import com.example.data.UnexportedChange
import com.example.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExportChangesDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onExportConfirmed: () -> Unit,
    onNavigateToCategory: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val isExportNeeded by viewModel.isExportNeeded.collectAsState()
    val unexportedChanges by viewModel.unexportedChanges.collectAsState()
    val lastSuccessfulExportTime by viewModel.lastSuccessfulExportTime.collectAsState()

    val groupedSummaries = remember(unexportedChanges, isExportNeeded) {
        viewModel.getGroupedExportSummaries(unexportedChanges)
    }

    val totalChangesCount = remember(groupedSummaries) {
        groupedSummaries.sumOf { it.totalCount }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .testTag("dialog_export_changes_summary"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isExportNeeded || totalChangesCount > 0)
                                        MaterialTheme.colorScheme.errorContainer
                                    else
                                        MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isExportNeeded || totalChangesCount > 0)
                                    Icons.Default.CloudDownload
                                else
                                    Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isExportNeeded || totalChangesCount > 0)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Export to Excel",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isExportNeeded || totalChangesCount > 0)
                                    "$totalChangesCount pending change${if (totalChangesCount != 1) "s" else ""} ready for export"
                                else
                                    "All records up to date",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isExportNeeded || totalChangesCount > 0)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_export_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content area
                if (groupedSummaries.isEmpty() && !isExportNeeded) {
                    // Up to date state
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "No Pending Changes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "All Sales, Shops, Locations, Products, and settings are currently synced with your Excel backup.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            if (lastSuccessfulExportTime > 0) {
                                val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
                                val formattedDate = remember(lastSuccessfulExportTime) {
                                    sdf.format(Date(lastSuccessfulExportTime))
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Last Exported: $formattedDate",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Unexported changes list
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Which changes need to be exported:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (onNavigateToCategory != null) {
                                Text(
                                    text = "Tap to view live ↗",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("list_export_pending_changes")
                        ) {
                            items(groupedSummaries) { summary ->
                                ExportCategoryCard(
                                    summary = summary,
                                    onNavigate = if (onNavigateToCategory != null) {
                                        {
                                            onDismiss()
                                            onNavigateToCategory(summary.category)
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_cancel_export")
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onExportConfirmed()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("btn_confirm_export_excel")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isExportNeeded || totalChangesCount > 0)
                                "Export to Excel"
                            else
                                "Export Fresh Copy",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportCategoryCard(
    summary: ExportCategorySummary,
    onNavigate: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(true) }

    val (icon, tint) = getCategoryVisuals(summary.category)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (onNavigate != null) {
                    onNavigate()
                } else {
                    isExpanded = !isExpanded
                }
            }
            .testTag("category_card_${summary.category.lowercase().replace(" ", "_")}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(tint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = summary.category,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = tint
                            )
                            if (onNavigate != null) {
                                Surface(
                                    color = tint.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "View Live ↗",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tint,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = summary.headlineSentence,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = tint.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${summary.totalCount}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = tint,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Expanded detail items
            AnimatedVisibility(visible = isExpanded && summary.items.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(2.dp))

                    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

                    summary.items.forEach { item ->
                        val formattedTime = remember(item.timestamp) {
                            timeFormatter.format(Date(item.timestamp))
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onNavigate?.invoke()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = tint
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.summarySentence,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!item.detailText.isNullOrBlank()) {
                                        Text(
                                            text = item.detailText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = formattedTime,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = 10.sp
                                    )
                                    if (onNavigate != null) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Go to item",
                                            tint = tint.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
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

@Composable
private fun getCategoryVisuals(category: String): Pair<ImageVector, Color> {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error

    return when (category) {
        "Sales" -> Pair(Icons.Default.ShoppingCart, primaryColor)
        "Locations" -> Pair(Icons.Default.LocationOn, secondaryColor)
        "Shops" -> Pair(Icons.Default.Store, tertiaryColor)
        "Products" -> Pair(Icons.Default.Inventory2, primaryColor)
        "Expenses" -> Pair(Icons.Default.ReceiptLong, errorColor)
        "Daily Tasks", "Tasks" -> Pair(Icons.Default.Checklist, secondaryColor)
        "Shop Remarks", "Remarks" -> Pair(Icons.Default.Comment, tertiaryColor)
        "Cost Engine", "Dynamic Cost" -> Pair(Icons.Default.Calculate, primaryColor)
        "Weekly Timetable", "Timetable" -> Pair(Icons.Default.CalendarMonth, secondaryColor)
        "Sales Targets", "Targets" -> Pair(Icons.Default.TrackChanges, tertiaryColor)
        else -> Pair(Icons.Default.Sync, primaryColor)
    }
}
