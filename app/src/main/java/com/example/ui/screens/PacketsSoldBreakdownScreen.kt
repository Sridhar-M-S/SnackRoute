package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacketsSoldBreakdownScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onNavigateToSales: () -> Unit
) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()

    val breakdownGroups = remember(sales, products) {
        val soldSales = sales.filter { it.packetsSold > 0 }
        
        val groupedByCategory = soldSales.groupBy { sale ->
            val matchingProduct = products.find { it.productName.equals(sale.productName, ignoreCase = true) }
            matchingProduct?.productCategory ?: "Standard"
        }

        groupedByCategory.map { (categoryName, salesInCategory) ->
            val groupedByVariety = salesInCategory.groupBy { it.productName }
            val varieties = groupedByVariety.map { (varietyName, salesInVariety) ->
                val groupedByPrice = salesInVariety.groupBy { it.ratePerPacket }
                val priceGroups = groupedByPrice.map { (sellingPrice, salesInPrice) ->
                    val totalPacketsSold = salesInPrice.sumOf { it.packetsSold }
                    val totalSalesAmount = sellingPrice * totalPacketsSold
                    PriceGroup(sellingPrice, totalPacketsSold, totalSalesAmount)
                }.sortedBy { it.sellingPrice }

                VarietyGroup(varietyName, priceGroups)
            }.sortedBy { it.varietyName }

            CategoryGroup(categoryName, varieties)
        }.sortedBy { it.categoryName }
    }

    val grandTotalPackets = remember(breakdownGroups) {
        breakdownGroups.sumOf { cat ->
            cat.varieties.sumOf { v ->
                v.priceGroups.sumOf { p -> p.totalPacketsSold }
            }
        }
    }

    val grandTotalAmount = remember(breakdownGroups) {
        breakdownGroups.sumOf { cat ->
            cat.varieties.sumOf { v ->
                v.priceGroups.sumOf { p -> p.totalSalesAmount }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Packets Sold Breakdown", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("breakdown_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (breakdownGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No sold packets records found.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(breakdownGroups) { catGroup ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("category_card_${catGroup.categoryName.lowercase()}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Category: ${catGroup.categoryName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                catGroup.varieties.forEach { varGroup ->
                                    Column(
                                        modifier = Modifier.padding(start = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "• ${varGroup.varietyName}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        varGroup.priceGroups.forEach { priceGroup ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp)
                                                    .padding(vertical = 4.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        viewModel.setSalesBreakdownFilters(
                                                            catGroup.categoryName,
                                                            varGroup.varietyName,
                                                            priceGroup.sellingPrice
                                                        )
                                                        onNavigateToSales()
                                                    }
                                                    .padding(8.dp)
                                                    .testTag("price_row_${varGroup.varietyName.lowercase()}_${priceGroup.sellingPrice}"),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "  - ₹${"%.2f".format(priceGroup.sellingPrice)} → ${priceGroup.totalPacketsSold} Packets Sold",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "₹${"%.2f".format(priceGroup.totalSalesAmount)}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
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

            // Bottom Grand Totals Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("grand_totals_panel"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Grand Total Packets Sold:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$grandTotalPackets pkts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("grand_total_packets")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Grand Total Sales Amount:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "₹${"%.2f".format(grandTotalAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("grand_total_amount")
                        )
                    }
                }
            }
        }
    }
}

data class PriceGroup(
    val sellingPrice: Double,
    val totalPacketsSold: Int,
    val totalSalesAmount: Double
)

data class VarietyGroup(
    val varietyName: String,
    val priceGroups: List<PriceGroup>
)

data class CategoryGroup(
    val categoryName: String,
    val varieties: List<VarietyGroup>
)
