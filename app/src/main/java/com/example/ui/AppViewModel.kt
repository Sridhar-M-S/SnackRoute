package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.utils.Exporter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.*
import java.util.concurrent.TimeUnit

data class AppError(
    val module: String,
    val operation: String,
    val errorType: String,
    val errorMessage: String,
    val possibleReason: String,
    val stackTrace: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AppRepository(
        db,
        db.locationDao(),
        db.shopDao(),
        db.productDao(),
        db.productPriceDao(),
        db.salesDao(),
        db.timetableDao(),
        db.dailyTargetDao(),
        db.badgeDao(),
        db.errorLogDao()
    )

    // --- Centralized Error States ---
    private val _activeError = MutableStateFlow<AppError?>(null)
    val activeError: StateFlow<AppError?> = _activeError.asStateFlow()

    fun dismissError() {
        _activeError.value = null
    }

    fun triggerError(
        module: String,
        operation: String,
        errorType: String,
        errorMessage: String,
        possibleReason: String,
        exception: Throwable? = null
    ) {
        val stackTraceStr = exception?.stackTraceToString() ?: ""
        val error = AppError(
            module = module,
            operation = operation,
            errorType = errorType,
            errorMessage = errorMessage,
            possibleReason = possibleReason,
            stackTrace = stackTraceStr
        )
        _activeError.value = error

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.insertErrorLog(
                    ErrorLog(
                        module = module,
                        operation = operation,
                        errorType = errorType,
                        errorMessage = errorMessage,
                        stackTrace = stackTraceStr,
                        possibleReason = possibleReason
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to save error log to DB", e)
            }
        }
    }

    fun triggerError(
        module: String,
        operation: String,
        exception: Throwable,
        possibleReason: String
    ) {
        triggerError(
            module = module,
            operation = operation,
            errorType = exception.javaClass.simpleName,
            errorMessage = exception.localizedMessage ?: exception.message ?: "Unknown technical exception.",
            possibleReason = possibleReason,
            exception = exception
        )
    }

    fun triggerError(
        module: String,
        operation: String,
        errorMessage: String,
        stackTrace: String,
        possibleReason: String
    ) {
        val error = AppError(
            module = module,
            operation = operation,
            errorType = "Validation Error",
            errorMessage = errorMessage,
            possibleReason = possibleReason,
            stackTrace = stackTrace
        )
        _activeError.value = error

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.insertErrorLog(
                    ErrorLog(
                        module = module,
                        operation = operation,
                        errorType = "Validation Error",
                        errorMessage = errorMessage,
                        stackTrace = stackTrace,
                        possibleReason = possibleReason
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val allErrorLogs: StateFlow<List<ErrorLog>> = repository.allErrorLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun clearErrorLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.clearErrorLogs()
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to clear error logs", e)
            }
        }
    }

    fun exportErrorLogsToExcel(context: Context) {
        Exporter.exportErrorLogs(context, allErrorLogs.value)
    }

    // --- Preferences & custom Gemini API Key ---
    private val prefs = application.getSharedPreferences("snackroute_prefs", Context.MODE_PRIVATE)
    private val _userGeminiApiKey = MutableStateFlow(prefs.getString("gemini_api_key", "") ?: "")
    val userGeminiApiKey: StateFlow<String> = _userGeminiApiKey.asStateFlow()

    fun saveGeminiApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key).apply()
        _userGeminiApiKey.value = key
    }

    // --- Gamification States & Persistence ---
    private val _bonusXp = MutableStateFlow(prefs.getInt("bonus_xp", 0))
    private val _bonusCoins = MutableStateFlow(prefs.getInt("bonus_coins", 0))
    private val _rewardedMissionIds = MutableStateFlow(prefs.getStringSet("rewarded_mission_ids", emptySet()) ?: emptySet())
    private val _sessionCombo = MutableStateFlow(0)
    private var lastSaleTime: Long = 0L

    private val _gamificationEvents = MutableSharedFlow<GamificationEvent>(extraBufferCapacity = 100)
    val gamificationEvents: SharedFlow<GamificationEvent> = _gamificationEvents.asSharedFlow()

    // Date formats for unique mission cycle tracking
    private val sdfDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    private val sdfWeek = SimpleDateFormat("yyyy'W'ww", Locale.getDefault())
    private val sdfMonth = SimpleDateFormat("yyyy'M'MM", Locale.getDefault())

    fun getTodayStartMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // Helper functions for Level & Rank Math
    fun getLevelForXp(xp: Int): Int {
        var lvl = 1
        var required = 500
        var accumulated = 0
        while (xp >= accumulated + required) {
            accumulated += required
            lvl++
            required = (required * 1.25).toInt() / 50 * 50
            if (required < 500) required = 500
        }
        return lvl
    }

    fun getXpThresholdForLevel(level: Int): Int {
        var lvl = 1
        var required = 500
        var accumulated = 0
        while (lvl < level) {
            accumulated += required
            lvl++
            required = (required * 1.25).toInt() / 50 * 50
            if (required < 500) required = 500
        }
        return accumulated
    }

    fun getTitleForLevel(level: Int): String {
        return when {
            level >= 50 -> "Snack Empire"
            level >= 30 -> "Business Legend"
            level >= 20 -> "Distribution Master"
            level >= 10 -> "Snack Champion"
            level >= 5 -> "Business Pro"
            level == 4 -> "Sales Expert"
            level == 3 -> "Route Explorer"
            level == 2 -> "Local Distributor"
            else -> "Beginner Seller"
        }
    }

    fun getRankForXp(xp: Int): String {
        return when {
            xp >= 100000 -> "Business Legend"
            xp >= 60000 -> "Snack King"
            xp >= 30000 -> "Elite Business Owner"
            xp >= 15000 -> "Master Distributor"
            xp >= 7000 -> "Diamond Seller"
            xp >= 3000 -> "Gold Seller"
            xp >= 1000 -> "Silver Seller"
            else -> "Bronze Seller"
        }
    }

    fun calculateStreak(sales: List<SalesEntry>): Int {
        if (sales.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val activeDates = sales.map { sdf.format(Date(it.entryDate)) }.distinct().sortedDescending()
        if (activeDates.isEmpty()) return 0

        val todayCalendar = Calendar.getInstance()
        val todayStr = sdf.format(todayCalendar.time)
        
        todayCalendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(todayCalendar.time)

        val latestActiveDate = activeDates.first()
        if (latestActiveDate != todayStr && latestActiveDate != yesterdayStr) {
            return 0
        }

        var streak = 0
        var checkCalendar = Calendar.getInstance()
        try {
            val startParts = latestActiveDate.let {
                Triple(it.substring(0, 4).toInt(), it.substring(4, 6).toInt() - 1, it.substring(6, 8).toInt())
            }
            checkCalendar.set(startParts.first, startParts.second, startParts.third)

            for (i in 0 until activeDates.size) {
                val expectedStr = sdf.format(checkCalendar.time)
                if (activeDates.contains(expectedStr)) {
                    streak++
                    checkCalendar.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            return 1
        }
        return streak
    }

    fun getBossChallenges(sales: List<SalesEntry>, shops: List<ShopMaster>): List<BossChallenge> {
        val maxPacketsInSingleDay = sales.groupBy { sdfDay.format(Date(it.entryDate)) }
            .map { it.value.sumOf { s -> s.packetsSold } }
            .maxOrNull() ?: 0

        val maxProfitInSingleDay = sales.groupBy { sdfDay.format(Date(it.entryDate)) }
            .map { it.value.sumOf { s -> s.totalProfit } }
            .maxOrNull() ?: 0.0

        val totalShopsAdded = shops.size

        val maxLocationsInSingleDay = sales.groupBy { sdfDay.format(Date(it.entryDate)) }
            .map { it.value.map { s -> s.locationNumber }.distinct().size }
            .maxOrNull() ?: 0

        return listOf(
            BossChallenge("snack_titan", "Defeat the Snack Titan", "Boss Challenge (Level 1)", "Sell 500 packets in a single day to conquer the Titan.", maxPacketsInSingleDay, 500, 500, 200, maxPacketsInSingleDay >= 500, "Snack Titan"),
            BossChallenge("gold_rush", "Slay the Gold Dragon", "Boss Challenge (Level 2)", "Earn ₹10,000 in profit in a single day.", maxProfitInSingleDay.toInt(), 10000, 10000, 500, maxProfitInSingleDay >= 10000.0, "Gold Dragon"),
            BossChallenge("expansion_emperor", "Overthrow the Expansion Emperor", "Boss Challenge (Level 3)", "Build a franchise of 20 active shops.", totalShopsAdded, 20, 800, 400, totalShopsAdded >= 20, "Expansion Emperor"),
            BossChallenge("route_sovereign", "Dethrone the Route Sovereign", "Boss Challenge (Level 4)", "Sell in 5 different route locations in a single day.", maxLocationsInSingleDay, 5, 1200, 600, maxLocationsInSingleDay >= 5, "Route Sovereign")
        )
    }

    fun calculateGamificationState(
        sales: List<SalesEntry>,
        shops: List<ShopMaster>,
        locationsList: List<LocationMaster>,
        badges: List<UserBadge>,
        bonusXp: Int,
        bonusCoins: Int,
        rewardedIds: Set<String>,
        combo: Int
    ): GamificationState {
        val baseShopXp = shops.size * 50
        val baseSalesXp = sales.size * 10
        val basePacketsXp = sales.sumOf { it.packetsSold } * 1
        val baseLocationXp = sales.map { it.locationNumber }.distinct().size * 30
        val baseBadgeXp = badges.size * 100
        
        val totalXp = baseShopXp + baseSalesXp + basePacketsXp + baseLocationXp + baseBadgeXp + bonusXp
        
        val level = getLevelForXp(totalXp)
        val currentLevelXpStart = getXpThresholdForLevel(level)
        val nextLevelXpStart = getXpThresholdForLevel(level + 1)
        
        val xpNeededForNextLevel = nextLevelXpStart - currentLevelXpStart
        val xpProgressInLevel = totalXp - currentLevelXpStart
        val progressPercent = if (xpNeededForNextLevel > 0) xpProgressInLevel.toFloat() / xpNeededForNextLevel else 0f
        
        val baseProfitCoins = (sales.sumOf { it.totalProfit } * 0.1).toInt()
        val baseShopCoins = shops.size * 20
        val baseSalesCoins = sales.size * 10
        val baseBadgeCoins = badges.size * 100
        
        val totalCoins = baseProfitCoins + baseShopCoins + baseSalesCoins + baseBadgeCoins + bonusCoins
        val rank = getRankForXp(totalXp)
        val streak = calculateStreak(sales)
        val title = getTitleForLevel(level)
        
        val now = Date()
        val dayId = sdfDay.format(now)
        val weekId = sdfWeek.format(now)
        val monthId = sdfMonth.format(now)
        
        val todayStart = getTodayStartMillis()
        val rollingWeekStart = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val rollingMonthStart = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
        
        val salesToday = sales.filter { it.entryDate >= todayStart }
        val shopsCreatedToday = shops.filter { it.startingDate >= todayStart }
        
        val salesThisWeek = sales.filter { it.entryDate >= rollingWeekStart }
        val salesThisMonth = sales.filter { it.entryDate >= rollingMonthStart }
        val shopsCreatedThisMonth = shops.filter { it.startingDate >= rollingMonthStart }

        val daily = listOf(
            Mission("daily_visit_3_shops_$dayId", "Shop Runner", "Make sales at 3 different shops today", salesToday.map { it.shopNumber }.distinct().size, 3, 50, 20, salesToday.map { it.shopNumber }.distinct().size >= 3, "daily"),
            Mission("daily_sell_50_packets_$dayId", "Packet Hustle", "Sell 50 snack packets today", salesToday.sumOf { it.packetsSold }, 50, 50, 30, salesToday.sumOf { it.packetsSold } >= 50, "daily"),
            Mission("daily_earn_profit_$dayId", "Profit Maker", "Earn ₹500 profit today", salesToday.sumOf { it.totalProfit }.toInt(), 500, 60, 40, salesToday.sumOf { it.totalProfit } >= 500.0, "daily"),
            Mission("daily_add_shop_$dayId", "Network Builder", "Register 1 new shop today", shopsCreatedToday.size, 1, 80, 50, shopsCreatedToday.size >= 1, "daily")
        )

        val weekly = listOf(
            Mission("weekly_visit_15_shops_$weekId", "Weekly Shop Marathon", "Sell to 15 different shops this week", salesThisWeek.map { it.shopNumber }.distinct().size, 15, 200, 100, salesThisWeek.map { it.shopNumber }.distinct().size >= 15, "weekly"),
            Mission("weekly_sell_300_packets_$weekId", "Bulk Distributor", "Sell 300 snack packets this week", salesThisWeek.sumOf { it.packetsSold }, 300, 250, 150, salesThisWeek.sumOf { it.packetsSold } >= 300, "weekly"),
            Mission("weekly_reach_revenue_$weekId", "Sales Grandmaster", "Reach ₹5,000 sales this week", salesThisWeek.sumOf { it.totalAmount }.toInt(), 5000, 300, 200, salesThisWeek.sumOf { it.totalAmount } >= 5000.0, "weekly")
        )

        val monthly = listOf(
            Mission("monthly_sell_1200_packets_$monthId", "Snack Titan", "Sell 1,200 snack packets this month", salesThisMonth.sumOf { it.packetsSold }, 1200, 1000, 500, salesThisMonth.sumOf { it.packetsSold } >= 1200, "monthly"),
            Mission("monthly_reach_sales_$monthId", "Empire Earnings", "Earn ₹20,000 sales this month", salesThisMonth.sumOf { it.totalAmount }.toInt(), 20000, 1200, 600, salesThisMonth.sumOf { it.totalAmount } >= 20000.0, "monthly"),
            Mission("monthly_add_5_shops_$monthId", "Territory Expansion", "Add 5 new shops this month", shopsCreatedThisMonth.size, 5, 800, 400, shopsCreatedThisMonth.size >= 5, "monthly")
        )

        val bossList = getBossChallenges(sales, shops)

        return GamificationState(
            level = level,
            xp = totalXp,
            xpNeededForNextLevel = nextLevelXpStart,
            xpProgress = progressPercent,
            coins = totalCoins,
            rank = rank,
            streak = streak,
            title = title,
            totalSalesCount = sales.size,
            totalShopsCount = shops.size,
            totalLocationsCount = locationsList.size,
            unlockedBadgesCount = badges.size,
            sessionCombo = combo,
            dailyMissions = daily,
            weeklyMissions = weekly,
            monthlyMissions = monthly,
            bossChallenges = bossList
        )
    }

    val gamificationState: StateFlow<GamificationState> = combine(
        repository.allSales,
        repository.allShops,
        repository.allLocations,
        repository.unlockedBadges,
        _bonusXp,
        _bonusCoins,
        _rewardedMissionIds,
        _sessionCombo
    ) { array ->
        val sales = array[0] as List<SalesEntry>
        val shops = array[1] as List<ShopMaster>
        val locs = array[2] as List<LocationMaster>
        val badges = array[3] as List<UserBadge>
        val bXp = array[4] as Int
        val bCoins = array[5] as Int
        val rIds = array[6] as Set<String>
        val combo = array[7] as Int
        calculateGamificationState(sales, shops, locs, badges, bXp, bCoins, rIds, combo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GamificationState())

    fun incrementSessionCombo() {
        val now = System.currentTimeMillis()
        if (now - lastSaleTime < 10 * 60 * 1000L) {
            _sessionCombo.value += 1
        } else {
            _sessionCombo.value = 1
        }
        lastSaleTime = now

        val currentCombo = _sessionCombo.value
        if (currentCombo >= 2) {
            val comboBonusXp = when {
                currentCombo >= 10 -> 100
                currentCombo >= 5 -> 50
                currentCombo >= 3 -> 20
                else -> 10
            }
            val comboBonusCoins = when {
                currentCombo >= 10 -> 50
                currentCombo >= 5 -> 25
                currentCombo >= 3 -> 10
                else -> 5
            }

            val newXp = _bonusXp.value + comboBonusXp
            val newCoins = _bonusCoins.value + comboBonusCoins
            prefs.edit()
                .putInt("bonus_xp", newXp)
                .putInt("bonus_coins", newCoins)
                .apply()

            _bonusXp.value = newXp
            _bonusCoins.value = newCoins

            viewModelScope.launch {
                _gamificationEvents.emit(GamificationEvent.ComboUpdate(currentCombo, comboBonusXp))
                _gamificationEvents.emit(GamificationEvent.XpGain(comboBonusXp, "Sales Combo x$currentCombo"))
                _gamificationEvents.emit(GamificationEvent.CoinGain(comboBonusCoins, "Sales Combo x$currentCombo"))
            }
        }
    }

    fun checkAndRewardMissions(
        sales: List<SalesEntry>,
        shops: List<ShopMaster>,
        unlockedBadges: List<UserBadge>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val todayStart = getTodayStartMillis()
            val rollingWeekStart = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            val rollingMonthStart = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
            
            val salesToday = sales.filter { it.entryDate >= todayStart }
            val shopsCreatedToday = shops.filter { it.startingDate >= todayStart }
            
            val salesThisWeek = sales.filter { it.entryDate >= rollingWeekStart }
            val salesThisMonth = sales.filter { it.entryDate >= rollingMonthStart }
            val shopsCreatedThisMonth = shops.filter { it.startingDate >= rollingMonthStart }

            val now = Date()
            val dayId = sdfDay.format(now)
            val weekId = sdfWeek.format(now)
            val monthId = sdfMonth.format(now)

            val potentialMissions = listOf(
                Mission("daily_visit_3_shops_$dayId", "Shop Runner", "Make sales at 3 different shops today", salesToday.map { it.shopNumber }.distinct().size, 3, 50, 20, salesToday.map { it.shopNumber }.distinct().size >= 3, "daily"),
                Mission("daily_sell_50_packets_$dayId", "Packet Hustle", "Sell 50 snack packets today", salesToday.sumOf { it.packetsSold }, 50, 50, 30, salesToday.sumOf { it.packetsSold } >= 50, "daily"),
                Mission("daily_earn_profit_$dayId", "Profit Maker", "Earn ₹500 profit today", salesToday.sumOf { it.totalProfit }.toInt(), 500, 60, 40, salesToday.sumOf { it.totalProfit } >= 500.0, "daily"),
                Mission("daily_add_shop_$dayId", "Network Builder", "Register 1 new shop today", shopsCreatedToday.size, 1, 80, 50, shopsCreatedToday.size >= 1, "daily"),
                
                Mission("weekly_visit_15_shops_$weekId", "Weekly Shop Marathon", "Sell to 15 different shops this week", salesThisWeek.map { it.shopNumber }.distinct().size, 15, 200, 100, salesThisWeek.map { it.shopNumber }.distinct().size >= 15, "weekly"),
                Mission("weekly_sell_300_packets_$weekId", "Bulk Distributor", "Sell 300 snack packets this week", salesThisWeek.sumOf { it.packetsSold }, 300, 250, 150, salesThisWeek.sumOf { it.packetsSold } >= 300, "weekly"),
                Mission("weekly_reach_revenue_$weekId", "Sales Grandmaster", "Reach ₹5,000 sales this week", salesThisWeek.sumOf { it.totalAmount }.toInt(), 5000, 300, 200, salesThisWeek.sumOf { it.totalAmount } >= 5000.0, "weekly"),
                
                Mission("monthly_sell_1200_packets_$monthId", "Snack Titan", "Sell 1,200 snack packets this month", salesThisMonth.sumOf { it.packetsSold }, 1200, 1000, 500, salesThisMonth.sumOf { it.packetsSold } >= 1200, "monthly"),
                Mission("monthly_reach_sales_$monthId", "Empire Earnings", "Earn ₹20,000 sales this month", salesThisMonth.sumOf { it.totalAmount }.toInt(), 20000, 1200, 600, salesThisMonth.sumOf { it.totalAmount } >= 20000.0, "monthly"),
                Mission("monthly_add_5_shops_$monthId", "Territory Expansion", "Add 5 new shops this month", shopsCreatedThisMonth.size, 5, 800, 400, shopsCreatedThisMonth.size >= 5, "monthly")
            )

            val rewarded = _rewardedMissionIds.value.toMutableSet()
            var bonusXpChange = 0
            var bonusCoinsChange = 0
            var updated = false

            for (m in potentialMissions) {
                if (m.progress >= m.target && !rewarded.contains(m.id)) {
                    rewarded.add(m.id)
                    bonusXpChange += m.xpReward
                    bonusCoinsChange += m.coinReward
                    updated = true
                    
                    _gamificationEvents.emit(GamificationEvent.MissionComplete(m.title))
                    _gamificationEvents.emit(GamificationEvent.XpGain(m.xpReward, "Mission: ${m.title}"))
                    _gamificationEvents.emit(GamificationEvent.CoinGain(m.coinReward, "Mission: ${m.title}"))
                }
            }

            val bossChallenges = getBossChallenges(sales, shops)
            for (b in bossChallenges) {
                val challengeId = "boss_challenge_${b.id}"
                if (b.progress >= b.target && !rewarded.contains(challengeId)) {
                    rewarded.add(challengeId)
                    bonusXpChange += b.xpReward
                    bonusCoinsChange += b.coinReward
                    updated = true
                    
                    _gamificationEvents.emit(GamificationEvent.BossDefeated(b.bossName))
                    _gamificationEvents.emit(GamificationEvent.XpGain(b.xpReward, "Boss Defeated: ${b.title}"))
                    _gamificationEvents.emit(GamificationEvent.CoinGain(b.coinReward, "Boss Defeated: ${b.title}"))
                }
            }

            if (updated) {
                val newXp = _bonusXp.value + bonusXpChange
                val newCoins = _bonusCoins.value + bonusCoinsChange
                
                prefs.edit()
                    .putInt("bonus_xp", newXp)
                    .putInt("bonus_coins", newCoins)
                    .putStringSet("rewarded_mission_ids", rewarded)
                    .apply()
                
                _bonusXp.value = newXp
                _bonusCoins.value = newCoins
                _rewardedMissionIds.value = rewarded
            }
        }
    }

    // --- Core Database Flows ---
    val dailyTarget: StateFlow<DailyTarget?> = repository.dailyTarget
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentDailySales: StateFlow<List<SalesEntry>> = repository.allSales
        .map { salesList ->
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val today = calendar.timeInMillis
            salesList.filter { it.entryDate >= today }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayPackets = currentDailySales.map { it.sumOf { s -> s.packetsSold } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val todaySales = currentDailySales.map { it.sumOf { s -> s.totalAmount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val todayProfit = currentDailySales.map { it.sumOf { s -> s.totalProfit } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun setDailyTarget(target: DailyTarget) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertDailyTarget(target)
    }

    fun calculateHealthScore(shop: ShopMaster, sales: List<SalesEntry>): Int {
        if (sales.isEmpty()) return 50 // Default for new shops

        val shopSales = sales.filter { it.shopNumber == shop.shopNumber }
        if (shopSales.isEmpty()) return 50

        // Metrics (simplified)
        val totalSales = shopSales.sumOf { it.totalAmount }
        val totalProfit = shopSales.sumOf { it.totalProfit }
        val returns = shopSales.sumOf { it.packetsReturned }
        val lastVisit = shopSales.maxOfOrNull { it.entryDate } ?: 0L
        val rating = shop.rating
        
        // Scoring (0-100)
        var score = 0.0
        
        // Sales/Profit (e.g., scale up to 40 points)
        score += (totalSales.coerceIn(0.0, 50000.0) / 50000.0) * 20
        score += (totalProfit.coerceIn(0.0, 10000.0) / 10000.0) * 20
        
        // Frequency (e.g., 20 points)
        val frequency = shopSales.size
        score += (frequency.coerceIn(0, 30) / 30.0) * 20
        
        // Rating (e.g., 20 points)
        score += (rating.coerceIn(0f, 5f) / 5f) * 20
        
        // Returns penalty (e.g., up to -10 points)
        val returnRate = if (shopSales.sumOf { it.packetsSold } > 0) returns.toDouble() / shopSales.sumOf { it.packetsSold } else 0.0
        score -= returnRate * 20
        
        return score.toInt().coerceIn(0, 100)
    }

    fun getHealthCategory(score: Int): String {
        return when {
            score >= 90 -> "Excellent"
            score >= 75 -> "Good"
            score >= 50 -> "Average"
            else -> "Poor"
        }
    }

    // --- Monthly Growth Analysis ---
    data class MonthlyMetrics(
        val packets: Int,
        val sales: Double,
        val profit: Double
    )

    data class MonthlyGrowthData(
        val currentMonth: MonthlyMetrics,
        val previousMonth: MonthlyMetrics,
        val packetsGrowthPercent: Double,
        val salesGrowthPercent: Double,
        val profitGrowthPercent: Double
    )

    val monthlyGrowth: StateFlow<MonthlyGrowthData?> = repository.allSales
        .map { salesList ->
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)
            
            calendar.add(Calendar.MONTH, -1)
            val prevMonth = calendar.get(Calendar.MONTH)
            val prevYear = calendar.get(Calendar.YEAR)
            
            fun getMetricsForMonth(month: Int, year: Int): MonthlyMetrics {
                val filtered = salesList.filter {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = it.entryDate
                    cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year
                }
                return MonthlyMetrics(
                    packets = filtered.sumOf { it.packetsSold },
                    sales = filtered.sumOf { it.totalAmount },
                    profit = filtered.sumOf { it.totalProfit }
                )
            }

            val current = getMetricsForMonth(currentMonth, currentYear)
            val previous = getMetricsForMonth(prevMonth, prevYear)

            fun calculateGrowth(current: Double, previous: Double): Double {
                if (previous == 0.0) return if (current > 0) 100.0 else 0.0
                return ((current - previous) / previous) * 100
            }

            MonthlyGrowthData(
                currentMonth = current,
                previousMonth = previous,
                packetsGrowthPercent = calculateGrowth(current.packets.toDouble(), previous.packets.toDouble()),
                salesGrowthPercent = calculateGrowth(current.sales, previous.sales),
                profitGrowthPercent = calculateGrowth(current.profit, previous.profit)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Badge Flows ---
    val allBadges: StateFlow<List<Badge>> = repository.allBadges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val unlockedBadges: StateFlow<List<UserBadge>> = repository.unlockedBadges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timetable: StateFlow<List<TimetableEntry>> = repository.allTimetableEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val locations: StateFlow<List<LocationMaster>> = repository.allLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales: StateFlow<List<SalesEntry>> = repository.allSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductMaster>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val salesSearchQuery = MutableStateFlow("")
    val salesFilterShopNumber = MutableStateFlow<String?>(null)

    fun setSalesSearchQuery(query: String) {
        salesSearchQuery.value = query
    }

    fun setSalesFilterShopNumber(shopNumber: String?) {
        salesFilterShopNumber.value = shopNumber
    }

    private val _resolvedUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolvedUrls: StateFlow<Map<String, String>> = _resolvedUrls.asStateFlow()

    fun triggerUrlResolution(urls: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentMap = _resolvedUrls.value.toMutableMap()
            var changed = false
            for (url in urls) {
                if (url.isBlank() || currentMap.containsKey(url)) continue
                if (url.contains("goo.gl") || url.contains("maps.app.goo.gl")) {
                    val resolved = resolveShortenedUrlRecursively(url)
                    if (resolved != url) {
                        currentMap[url] = resolved
                        changed = true
                    }
                }
            }
            if (changed) {
                _resolvedUrls.value = currentMap
            }
        }
    }

    private fun resolveShortenedUrlRecursively(urlStr: String, maxHops: Int = 3): String {
        val client = okhttp3.OkHttpClient.Builder()
            .followRedirects(true)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        try {
            val request = okhttp3.Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val bodyText = response.body?.string() ?: ""
                
                // If coordinates are in the final URL, we return that
                val coordsInUrl = extractCoordinatesFromText(finalUrl)
                if (coordsInUrl != null) {
                    return finalUrl
                }
                
                // If not, maybe we can find coordinates in the HTML body
                val coordsInBody = extractCoordinatesFromText(bodyText)
                if (coordsInBody != null) {
                    return bodyText
                }
                
                return finalUrl
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return urlStr
    }

    private val _nearestQueryCoords = MutableStateFlow<Pair<Double, Double>?>(null)
    val nearestQueryCoords: StateFlow<Pair<Double, Double>?> = _nearestQueryCoords.asStateFlow()

    private val _isResolvingQuery = MutableStateFlow(false)
    val isResolvingQuery: StateFlow<Boolean> = _isResolvingQuery.asStateFlow()

    private var resolveQueryJob: kotlinx.coroutines.Job? = null

    fun resolveNearestQueryCoords(context: Context, query: String) {
        resolveQueryJob?.cancel()
        if (query.isBlank()) {
            _nearestQueryCoords.value = null
            _isResolvingQuery.value = false
            return
        }

        resolveQueryJob = viewModelScope.launch(Dispatchers.IO) {
            _isResolvingQuery.value = true
            try {
                val trimmed = query.trim()
                
                // 1. Try to extract coordinates directly from the string
                var coords = extractCoordinatesFromText(trimmed)
                if (coords != null) {
                    _nearestQueryCoords.value = coords
                    return@launch
                }

                // 2. Try to resolve if it is a Google Maps shortened URL
                if (trimmed.contains("goo.gl") || trimmed.contains("maps.app.goo.gl")) {
                    val resolved = resolveShortenedUrlRecursively(trimmed)
                    coords = extractCoordinatesFromText(resolved)
                    if (coords != null) {
                        _nearestQueryCoords.value = coords
                        return@launch
                    }
                }

                // 3. Try Offline Geocoder
                coords = offlineGeocode(trimmed)
                if (coords != null) {
                    _nearestQueryCoords.value = coords
                    return@launch
                }

                // 4. Try Geocoder for address-based lookup
                coords = geocodeAddress(context, trimmed)
                if (coords != null) {
                    _nearestQueryCoords.value = coords
                    return@launch
                }

                // 5. Try matching with pre-defined locations in DB
                val currentLocations = repository.allLocations.firstOrNull() ?: emptyList()
                val matchedLoc = currentLocations.firstOrNull {
                    it.locationNumber.equals(trimmed, ignoreCase = true) ||
                    it.locationName.contains(trimmed, ignoreCase = true)
                }
                if (matchedLoc != null) {
                    // Try to get center coords for this location based on its shops
                    val currentShops = repository.allShops.firstOrNull() ?: emptyList()
                    val shopsInLoc = currentShops.filter { it.locationNumber == matchedLoc.locationNumber }
                    val shopCoords = shopsInLoc.mapNotNull { shop ->
                        if (shop.latitude != null && shop.longitude != null) {
                            Pair(shop.latitude, shop.longitude)
                        } else {
                            val link = shop.googleMapLink
                            if (!link.isNullOrEmpty()) {
                                extractCoordinatesFromText(link) ?: extractCoordinatesFromText(resolveShortenedUrlRecursively(link))
                            } else null
                        }
                    }
                    if (shopCoords.isNotEmpty()) {
                        coords = Pair(shopCoords.map { it.first }.average(), shopCoords.map { it.second }.average())
                    } else {
                        val offlineLoc = offlineGeocode(matchedLoc.locationName)
                        if (offlineLoc != null) {
                            coords = offlineLoc
                        } else {
                            // Fallback to location-based deterministic offset from a central point (Bangalore)
                            val idx = currentLocations.indexOf(matchedLoc)
                            coords = Pair(12.971598 + 0.005 * (idx + 1), 77.594562 + 0.005 * (idx + 1))
                        }
                    }
                    _nearestQueryCoords.value = coords
                    return@launch
                }

                // 6. Try matching with shop names in DB
                val currentShops = repository.allShops.firstOrNull() ?: emptyList()
                val matchedShop = currentShops.firstOrNull {
                    it.storeName.contains(trimmed, ignoreCase = true) ||
                    it.shopNumber.equals(trimmed, ignoreCase = true)
                }
                if (matchedShop != null) {
                    if (matchedShop.latitude != null && matchedShop.longitude != null) {
                        coords = Pair(matchedShop.latitude, matchedShop.longitude)
                    } else if (!matchedShop.googleMapLink.isNullOrEmpty()) {
                        val link = matchedShop.googleMapLink
                        coords = extractCoordinatesFromText(link) ?: extractCoordinatesFromText(resolveShortenedUrlRecursively(link))
                    }
                    if (coords != null) {
                        _nearestQueryCoords.value = coords
                        return@launch
                    }
                }

                _nearestQueryCoords.value = null
            } catch (e: Exception) {
                e.printStackTrace()
                _nearestQueryCoords.value = null
            } finally {
                _isResolvingQuery.value = false
            }
        }
    }

    fun extractCoordinatesFromText(text: String): Pair<Double, Double>? {
        if (text.isBlank()) return null
        val decoded = try {
            java.net.URLDecoder.decode(text, "UTF-8")
        } catch (e: Exception) {
            text
        }

        // 1. Try to find @lat,lng format
        val atPattern = Regex("@(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)")
        atPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        // 2. Try to find parameter pattern e.g. q=lat,lng or query=lat,lng
        val paramPattern = Regex("(?:[?&](?:q|query|daddr|saddr|ll|cbll)=)(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)")
        paramPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        // 2.5 Try to find place, dir or search path pattern: e.g. /place/lat,lng or /dir/lat,lng
        val pathPattern = Regex("/(?:place|dir|search)/(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)")
        pathPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        // 3. Try DMS format: e.g. 12°58'17.8"N 77°35'40.4"E
        fun parseDMS(deg: String, min: String, sec: String, dir: String): Double? {
            val d = deg.toDoubleOrNull() ?: return null
            val m = min.toDoubleOrNull() ?: 0.0
            val s = sec.toDoubleOrNull() ?: 0.0
            var decimal = d + (m / 60.0) + (s / 3600.0)
            if (dir.equals("S", ignoreCase = true) || dir.equals("W", ignoreCase = true)) {
                decimal = -decimal
            }
            return decimal
        }

        val dmsRegex = Regex("(\\d+)[°\\s]+(\\d+)[\\'\\s]+(\\d+(?:\\.\\d+)?)\"?\\s*([NSEWnsew])")
        val dmsMatches = dmsRegex.findAll(decoded).toList()
        if (dmsMatches.size >= 2) {
            val lat = parseDMS(dmsMatches[0].groupValues[1], dmsMatches[0].groupValues[2], dmsMatches[0].groupValues[3], dmsMatches[0].groupValues[4])
            val lng = parseDMS(dmsMatches[1].groupValues[1], dmsMatches[1].groupValues[2], dmsMatches[1].groupValues[3], dmsMatches[1].groupValues[4])
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        // 4. Try generic decimal pair: e.g. "12.971598, 77.594562"
        val genericPattern = Regex("(-?\\d{1,3}\\.\\d+)[\\s,]+(-?\\d{1,3}\\.\\d+)")
        genericPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        return null
    }

    private fun geocodeAddress(context: Context, address: String): Pair<Double, Double>? {
        return try {
            val geocoder = android.location.Geocoder(context)
            val addresses = geocoder.getFromLocationName(address, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                Pair(addr.latitude, addr.longitude)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun offlineGeocode(address: String): Pair<Double, Double>? {
        val clean = address.trim().lowercase()
        val dict = mapOf(
            "bangalore" to Pair(12.971598, 77.594562),
            "bengaluru" to Pair(12.971598, 77.594562),
            "indiranagar" to Pair(12.9640, 77.6385),
            "koramangala" to Pair(12.9352, 77.6244),
            "whitefield" to Pair(12.9698, 77.7500),
            "jayanagar" to Pair(12.9307, 77.5832),
            "m.g. road" to Pair(12.9738, 77.6119),
            "mg road" to Pair(12.9738, 77.6119),
            "malleshwaram" to Pair(13.0031, 77.5643),
            "hsr layout" to Pair(12.9116, 77.6388),
            "hebbal" to Pair(13.0359, 77.5970),
            "electronic city" to Pair(12.8452, 77.6602),
            "marathahalli" to Pair(12.9569, 77.7011),
            "btm layout" to Pair(12.9166, 77.6101),
            "rajajinagar" to Pair(12.9901, 77.5525),
            "banashankari" to Pair(12.9254, 77.5468),
            "yeshwanthpur" to Pair(13.0232, 77.5529),
            "bellandur" to Pair(12.9304, 77.6784),
            "yelahanka" to Pair(13.1007, 77.5963),
            "bannerghatta" to Pair(12.8063, 77.5772),
            "mysore" to Pair(12.2958, 76.6394),
            "mysuru" to Pair(12.2958, 76.6394),
            "mangalore" to Pair(12.9141, 74.8560),
            "mangaluru" to Pair(12.9141, 74.8560),
            "hubli" to Pair(15.3647, 75.1240),
            "belgaum" to Pair(15.8497, 74.4977),
            "dharwad" to Pair(15.4589, 75.0078),
            "delhi" to Pair(28.6139, 77.2090),
            "new delhi" to Pair(28.6139, 77.2090),
            "mumbai" to Pair(19.0760, 72.8777),
            "bombay" to Pair(19.0760, 72.8777),
            "chennai" to Pair(13.0827, 80.2707),
            "madras" to Pair(13.0827, 80.2707),
            "kolkata" to Pair(22.5726, 88.3639),
            "calcutta" to Pair(22.5726, 88.3639),
            "hyderabad" to Pair(17.3850, 78.4867),
            "pune" to Pair(18.5204, 73.8567),
            "ahmedabad" to Pair(23.0225, 72.5714),
            "jaipur" to Pair(26.9124, 75.7873),
            "kochi" to Pair(9.9312, 76.2673),
            "cochin" to Pair(9.9312, 76.2673)
        )
        for ((key, coords) in dict) {
            if (clean.contains(key)) {
                return coords
            }
        }
        return null
    }

    private val _isResolvingCoordinates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isResolvingCoordinates: StateFlow<Map<String, Boolean>> = _isResolvingCoordinates.asStateFlow()

    private val _coordinateResolutionError = MutableStateFlow<Map<String, String?>>(emptyMap())
    val coordinateResolutionError: StateFlow<Map<String, String?>> = _coordinateResolutionError.asStateFlow()

    sealed class CoordinateResolutionResult {
        data class Success(val latitude: Double, val longitude: Double) : CoordinateResolutionResult()
        data class Failure(val reason: String) : CoordinateResolutionResult()
    }

    suspend fun resolveCoordinatesForLinkOrQuery(
        context: Context,
        link: String?,
        storeName: String,
        locationNumber: String
    ): CoordinateResolutionResult {
        if (!link.isNullOrBlank()) {
            val trimmedLink = link.trim()
            
            // 1. Check if it's a valid URL structure
            if (!trimmedLink.startsWith("http://") && !trimmedLink.startsWith("https://")) {
                return CoordinateResolutionResult.Failure("Invalid Google Maps Link")
            }
            if (!trimmedLink.contains("google.com") && !trimmedLink.contains("goo.gl") && !trimmedLink.contains("google.co")) {
                return CoordinateResolutionResult.Failure("Invalid Google Maps Link")
            }

            // 2. Extract directly if possible (e.g. long link)
            val directCoords = extractCoordinatesFromText(trimmedLink)
            if (directCoords != null) {
                val (lat, lng) = directCoords
                if (lat in -90.0..90.0 && lng in -180.0..180.0 && (lat != 0.0 || lng != 0.0)) {
                    return CoordinateResolutionResult.Success(lat, lng)
                }
            }

            // 3. Resolve shortened URL if needed
            var finalUrl = trimmedLink
            var bodyText: String? = null
            var networkErrorOccurred = false
            var resolutionFailed = false

            if (trimmedLink.contains("goo.gl") || trimmedLink.contains("maps.app.goo.gl")) {
                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                try {
                    val request = okhttp3.Request.Builder()
                        .url(trimmedLink)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful && response.code >= 400) {
                            resolutionFailed = true
                        } else {
                            finalUrl = response.request.url.toString()
                            bodyText = response.body?.string()
                        }
                    }
                } catch (e: java.net.UnknownHostException) {
                    networkErrorOccurred = true
                } catch (e: java.io.IOException) {
                    networkErrorOccurred = true
                } catch (e: Exception) {
                    resolutionFailed = true
                }
            }

            if (networkErrorOccurred) {
                return CoordinateResolutionResult.Failure("Network Error")
            }
            if (resolutionFailed) {
                return CoordinateResolutionResult.Failure("Unable to Resolve Short URL")
            }

            // 4. Try extracting from resolved URL or body
            val resolvedCoords = extractCoordinatesFromText(finalUrl)
            if (resolvedCoords != null) {
                val (lat, lng) = resolvedCoords
                if (lat in -90.0..90.0 && lng in -180.0..180.0 && (lat != 0.0 || lng != 0.0)) {
                    return CoordinateResolutionResult.Success(lat, lng)
                }
            }

            if (!bodyText.isNullOrBlank()) {
                val bodyCoords = extractCoordinatesFromText(bodyText)
                if (bodyCoords != null) {
                    val (lat, lng) = bodyCoords
                    if (lat in -90.0..90.0 && lng in -180.0..180.0 && (lat != 0.0 || lng != 0.0)) {
                        return CoordinateResolutionResult.Success(lat, lng)
                    }
                }
            }

            // If we had a Google Maps link but couldn't get coordinates, return Coordinates Not Found
            return CoordinateResolutionResult.Failure("Coordinates Not Found")
        }

        // 5. Fallback to geocoding (offline then online)
        val location = repository.getLocationByNumber(locationNumber)
        val locationName = location?.locationName ?: ""
        val queryText = if (locationName.isNotEmpty()) {
            "$storeName, $locationName"
        } else {
            storeName
        }

        val offlineResult = offlineGeocode(queryText)
        if (offlineResult != null) {
            return CoordinateResolutionResult.Success(offlineResult.first, offlineResult.second)
        }

        val geocoded = geocodeAddress(context, queryText)
        if (geocoded != null) {
            return CoordinateResolutionResult.Success(geocoded.first, geocoded.second)
        }

        if (locationName.isNotEmpty()) {
            val offlineLoc = offlineGeocode(locationName)
            if (offlineLoc != null) {
                return CoordinateResolutionResult.Success(offlineLoc.first, offlineLoc.second)
            }
            val geocodedLoc = geocodeAddress(context, locationName)
            if (geocodedLoc != null) {
                return CoordinateResolutionResult.Success(geocodedLoc.first, geocodedLoc.second)
            }
        }

        return CoordinateResolutionResult.Failure("Geocoding Failed")
    }

    fun resolveAndSaveCoordinatesForShop(context: Context, shop: ShopMaster) {
        viewModelScope.launch(Dispatchers.IO) {
            val shopNo = shop.shopNumber
            _isResolvingCoordinates.value = _isResolvingCoordinates.value + (shopNo to true)
            _coordinateResolutionError.value = _coordinateResolutionError.value + (shopNo to null)
            try {
                val resolvedShop = resolveShopCoords(context, shop)
                repository.updateShop(shopNo, resolvedShop)
                if (resolvedShop.coordinateStatus == "Invalid") {
                    _coordinateResolutionError.value = _coordinateResolutionError.value + (shopNo to resolvedShop.coordinateError)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _coordinateResolutionError.value = _coordinateResolutionError.value + (shopNo to "Unable to retrieve location coordinates: ${e.localizedMessage}")
            } finally {
                _isResolvingCoordinates.value = _isResolvingCoordinates.value + (shopNo to false)
            }
        }
    }

    suspend fun resolveShopCoords(context: Context, shop: ShopMaster): ShopMaster {
        // 1. If the shop already has coordinates and status, and map link hasn't changed, preserve them
        try {
            val oldShop = repository.getShopByNumber(shop.shopNumber)
            if (oldShop != null && oldShop.googleMapLink == shop.googleMapLink && oldShop.latitude != null && oldShop.longitude != null && oldShop.coordinateStatus == "Valid") {
                return shop.copy(
                    latitude = oldShop.latitude,
                    longitude = oldShop.longitude,
                    coordinateStatus = oldShop.coordinateStatus,
                    lastCoordinateUpdate = oldShop.lastCoordinateUpdate ?: System.currentTimeMillis(),
                    coordinateError = oldShop.coordinateError
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return when (val result = resolveCoordinatesForLinkOrQuery(context, shop.googleMapLink, shop.storeName, shop.locationNumber)) {
            is CoordinateResolutionResult.Success -> {
                shop.copy(
                    latitude = result.latitude,
                    longitude = result.longitude,
                    coordinateStatus = "Valid",
                    lastCoordinateUpdate = System.currentTimeMillis(),
                    coordinateError = null
                )
            }
            is CoordinateResolutionResult.Failure -> {
                shop.copy(
                    latitude = null,
                    longitude = null,
                    coordinateStatus = "Invalid",
                    lastCoordinateUpdate = System.currentTimeMillis(),
                    coordinateError = result.reason
                )
            }
        }
    }

    val shops: StateFlow<List<ShopMaster>> = combine(
        repository.allShops,
        repository.allSales
    ) { allShops, allSales ->
        allShops.map { shop ->
            val salesForShop = allSales.filter { it.shopNumber == shop.shopNumber }
            val firstPurchaseDate = salesForShop.minOfOrNull { it.entryDate }
            val finalStartingDate = firstPurchaseDate ?: shop.startingDate
            val analytics = com.example.utils.RatingCalculator.calculateAnalytics(salesForShop)
            shop.copy(
                startingDate = finalStartingDate,
                rating = analytics.currentRating,
                score = (analytics.currentRating * 20).toInt()
            )
        }
    }.onEach { shopList ->
        val urls = shopList.mapNotNull { it.googleMapLink }
        triggerUrlResolution(urls)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Next Shop Number State ---
    private val _nextShopNumber = MutableStateFlow("1")
    val nextShopNumber: StateFlow<String> = _nextShopNumber.asStateFlow()

    private val _shopLocationFilter = MutableStateFlow<String?>(null)
    val shopLocationFilter: StateFlow<String?> = _shopLocationFilter.asStateFlow()

    // Pre-filled data for Sales Entry
    private val _prefilledSaleData = MutableStateFlow<Triple<String, String, String>?>(null) // ShopNumber, ShopName, LocationName
    val prefilledSaleData: StateFlow<Triple<String, String, String>?> = _prefilledSaleData.asStateFlow()

    fun setShopLocationFilter(locationNumber: String?) {
        _shopLocationFilter.value = locationNumber
    }

    fun setPrefilledSaleData(shopNumber: String?, shopName: String?, locationName: String?) {
        if (shopNumber != null && shopName != null && locationName != null) {
            _prefilledSaleData.value = Triple(shopNumber, shopName, locationName)
        } else {
            _prefilledSaleData.value = null
        }
    }

    init {
        refreshNextShopNumber()
        viewModelScope.launch(Dispatchers.IO) {
            repository.initializeTimetableIfNeeded()
            
            // Populate badges if empty
            if (repository.allBadges.first().isEmpty()) {
                val predefined = listOf(
                    Badge("first_sale", "First Sale", "Completed your very first sale!", "ic_badge_first_sale"),
                    Badge("100_shops", "100 Shops Added", "Added 100 shops to your network!", "ic_badge_100_shops"),
                    Badge("1000_packets", "1,000 Packets Sold", "Sold over 1,000 packets!", "ic_badge_1000_packets"),
                    Badge("10000_profit", "₹10,000 Profit", "Generated ₹10,000 in profit!", "ic_badge_10000_profit"),
                    Badge("1000_sales", "1,000 Sales", "Completed 1,000 individual sales!", "ic_badge_1000_sales"),
                    Badge("10000_packets", "10,000 Packets Sold", "Sold over 10,000 snack packets!", "ic_badge_10000_packets"),
                    Badge("100000_sales", "₹1,00,000 Revenue", "Reached ₹1,00,000 in total revenue!", "ic_badge_100000_sales"),
                    Badge("50000_profit", "₹50,000 Profit", "Earned ₹50,000 in pure business profit!", "ic_badge_50000_profit"),
                    Badge("100_day_streak", "Century Streak", "Maintained a consistent 100-day business streak!", "ic_badge_100_day_streak")
                )
                predefined.forEach { repository.insertBadge(it) }
            }
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            combine(repository.allSales, repository.allShops) { s, sh ->
                Pair(s, sh)
            }.collect { (s, sh) ->
                checkAndUnlockBadges(s, sh)
            }
        }

        // Monitoring level changes for celebrations
        var previousLevel = -1
        viewModelScope.launch {
            gamificationState.collect { state ->
                if (previousLevel == -1) {
                    previousLevel = state.level
                } else if (state.level > previousLevel) {
                    _gamificationEvents.emit(GamificationEvent.LevelUp(state.level, state.title))
                    previousLevel = state.level
                }
            }
        }

        // Reactive tracking of database changes for awarding live event feed popups
        var previousShopsCount = -1
        var previousSalesCount = -1
        var previousPacketsCount = -1

        viewModelScope.launch {
            combine(repository.allShops, repository.allSales) { sh, sa -> Pair(sh, sa) }
                .collect { (shops, sales) ->
                    if (previousShopsCount == -1) {
                        previousShopsCount = shops.size
                        previousSalesCount = sales.size
                        previousPacketsCount = sales.sumOf { it.packetsSold }
                    } else {
                        val shopDiff = shops.size - previousShopsCount
                        if (shopDiff > 0) {
                            _gamificationEvents.emit(GamificationEvent.XpGain(shopDiff * 50, "Shop Registered"))
                            _gamificationEvents.emit(GamificationEvent.CoinGain(shopDiff * 20, "Shop Registered"))
                        }
                        
                        val saleDiff = sales.size - previousSalesCount
                        if (saleDiff > 0) {
                            val packetDiff = sales.sumOf { it.packetsSold } - previousPacketsCount
                            _gamificationEvents.emit(GamificationEvent.XpGain(saleDiff * 10 + packetDiff * 1, "Sales Completed"))
                            _gamificationEvents.emit(GamificationEvent.CoinGain(saleDiff * 10, "Sales Completed"))
                        }
                        
                        previousShopsCount = shops.size
                        previousSalesCount = sales.size
                        previousPacketsCount = sales.sumOf { it.packetsSold }
                    }
                    
                    val badges = repository.unlockedBadges.first()
                    checkAndRewardMissions(sales, shops, badges)
                }
        }
    }

    private suspend fun checkAndUnlockBadges(sales: List<SalesEntry>, shops: List<ShopMaster>) {
        val currentlyUnlocked = repository.unlockedBadges.first().map { it.badgeId }.toSet()
        val totalPackets = sales.sumOf { it.packetsSold }
        val totalProfit = sales.sumOf { it.totalProfit }
        val totalRevenue = sales.sumOf { it.totalAmount }
        val streak = calculateStreak(sales)
        
        if ("first_sale" !in currentlyUnlocked && sales.isNotEmpty()) {
            repository.unlockBadge("first_sale")
            _gamificationEvents.emit(GamificationEvent.AchievementUnlocked("First Sale"))
        }
        if ("100_shops" !in currentlyUnlocked && shops.size >= 100) {
            repository.unlockBadge("100_shops")
            _gamificationEvents.emit(GamificationEvent.AchievementUnlocked("100 Shops Added"))
        }
        if ("1000_packets" !in currentlyUnlocked && totalPackets >= 1000) {
            repository.unlockBadge("1000_packets")
            _gamificationEvents.emit(GamificationEvent.AchievementUnlocked("1,000 Packets Sold"))
        }
        if ("10000_profit" !in currentlyUnlocked && totalProfit >= 10000) {
            repository.unlockBadge("10000_profit")
            _gamificationEvents.emit(GamificationEvent.AchievementUnlocked("₹10,000 Profit"))
        }
        if ("1000_sales" !in currentlyUnlocked && sales.size >= 1000) {
            repository.unlockBadge("1000_sales")
            _gamificationEvents.emit(GamificationEvent.AchievementUnlocked("1,000 Sales"))
        }
        if ("10000_packets" !in currentlyUnlocked && totalPackets >= 10000) {
            repository.unlockBadge("10000_packets")
            _gamificationEvents.emit(GamificationEvent.AchievementUnlocked("10,000 Packets Sold"))
        }
        if ("100000_sales" !in currentlyUnlocked && totalRevenue >= 100000.0) {
            repository.unlockBadge("100000_sales")
            _gamificationEvents.emit(GamificationEvent.AchievementUnlocked("₹1,00,000 Revenue"))
        }
        if ("50000_profit" !in currentlyUnlocked && totalProfit >= 50000.0) {
            repository.unlockBadge("50000_profit")
            _gamificationEvents.emit(GamificationEvent.AchievementUnlocked("₹50,000 Profit"))
        }
        if ("100_day_streak" !in currentlyUnlocked && streak >= 100) {
            repository.unlockBadge("100_day_streak")
            _gamificationEvents.emit(GamificationEvent.AchievementUnlocked("Century Streak"))
        }
    }

    fun updateTimetableEntry(entry: TimetableEntry) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateTimetableEntry(entry)
    }

    fun refreshNextShopNumber() {
        viewModelScope.launch {
            _nextShopNumber.value = repository.generateNextShopNumber()
        }
    }

    // --- CRUD Operations ---
    fun addLocation(location: LocationMaster) = viewModelScope.launch {
        try {
            repository.insertLocation(location)
        } catch (e: Exception) {
            triggerError(
                module = "Location Master",
                operation = "Add Location",
                exception = e,
                possibleReason = "The Location Number '${location.locationNumber}' might already exist, or some database fields are invalid."
            )
        }
    }

    fun updateLocation(location: LocationMaster) = viewModelScope.launch {
        try {
            repository.updateLocation(location)
        } catch (e: Exception) {
            triggerError(
                module = "Location Master",
                operation = "Update Location",
                exception = e,
                possibleReason = "An unexpected error occurred while updating the Location Master."
            )
        }
    }

    fun deleteLocation(location: LocationMaster) = viewModelScope.launch {
        try {
            repository.deleteLocation(location)
        } catch (e: Exception) {
            triggerError(
                module = "Location Master",
                operation = "Delete Location",
                exception = e,
                possibleReason = "This location could not be deleted. It may be referenced by existing Shop Master records."
            )
        }
    }

    fun deleteAllLocations() = viewModelScope.launch {
        try {
            repository.deleteAllLocations()
        } catch (e: Exception) {
            triggerError(
                module = "Location Master",
                operation = "Delete All Locations",
                exception = e,
                possibleReason = "Unable to delete all locations. Existing references may block this action."
            )
        }
    }

    fun addShop(shop: ShopMaster) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val resolvedShop = resolveShopCoords(getApplication(), shop)
            repository.insertShop(resolvedShop)
            refreshNextShopNumber()
        } catch (e: Exception) {
            triggerError(
                module = "Shop Master",
                operation = "Add Shop",
                exception = e,
                possibleReason = "The Shop Number '${shop.shopNumber}' might already exist, or some database fields are invalid."
            )
        }
    }

    fun updateShop(oldShopNumber: String, shop: ShopMaster) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val resolvedShop = resolveShopCoords(getApplication(), shop)
            try {
                val oldShop = repository.getShopByNumber(oldShopNumber)
                if (oldShop != null && !oldShop.storeImage.isNullOrEmpty() && oldShop.storeImage != resolvedShop.storeImage) {
                    val oldFile = File(oldShop.storeImage)
                    if (oldFile.exists()) {
                        oldFile.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            repository.updateShop(oldShopNumber, resolvedShop)
            refreshNextShopNumber()
            // Run background image cleanup
            com.example.utils.BackupHelper.cleanupUnusedImages(getApplication())
        } catch (e: Exception) {
            triggerError(
                module = "Shop Master",
                operation = "Update Shop",
                exception = e,
                possibleReason = "An unexpected error occurred while updating the Shop Master."
            )
        }
    }

    fun deleteShop(shop: ShopMaster) = viewModelScope.launch {
        try {
            repository.deleteShop(shop)
            if (!shop.storeImage.isNullOrEmpty()) {
                try {
                    val file = File(shop.storeImage)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            refreshNextShopNumber()
            // Run background image cleanup
            com.example.utils.BackupHelper.cleanupUnusedImages(getApplication())
        } catch (e: Exception) {
            triggerError(
                module = "Shop Master",
                operation = "Delete Shop",
                exception = e,
                possibleReason = "Unable to delete this shop. There may be associated Sales Master entries."
            )
        }
    }

    fun deleteAllShops() = viewModelScope.launch {
        try {
            try {
                val filesDir = getApplication<Application>().filesDir
                val imageFiles = filesDir.listFiles { file ->
                    file.name.startsWith("shop_img_")
                }
                imageFiles?.forEach { it.delete() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            repository.deleteAllShops()
            refreshNextShopNumber()
        } catch (e: Exception) {
            triggerError(
                module = "Shop Master",
                operation = "Delete All Shops",
                exception = e,
                possibleReason = "Unable to delete all shops. Active Sales Master records may block this action."
            )
        }
    }

    // --- Excel Import State and Function ---
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importSummary = MutableStateFlow<com.example.utils.Exporter.ImportSummary?>(null)
    val importSummary: StateFlow<com.example.utils.Exporter.ImportSummary?> = _importSummary.asStateFlow()

    fun clearImportSummary() {
        _importSummary.value = null
    }

    fun importShopsFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importSummary.value = null
            try {
                val currentLocations = locations.value
                val currentShops = shops.value
                
                val summary = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.utils.Exporter.importShops(context, uri, currentLocations, currentShops)
                }
                
                if (summary.parsedShops.isNotEmpty()) {
                    val resolvedShops = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        summary.parsedShops.map { shop ->
                            async { resolveShopCoords(context, shop) }
                        }.awaitAll()
                    }
                    val invalidCoordsCount = resolvedShops.count { shop ->
                        shop.latitude == null || shop.longitude == null || (shop.latitude == 0.0 && shop.longitude == 0.0) || shop.latitude !in -90.0..90.0 || shop.longitude !in -180.0..180.0
                    }
                    repository.insertShops(resolvedShops)
                    refreshNextShopNumber()
                    // Clean up any old/orphaned shop images after importing
                    com.example.utils.BackupHelper.cleanupUnusedImages(context)
                    _importSummary.value = summary.copy(invalidCoordinatesCount = invalidCoordsCount)

                    // Log resolved shops with missing coordinates
                    resolvedShops.forEach { shop ->
                        if (shop.latitude == null || shop.longitude == null) {
                            triggerError(
                                module = "Coordinate Resolution",
                                operation = "Import Coordinate Parsing",
                                errorMessage = "Shop ${shop.shopNumber} (${shop.storeName}): Coordinates could not be resolved from link: ${shop.googleMapLink ?: "None"}",
                                stackTrace = "Coordinate status: ${shop.coordinateStatus}. Reason: ${shop.coordinateError ?: "No coordinate status"}",
                                possibleReason = "Action: Coordinates could not be resolved. Please verify the Google Maps Link manually in the Shop Master."
                            )
                        }
                    }
                } else {
                    _importSummary.value = summary
                }

                if (summary.skippedRows > 0) {
                    val msg = "Imported shops with ${summary.skippedRows} skipped/failed rows (Duplicates: ${summary.duplicateRecordsCount}, Invalid Locations: ${summary.invalidLocationNumbersCount}, Validation Failures: ${summary.failedRowsCount})"
                    triggerError(
                        module = "Shop Excel Import",
                        operation = "importShopsFromExcel",
                        errorMessage = msg,
                        stackTrace = "Skipped rows occurred during Excel parsing.",
                        possibleReason = "Ensure Location Numbers exist in Location Master, and there are no duplicate Shop Numbers."
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                triggerError(
                    module = "Shop Excel Import",
                    operation = "importShopsFromExcel",
                    exception = e,
                    possibleReason = "The Excel file might be corrupt, have incorrect headers, or contain invalid values."
                )
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun importLocationsFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importSummary.value = null
            try {
                val currentLocations = locations.value
                val summary = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.utils.Exporter.importLocations(context, uri, currentLocations)
                }
                if (summary.parsedLocations.isNotEmpty()) {
                    repository.insertLocations(summary.parsedLocations)
                }
                _importSummary.value = summary

                if (summary.skippedRows > 0) {
                    triggerError(
                        module = "Location Excel Import",
                        operation = "importLocationsFromExcel",
                        errorMessage = "Imported locations with ${summary.skippedRows} skipped rows (Duplicates: ${summary.duplicateRecordsCount}, Failures: ${summary.failedRowsCount})",
                        stackTrace = "Skipped rows during parsing.",
                        possibleReason = "Ensure that Location Numbers are unique."
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                triggerError(
                    module = "Location Excel Import",
                    operation = "importLocationsFromExcel",
                    exception = e,
                    possibleReason = "The Excel file might be corrupt, have incorrect columns, or contain invalid values."
                )
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun importSalesFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importSummary.value = null
            try {
                val currentShops = shops.value
                val currentProducts = products.value
                val allPrices = repository.getAllPrices()
                val currentSales = sales.value
                val summary = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.utils.Exporter.importSales(context, uri, currentShops, currentProducts, allPrices, currentSales)
                }
                if (summary.parsedSales.isNotEmpty()) {
                    repository.insertSalesList(summary.parsedSales)
                }
                _importSummary.value = summary

                if (summary.skippedRows > 0) {
                    triggerError(
                        module = "Sales Excel Import",
                        operation = "importSalesFromExcel",
                        errorMessage = "Imported sales with ${summary.skippedRows} skipped rows (Invalid Shops: ${summary.invalidShopNumbersCount}, Invalid Products: ${summary.invalidProductsCount}, Invalid Dates: ${summary.invalidDatesCount}, Failures: ${summary.failedRowsCount})",
                        stackTrace = "Skipped rows during parsing.",
                        possibleReason = "Ensure all referenced Shop Numbers and Product Names exist and dates are in correct format."
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                triggerError(
                    module = "Sales Excel Import",
                    operation = "importSalesFromExcel",
                    exception = e,
                    possibleReason = "The Excel file might be corrupt, or missing expected headers like Shop Number, Product, and Date."
                )
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun importProductsFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importSummary.value = null
            try {
                // Perform parsing on IO thread
                val summary = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.utils.Exporter.importProducts(context, uri)
                }
                
                var successCount = 0
                var updateCount = 0
                var duplicateCount = 0
                
                if (summary.parsedProducts.isNotEmpty()) {
                    summary.parsedProducts.forEach { (product, prices) ->
                        val trimmedName = product.productName.trim()
                        val existingProduct = products.value.find { it.productName.trim().equals(trimmedName, ignoreCase = true) }
                        
                        if (existingProduct != null) {
                            var needsProductUpdate = false
                            var updatedProduct = existingProduct
                            if (existingProduct.productCategory != product.productCategory || existingProduct.status != product.status) {
                                updatedProduct = existingProduct.copy(
                                    productCategory = product.productCategory,
                                    status = product.status
                                )
                                repository.updateProduct(updatedProduct)
                                needsProductUpdate = true
                            }
                            
                            val existingPrices = repository.getAllPrices().filter { it.productId == existingProduct.id }
                            var priceChanged = false
                            
                            prices.forEach { incomingPrice ->
                                val matchPrice = existingPrices.find { it.sellingPrice == incomingPrice.sellingPrice }
                                if (matchPrice != null) {
                                    if (matchPrice.profitPerPacket != incomingPrice.profitPerPacket) {
                                        repository.updatePrice(matchPrice.copy(profitPerPacket = incomingPrice.profitPerPacket))
                                        priceChanged = true
                                    }
                                } else {
                                    repository.insertPrice(incomingPrice.copy(productId = existingProduct.id))
                                    priceChanged = true
                                }
                            }
                            
                            if (needsProductUpdate || priceChanged) {
                                updateCount++
                            } else {
                                duplicateCount++
                            }
                        } else {
                            val newId = repository.insertProduct(product)
                            prices.forEach { price ->
                                repository.insertPrice(price.copy(productId = newId.toInt()))
                            }
                            successCount++
                        }
                    }
                }
                
                val finalSummary = summary.copy(
                    type = com.example.utils.Exporter.ImportType.PRODUCTS,
                    successfullyImported = successCount,
                    updatedRecordsCount = updateCount,
                    duplicateRecordsCount = duplicateCount,
                    skippedRows = summary.failedRowsCount + duplicateCount
                )
                _importSummary.value = finalSummary

                if (finalSummary.skippedRows > 0) {
                    triggerError(
                        module = "Product Excel Import",
                        operation = "importProductsFromExcel",
                        errorMessage = "Imported products with ${finalSummary.skippedRows} skipped rows (Duplicates: $duplicateCount, Failures: ${summary.failedRowsCount})",
                        stackTrace = "Skipped rows during parsing.",
                        possibleReason = "Ensure column types are numeric for pricing values."
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                triggerError(
                    module = "Product Excel Import",
                    operation = "importProductsFromExcel",
                    exception = e,
                    possibleReason = "The Excel file might be corrupt, or missing expected columns like Product Name, Category, Price, Profit."
                )
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun getPricesForProduct(productId: Int): Flow<List<ProductPrice>> {
        return repository.getPricesForProduct(productId)
    }

    suspend fun getAllPrices(): List<ProductPrice> {
        return repository.getAllPrices()
    }

    suspend fun addProduct(product: ProductMaster): Long {
        return try {
            repository.insertProduct(product)
        } catch (e: Exception) {
            triggerError(
                module = "Product Master",
                operation = "Add Product",
                exception = e,
                possibleReason = "A product with the name '${product.productName}' might already exist, or a database field is invalid."
            )
            -1L
        }
    }

    suspend fun addProductWithPrices(product: ProductMaster, prices: List<ProductPrice>) {
        try {
            repository.insertProductWithPrices(product, prices)
        } catch (e: Exception) {
            triggerError(
                module = "Product Master",
                operation = "Add Product with Prices",
                exception = e,
                possibleReason = "A product with the name '${product.productName}' might already exist, or its price configuration violates a constraint."
            )
            throw e
        }
    }

    fun updateProduct(product: ProductMaster) = viewModelScope.launch {
        try {
            repository.updateProduct(product)
        } catch (e: Exception) {
            triggerError(
                module = "Product Master",
                operation = "Update Product",
                exception = e,
                possibleReason = "An unexpected error occurred while updating the product details."
            )
        }
    }

    fun deleteProduct(product: ProductMaster) = viewModelScope.launch {
        try {
            repository.deleteProductWithPrices(product)
        } catch (e: Exception) {
            triggerError(
                module = "Product Master",
                operation = "Delete Product",
                exception = e,
                possibleReason = "Unable to delete product '${product.productName}'. It might be referenced by active sales records."
            )
        }
    }

    fun deletePricesForProduct(productId: Int) = viewModelScope.launch {
        try {
            repository.deletePricesForProduct(productId)
        } catch (e: Exception) {
            triggerError(
                module = "Product Master",
                operation = "Delete Product Prices",
                exception = e,
                possibleReason = "An unexpected database error occurred while removing product pricing."
            )
        }
    }

    fun addPrice(price: ProductPrice) = viewModelScope.launch {
        try {
            repository.insertPrice(price)
        } catch (e: Exception) {
            triggerError(
                module = "Product Master",
                operation = "Add Product Price",
                exception = e,
                possibleReason = "This product price configuration could not be added."
            )
        }
    }

    fun deleteAllProducts() = viewModelScope.launch {
        try {
            repository.deleteAllProducts()
        } catch (e: Exception) {
            triggerError(
                module = "Product Master",
                operation = "Delete All Products",
                exception = e,
                possibleReason = "Unable to delete all products. Existing references in other modules may block this."
            )
        }
    }

    fun addSales(salesEntry: SalesEntry) = viewModelScope.launch {
        try {
            repository.insertSales(salesEntry)
            incrementSessionCombo()
        } catch (e: Exception) {
            triggerError(
                module = "Sales Master",
                operation = "Add Sales Entry",
                exception = e,
                possibleReason = "A conflicting or duplicate sales entry may already exist."
            )
        }
    }

    fun updateSales(salesEntry: SalesEntry) = viewModelScope.launch {
        try {
            repository.updateSales(salesEntry)
        } catch (e: Exception) {
            triggerError(
                module = "Sales Master",
                operation = "Update Sales Entry",
                exception = e,
                possibleReason = "Unable to update sales details. Ensure all referenced fields are valid."
            )
        }
    }

    fun deleteSales(salesEntry: SalesEntry) = viewModelScope.launch {
        try {
            repository.deleteSales(salesEntry)
        } catch (e: Exception) {
            triggerError(
                module = "Sales Master",
                operation = "Delete Sales Entry",
                exception = e,
                possibleReason = "An unexpected error occurred while deleting the sales record."
            )
        }
    }
    
    fun deleteAllSales() = viewModelScope.launch {
        try {
            repository.deleteAllSales()
        } catch (e: Exception) {
            triggerError(
                module = "Sales Master",
                operation = "Delete All Sales Entries",
                exception = e,
                possibleReason = "Unable to empty the sales log database."
            )
        }
    }

    // --- Persistent Shop Image Saving Utility ---
    fun saveImageToStorage(uri: Uri): String? {
        val context = getApplication<Application>()
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: ""
            if (mimeType.isNotEmpty() && !mimeType.contains("jpeg") && !mimeType.contains("jpg") && !mimeType.contains("png") && !mimeType.contains("webp")) {
                throw IllegalArgumentException("Unsupported image format: '$mimeType'. Only JPG, PNG, and WEBP files are supported.")
            }
            val inputStream = contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException("Could not open source image stream.")
            val file = File(context.filesDir, "shop_img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                inputStream.copyTo(out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            triggerError(
                module = "Image Processing",
                operation = "Save Shop Image",
                exception = e,
                possibleReason = "The image file might be corrupt, stored on an inaccessible partition, or in an invalid format."
            )
            null
        }
    }

    // --- Backup & Restore Actions ---
    fun backupData(context: Context): Boolean {
        val backupDir = File(context.filesDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()
        val backupFile = File(backupDir, "snackroute_full_backup.zip")
        return com.example.utils.BackupHelper.createBackupZip(context, backupFile)
    }

    fun restoreData(context: Context): Boolean {
        val backupDir = File(context.filesDir, "backups")
        val backupFile = File(backupDir, "snackroute_full_backup.zip")
        if (!backupFile.exists()) return false
        return com.example.utils.BackupHelper.restoreBackupZip(context, backupFile)
    }

    fun restoreFromUri(context: Context, uri: Uri): Boolean {
        return com.example.utils.BackupHelper.restoreBackupFromUri(context, uri)
    }

    // --- Offline Heuristic Business Suggestions (AI Insights) ---
    val businessInsights: Flow<List<BusinessSuggestion>> = combine(
        locations, shops, products, sales
    ) { locs, shps, prods, sles ->
        val list = mutableListOf<BusinessSuggestion>()

        if (sles.isEmpty()) {
            list.add(
                BusinessSuggestion(
                    title = "Welcome aboard!",
                    message = "Let's log your first sales entry to generate powerful route distribution analytics.",
                    type = SuggestionType.INFO
                )
            )
            return@combine list
        }

        // Shop performances
        val shopSalesGroup = sles.groupBy { it.shopNumber }
        val shopProfits = shopSalesGroup.mapValues { (_, entries) -> entries.sumOf { it.totalProfit } }
        val shopQuantities = shopSalesGroup.mapValues { (_, entries) -> entries.sumOf { it.packetsSold } }

        // Alert low sales shops
        shps.forEach { shop ->
            val totalSold = shopQuantities[shop.shopNumber] ?: 0
            if (totalSold > 0 && totalSold < 5) {
                list.add(
                    BusinessSuggestion(
                        title = "Low Sales Warning",
                        message = "${shop.storeName} has low sales (${totalSold} packets). Visit this shop again with fresh cheese or caramel popcorn variants.",
                        type = SuggestionType.WARNING
                    )
                )
            }
        }

        // Location performance
        val locSalesGroup = sles.groupBy { it.locationNumber }
        val locProfits = locSalesGroup.mapValues { (_, entries) -> entries.sumOf { it.totalProfit } }
        val locNames = locs.associate { it.locationNumber to it.locationName }

        val bestLocEntry = locProfits.maxByOrNull { it.value }
        if (bestLocEntry != null && bestLocEntry.value > 0) {
            val locName = locNames[bestLocEntry.key] ?: bestLocEntry.key
            list.add(
                BusinessSuggestion(
                    title = "High Profit Location",
                    message = "Route $locName is extremely profitable. Allocate extra distribution resources here.",
                    type = SuggestionType.SUCCESS
                )
            )
        }

        // Product inventory & distribution suggestions
        val prodSales = sles.groupBy { it.productName }.mapValues { (_, entries) -> entries.sumOf { it.packetsSold } }
        val bestProd = prodSales.maxByOrNull { it.value }
        if (bestProd != null && bestProd.value > 10) {
            list.add(
                BusinessSuggestion(
                    title = "Increase Stock Allocation",
                    message = "${bestProd.key} is selling incredibly fast. Increase loading capacity for this snack variant in your distribution trucks today.",
                    type = SuggestionType.SUCCESS
                )
            )
        }

        val worstProd = prodSales.minByOrNull { it.value }
        if (worstProd != null && worstProd.value < 2 && prodSales.size > 2) {
            list.add(
                BusinessSuggestion(
                    title = "Review Stock Selection",
                    message = "${worstProd.key} is not selling well. Consider run-down promotions or replacing with banana chips/mixture varieties.",
                    type = SuggestionType.WARNING
                )
            )
        }

        // Top and Worst performing location metrics
        val worstLocEntry = locProfits.minByOrNull { it.value }
        if (worstLocEntry != null && locProfits.size > 1 && worstLocEntry.value < bestLocEntry!!.value * 0.3) {
            val locName = locNames[worstLocEntry.key] ?: worstLocEntry.key
            list.add(
                BusinessSuggestion(
                    title = "Distribution Review Required",
                    message = "Route $locName has low relative sales volume. Consider checking store visual placements or reviewing distributor drop frequencies.",
                    type = SuggestionType.WARNING
                )
            )
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- AI Chat Assistant State ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage(
            sender = "assistant",
            text = "Welcome to the SnackRoute Pro AI Business Assistant! 📊🤖\n\nI have real-time access to your Locations, Shops, Products, and Sales history.\n\nAsk me anything like:\n• \"Which product is the most profitable?\"\n• \"Which shop has the lowest sales volume?\"\n• \"Provide pricing strategies to boost my earnings.\"\n• \"Give me a summary of total sales and profits.\""
        )
    ))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun sendChatMessage(userQuestion: String) {
        if (userQuestion.isBlank()) return
        
        val userMsg = ChatMessage("user", userQuestion)
        _chatMessages.value = _chatMessages.value + userMsg
        _isChatLoading.value = true
        
        viewModelScope.launch(Dispatchers.IO) {
            val locList = locations.value
            val shpList = shops.value
            val prodList = products.value
            val salesList = sales.value
            
            // Fetch and group product price configurations
            val allPrices = repository.getAllPrices()
            val productPricesMap = allPrices.groupBy { it.productId }
            
            val contextPrompt = """
You are "SnackRoute Pro AI Assistant", a professional business intelligence advisor for snack distributors.
Below is the live distributor data from the application database.
CRITICAL INSTRUCTION: You MUST make your response extremely short, sweet, direct, and concise (ideally 1 to 3 sentences, maximum 50 words). Get straight to the point to answer the user's question, avoiding any unnecessary preambles or repeating the context.

--- DISTRIBUTOR BUSINESS DATA ---
Locations:
${locList.map { "- No: ${it.locationNumber}, Name: ${it.locationName}" }.joinToString("\n")}

Shops:
${shpList.map { "- No: ${it.shopNumber}, Name: ${it.storeName}, Loc: ${it.locationNumber}, Rating: ${it.rating}, Score: ${it.score}" }.joinToString("\n")}

Products & Prices:
${prodList.map { p -> "- ${p.productName} (Category: ${p.productCategory}): " + (productPricesMap[p.id] ?: emptyList()).joinToString(", ") { "Price ₹${it.sellingPrice} (Profit ₹${it.profitPerPacket})" } }.joinToString("\n")}

Recent Sales:
${salesList.take(50).map { "- Shop: ${it.shopName} (${it.shopNumber}), Product: ${it.productName}, Qty: ${it.packetsSold} pkts, Rate: ₹${it.ratePerPacket}, Profit: ₹${it.totalProfit}, Date: ${it.entryDateFormatted}" }.joinToString("\n")}
${if (salesList.size > 50) "... [Truncated for context size, total sales records: ${salesList.size}]" else ""}

Summary Analytics:
- Total Locations: ${locList.size}
- Total Shops: ${shpList.size}
- Total Products: ${prodList.size}
- Total Sales Recorded: ${salesList.size}
- Total Sales Volume: ${salesList.sumOf { it.packetsSold }} packets
- Total Revenue: ₹${salesList.sumOf { it.packetsSold * it.ratePerPacket }}
- Total Estimated Profit: ₹${salesList.sumOf { it.totalProfit }}
---------------------------------

User Question: $userQuestion
"""
            val reply = com.example.utils.GeminiHelper.queryGemini(contextPrompt, userGeminiApiKey.value)
            
            _chatMessages.value = _chatMessages.value + ChatMessage("assistant", reply)
            _isChatLoading.value = false
        }
    }

    fun clearChat() {
        _chatMessages.value = listOf(
            ChatMessage(
                sender = "assistant",
                text = "Welcome to the SnackRoute Pro AI Business Assistant! 📊🤖\n\nI have real-time access to your Locations, Shops, Products, and Sales history.\n\nAsk me anything like:\n• \"Which product is the most profitable?\"\n• \"Which shop has the lowest sales volume?\"\n• \"Provide pricing strategies to boost my earnings.\"\n• \"Give me a summary of total sales and profits.\""
            )
        )
    }
}

data class ChatMessage(
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SuggestionType {
    INFO, SUCCESS, WARNING
}

data class BusinessSuggestion(
    val title: String,
    val message: String,
    val type: SuggestionType
)
