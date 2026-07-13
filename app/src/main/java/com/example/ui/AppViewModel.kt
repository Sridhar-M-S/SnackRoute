package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import okhttp3.*
import java.util.concurrent.TimeUnit

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
        db.badgeDao()
    )

    // --- Preferences & custom Gemini API Key ---
    private val prefs = application.getSharedPreferences("snackroute_prefs", Context.MODE_PRIVATE)
    private val _userGeminiApiKey = MutableStateFlow(prefs.getString("gemini_api_key", "") ?: "")
    val userGeminiApiKey: StateFlow<String> = _userGeminiApiKey.asStateFlow()

    fun saveGeminiApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key).apply()
        _userGeminiApiKey.value = key
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
        var currentUrl = urlStr
        val client = okhttp3.OkHttpClient.Builder()
            .followRedirects(false)
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        for (i in 0 until maxHops) {
            if (!currentUrl.contains("goo.gl") && !currentUrl.contains("maps.app.goo.gl")) {
                break
            }
            try {
                val request = okhttp3.Request.Builder().url(currentUrl).head().build()
                client.newCall(request).execute().use { response ->
                    if (response.isRedirect) {
                        val loc = response.header("Location")
                        if (!loc.isNullOrEmpty()) {
                            currentUrl = loc
                        } else {
                            return@use
                        }
                    } else {
                        return@use
                    }
                }
            } catch (e: Exception) {
                break
            }
        }
        return currentUrl
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

    suspend fun resolveShopCoords(context: Context, shop: ShopMaster): ShopMaster {
        // 1. If the shop already has coordinates, preserve them
        if (shop.latitude != null && shop.longitude != null) {
            return shop
        }

        // 2. If it's an update, preserve coords from the old shop if map link hasn't changed
        try {
            val oldShop = repository.getShopByNumber(shop.shopNumber)
            if (oldShop != null && oldShop.googleMapLink == shop.googleMapLink && oldShop.latitude != null && oldShop.longitude != null) {
                return shop.copy(latitude = oldShop.latitude, longitude = oldShop.longitude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val link = shop.googleMapLink
        var lat: Double? = null
        var lng: Double? = null

        if (!link.isNullOrEmpty()) {
            val extracted = extractCoordinatesFromText(link)
            if (extracted != null) {
                lat = extracted.first
                lng = extracted.second
            } else {
                if (link.contains("goo.gl") || link.contains("maps.app.goo.gl")) {
                    val resolved = resolveShortenedUrlRecursively(link)
                    val resolvedExtracted = extractCoordinatesFromText(resolved)
                    if (resolvedExtracted != null) {
                        lat = resolvedExtracted.first
                        lng = resolvedExtracted.second
                    }
                }
            }
        }

        if (lat == null || lng == null) {
            val location = repository.getLocationByNumber(shop.locationNumber)
            val locationName = location?.locationName ?: ""
            val queryText = if (locationName.isNotEmpty()) {
                "${shop.storeName}, $locationName"
            } else {
                shop.storeName
            }
            
            // Try offline geocoder first
            val offlineResult = offlineGeocode(queryText)
            if (offlineResult != null) {
                lat = offlineResult.first
                lng = offlineResult.second
            } else {
                val geocoded = geocodeAddress(context, queryText)
                if (geocoded != null) {
                    lat = geocoded.first
                    lng = geocoded.second
                } else if (locationName.isNotEmpty()) {
                    val offlineLoc = offlineGeocode(locationName)
                    if (offlineLoc != null) {
                        lat = offlineLoc.first
                        lng = offlineLoc.second
                    } else {
                        val geocodedLoc = geocodeAddress(context, locationName)
                        if (geocodedLoc != null) {
                            lat = geocodedLoc.first
                            lng = geocodedLoc.second
                        }
                    }
                }
            }
        }

        if (lat == null || lng == null) {
            val idx = shop.shopNumber.filter { it.isDigit() }.toIntOrNull() ?: 0
            lat = 12.971598 + 0.005 * (idx % 20 + 1)
            lng = 77.594562 + 0.005 * (idx % 20 + 1)
        }

        return shop.copy(latitude = lat, longitude = lng)
    }

    val shops: StateFlow<List<ShopMaster>> = combine(
        repository.allShops,
        repository.allSales
    ) { allShops, allSales ->
        allShops.map { shop ->
            val salesForShop = allSales.filter { it.shopNumber == shop.shopNumber }
            val analytics = com.example.utils.RatingCalculator.calculateAnalytics(salesForShop)
            shop.copy(
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
                    Badge("10000_profit", "₹10,000 Profit", "Generated ₹10,000 in profit!", "ic_badge_10000_profit")
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
    }

    private suspend fun checkAndUnlockBadges(sales: List<SalesEntry>, shops: List<ShopMaster>) {
        val currentlyUnlocked = repository.unlockedBadges.first().map { it.badgeId }.toSet()
        
        if ("first_sale" !in currentlyUnlocked && sales.isNotEmpty()) {
            repository.unlockBadge("first_sale")
        }
        if ("100_shops" !in currentlyUnlocked && shops.size >= 100) {
            repository.unlockBadge("100_shops")
        }
        if ("1000_packets" !in currentlyUnlocked && sales.sumOf { it.packetsSold } >= 1000) {
            repository.unlockBadge("1000_packets")
        }
        if ("10000_profit" !in currentlyUnlocked && sales.sumOf { it.totalProfit } >= 10000) {
            repository.unlockBadge("10000_profit")
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
        repository.insertLocation(location)
    }

    fun updateLocation(location: LocationMaster) = viewModelScope.launch {
        repository.updateLocation(location)
    }

    fun deleteLocation(location: LocationMaster) = viewModelScope.launch {
        repository.deleteLocation(location)
    }

    fun deleteAllLocations() = viewModelScope.launch {
        repository.deleteAllLocations()
    }

    fun addShop(shop: ShopMaster) = viewModelScope.launch(Dispatchers.IO) {
        val resolvedShop = resolveShopCoords(getApplication(), shop)
        repository.insertShop(resolvedShop)
        refreshNextShopNumber()
    }

    fun updateShop(oldShopNumber: String, shop: ShopMaster) = viewModelScope.launch(Dispatchers.IO) {
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
    }

    fun deleteShop(shop: ShopMaster) = viewModelScope.launch {
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
    }

    fun deleteAllShops() = viewModelScope.launch {
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
                    repository.insertShops(resolvedShops)
                    refreshNextShopNumber()
                    // Clean up any old/orphaned shop images after importing
                    com.example.utils.BackupHelper.cleanupUnusedImages(context)
                }
                
                _importSummary.value = summary
            } catch (e: Exception) {
                e.printStackTrace()
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
            } catch (e: Exception) {
                e.printStackTrace()
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
            } catch (e: Exception) {
                e.printStackTrace()
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
                
                _importSummary.value = summary.copy(
                    type = com.example.utils.Exporter.ImportType.PRODUCTS,
                    successfullyImported = successCount,
                    updatedRecordsCount = updateCount,
                    duplicateRecordsCount = duplicateCount,
                    skippedRows = summary.failedRowsCount + duplicateCount
                )
            } catch (e: Exception) {
                e.printStackTrace()
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
        return repository.insertProduct(product)
    }

    suspend fun addProductWithPrices(product: ProductMaster, prices: List<ProductPrice>) {
        repository.insertProductWithPrices(product, prices)
    }

    fun updateProduct(product: ProductMaster) = viewModelScope.launch {
        repository.updateProduct(product)
    }

    fun deleteProduct(product: ProductMaster) = viewModelScope.launch {
        repository.deleteProductWithPrices(product)
    }

    fun deletePricesForProduct(productId: Int) = viewModelScope.launch {
        repository.deletePricesForProduct(productId)
    }

    fun addPrice(price: ProductPrice) = viewModelScope.launch {
        repository.insertPrice(price)
    }

    fun deleteAllProducts() = viewModelScope.launch {
        repository.deleteAllProducts()
    }

    fun addSales(salesEntry: SalesEntry) = viewModelScope.launch {
        repository.insertSales(salesEntry)
    }

    fun updateSales(salesEntry: SalesEntry) = viewModelScope.launch {
        repository.updateSales(salesEntry)
    }

    fun deleteSales(salesEntry: SalesEntry) = viewModelScope.launch {
        repository.deleteSales(salesEntry)
    }
    
    fun deleteAllSales() = viewModelScope.launch {
        repository.deleteAllSales()
    }

    // --- Persistent Shop Image Saving Utility ---
    fun saveImageToStorage(uri: Uri): String? {
        val context = getApplication<Application>()
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(context.filesDir, "shop_img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                inputStream.copyTo(out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
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
Below is the live distributor data from the application database. Analyze it and provide accurate, expert insights to the user.
Note: The database is read-only. Answer questions comprehensively, suggest route optimizations, sales boosts, and analyze product profit margins.

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
