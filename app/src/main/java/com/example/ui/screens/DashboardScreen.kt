package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SalesEntry
import com.example.ui.AppViewModel
import com.example.ui.BusinessSuggestion
import com.example.ui.SuggestionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onNavigateToTab: (String) -> Unit,
    onQuickAddSales: () -> Unit
) {
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val shops by viewModel.shops.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val suggestions by viewModel.businessInsights.collectAsStateWithLifecycle(emptyList())

    // --- Statistics Calculations ---
    val totalLocations = locations.size
    val totalShops = shops.size
    val totalProducts = products.size

    val todayCalendar = Calendar.getInstance()
    val todayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val todayStr = todayFormat.format(todayCalendar.time)

    val todaySalesEntries = sales.filter {
        todayFormat.format(Date(it.entryDate)) == todayStr
    }

    val todaySalesCount = todaySalesEntries.sumOf { it.packetsSold }
    val todayRevenue = todaySalesEntries.sumOf { it.totalAmount }
    val todayProfit = todaySalesEntries.sumOf { it.totalProfit }

    // Monthly Profit
    val currentMonthFormat = SimpleDateFormat("yyyyMM", Locale.getDefault())
    val currentMonthStr = currentMonthFormat.format(todayCalendar.time)
    val monthlySalesEntries = sales.filter {
        currentMonthFormat.format(Date(it.entryDate)) == currentMonthStr
    }
    val monthlyProfit = monthlySalesEntries.sumOf { it.totalProfit }

    // Pending Collections
    val pendingCollections = sales.filter {
        it.status == "Pending" || it.status == "Partially Paid"
    }.sumOf { it.totalAmount }

    // Product performance
    val productSales = sales.groupBy { it.productName }.mapValues { (_, entries) -> entries.sumOf { it.packetsSold } }
    val topSellingProduct = productSales.maxByOrNull { it.value }?.key ?: "No Sales Yet"

    // Shop performance
    val shopSales = sales.groupBy { it.shopNumber }.mapValues { (_, entries) -> entries.sumOf { it.totalProfit } }
    val bestShopNum = shopSales.maxByOrNull { it.value }?.key
    val worstShopNum = shopSales.minByOrNull { it.value }?.key

    val bestPerformingShop = shops.firstOrNull { it.shopNumber == bestShopNum }?.storeName ?: "No Sales Yet"
    val worstPerformingShop = if (shopSales.size > 1) {
        shops.firstOrNull { it.shopNumber == worstShopNum }?.storeName ?: "No Sales Yet"
    } else {
        "N/A"
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SnackRoute Pro",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Smart Store & Sales Management",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onQuickAddSales,
                icon = { Icon(Icons.Default.Add, "Quick Add Sales") },
                text = { Text("Log Sales") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("quick_add_sales_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // --- AI Suggestions Section ---
            if (suggestions.isNotEmpty()) {
                item {
                    Text(
                        text = "AI Business Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(suggestions) { suggestion ->
                            SuggestionCard(suggestion = suggestion)
                        }
                    }
                }
            }

            // --- Bento Grid Dashboard Section ---
            item {
                Text(
                    text = "Performance Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Row 1: Today's Sales Card (Full Width Bento Card)
                    BentoSalesCard(
                        salesCount = todaySalesCount,
                        revenue = todayRevenue,
                        onClick = { onNavigateToTab("Sales") }
                    )

                    // Row 2: Today's Profit & Pending Collections (2 Columns)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BentoProfitCard(
                            profit = todayProfit,
                            modifier = Modifier.weight(1f)
                        )
                        BentoPendingCard(
                            pending = pendingCollections,
                            shopCount = totalShops,
                            onClick = { onNavigateToTab("Sales") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 3: Best Seller & Quick Stats Column (2 Columns)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BentoBestSellerCard(
                            topProduct = topSellingProduct,
                            unitsSold = productSales[topSellingProduct] ?: 0,
                            modifier = Modifier.weight(1f)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BentoMiniStatCard(
                                label = "Locations",
                                value = totalLocations.toString(),
                                icon = Icons.Default.Map,
                                iconColor = MaterialTheme.colorScheme.primary,
                                iconBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                onClick = { onNavigateToTab("Locations") }
                            )
                            BentoMiniStatCard(
                                label = "Shops",
                                value = totalShops.toString(),
                                icon = Icons.Default.Storefront,
                                iconColor = MaterialTheme.colorScheme.secondary,
                                iconBgColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                onClick = { onNavigateToTab("Shops") }
                            )
                        }
                    }
                }
            }

            // --- Leaders & Stats Summary Card (Bento Style) ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Leaderboard Highlights",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            // Remaining Master counter: Products
                            AssistChip(
                                onClick = { onNavigateToTab("Products") },
                                label = { Text("$totalProducts Products") },
                                leadingIcon = { Icon(Icons.Default.Category, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        
                        LeaderRow(label = "Top Selling Product", value = topSellingProduct, icon = Icons.Default.Star, color = Color(0xFFFFD700))
                        LeaderRow(label = "Best Performing Shop", value = bestPerformingShop, icon = Icons.Default.ThumbUp, color = Color(0xFF4CAF50))
                        LeaderRow(label = "Lowest Performing Shop", value = worstPerformingShop, icon = Icons.Default.TrendingDown, color = Color(0xFFF44336))
                    }
                }
            }

            // --- Recent Sales ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Sales Entries",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = { onNavigateToTab("Sales") }) {
                        Text("View All")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (sales.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recent sales entries logged",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sales.take(5).forEach { sale ->
                            RecentSaleRow(sale = sale)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionCard(suggestion: BusinessSuggestion) {
    val containerColor = when (suggestion.type) {
        SuggestionType.SUCCESS -> Color(0xFFE8F5E9)
        SuggestionType.WARNING -> Color(0xFFFFF3E0)
        SuggestionType.INFO -> Color(0xFFE3F2FD)
    }
    val contentColor = when (suggestion.type) {
        SuggestionType.SUCCESS -> Color(0xFF2E7D32)
        SuggestionType.WARNING -> Color(0xFFE65100)
        SuggestionType.INFO -> Color(0xFF0D47A1)
    }
    val icon = when (suggestion.type) {
        SuggestionType.SUCCESS -> Icons.Default.Lightbulb
        SuggestionType.WARNING -> Icons.Default.Warning
        SuggestionType.INFO -> Icons.Default.Info
    }

    Card(
        modifier = Modifier
            .width(280.dp)
            .height(115.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Top)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = suggestion.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = suggestion.message,
                    fontSize = 12.sp,
                    color = contentColor.copy(alpha = 0.9f),
                    maxLines = 3,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = contentColor.copy(alpha = 0.75f))
                Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = subValue, fontSize = 11.sp, color = contentColor.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
fun MasterCounterItem(
    label: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = count.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun LeaderRow(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun RecentSaleRow(sale: SalesEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sale.shopName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${sale.packetsSold} packets of ${sale.productName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${"%.2f".format(sale.totalAmount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                val badgeColor = when (sale.status) {
                    "Paid" -> Color(0xFFE8F5E9)
                    "Partially Paid" -> Color(0xFFFFF3E0)
                    else -> Color(0xFFFFEBEE)
                }
                val textColor = when (sale.status) {
                    "Paid" -> Color(0xFF2E7D32)
                    "Partially Paid" -> Color(0xFFE65100)
                    else -> Color(0xFFC62828)
                }
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(badgeColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = sale.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun BentoSalesCard(
    salesCount: Int,
    revenue: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TODAY'S SALES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "₹${"%,.2f".format(revenue)}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$salesCount Packets",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Distributed Live Today",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 10.dp)
            )
        }
    }
}

@Composable
fun BentoProfitCard(
    profit: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "TODAY'S PROFIT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "₹${"%,.2f".format(profit)}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Calculated Live",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.Default.CurrencyRupee,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
fun BentoPendingCard(
    pending: Double,
    shopCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "PENDING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "₹${"%,.2f".format(pending)}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Collection Pipeline",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Default.PendingActions,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.1f),
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
fun BentoBestSellerCard(
    topProduct: String,
    unitsSold: Int,
    modifier: Modifier = Modifier
) {
    val bentoOrange = Color(0xFFFF6D00)
    Card(
        modifier = modifier.height(170.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "BEST SELLER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = topProduct,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (unitsSold > 0) "$unitsSold units sold" else "No sales logged",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            // Decorative sparkline bar chart using Canvas!
            Canvas(
                modifier = Modifier
                    .size(width = 56.dp, height = 36.dp)
                    .align(Alignment.BottomEnd)
            ) {
                val bars = listOf(0.3f, 0.6f, 0.4f, 1.0f, 0.8f)
                val barWidth = 6.dp.toPx()
                val spacing = 4.dp.toPx()
                bars.forEachIndexed { idx, heightFrac ->
                    drawRoundRect(
                        color = bentoOrange,
                        topLeft = Offset(idx * (barWidth + spacing), size.height * (1f - heightFrac)),
                        size = Size(barWidth, size.height * heightFrac),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun BentoMiniStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    iconBgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(79.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
