package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppViewModel
import com.example.utils.Exporter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val shops by viewModel.shops.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()

    var activeReportTab by remember { mutableStateOf("Summary") } // Summary, Profit, Shops, Products, Pending
    val tabs = listOf("Summary", "Profit", "Shops", "Products", "Pending")

    // --- Calculations ---
    val totalRevenue = sales.sumOf { it.totalAmount }
    val totalProfit = sales.sumOf { it.totalProfit }
    val totalVolume = sales.sumOf { it.packetsSold }

    // Grouping for reports
    val locationWise = sales.groupBy { it.locationNumber }.mapValues { (_, entries) ->
        Pair(entries.sumOf { it.totalAmount }, entries.sumOf { it.totalProfit })
    }
    
    val shopWise = sales.groupBy { it.shopNumber }.mapValues { (shopNum, entries) ->
        val storeName = shops.firstOrNull { it.shopNumber == shopNum }?.storeName ?: shopNum
        Triple(storeName, entries.sumOf { it.totalAmount }, entries.sumOf { it.totalProfit })
    }

    val productWise = sales.groupBy { it.productName }.mapValues { (_, entries) ->
        Pair(entries.sumOf { it.packetsSold }, entries.sumOf { it.totalProfit })
    }

    val pendingCollectionsList = sales.filter { it.status == "Pending" || it.status == "Partially Paid" }
    val pendingAmount = pendingCollectionsList.sumOf { it.totalAmount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Reports", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            Exporter.exportSales(context, sales)
                            Toast.makeText(context, "Full Sales Ledger exported", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("export_reports_all_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export Full Ledger")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- Tabs Scrollable Row ---
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(tabs) { tab ->
                    FilterChip(
                        selected = activeReportTab == tab,
                        onClick = { activeReportTab = tab },
                        label = { Text(tab) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                when (activeReportTab) {
                    "Summary" -> {
                        item {
                            Text("Snack Distribution KPIs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ReportStatCard(label = "Total Volume", value = "$totalVolume Packs", color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.weight(1f))
                                ReportStatCard(label = "Total Revenue", value = "₹${"%.2f".format(totalRevenue)}", color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f))
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Route-Wise Distribution Analysis", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (locationWise.isEmpty()) {
                                        Text("Log sales to visualize route-wise performance", fontSize = 12.sp, color = Color.Gray)
                                    } else {
                                        // Custom Canvas Bar Chart for locations
                                        val locList = locationWise.toList().take(5)
                                        val maxVal = locList.maxOf { it.second.second }.coerceAtLeast(1.0)
                                        
                                        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                                            val barWidth = 32.dp.toPx()
                                            val spacing = 20.dp.toPx()
                                            var currentX = 20.dp.toPx()
                                            
                                            for ((locCode, values) in locList) {
                                                val profit = values.second
                                                val barHeight = (profit / maxVal) * 100.dp.toPx()
                                                
                                                // Draw Bar
                                                drawRect(
                                                    color = Color(0xFFFF9100),
                                                    topLeft = Offset(currentX, size.height - 20.dp.toPx() - barHeight.toFloat()),
                                                    size = Size(barWidth, barHeight.toFloat())
                                                )
                                                currentX += barWidth + spacing
                                            }
                                            
                                            // Bottom Axis
                                            drawLine(
                                                color = Color.LightGray,
                                                start = Offset(10.dp.toPx(), size.height - 18.dp.toPx()),
                                                end = Offset(size.width - 10.dp.toPx(), size.height - 18.dp.toPx()),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }

                                        // Legends
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            locList.forEach { (locCode, values) ->
                                                val locName = locations.firstOrNull { it.locationNumber == locCode }?.locationName ?: locCode
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFFF9100), RoundedCornerShape(2.dp)))
                                                    Text(locName, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(60.dp))
                                                    Text("₹${values.second.toInt()}", fontSize = 9.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("Monthly Distribution Timeline", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }

                        item {
                            // Timeline visual
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val monthlySales = sales.groupBy {
                                        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(it.entryDate))
                                    }
                                    if (monthlySales.isEmpty()) {
                                        Text("No data available yet", fontSize = 12.sp, color = Color.Gray)
                                    } else {
                                        monthlySales.forEach { (month, entries) ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(month, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("₹${"%.2f".format(entries.sumOf { it.totalProfit })} Profit", color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Profit" -> {
                        item {
                            Text("Profit Margin & Performance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Accumulated Profit: ₹${"%.2f".format(totalProfit)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    Text("Average margin per packet: ₹${"%.2f".format(if (totalVolume > 0) totalProfit / totalVolume else 0.0)}", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                        item {
                            Text("Top Routes by Net Profit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        items(locationWise.toList().sortedByDescending { it.second.second }) { (locCode, values) ->
                            val locName = locations.firstOrNull { it.locationNumber == locCode }?.locationName ?: locCode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(locName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("₹${"%.2f".format(values.second)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 13.sp)
                            }
                        }
                    }

                    "Shops" -> {
                        item {
                            Text("Store Master Performance Rankings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        val sortedShops = shopWise.toList().sortedByDescending { it.second.third }
                        if (sortedShops.isEmpty()) {
                            item { Text("No store log data available", fontSize = 12.sp, color = Color.Gray) }
                        } else {
                            items(sortedShops) { (shopNum, details) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(details.first, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Shop Code: $shopNum", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("₹${"%.2f".format(details.third)} Profit", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("₹${"%.2f".format(details.second)} Rev", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }

                    "Products" -> {
                        item {
                            Text("Snack Variant Performance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        val sortedProds = productWise.toList().sortedByDescending { it.second.first }
                        if (sortedProds.isEmpty()) {
                            item { Text("No product sales logged yet", fontSize = 12.sp, color = Color.Gray) }
                        } else {
                            items(sortedProds) { (prodName, details) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(prodName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${details.first} packets sold", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Text("₹${"%.2f".format(details.second)} Profit", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    "Pending" -> {
                        item {
                            Text("Pending Collection Pipeline", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Total Pending Collections", fontSize = 12.sp)
                                        Text("₹${"%.2f".format(pendingAmount)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                        if (pendingCollectionsList.isEmpty()) {
                            item { Text("All collections are paid perfectly! Great job!", fontSize = 13.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold) }
                        } else {
                            items(pendingCollectionsList) { sale ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(sale.shopName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${sale.entryDateFormatted} • ${sale.productName}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("₹${"%.2f".format(sale.totalAmount)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(sale.status, fontSize = 11.sp, color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold)
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
fun ReportStatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(85.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
