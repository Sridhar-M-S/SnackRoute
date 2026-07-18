package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import com.example.data.DailyTarget
import com.example.data.Badge
import com.example.data.UserBadge
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.AppViewModel
import com.example.ui.BusinessSuggestion
import com.example.ui.SuggestionType
import com.example.ui.GamificationState
import com.example.ui.Mission
import com.example.ui.BossChallenge
import com.example.ui.GamificationEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onNavigateToTab: (String) -> Unit,
    onQuickAddSales: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenTimetable: () -> Unit
) {
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val shops by viewModel.shops.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val suggestions by viewModel.businessInsights.collectAsStateWithLifecycle(emptyList())

    val gameProgress by viewModel.gamificationState.collectAsStateWithLifecycle()
    val dailyTarget by viewModel.dailyTarget.collectAsStateWithLifecycle()
    val todayPacketsVal by viewModel.todayPackets.collectAsStateWithLifecycle()
    val todaySalesVal by viewModel.todaySales.collectAsStateWithLifecycle()
    val todayProfitVal by viewModel.todayProfit.collectAsStateWithLifecycle()
    val allBadges by viewModel.allBadges.collectAsStateWithLifecycle()
    val unlockedBadges by viewModel.unlockedBadges.collectAsStateWithLifecycle()
    var activeCelebration by remember { mutableStateOf<GamificationEvent?>(null) }

    LaunchedEffect(Unit) {
        viewModel.gamificationEvents.collect { event ->
            if (event !is GamificationEvent.XpGain && 
                event !is GamificationEvent.CoinGain && 
                event !is GamificationEvent.ComboUpdate) {
                activeCelebration = event
            }
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
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
                navigationIcon = {
                    IconButton(
                        onClick = onOpenTimetable,
                        modifier = Modifier.testTag("open_timetable_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Weekly Timetable",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
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
            // --- Gamification HUD Header ---
            item {
                GameHudCard(
                    state = gameProgress,
                    onClick = { onNavigateToTab("Levels") }
                )
            }

            // --- Combo Alert ---
            if (gameProgress.sessionCombo > 1) {
                item {
                    val comboBonus = when {
                        gameProgress.sessionCombo >= 10 -> 100
                        gameProgress.sessionCombo >= 5 -> 50
                        gameProgress.sessionCombo >= 3 -> 20
                        else -> 10
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToTab("Sales") },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF5722)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🔥", fontSize = 28.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "${gameProgress.sessionCombo}X Sales Combo Active!",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Log another sale within 10 minutes for massive XP multipliers!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                            Text(
                                text = "+$comboBonus XP!",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // --- Missions Arena (Daily run & Boss fights) ---
            item {
                MissionsArenaWidget(state = gameProgress)
            }

            // --- Today's Target and Achievements (moved from Shops page) ---
            item {
                DailyTargetWidget(
                    dailyTarget = dailyTarget,
                    todayPackets = todayPacketsVal,
                    todaySales = todaySalesVal,
                    todayProfit = todayProfitVal,
                    onSetTarget = { viewModel.setDailyTarget(it) }
                )
            }
            item {
                BadgeSection(allBadges = allBadges, unlockedBadges = unlockedBadges)
            }

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

            // --- Top Rated Shops Card ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("top_rated_shops_dashboard_card"),
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Top Rated Shops",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            AssistChip(
                                onClick = { onNavigateToTab("Shops") },
                                label = { Text("View All") },
                                leadingIcon = { Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        
                        val topShops = shops.sortedByDescending { it.rating }.take(10)
                        if (topShops.isEmpty()) {
                            Text(
                                text = "Add stores and log sales to see automatic performance ratings.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                topShops.forEachIndexed { index, shop ->
                                    val locName = locations.firstOrNull { it.locationNumber == shop.locationNumber }?.locationName ?: shop.locationNumber
                                    val salesForShop = sales.filter { it.shopNumber == shop.shopNumber }
                                    val analytics = com.example.utils.RatingCalculator.calculateAnalytics(salesForShop)
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToTab("Shops") }
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Rank Badge
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(
                                                        color = when (index) {
                                                            0 -> Color(0xFFFFD700)
                                                            1 -> Color(0xFFC0C0C0)
                                                            2 -> Color(0xFFCD7F32)
                                                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                        },
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${index + 1}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (index < 3) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            
                                            Column {
                                                Text(
                                                    text = shop.storeName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$locName • ${analytics.ratingDescription}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color(0xFFFFD700),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "${"%.1f".format(shop.rating)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
        } // Close Scaffold lambda block

        if (activeCelebration != null) {
            CelebrationOverlay(
                event = activeCelebration!!,
                onDismiss = { activeCelebration = null }
            )
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

@Composable
fun GameHudCard(state: GamificationState, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("game_hud_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "LVL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                            Text(
                                text = state.level.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = state.rank,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFD700).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🪙", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = state.coins.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFC107)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF5722).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5722))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🔥", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${state.streak}D",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF5722)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "XP Progress",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "${state.xp} / ${state.xpNeededForNextLevel} XP",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { state.xpProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap to enter Levels Arena 🏆",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MissionsArenaWidget(state: GamificationState) {
    var selectedTab by remember { mutableStateOf(0) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Missions Arena ⚔️",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                AssistChip(
                    onClick = {},
                    label = { Text("Rank: ${state.rank}") },
                    leadingIcon = { Icon(Icons.Default.TrendingUp, null, modifier = Modifier.size(14.dp)) }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Daily Run", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Milestones", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Boss Fights", style = MaterialTheme.typography.labelMedium) }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            when (selectedTab) {
                0 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.dailyMissions.isEmpty()) {
                            Text(
                                text = "All daily missions cleared! Check tomorrow.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            state.dailyMissions.forEach { mission ->
                                MissionItemRow(mission = mission)
                            }
                        }
                    }
                }
                1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val milestoneMissions = state.weeklyMissions + state.monthlyMissions
                        if (milestoneMissions.isEmpty()) {
                            Text(
                                text = "No milestone missions active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            milestoneMissions.forEach { mission ->
                                MissionItemRow(mission = mission)
                            }
                        }
                    }
                }
                2 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.bossChallenges.isEmpty()) {
                            Text(
                                text = "All boss challenges slain! You are the ultimate leader.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            state.bossChallenges.forEach { boss ->
                                BossChallengeItemRow(boss = boss)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MissionItemRow(mission: Mission) {
    val progressPercent = if (mission.target > 0) mission.progress.toFloat() / mission.target else 0f
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (mission.isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (mission.isCompleted) Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (mission.isCompleted) {
                Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
            } else {
                Icon(Icons.Default.Flag, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mission.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (mission.isCompleted) Color(0xFF388E3C) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = mission.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { progressPercent.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (mission.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${mission.progress}/${mission.target}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun BossChallengeItemRow(boss: BossChallenge) {
    val progressPercent = if (boss.target > 0) boss.progress.toFloat() / boss.target else 0f
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (boss.isCompleted) Color(0xFFFFE4E6).copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (boss.isCompleted) Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else Color(0xFFE91E63).copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (boss.isCompleted) {
                Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
            } else {
                Text(text = "👹", fontSize = 18.sp)
            }
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = boss.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = if (boss.isCompleted) Color(0xFF388E3C) else Color(0xFFC2185B)
                )
                Text(
                    text = if (boss.isCompleted) "SLAYED" else "ALIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = if (boss.isCompleted) Color(0xFF388E3C) else Color(0xFFC2185B),
                    fontSize = 9.sp
                )
            }
            Text(
                text = boss.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { progressPercent.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (boss.isCompleted) Color(0xFF4CAF50) else Color(0xFFE91E63),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${boss.progress}/${boss.target}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun CelebrationOverlay(event: GamificationEvent, onDismiss: () -> Unit) {
    LaunchedEffect(event) {
        kotlinx.coroutines.delay(3500)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        if (event is GamificationEvent.LevelUp) {
            ConfettiScreen()
        }

        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (event) {
                    is GamificationEvent.LevelUp -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MilitaryTech, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        Text(
                            text = "LEVEL UP! 👑",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFC107)
                        )
                        Text(
                            text = "You've reached Level ${event.level}!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Title Unlocked:\n${event.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is GamificationEvent.MissionComplete -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        Text(
                            text = "MISSION COMPLETE! 🎉",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "Daily Reward Credited!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    is GamificationEvent.AchievementUnlocked -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF9800)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        Text(
                            text = "BADGE UNLOCKED! 🏆",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9800),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = event.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    is GamificationEvent.BossDefeated -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE91E63)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        Text(
                            text = "BOSS DEFEATED! ⚔️",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE91E63),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "You defeated ${event.bossName}!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    is GamificationEvent.XpGain -> {
                        Text(
                            text = "+${event.amount} XP",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = event.reason,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    is GamificationEvent.CoinGain -> {
                        Text(
                            text = "+${event.amount} COINS 🪙",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFC107)
                        )
                        Text(
                            text = event.reason,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    is GamificationEvent.ComboUpdate -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5722)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🔥", fontSize = 32.sp)
                        }
                        Text(
                            text = "${event.count}X SALES COMBO!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF5722)
                        )
                        Text(
                            text = "+${event.bonusXp} Combo Bonus XP!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Awesome!")
                }
            }
        }
    }
}

@Composable
fun ConfettiScreen() {
    val particles = remember {
        List(80) {
            ConfettiParticle(
                x = (0..1000).random().toFloat(),
                y = (-200..0).random().toFloat(),
                speed = (6..18).random().toFloat(),
                color = Color(
                    red = (150..255).random() / 255f,
                    green = (150..255).random() / 255f,
                    blue = (150..255).random() / 255f
                ),
                size = (6..16).random().toFloat()
            )
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val animFactor by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confetti_anim"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val currentY = p.y + animFactor * (p.speed / 10f)
            val currentX = p.x + kotlin.math.sin(currentY / 50f) * 30f
            if (currentY in 0f..size.height) {
                drawCircle(
                    color = p.color,
                    radius = p.size / 2,
                    center = Offset(currentX % size.width, currentY)
                )
            }
        }
    }
}

data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val speed: Float,
    val color: Color,
    val size: Float
)

@Composable
fun BadgeSection(allBadges: List<Badge>, unlockedBadges: List<UserBadge>) {
    val unlockedIds = unlockedBadges.map { it.badgeId }.toSet()
    
    if (allBadges.isEmpty()) return

    Card(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Achievements", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allBadges) { badge ->
                    BadgeItem(badge, badge.id in unlockedIds)
                }
            }
        }
    }
}

@Composable
fun BadgeItem(badge: Badge, isUnlocked: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = if (isUnlocked) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.size(48.dp)
        )
        Text(badge.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun DailyTargetWidget(
    dailyTarget: DailyTarget?,
    todayPackets: Int,
    todaySales: Double,
    todayProfit: Double,
    onSetTarget: (DailyTarget) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        DailyTargetDialog(
            onDismiss = { showDialog = false },
            onSave = { p, s, pr -> onSetTarget(DailyTarget(1, p, s, pr)) },
            initialPackets = dailyTarget?.packetTarget ?: 0,
            initialSales = dailyTarget?.salesAmountTarget ?: 0.0,
            initialProfit = dailyTarget?.profitTarget ?: 0.0
        )
    }

    Card(modifier = Modifier.padding(16.dp).fillMaxWidth(), onClick = { showDialog = true }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Today's Target", style = MaterialTheme.typography.titleMedium)
            if (dailyTarget == null) {
                Text("No target set. Click to set.")
            } else {
                val packetProgress = (todayPackets.toFloat() / dailyTarget.packetTarget.coerceAtLeast(1)).coerceIn(0f, 1f)
                val salesProgress = (todaySales.toFloat() / dailyTarget.salesAmountTarget.coerceAtLeast(1.0)).coerceIn(0.0, 1.0).toFloat()
                val profitProgress = (todayProfit.toFloat() / dailyTarget.profitTarget.coerceAtLeast(1.0)).coerceIn(0.0, 1.0).toFloat()
                
                Text("Packets: $todayPackets / ${dailyTarget.packetTarget}")
                LinearProgressIndicator(progress = packetProgress, modifier = Modifier.fillMaxWidth())
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Sales: ₹${todaySales.toInt()} / ₹${dailyTarget.salesAmountTarget.toInt()}")
                LinearProgressIndicator(progress = salesProgress, modifier = Modifier.fillMaxWidth())
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Profit: ₹${todayProfit.toInt()} / ₹${dailyTarget.profitTarget.toInt()}")
                LinearProgressIndicator(progress = profitProgress, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun DailyTargetDialog(
    onDismiss: () -> Unit,
    onSave: (Int, Double, Double) -> Unit,
    initialPackets: Int,
    initialSales: Double,
    initialProfit: Double
) {
    var packets by remember { mutableStateOf(initialPackets.toString()) }
    var sales by remember { mutableStateOf(initialSales.toInt().toString()) }
    var profit by remember { mutableStateOf(initialProfit.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Daily Target") },
        text = {
            Column {
                OutlinedTextField(value = packets, onValueChange = { packets = it }, label = { Text("Packets Target") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = sales, onValueChange = { sales = it }, label = { Text("Sales Amount Target") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = profit, onValueChange = { profit = it }, label = { Text("Profit Target") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(packets.toIntOrNull() ?: 0, sales.toDoubleOrNull() ?: 0.0, profit.toDoubleOrNull() ?: 0.0)
                onDismiss()
            }) { Text("Save") }
        }
    )
}
