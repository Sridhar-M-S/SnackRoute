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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.GoogleAuthUtil
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Network
import android.net.NetworkRequest

data class AppError(
    val module: String,
    val operation: String,
    val errorType: String,
    val errorMessage: String,
    val possibleReason: String,
    val stackTrace: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class LastSaleProduct(
    val productName: String,
    val productVariety: String,
    val sellingPrice: Double,
    val packetsSupplied: Int,
    val remarks: String?
)

data class ReminderItem(
    val shop: com.example.data.ShopMaster,
    val lastSaleDate: Long,
    val daysSince: Int,
    val recommendedProducts: Map<String, Int>,
    val interval: Int,
    val lastSaleProducts: List<LastSaleProduct>,
    val daysDifference: Int = 0
)

data class InAppNotification(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) {
    fun toSerializedString(): String {
        return "$id||$title||$message||$timestamp||$isRead"
    }

    companion object {
        fun fromSerializedString(serialized: String): InAppNotification? {
            val parts = serialized.split("||")
            if (parts.size < 5) return null
            return InAppNotification(
                id = parts[0],
                title = parts[1],
                message = parts[2],
                timestamp = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                isRead = parts[4].toBoolean()
            )
        }
    }
}


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
        db.errorLogDao(),
        db.dailyTaskDao(),
        db.dynamicCostDao(),
        db.shopRemarkDao()
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

    // --- Google Drive Sync States & Persistence ---
    private val _googleSignInAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val googleSignInAccount: StateFlow<GoogleSignInAccount?> = _googleSignInAccount.asStateFlow()

    private val _syncProgress = MutableStateFlow<Int>(-1)
    val syncProgress: StateFlow<Int> = _syncProgress.asStateFlow()

    private val _syncProgressText = MutableStateFlow<String>("")
    val syncProgressText: StateFlow<String> = _syncProgressText.asStateFlow()

    private val _syncStatus = MutableStateFlow<String>(prefs.getString("gdrive_sync_status", "Idle") ?: "Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _lastSyncedTime = MutableStateFlow<Long>(prefs.getLong("gdrive_last_sync_time", 0L))
    val lastSyncedTime: StateFlow<Long> = _lastSyncedTime.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String>(prefs.getString("gdrive_last_error", "") ?: "")
    val lastSyncError: StateFlow<String> = _lastSyncError.asStateFlow()

    private val _isAutoSyncEnabled = MutableStateFlow<Boolean>(prefs.getBoolean("gdrive_auto_sync_enabled", true))
    val isAutoSyncEnabled: StateFlow<Boolean> = _isAutoSyncEnabled.asStateFlow()

    private val _isOfflineQueueActive = MutableStateFlow<Boolean>(false)
    val isOfflineQueueActive: StateFlow<Boolean> = _isOfflineQueueActive.asStateFlow()

    private val _inAppNotifications = MutableStateFlow<List<InAppNotification>>(emptyList())
    val inAppNotifications: StateFlow<List<InAppNotification>> = _inAppNotifications.asStateFlow()

    private fun loadInAppNotifications() {
        val serializedSet = prefs.getStringSet("in_app_notifications_set", emptySet()) ?: emptySet()
        val list = serializedSet.mapNotNull { InAppNotification.fromSerializedString(it) }
            .sortedByDescending { it.timestamp }
        _inAppNotifications.value = list
    }

    private fun saveInAppNotifications(list: List<InAppNotification>) {
        val serializedSet = list.map { it.toSerializedString() }.toSet()
        prefs.edit().putStringSet("in_app_notifications_set", serializedSet).apply()
        _inAppNotifications.value = list.sortedByDescending { it.timestamp }
    }

    fun addInAppNotification(title: String, message: String) {
        val newNotification = InAppNotification(title = title, message = message)
        val current = _inAppNotifications.value.toMutableList()
        current.add(0, newNotification)
        saveInAppNotifications(current)
    }

    fun markInAppNotificationRead(id: String) {
        val updated = _inAppNotifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        saveInAppNotifications(updated)
    }

    fun deleteInAppNotification(id: String) {
        val updated = _inAppNotifications.value.filter { it.id != id }
        saveInAppNotifications(updated)
    }

    fun clearAllInAppNotifications() {
        saveInAppNotifications(emptyList())
    }

    // --- Sales Reminder States & Flows ---
    private val _isReminderEnabled = MutableStateFlow(prefs.getBoolean("sales_reminder_enabled", true))
    val isReminderEnabled: StateFlow<Boolean> = _isReminderEnabled.asStateFlow()

    private val _defaultReminderInterval = MutableStateFlow(prefs.getInt("sales_reminder_interval", 7))
    val defaultReminderInterval: StateFlow<Int> = _defaultReminderInterval.asStateFlow()

    private val _reminderTime = MutableStateFlow(prefs.getString("sales_reminder_time", "20:00") ?: "20:00")
    val reminderTime: StateFlow<String> = _reminderTime.asStateFlow()

    private val _notifyAfterDays = MutableStateFlow(prefs.getInt("sales_reminder_notify_after_days", 7))
    val notifyAfterDays: StateFlow<Int> = _notifyAfterDays.asStateFlow()

    private val _keepVisibleDays = MutableStateFlow(prefs.getInt("sales_reminder_keep_visible_days", 30))
    val keepVisibleDays: StateFlow<Int> = _keepVisibleDays.asStateFlow()

    private fun getMidnight(timeMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun updateReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sales_reminder_enabled", enabled).apply()
        _isReminderEnabled.value = enabled
    }

    fun updateDefaultReminderInterval(interval: Int) {
        updateNotifyAfterDays(interval)
    }

    fun updateNotifyAfterDays(days: Int) {
        prefs.edit().putInt("sales_reminder_notify_after_days", days).apply()
        _notifyAfterDays.value = days
        prefs.edit().putInt("sales_reminder_interval", days).apply()
        _defaultReminderInterval.value = days
    }

    fun updateKeepVisibleDays(days: Int) {
        prefs.edit().putInt("sales_reminder_keep_visible_days", days).apply()
        _keepVisibleDays.value = days
    }

    fun updateReminderTime(time: String) {
        prefs.edit().putString("sales_reminder_time", time).apply()
        _reminderTime.value = time
    }

    fun sendTestNotification() {
        val context = getApplication<Application>().applicationContext
        com.example.utils.NotificationHelper.showNotification(
            context,
            "Test Sales Reminder",
            "This is a test reminder notification."
        )
        addInAppNotification(
            "Test Sales Reminder",
            "This is a test reminder notification."
        )
    }

    val dueReminders: StateFlow<List<ReminderItem>> = combine(
        repository.getShopsWithLastSale(),
        _isReminderEnabled,
        _notifyAfterDays,
        _keepVisibleDays,
        repository.allProducts
    ) { shopsWithLastSale, enabled, notifyAfter, keepVisible, products ->
        if (!enabled) return@combine emptyList<ReminderItem>()

        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val nowMidnight = getMidnight(now)

        val activeShopItems = shopsWithLastSale.mapNotNull { item ->
            val shop = item.shop
            val lastSaleDate = item.lastSaleDate
            val lastCompletedTime = prefs.getLong("completed_reminder_shop_${shop.shopNumber}", 0L)

            if (lastCompletedTime >= lastSaleDate) return@mapNotNull null

            val interval = shop.customReminderInterval ?: notifyAfter
            val lastSaleMidnight = getMidnight(lastSaleDate)
            val daysSince = ((nowMidnight - lastSaleMidnight) / oneDayMs).toInt()

            if (daysSince < interval) return@mapNotNull null
            if (daysSince > keepVisible) return@mapNotNull null

            val daysDifference = interval - daysSince

            Triple(item, daysSince, interval)
        }

        if (activeShopItems.isEmpty()) return@combine emptyList<ReminderItem>()

        val activeShopNumbers = activeShopItems.map { it.first.shop.shopNumber }
        val salesForActiveShops = repository.getSalesForShopsDirect(activeShopNumbers)
        val salesByShop = salesForActiveShops.groupBy { it.shopNumber }

        activeShopItems.map { (item, daysSince, interval) ->
            val shop = item.shop
            val lastSaleDate = item.lastSaleDate
            val shopSales = salesByShop[shop.shopNumber] ?: emptyList()

            val productAverages = shopSales.groupBy { it.productName }
                .mapValues { (_, entries) ->
                    val avg = entries.map { it.packetsSold }.average()
                    if (avg.isNaN()) 0 else kotlin.math.ceil(avg).toInt()
                }

            val latestSales = shopSales.filter { it.entryDate == lastSaleDate }
            val lastSaleProducts = latestSales.map { sale ->
                val matchingProduct = products.find { it.productName == sale.productName }
                val variety = matchingProduct?.productCategory ?: "Standard"
                LastSaleProduct(
                    productName = sale.productName,
                    productVariety = variety,
                    sellingPrice = sale.ratePerPacket,
                    packetsSupplied = sale.packetsGiven,
                    remarks = sale.remarks
                )
            }

            val daysDifference = interval - daysSince

            ReminderItem(
                shop = shop,
                lastSaleDate = lastSaleDate,
                daysSince = daysSince,
                recommendedProducts = productAverages,
                interval = interval,
                lastSaleProducts = lastSaleProducts,
                daysDifference = daysDifference
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markReminderCompleted(shopNumber: String) {
        prefs.edit().putLong("completed_reminder_shop_$shopNumber", System.currentTimeMillis()).apply()
        val currentEnabled = _isReminderEnabled.value
        _isReminderEnabled.value = !currentEnabled
        _isReminderEnabled.value = currentEnabled
    }

    fun markRemindersCompleted(shopNumbers: List<String>) {
        val editor = prefs.edit()
        val now = System.currentTimeMillis()
        shopNumbers.forEach { shopNo ->
            editor.putLong("completed_reminder_shop_$shopNo", now)
        }
        editor.apply()
        val currentEnabled = _isReminderEnabled.value
        _isReminderEnabled.value = !currentEnabled
        _isReminderEnabled.value = currentEnabled
    }

    fun markAllRemindersCompleted() {
        val shopNumbers = dueReminders.value.map { it.shop.shopNumber }
        if (shopNumbers.isNotEmpty()) {
            markRemindersCompleted(shopNumbers)
        }
    }

    fun markTodayRemindersCompleted(todayReminders: List<ReminderItem>) {
        val shopNumbers = todayReminders.map { it.shop.shopNumber }
        if (shopNumbers.isNotEmpty()) {
            markRemindersCompleted(shopNumbers)
        }
    }

    fun markMissedRemindersCompleted(missedReminders: List<ReminderItem>) {
        val shopNumbers = missedReminders.map { it.shop.shopNumber }
        if (shopNumbers.isNotEmpty()) {
            markRemindersCompleted(shopNumbers)
        }
    }

    // --- Shop Remarks State & Flows ---
    val allRemarks: StateFlow<List<ShopRemark>> = repository.allRemarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPrices: StateFlow<List<ProductPrice>> = repository.getAllPricesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRemark(shopNumber: String, shopName: String, locationNumber: String, remarkText: String, salesEntryId: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val remark = ShopRemark(
                shopNumber = shopNumber,
                shopName = shopName,
                locationNumber = locationNumber,
                remark = remarkText,
                salesEntryId = salesEntryId
            )
            repository.insertRemark(remark)
        }
    }

    fun updateRemarkStatus(remark: ShopRemark, newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateRemark(remark.copy(status = newStatus))
        }
    }

    fun addRemarkReply(remark: ShopRemark, replyText: String, type: String = "Reply") {
        viewModelScope.launch(Dispatchers.IO) {
            val newItem = RemarkHistoryItem(
                id = java.util.UUID.randomUUID().toString(),
                date = System.currentTimeMillis(),
                note = replyText,
                type = type
            )
            val updatedHistory = remark.history.toMutableList().apply { add(newItem) }
            repository.updateRemark(remark.copy(history = updatedHistory))
        }
    }

    fun editRemarkText(remark: ShopRemark, newText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateRemark(remark.copy(remark = newText))
        }
    }

    fun deleteRemark(remark: ShopRemark) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRemark(remark)
        }
    }


    // --- Dynamic Profit Calculation Settings & Flows ---
    private val _isDynamicProfitEnabled = MutableStateFlow(prefs.getBoolean("is_dynamic_profit_enabled", false))
    val isDynamicProfitEnabled: StateFlow<Boolean> = _isDynamicProfitEnabled.asStateFlow()

    fun setDynamicProfitEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_dynamic_profit_enabled", enabled).apply()
        _isDynamicProfitEnabled.value = enabled
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val salesList = repository.allSales.first()
                val productsList = repository.allProducts.first()
                val pricesList = repository.getAllPrices()
                val calculationsList = repository.allCalculations.first()
                
                val updatedSales = salesList.mapNotNull { sale ->
                    val product = productsList.find { it.productName.equals(sale.productName, ignoreCase = true) }
                    val price = if (product != null) {
                        pricesList.find { it.productId == product.id && Math.abs(it.sellingPrice - sale.ratePerPacket) < 0.01 }
                    } else null
                    
                    val profitPerPacket = if (price != null) {
                        if (enabled) {
                            val saleDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sale.entryDate))
                            val calc = calculationsList
                                .filter { it.productPriceId == price.priceId && it.calculationDate <= saleDateStr }
                                .maxByOrNull { it.calculationDate }
                            calc?.profitSnapshot ?: price.profitPerPacket
                        } else {
                            price.profitPerPacket
                        }
                    } else {
                        sale.profitPerPacket
                    }
                    
                    if (sale.profitPerPacket != profitPerPacket) {
                        sale.copy(
                            profitPerPacket = profitPerPacket,
                            totalProfit = sale.packetsSold * profitPerPacket
                        )
                    } else {
                        null
                    }
                }
                
                if (updatedSales.isNotEmpty()) {
                    repository.updateSalesList(updatedSales)
                }
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val message = if (enabled) {
                        "Dynamic Cost Engine enabled. Historical sales recalculated successfully."
                    } else {
                        "Dynamic Cost Engine disabled. Manual Product Master values restored."
                    }
                    android.widget.Toast.makeText(getApplication(), message, android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                triggerError(
                    module = "DynamicCost",
                    operation = "setDynamicProfitEnabled",
                    exception = e,
                    possibleReason = "An error occurred while toggling dynamic cost calculation."
                )
            }
        }
    }

    val allCostCalculations: StateFlow<List<CostCalculation>> = repository.allCalculations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setGoogleAccount(account: GoogleSignInAccount?) {
        _googleSignInAccount.value = account
        if (account != null) {
            prefs.edit()
                .putString("gdrive_connected_email", account.email)
                .putString("gdrive_connected_name", account.displayName)
                .apply()
            triggerDriveSync()
        } else {
            prefs.edit()
                .remove("gdrive_connected_email")
                .remove("gdrive_connected_name")
                .apply()
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        _isAutoSyncEnabled.value = enabled
        prefs.edit().putBoolean("gdrive_auto_sync_enabled", enabled).apply()
        if (enabled) {
            triggerDriveSync()
        }
    }

    fun signOutGoogle(context: Context, onComplete: () -> Unit = {}) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
            setGoogleAccount(null)
            _syncStatus.value = "Idle"
            _lastSyncError.value = ""
            prefs.edit()
                .remove("gdrive_sync_status")
                .remove("gdrive_last_error")
                .apply()
            onComplete()
        }
    }

    fun reloadPreferences() {
        _userGeminiApiKey.value = prefs.getString("gemini_api_key", "") ?: ""
        _bonusXp.value = prefs.getInt("bonus_xp", 0)
        _bonusCoins.value = prefs.getInt("bonus_coins", 0)
        _rewardedMissionIds.value = prefs.getStringSet("rewarded_mission_ids", emptySet()) ?: emptySet()
        refreshNextShopNumber()
    }

    private fun checkAndRunAutoSync() {
        if (_isAutoSyncEnabled.value && _googleSignInAccount.value != null) {
            triggerDriveSync()
        }
    }

    private fun isInternetAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(net) ?: return false
            cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    fun triggerDriveSync() {
        val account = _googleSignInAccount.value ?: return
        val context = getApplication<Application>()
        
        viewModelScope.launch(Dispatchers.IO) {
            if (!isInternetAvailable(context)) {
                _isOfflineQueueActive.value = true
                _syncStatus.value = "Failed"
                _lastSyncError.value = "Device is offline. Sync has been queued and will automatically retry when internet is available."
                prefs.edit()
                    .putString("gdrive_sync_status", "Failed")
                    .putString("gdrive_last_error", _lastSyncError.value)
                    .apply()
                return@launch
            }
            
            _isOfflineQueueActive.value = false
            _syncStatus.value = "InProgress"
            _syncProgress.value = 0
            _syncProgressText.value = "Starting Google Drive sync..."
            _lastSyncError.value = ""
            prefs.edit()
                .putString("gdrive_sync_status", "InProgress")
                .putString("gdrive_last_error", "")
                .apply()

            val result = com.example.utils.GoogleDriveSyncHelper.uploadBackup(context, account.account!!) { text, percent ->
                _syncProgressText.value = text
                _syncProgress.value = percent
            }

            _syncProgress.value = -1 // hidden
            
            result.onSuccess { timestamp ->
                _syncStatus.value = "Success"
                _lastSyncedTime.value = timestamp
                _lastSyncError.value = ""
                prefs.edit()
                    .putString("gdrive_sync_status", "Success")
                    .putLong("gdrive_last_sync_time", timestamp)
                    .putString("gdrive_last_error", "")
                    .apply()
            }.onFailure { exception ->
                val errorMsg = exception.message ?: "Unknown sync error"
                _syncStatus.value = "Failed"
                _lastSyncError.value = "Upload Failed: $errorMsg"
                prefs.edit()
                    .putString("gdrive_sync_status", "Failed")
                    .putString("gdrive_last_error", _lastSyncError.value)
                    .apply()
            }
        }
    }

    fun restoreFromGoogleDrive(onComplete: (Boolean, String) -> Unit) {
        val account = _googleSignInAccount.value
        if (account == null) {
            onComplete(false, "Google account is not connected.")
            return
        }
        val context = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            if (!isInternetAvailable(context)) {
                onComplete(false, "Device is offline. Internet connection is required to download backups.")
                return@launch
            }

            _syncStatus.value = "InProgress"
            _syncProgress.value = 0
            _syncProgressText.value = "Starting restore from Google Drive..."
            _lastSyncError.value = ""
            prefs.edit()
                .putString("gdrive_sync_status", "InProgress")
                .putString("gdrive_last_error", "")
                .apply()

            val result = com.example.utils.GoogleDriveSyncHelper.downloadAndRestoreBackup(context, account.account!!) { text, percent ->
                _syncProgressText.value = text
                _syncProgress.value = percent
            }

            _syncProgress.value = -1

            result.onSuccess {
                reloadPreferences()
                
                _syncStatus.value = "Success"
                _lastSyncedTime.value = System.currentTimeMillis()
                _lastSyncError.value = ""
                prefs.edit()
                    .putString("gdrive_sync_status", "Success")
                    .putLong("gdrive_last_sync_time", _lastSyncedTime.value)
                    .putString("gdrive_last_error", "")
                    .apply()
                
                onComplete(true, "Application backup restored successfully!")
            }.onFailure { exception ->
                val errorMsg = exception.message ?: "Unknown download/restore error"
                _syncStatus.value = "Failed"
                _lastSyncError.value = "Download Failed: $errorMsg"
                prefs.edit()
                    .putString("gdrive_sync_status", "Failed")
                    .putString("gdrive_last_error", _lastSyncError.value)
                    .apply()
                onComplete(false, errorMsg)
            }
        }
    }

    // Date formats for unique mission cycle tracking
    private val sdfDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    private val sdfWeek = SimpleDateFormat("yyyy'W'ww", Locale.getDefault())
    private val sdfMonth = SimpleDateFormat("yyyy'M'MM", Locale.getDefault())

    private fun formatDay(date: Date): String = synchronized(sdfDay) { sdfDay.format(date) }
    private fun formatWeek(date: Date): String = synchronized(sdfWeek) { sdfWeek.format(date) }
    private fun formatMonth(date: Date): String = synchronized(sdfMonth) { sdfMonth.format(date) }

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
        val maxPacketsInSingleDay = sales.groupBy { formatDay(Date(it.entryDate)) }
            .map { it.value.sumOf { s -> s.packetsSold } }
            .maxOrNull() ?: 0

        val maxProfitInSingleDay = sales.groupBy { formatDay(Date(it.entryDate)) }
            .map { it.value.sumOf { s -> s.totalProfit } }
            .maxOrNull() ?: 0.0

        val totalShopsAdded = shops.size

        val maxLocationsInSingleDay = sales.groupBy { formatDay(Date(it.entryDate)) }
            .map { it.value.map { s -> s.locationNumber }.distinct().size }
            .maxOrNull() ?: 0

        return listOf(
            BossChallenge("snack_titan", "Defeat the Snack Titan", "Boss Challenge (Level 1)", "Sell 500 packets in a single day to conquer the Titan.", maxPacketsInSingleDay, 500, 500, 200, maxPacketsInSingleDay >= 500, "Snack Titan"),
            BossChallenge("gold_rush", "Slay the Gold Dragon", "Boss Challenge (Level 2)", "Earn ₹10,000 in profit in a single day.", maxProfitInSingleDay.toInt(), 10000, 10000, 500, maxProfitInSingleDay >= 10000.0, "Gold Dragon"),
            BossChallenge("expansion_emperor", "Overthrow the Expansion Emperor", "Boss Challenge (Level 3)", "Build a franchise of 20 active shops.", totalShopsAdded, 20, 800, 400, totalShopsAdded >= 20, "Expansion Emperor"),
            BossChallenge("route_sovereign", "Dethrone the Route Sovereign", "Boss Challenge (Level 4)", "Sell in 5 different route locations in a single day.", maxLocationsInSingleDay, 5, 1200, 600, maxLocationsInSingleDay >= 5, "Route Sovereign")
        )
    }    fun calculateGamificationState(
        sales: List<SalesEntry>,
        shops: List<ShopMaster>,
        locationsList: List<LocationMaster>,
        badges: List<UserBadge>,
        bonusXp: Int,
        bonusCoins: Int,
        rewardedIds: Set<String>,
        combo: Int
    ): GamificationState {
        val comboRes = calculateComboResult(sales)
        val activeCombo = comboRes.currentCombo
        val comboXp = comboRes.totalComboXp
        val comboCoins = comboRes.totalComboCoins
        
        val (missionsXp, missionsCoins) = calculateCompletedMissionsXpAndCoins(sales, shops)
        val (bossXp, bossCoins) = calculateCompletedBossXpAndCoins(sales, shops)
        
        val baseShopXp = shops.size * 50
        val baseSalesXp = sales.size * 10
        val basePacketsXp = sales.sumOf { it.packetsSold } * 1
        val baseLocationXp = sales.map { it.locationNumber }.distinct().size * 30
        val baseBadgeXp = badges.size * 100
        
        val totalXp = baseShopXp + baseSalesXp + basePacketsXp + baseLocationXp + baseBadgeXp + comboXp + missionsXp + bossXp
        
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
        
        val totalCoins = baseProfitCoins + baseShopCoins + baseSalesCoins + baseBadgeCoins + comboCoins + missionsCoins + bossCoins
        val rank = getRankForXp(totalXp)
        val streak = calculateStreak(sales)
        val title = getTitleForLevel(level)
        
        val now = Date()
        val dayId = formatDay(now)
        val weekId = formatWeek(now)
        val monthId = formatMonth(now)
        
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
            sessionCombo = activeCombo,
            dailyMissions = daily,
            weeklyMissions = weekly,
            monthlyMissions = monthly,
            bossChallenges = bossList
        )
    }



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
            val dayId = formatDay(now)
            val weekId = formatWeek(now)
            val monthId = formatMonth(now)

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



    // --- Badge Flows ---
    val allBadges: StateFlow<List<Badge>> = repository.allBadges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val unlockedBadges: StateFlow<List<UserBadge>> = repository.unlockedBadges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timetable: StateFlow<List<TimetableEntry>> = repository.allTimetableEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val locations: StateFlow<List<LocationMaster>> = repository.allLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductMaster>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales: StateFlow<List<SalesEntry>> = combine(
        repository.allSales,
        products,
        repository.getAllPricesFlow(),
        allCostCalculations,
        isDynamicProfitEnabled
    ) { rawSales, prods, prices, calcs, dynamicEnabled ->
        android.util.Log.d("DynamicCostEngine", "Mapping Sales: Dynamic Cost Engine = ${if (dynamicEnabled) "ON" else "OFF"}")
        rawSales.map { sale ->
            val product = prods.find { it.productName.equals(sale.productName, ignoreCase = true) }
            val priceObj = product?.let { p ->
                prices.find { it.productId == p.id && Math.abs(it.sellingPrice - sale.ratePerPacket) < 0.01 }
            }
            
            val productPrices = product?.let { p -> prices.filter { it.productId == p.id } } ?: emptyList()
            val priceIds = productPrices.map { it.priceId }
            val saleDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(sale.entryDate))
            
            val applicableCalc = if (dynamicEnabled && priceIds.isNotEmpty()) {
                if (priceObj != null) {
                    calcs
                        .filter { it.productPriceId == priceObj.priceId && it.calculationDate <= saleDateStr }
                        .maxByOrNull { it.calculationDate }
                } else {
                    calcs
                        .filter { it.productPriceId in priceIds && it.calculationDate <= saleDateStr }
                        .maxByOrNull { it.calculationDate }
                }
            } else null
            
            val productionCost = if (applicableCalc != null) {
                applicableCalc.totalProductionCost
            } else {
                val refPrice = priceObj ?: productPrices.firstOrNull()
                refPrice?.let { it.sellingPrice - it.profitPerPacket } ?: 0.0
            }
            
            val calculatedProfitPerPacket = sale.ratePerPacket - productionCost
            
            // Check if the saved profit is a manual override.
            // A manual override is when the saved profit does NOT match the calculated profit (based on standard or dynamic cost).
            val standardProdCost = (priceObj ?: productPrices.firstOrNull())?.let { it.sellingPrice - it.profitPerPacket } ?: 0.0
            val standardExpectedProfit = sale.ratePerPacket - standardProdCost
            
            val isManualOverride = if (applicableCalc != null) {
                Math.abs(sale.profitPerPacket - calculatedProfitPerPacket) > 0.01 && 
                Math.abs(sale.profitPerPacket - standardExpectedProfit) > 0.01
            } else {
                Math.abs(sale.profitPerPacket - standardExpectedProfit) > 0.01
            }
            
            val finalProfitPerPacket = if (isManualOverride) {
                sale.profitPerPacket
            } else {
                calculatedProfitPerPacket
            }
            
            val newTotalProfit = sale.packetsSold * finalProfitPerPacket
            val newTotalAmount = sale.packetsSold * sale.ratePerPacket
            
            sale.copy(
                ratePerPacket = sale.ratePerPacket,
                totalAmount = newTotalAmount,
                profitPerPacket = finalProfitPerPacket,
                totalProfit = newTotalProfit
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val gamificationState: StateFlow<GamificationState> = combine(
        sales,
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

    val currentDailySales: StateFlow<List<SalesEntry>> = sales
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

    val monthlyGrowth: StateFlow<MonthlyGrowthData?> = sales
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

    val salesSearchQuery = MutableStateFlow("")
    val salesFilterShopNumber = MutableStateFlow<String?>(null)

    fun setSalesSearchQuery(query: String) {
        salesSearchQuery.value = query
        if (query.isEmpty()) {
            salesFilterShopNumber.value = null
        }
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
        // If both are available (e.g., from Excel import or direct input), use them directly and skip coordinate resolution.
        if (shop.latitude != null && shop.longitude != null && (shop.latitude != 0.0 || shop.longitude != 0.0) && shop.latitude in -90.0..90.0 && shop.longitude in -180.0..180.0) {
            return shop.copy(
                coordinateStatus = "Valid",
                lastCoordinateUpdate = shop.lastCoordinateUpdate ?: System.currentTimeMillis(),
                coordinateError = null
            )
        }

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
        sales
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
        loadInAppNotifications()

        viewModelScope.launch(Dispatchers.Default) {
            combine(dueReminders, repository.allLocations) { reminders, locations ->
                Pair(reminders, locations)
            }.collect { (reminders, locations) ->
                val context = getApplication<Application>().applicationContext
                val locationMap = locations.associate { it.locationNumber to it.locationName }
                reminders.forEach { reminder ->
                    if (reminder.daysSince == reminder.interval) {
                        val shop = reminder.shop
                        val lastSaleDate = reminder.lastSaleDate
                        val prefKey = "notified_due_${shop.shopNumber}_$lastSaleDate"
                        if (!prefs.getBoolean(prefKey, false)) {
                            prefs.edit().putBoolean(prefKey, true).apply()
                            
                            val locName = locationMap[shop.locationNumber] ?: "Location ${shop.locationNumber}"
                            val title = "Sales Reminder: ${shop.storeName}"
                            val body = "A sales reminder is due today for ${shop.storeName} in $locName."
                            
                            com.example.utils.NotificationHelper.showNotification(context, title, body)
                            addInAppNotification(title, body)
                        }
                    }
                }
            }
        }

        // Initialize Google Sign-In account on launch
        _googleSignInAccount.value = GoogleSignIn.getLastSignedInAccount(application)

        // Register connectivity observer for auto-retry when back online
        try {
            val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (_isOfflineQueueActive.value) {
                        triggerDriveSync()
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Centered Auto-Sync listener (handles Add, Edit, Delete, Import, Restore automatically)
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                repository.allShops,
                repository.allSales,
                repository.allLocations,
                repository.allProducts,
                repository.allTimetableEntries
            ) { sh, sa, lo, pr, ti ->
                System.currentTimeMillis()
            }
            .drop(1)
            .debounce(2500L)
            .collect {
                if (_isAutoSyncEnabled.value && _googleSignInAccount.value != null) {
                    triggerDriveSync()
                }
            }
        }

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
            combine(sales, repository.allShops) { s, sh ->
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

        // Reactive tracking of completed missions & boss challenges
        var completedMissionIds = setOf<String>()
        var completedBossIds = setOf<String>()
        
        viewModelScope.launch {
            gamificationState.collect { state ->
                val allMissions = state.dailyMissions + state.weeklyMissions + state.monthlyMissions
                val currentCompletedMissions = allMissions.filter { it.isCompleted }.map { it.id }.toSet()
                val currentCompletedBosses = state.bossChallenges.filter { it.isCompleted }.map { it.id }.toSet()
                
                if (completedMissionIds.isEmpty() && currentCompletedMissions.isNotEmpty()) {
                    completedMissionIds = currentCompletedMissions
                } else {
                    for (mId in currentCompletedMissions) {
                        if (mId !in completedMissionIds) {
                            val m = allMissions.find { it.id == mId }
                            if (m != null) {
                                _gamificationEvents.emit(GamificationEvent.MissionComplete(m.title))
                                _gamificationEvents.emit(GamificationEvent.XpGain(m.xpReward, "Mission: ${m.title}"))
                                _gamificationEvents.emit(GamificationEvent.CoinGain(m.coinReward, "Mission: ${m.title}"))
                            }
                        }
                    }
                    completedMissionIds = currentCompletedMissions
                }
                
                if (completedBossIds.isEmpty() && currentCompletedBosses.isNotEmpty()) {
                    completedBossIds = currentCompletedBosses
                } else {
                    for (bId in currentCompletedBosses) {
                        if (bId !in completedBossIds) {
                            val b = state.bossChallenges.find { it.id == bId }
                            if (b != null) {
                                _gamificationEvents.emit(GamificationEvent.BossDefeated(b.bossName))
                                _gamificationEvents.emit(GamificationEvent.XpGain(b.xpReward, "Boss Defeated: ${b.title}"))
                                _gamificationEvents.emit(GamificationEvent.CoinGain(b.coinReward, "Boss Defeated: ${b.title}"))
                            }
                        }
                    }
                    completedBossIds = currentCompletedBosses
                }
            }
        }
    }

    private suspend fun checkAndUnlockBadges(sales: List<SalesEntry>, shops: List<ShopMaster>) {
        val currentlyUnlocked = repository.unlockedBadges.first().map { it.badgeId }.toSet()
        val totalPackets = sales.sumOf { it.packetsSold }
        val totalProfit = sales.sumOf { it.totalProfit }
        val totalRevenue = sales.sumOf { it.totalAmount }
        val streak = calculateStreak(sales)
        
        val shouldUnlock = mutableMapOf<String, Boolean>()
        shouldUnlock["first_sale"] = sales.isNotEmpty()
        shouldUnlock["100_shops"] = shops.size >= 100
        shouldUnlock["1000_packets"] = totalPackets >= 1000
        shouldUnlock["10000_profit"] = totalProfit >= 10000
        shouldUnlock["1000_sales"] = sales.size >= 1000
        shouldUnlock["10000_packets"] = totalPackets >= 10000
        shouldUnlock["100000_sales"] = totalRevenue >= 100000.0
        shouldUnlock["50000_profit"] = totalProfit >= 50000.0
        shouldUnlock["100_day_streak"] = streak >= 100

        val badgeNames = mapOf(
            "first_sale" to "First Sale",
            "100_shops" to "100 Shops Added",
            "1000_packets" to "1,000 Packets Sold",
            "10000_profit" to "₹10,000 Profit",
            "1000_sales" to "1,000 Sales",
            "10000_packets" to "10,000 Packets Sold",
            "100000_sales" to "₹1,00,000 Revenue",
            "50000_profit" to "₹50,000 Profit",
            "100_day_streak" to "Century Streak"
        )

        for ((badgeId, satisfied) in shouldUnlock) {
            if (satisfied && badgeId !in currentlyUnlocked) {
                repository.unlockBadge(badgeId)
                _gamificationEvents.emit(GamificationEvent.AchievementUnlocked(badgeNames[badgeId] ?: badgeId))
            } else if (!satisfied && badgeId in currentlyUnlocked) {
                repository.revokeBadge(badgeId)
            }
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
            val salesBefore = repository.allSales.first()
            val shopsBefore = repository.allShops.first()
            val badgesBefore = repository.unlockedBadges.first()
            val xpBefore = calculateTotalXpSnapshot(salesBefore, shopsBefore, badgesBefore, _bonusXp.value)

            val resolvedShop = resolveShopCoords(getApplication(), shop)
            repository.insertShop(resolvedShop)
            refreshNextShopNumber()

            val salesAfter = repository.allSales.first()
            val shopsAfter = repository.allShops.first()
            val badgesAfter = repository.unlockedBadges.first()
            val xpAfter = calculateTotalXpSnapshot(salesAfter, shopsAfter, badgesAfter, _bonusXp.value)

            val diff = xpAfter - xpBefore
            if (diff > 0) {
                _gamificationEvents.emit(GamificationEvent.XpGain(diff, "New Shop Added"))
                _gamificationEvents.emit(GamificationEvent.CoinGain(20, "New Shop Added"))
            }
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
            val oldShop = repository.getShopByNumber(oldShopNumber)
            val isChanged = oldShop == null ||
                oldShop.shopNumber != shop.shopNumber ||
                oldShop.locationNumber != shop.locationNumber ||
                oldShop.storeName != shop.storeName ||
                oldShop.storeImage != shop.storeImage ||
                oldShop.rating != shop.rating ||
                oldShop.googleMapLink != shop.googleMapLink ||
                oldShop.mobileNumber != shop.mobileNumber ||
                oldShop.notes != shop.notes ||
                oldShop.latitude != shop.latitude ||
                oldShop.longitude != shop.longitude

            if (!isChanged) {
                _gamificationEvents.emit(GamificationEvent.XpGain(0, "No Changes Detected"))
                return@launch
            }

            val salesBefore = repository.allSales.first()
            val shopsBefore = repository.allShops.first()
            val badgesBefore = repository.unlockedBadges.first()
            val xpBefore = calculateTotalXpSnapshot(salesBefore, shopsBefore, badgesBefore, _bonusXp.value)

            val resolvedShop = resolveShopCoords(getApplication(), shop)
            try {
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

            val salesAfter = repository.allSales.first()
            val shopsAfter = repository.allShops.first()
            val badgesAfter = repository.unlockedBadges.first()
            val xpAfter = calculateTotalXpSnapshot(salesAfter, shopsAfter, badgesAfter, _bonusXp.value)

            val diff = xpAfter - xpBefore
            _gamificationEvents.emit(GamificationEvent.XpGain(diff, "Shop Updated"))
        } catch (e: Exception) {
            triggerError(
                module = "Shop Master",
                operation = "Update Shop",
                exception = e,
                possibleReason = "An unexpected error occurred while updating the Shop Master."
            )
        }
    }

    fun deleteShop(shop: ShopMaster) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val salesBefore = repository.allSales.first()
            val shopsBefore = repository.allShops.first()
            val badgesBefore = repository.unlockedBadges.first()
            val xpBefore = calculateTotalXpSnapshot(salesBefore, shopsBefore, badgesBefore, _bonusXp.value)

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

            val salesAfter = repository.allSales.first()
            val shopsAfter = repository.allShops.first()
            val badgesAfter = repository.unlockedBadges.first()
            val xpAfter = calculateTotalXpSnapshot(salesAfter, shopsAfter, badgesAfter, _bonusXp.value)

            val diff = xpAfter - xpBefore
            if (diff < 0) {
                _gamificationEvents.emit(GamificationEvent.XpGain(diff, "Shop Deleted"))
            }
        } catch (e: Exception) {
            triggerError(
                module = "Shop Master",
                operation = "Delete Shop",
                exception = e,
                possibleReason = "Unable to delete this shop. There may be associated Sales Master entries."
            )
        }
    }

    fun deleteAllShops() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val salesBefore = repository.allSales.first()
            val shopsBefore = repository.allShops.first()
            val badgesBefore = repository.unlockedBadges.first()
            val xpBefore = calculateTotalXpSnapshot(salesBefore, shopsBefore, badgesBefore)

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

            val salesAfter = repository.allSales.first()
            val shopsAfter = repository.allShops.first()
            val badgesAfter = repository.unlockedBadges.first()
            val xpAfter = calculateTotalXpSnapshot(salesAfter, shopsAfter, badgesAfter)

            val diff = xpAfter - xpBefore
            if (diff < 0) {
                _gamificationEvents.emit(GamificationEvent.XpGain(diff, "All Shops Deleted"))
            }
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

                    val successCount = resolvedShops.size
                    val xpEarned = successCount * 50
                    _gamificationEvents.emit(GamificationEvent.XpGain(xpEarned, "Imported $successCount Shops"))
                    _gamificationEvents.emit(GamificationEvent.CoinGain(successCount * 20, "Imported $successCount Shops"))

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
                    
                    val successCount = summary.parsedLocations.size
                    val xpEarned = successCount * 10
                    val newXp = _bonusXp.value + xpEarned
                    prefs.edit().putInt("bonus_xp", newXp).apply()
                    _bonusXp.value = newXp
                    _gamificationEvents.emit(GamificationEvent.XpGain(xpEarned, "Imported $successCount Locations"))
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
                    
                    val successCount = summary.parsedSales.size
                    val packetCount = summary.parsedSales.sumOf { it.packetsSold }
                    val xpEarned = successCount * 10 + packetCount * 1
                    _gamificationEvents.emit(GamificationEvent.XpGain(xpEarned, "Imported $successCount Sales Records"))
                    _gamificationEvents.emit(GamificationEvent.CoinGain(successCount * 10, "Imported $successCount Sales Records"))
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

                if (successCount > 0) {
                    val xpEarned = successCount * 5
                    val newXp = _bonusXp.value + xpEarned
                    prefs.edit().putInt("bonus_xp", newXp).apply()
                    _bonusXp.value = newXp
                    _gamificationEvents.emit(GamificationEvent.XpGain(xpEarned, "Imported $successCount Products"))
                }

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

    fun addSales(salesEntry: SalesEntry) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val salesBefore = repository.allSales.first()
            val shopsBefore = repository.allShops.first()
            val badgesBefore = repository.unlockedBadges.first()
            val xpBefore = calculateTotalXpSnapshot(salesBefore, shopsBefore, badgesBefore, _bonusXp.value)

            repository.insertSales(salesEntry)
            if (!salesEntry.remarks.isNullOrBlank()) {
                val insertedSale = repository.allSales.first().firstOrNull { 
                    it.shopNumber == salesEntry.shopNumber && it.entryDate == salesEntry.entryDate 
                }
                repository.insertRemark(
                    ShopRemark(
                        shopNumber = salesEntry.shopNumber,
                        shopName = salesEntry.shopName,
                        locationNumber = salesEntry.locationNumber,
                        remark = salesEntry.remarks,
                        salesEntryId = insertedSale?.id
                    )
                )
            }
            incrementSessionCombo()

            val salesAfter = repository.allSales.first()
            val shopsAfter = repository.allShops.first()
            val badgesAfter = repository.unlockedBadges.first()
            val xpAfter = calculateTotalXpSnapshot(salesAfter, shopsAfter, badgesAfter, _bonusXp.value)

            val diff = xpAfter - xpBefore
            if (diff > 0) {
                _gamificationEvents.emit(GamificationEvent.XpGain(diff, "Sales Completed"))
                _gamificationEvents.emit(GamificationEvent.CoinGain(10, "Sales Completed"))
            }
        } catch (e: Exception) {
            triggerError(
                module = "Sales Master",
                operation = "Add Sales Entry",
                exception = e,
                possibleReason = "A conflicting or duplicate sales entry may already exist."
            )
        }
    }

    fun updateSales(salesEntry: SalesEntry) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val salesBefore = repository.allSales.first()
            val shopsBefore = repository.allShops.first()
            val badgesBefore = repository.unlockedBadges.first()
            val xpBefore = calculateTotalXpSnapshot(salesBefore, shopsBefore, badgesBefore, _bonusXp.value)

            repository.updateSales(salesEntry)
            val existingRemark = repository.getRemarkBySalesId(salesEntry.id)
            if (existingRemark != null) {
                if (salesEntry.remarks.isNullOrBlank()) {
                    repository.deleteRemark(existingRemark)
                } else {
                    repository.updateRemark(existingRemark.copy(remark = salesEntry.remarks))
                }
            } else if (!salesEntry.remarks.isNullOrBlank()) {
                repository.insertRemark(
                    ShopRemark(
                        shopNumber = salesEntry.shopNumber,
                        shopName = salesEntry.shopName,
                        locationNumber = salesEntry.locationNumber,
                        remark = salesEntry.remarks,
                        salesEntryId = salesEntry.id
                    )
                )
            }

            val salesAfter = repository.allSales.first()
            val shopsAfter = repository.allShops.first()
            val badgesAfter = repository.unlockedBadges.first()
            val xpAfter = calculateTotalXpSnapshot(salesAfter, shopsAfter, badgesAfter, _bonusXp.value)

            val diff = xpAfter - xpBefore
            _gamificationEvents.emit(GamificationEvent.XpGain(diff, "Sales Record Updated"))
        } catch (e: Exception) {
            triggerError(
                module = "Sales Master",
                operation = "Update Sales Entry",
                exception = e,
                possibleReason = "Unable to update sales details. Ensure all referenced fields are valid."
            )
        }
    }

    fun recalculateHistoricalSales(effectiveDateStr: String, recalculateAll: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sales = repository.allSales.first()
                val productsList = repository.allProducts.first()
                val pricesList = repository.getAllPrices()
                val calculationsList = repository.allCalculations.first()
                
                val effectiveTime = try {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(effectiveDateStr)?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
                
                val updatedSales = sales.mapNotNull { sale ->
                    if (!recalculateAll && sale.entryDate < effectiveTime) {
                        return@mapNotNull null
                    }
                    
                    val product = productsList.find { it.productName.equals(sale.productName, ignoreCase = true) }
                    val price = if (product != null) {
                        pricesList.find { it.productId == product.id && Math.abs(it.sellingPrice - sale.ratePerPacket) < 0.01 }
                    } else null
                    
                    val profitPerPacket = if (price != null) {
                        val saleDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(sale.entryDate))
                        val calc = calculationsList
                            .filter { it.productPriceId == price.priceId && it.calculationDate <= saleDateStr }
                            .maxByOrNull { it.calculationDate }
                        calc?.profitSnapshot ?: price.profitPerPacket
                    } else {
                        sale.profitPerPacket // keep existing if no match
                    }
                    
                    if (sale.profitPerPacket != profitPerPacket) {
                        sale.copy(
                            profitPerPacket = profitPerPacket,
                            totalProfit = sale.packetsSold * profitPerPacket
                        )
                    } else {
                        null
                    }
                }
                
                if (updatedSales.isNotEmpty()) {
                    repository.updateSalesList(updatedSales)
                }
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        "Historical sales recalculated successfully.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                triggerError(
                    module = "DynamicCost",
                    operation = "Recalculate Historical Sales",
                    exception = e,
                    possibleReason = "An error occurred while retroactively recalculating profit values."
                )
            }
        }
    }

    fun deleteSales(salesEntry: SalesEntry) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val salesBefore = repository.allSales.first()
            val shopsBefore = repository.allShops.first()
            val badgesBefore = repository.unlockedBadges.first()
            val xpBefore = calculateTotalXpSnapshot(salesBefore, shopsBefore, badgesBefore, _bonusXp.value)

            repository.deleteSales(salesEntry)
            val linkedRemark = repository.getRemarkBySalesId(salesEntry.id)
            if (linkedRemark != null) {
                repository.deleteRemark(linkedRemark)
            }

            val salesAfter = repository.allSales.first()
            val shopsAfter = repository.allShops.first()
            val badgesAfter = repository.unlockedBadges.first()
            val xpAfter = calculateTotalXpSnapshot(salesAfter, shopsAfter, badgesAfter, _bonusXp.value)

            val diff = xpAfter - xpBefore
            if (diff < 0) {
                _gamificationEvents.emit(GamificationEvent.XpGain(diff, "Sales Record Deleted"))
            }
        } catch (e: Exception) {
            triggerError(
                module = "Sales Master",
                operation = "Delete Sales Entry",
                exception = e,
                possibleReason = "An unexpected error occurred while deleting the sales record."
            )
        }
    }

    fun deleteSalesBySessionId(sessionId: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val salesBefore = repository.allSales.first()
            val shopsBefore = repository.allShops.first()
            val badgesBefore = repository.unlockedBadges.first()
            val xpBefore = calculateTotalXpSnapshot(salesBefore, shopsBefore, badgesBefore, _bonusXp.value)

            repository.deleteSalesBySessionId(sessionId)

            val salesAfter = repository.allSales.first()
            val shopsAfter = repository.allShops.first()
            val badgesAfter = repository.unlockedBadges.first()
            val xpAfter = calculateTotalXpSnapshot(salesAfter, shopsAfter, badgesAfter, _bonusXp.value)

            val diff = xpAfter - xpBefore
            if (diff < 0) {
                _gamificationEvents.emit(GamificationEvent.XpGain(diff, "Sales Record Deleted"))
            }
        } catch (e: Exception) {
            triggerError(
                module = "Sales Master",
                operation = "Delete Sales Entry Session",
                exception = e,
                possibleReason = "An unexpected error occurred while deleting the sales record session."
            )
        }
    }

    fun saveSalesSession(salesList: List<SalesEntry>, oldSessionId: String?, legacyIdToDelete: Int?) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val isEdit = !oldSessionId.isNullOrEmpty() || (legacyIdToDelete != null && legacyIdToDelete != 0)
            var isChanged = true
            if (isEdit) {
                val oldSalesOfSession = if (!oldSessionId.isNullOrEmpty()) {
                    repository.allSales.first().filter { it.sessionId == oldSessionId }
                } else {
                    repository.allSales.first().filter { it.id == legacyIdToDelete }
                }
                isChanged = !salesListEquals(oldSalesOfSession, salesList)
            }

            if (isEdit && !isChanged) {
                _gamificationEvents.emit(GamificationEvent.XpGain(0, "No Changes Detected"))
                return@launch
            }

            val salesBefore = repository.allSales.first()
            val shopsBefore = repository.allShops.first()
            val badgesBefore = repository.unlockedBadges.first()
            val xpBefore = calculateTotalXpSnapshot(salesBefore, shopsBefore, badgesBefore, _bonusXp.value)

            repository.saveSalesSession(salesList, oldSessionId, legacyIdToDelete)
            incrementSessionCombo()

            val salesAfter = repository.allSales.first()
            val shopsAfter = repository.allShops.first()
            val badgesAfter = repository.unlockedBadges.first()
            val xpAfter = calculateTotalXpSnapshot(salesAfter, shopsAfter, badgesAfter, _bonusXp.value)

            val diff = xpAfter - xpBefore
            if (isEdit) {
                _gamificationEvents.emit(GamificationEvent.XpGain(diff, "Sales Record Updated"))
            } else {
                if (diff > 0) {
                    _gamificationEvents.emit(GamificationEvent.XpGain(diff, "Sales Completed"))
                    _gamificationEvents.emit(GamificationEvent.CoinGain(salesList.size * 10, "Sales Completed"))
                }
            }
        } catch (e: Exception) {
            triggerError(
                module = "Sales Master",
                operation = "Save Sales Session",
                exception = e,
                possibleReason = "Unable to save sales session details. Ensure all referenced fields are valid."
            )
        }
    }
    
    fun deleteAllSales() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val salesBefore = repository.allSales.first()
            val shopsBefore = repository.allShops.first()
            val badgesBefore = repository.unlockedBadges.first()
            val xpBefore = calculateTotalXpSnapshot(salesBefore, shopsBefore, badgesBefore)

            repository.deleteAllSales()

            val salesAfter = repository.allSales.first()
            val shopsAfter = repository.allShops.first()
            val badgesAfter = repository.unlockedBadges.first()
            val xpAfter = calculateTotalXpSnapshot(salesAfter, shopsAfter, badgesAfter)

            val diff = xpAfter - xpBefore
            if (diff < 0) {
                _gamificationEvents.emit(GamificationEvent.XpGain(diff, "All Sales Deleted"))
            }
        } catch (e: Exception) {
            triggerError(
                module = "Sales Master",
                operation = "Delete All Sales Entries",
                exception = e,
                possibleReason = "Unable to empty the sales log database."
            )
        }
    }

    data class ComboResult(
        val currentCombo: Int,
        val totalComboXp: Int,
        val totalComboCoins: Int
    )

    fun calculateComboResult(sales: List<SalesEntry>): ComboResult {
        if (sales.isEmpty()) return ComboResult(0, 0, 0)
        val sortedSales = sales.sortedBy { it.entryDate }
        var currentCombo = 1
        var lastTime = sortedSales[0].entryDate
        var totalComboXp = 0
        var totalComboCoins = 0
        for (i in 1 until sortedSales.size) {
            val currentTime = sortedSales[i].entryDate
            if (currentTime - lastTime < 10 * 60 * 1000L) { // 10 minutes
                currentCombo += 1
                val xpBonus = when {
                    currentCombo >= 10 -> 100
                    currentCombo >= 5 -> 50
                    currentCombo >= 3 -> 20
                    else -> 10
                }
                val coinBonus = when {
                    currentCombo >= 10 -> 50
                    currentCombo >= 5 -> 25
                    currentCombo >= 3 -> 10
                    else -> 5
                }
                totalComboXp += xpBonus
                totalComboCoins += coinBonus
            } else {
                currentCombo = 1
            }
            lastTime = currentTime
        }
        val activeCombo = if (System.currentTimeMillis() - lastTime < 10 * 60 * 1000L) {
            currentCombo
        } else {
            0
        }
        return ComboResult(activeCombo, totalComboXp, totalComboCoins)
    }

    fun calculateCompletedMissionsXpAndCoins(
        sales: List<SalesEntry>,
        shops: List<ShopMaster>
    ): Pair<Int, Int> {
        var totalXp = 0
        var totalCoins = 0
        
        val dayIds = (sales.map { formatDay(Date(it.entryDate)) } + 
                      shops.map { formatDay(Date(it.startingDate)) }).distinct()
        for (dayId in dayIds) {
            val salesToday = sales.filter { formatDay(Date(it.entryDate)) == dayId }
            val shopsCreatedToday = shops.filter { formatDay(Date(it.startingDate)) == dayId }
            
            if (salesToday.map { it.shopNumber }.distinct().size >= 3) {
                totalXp += 50
                totalCoins += 20
            }
            if (salesToday.sumOf { it.packetsSold } >= 50) {
                totalXp += 50
                totalCoins += 30
            }
            if (salesToday.sumOf { it.totalProfit } >= 500.0) {
                totalXp += 60
                totalCoins += 40
            }
            if (shopsCreatedToday.size >= 1) {
                totalXp += 80
                totalCoins += 50
            }
        }
        
        val weekIds = sales.map { formatWeek(Date(it.entryDate)) }.distinct()
        for (weekId in weekIds) {
            val salesThisWeek = sales.filter { formatWeek(Date(it.entryDate)) == weekId }
            
            if (salesThisWeek.map { it.shopNumber }.distinct().size >= 15) {
                totalXp += 200
                totalCoins += 100
            }
            if (salesThisWeek.sumOf { it.packetsSold } >= 300) {
                totalXp += 250
                totalCoins += 150
            }
            if (salesThisWeek.sumOf { it.totalAmount } >= 5000.0) {
                totalXp += 300
                totalCoins += 200
            }
        }
        
        val monthIds = (sales.map { formatMonth(Date(it.entryDate)) } + 
                        shops.map { formatMonth(Date(it.startingDate)) }).distinct()
        for (monthId in monthIds) {
            val salesThisMonth = sales.filter { formatMonth(Date(it.entryDate)) == monthId }
            val shopsCreatedThisMonth = shops.filter { formatMonth(Date(it.startingDate)) == monthId }
            
            if (salesThisMonth.sumOf { it.packetsSold } >= 1200) {
                totalXp += 1000
                totalCoins += 500
            }
            if (salesThisMonth.sumOf { it.totalAmount } >= 20000.0) {
                totalXp += 1200
                totalCoins += 600
            }
            if (shopsCreatedThisMonth.size >= 5) {
                totalXp += 800
                totalCoins += 400
            }
        }
        
        return Pair(totalXp, totalCoins)
    }

    fun calculateCompletedBossXpAndCoins(
        sales: List<SalesEntry>,
        shops: List<ShopMaster>
    ): Pair<Int, Int> {
        var totalXp = 0
        var totalCoins = 0
        
        val maxPacketsInSingleDay = sales.groupBy { formatDay(Date(it.entryDate)) }
            .map { it.value.sumOf { s -> s.packetsSold } }
            .maxOrNull() ?: 0
            
        val maxProfitInSingleDay = sales.groupBy { formatDay(Date(it.entryDate)) }
            .map { it.value.sumOf { s -> s.totalProfit } }
            .maxOrNull() ?: 0.0
            
        val totalShopsAdded = shops.size
        
        val maxLocationsInSingleDay = sales.groupBy { formatDay(Date(it.entryDate)) }
            .map { it.value.map { s -> s.locationNumber }.distinct().size }
            .maxOrNull() ?: 0
            
        if (maxPacketsInSingleDay >= 500) {
            totalXp += 500
            totalCoins += 200
        }
        if (maxProfitInSingleDay >= 10000.0) {
            totalXp += 10000
            totalCoins += 500
        }
        if (totalShopsAdded >= 20) {
            totalXp += 800
            totalCoins += 400
        }
        if (maxLocationsInSingleDay >= 5) {
            totalXp += 1200
            totalCoins += 600
        }
        
        return Pair(totalXp, totalCoins)
    }

    private fun calculateTotalXpSnapshot(
        sales: List<SalesEntry>,
        shops: List<ShopMaster>,
        badges: List<UserBadge>,
        bXp: Int = 0
    ): Int {
        val baseShopXp = shops.size * 50
        val baseSalesXp = sales.size * 10
        val basePacketsXp = sales.sumOf { it.packetsSold } * 1
        val baseLocationXp = sales.map { it.locationNumber }.distinct().size * 30
        val baseBadgeXp = badges.size * 100
        
        val comboXp = calculateComboResult(sales).totalComboXp
        val missionsXp = calculateCompletedMissionsXpAndCoins(sales, shops).first
        val bossXp = calculateCompletedBossXpAndCoins(sales, shops).first
        
        return baseShopXp + baseSalesXp + basePacketsXp + baseLocationXp + baseBadgeXp + comboXp + missionsXp + bossXp
    }

    private fun salesListEquals(list1: List<SalesEntry>, list2: List<SalesEntry>): Boolean {
        if (list1.size != list2.size) return false
        val sorted1 = list1.sortedWith(compareBy({ it.productName }, { it.packetsGiven }, { it.packetsReturned }))
        val sorted2 = list2.sortedWith(compareBy({ it.productName }, { it.packetsGiven }, { it.packetsReturned }))
        for (i in sorted1.indices) {
            val e1 = sorted1[i]
            val e2 = sorted2[i]
            if (e1.productName != e2.productName ||
                e1.packetsGiven != e2.packetsGiven ||
                e1.packetsReturned != e2.packetsReturned ||
                e1.ratePerPacket != e2.ratePerPacket ||
                e1.totalAmount != e2.totalAmount ||
                e1.profitPerPacket != e2.profitPerPacket ||
                e1.totalProfit != e2.totalProfit ||
                e1.status != e2.status ||
                e1.remarks != e2.remarks ||
                e1.shopNumber != e2.shopNumber ||
                e1.locationNumber != e2.locationNumber) {
                return false
            }
        }
        return true
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

    // --- Daily Tasks ---
    private val _selectedTaskDate = MutableStateFlow(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    val selectedTaskDate: StateFlow<String> = _selectedTaskDate.asStateFlow()

    fun setSelectedTaskDate(date: String) {
        _selectedTaskDate.value = date
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val tasksForSelectedDate: StateFlow<List<DailyTask>> = _selectedTaskDate
        .flatMapLatest { date ->
            repository.getTasksByDate(date)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val distinctTaskDates: StateFlow<List<String>> = repository.distinctTaskDates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTask(title: String, description: String, date: String, reminderTime: String? = null, isReminderEnabled: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.insertTask(
                    DailyTask(
                        title = title,
                        description = description,
                        taskDate = date,
                        reminderTime = reminderTime,
                        isReminderEnabled = isReminderEnabled
                    )
                )
            } catch (e: Exception) {
                triggerError("DailyTasks", "addTask", "DatabaseError", e.message ?: "Failed to add task", "Check database connection", e)
            }
        }
    }

    fun updateTask(task: DailyTask) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateTask(task)
            } catch (e: Exception) {
                triggerError("DailyTasks", "updateTask", "DatabaseError", e.message ?: "Failed to update task", "Check database connection", e)
            }
        }
    }

    fun deleteTask(task: DailyTask) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteTask(task)
            } catch (e: Exception) {
                triggerError("DailyTasks", "deleteTask", "DatabaseError", e.message ?: "Failed to delete task", "Check database connection", e)
            }
        }
    }

    fun toggleTaskCompletion(task: DailyTask) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedTask = task.copy(isCompleted = !task.isCompleted)
                repository.updateTask(updatedTask)
                if (updatedTask.isCompleted) {
                    val xpReward = 15
                    val newXp = _bonusXp.value + xpReward
                    prefs.edit().putInt("bonus_xp", newXp).apply()
                    _bonusXp.value = newXp
                    _gamificationEvents.emit(GamificationEvent.XpGain(xpReward, "Daily Task Completed"))
                }
            } catch (e: Exception) {
                triggerError("DailyTasks", "toggleTask", "DatabaseError", e.message ?: "Failed to toggle task status", "Check database connection", e)
            }
        }
    }

    val allTasks: StateFlow<List<DailyTask>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun exportDailyTasksToExcel(context: Context) {
        viewModelScope.launch {
            try {
                val tasks = repository.allTasks.first()
                com.example.utils.Exporter.exportDailyTasks(context, tasks)
            } catch (e: Exception) {
                e.printStackTrace()
                triggerError(
                    module = "Daily Tasks",
                    operation = "exportDailyTasksToExcel",
                    errorMessage = e.message ?: "Failed to export tasks",
                    stackTrace = e.stackTraceToString(),
                    possibleReason = "Excel export failure."
                )
            }
        }
    }

    fun importDailyTasksFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importSummary.value = null
            try {
                val existingTasks = repository.allTasks.first()
                val summary = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.utils.Exporter.importDailyTasks(context, uri, existingTasks)
                }

                if (summary.parsedDailyTasks.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        summary.parsedDailyTasks.forEach { task ->
                            if (task.id > 0) {
                                repository.updateTask(task)
                            } else {
                                repository.insertTask(task)
                            }
                        }
                    }
                }
                _importSummary.value = summary
            } catch (e: Exception) {
                e.printStackTrace()
                triggerError(
                    module = "Daily Tasks",
                    operation = "importDailyTasksFromExcel",
                    errorMessage = e.message ?: "Failed to import tasks",
                    stackTrace = e.stackTraceToString(),
                    possibleReason = "Corrupt file or parsing error."
                )
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun exportDynamicCostEngineToExcel(context: Context) {
        viewModelScope.launch {
            try {
                val ingredients = repository.getAllIngredientsDirect()
                val purchases = repository.getAllPurchasesDirect()
                val calculations = repository.getAllCalculationsDirect()
                val calculationItems = repository.getAllCalculationItemsDirect()
                val isEnabled = isDynamicProfitEnabled.value
                
                com.example.utils.Exporter.exportDynamicCostEngine(
                    context = context,
                    ingredients = ingredients,
                    purchases = purchases,
                    calculations = calculations,
                    calculationItems = calculationItems,
                    isDynamicProfitEnabled = isEnabled
                )
            } catch (e: Exception) {
                e.printStackTrace()
                triggerError(
                    module = "DynamicCostEngine",
                    operation = "exportDynamicCostEngineToExcel",
                    errorMessage = e.message ?: "Failed to export cost engine data",
                    stackTrace = e.stackTraceToString(),
                    possibleReason = "Excel export failure."
                )
            }
        }
    }

    fun importDynamicCostEngineFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importSummary.value = null
            try {
                val summary = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.utils.Exporter.importDynamicCostEngine(context, uri)
                }

                if (summary.parsedIngredients.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repository.insertIngredients(summary.parsedIngredients)
                    }
                }
                if (summary.parsedPurchases.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repository.insertPurchases(summary.parsedPurchases)
                    }
                }
                if (summary.parsedCalculations.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repository.insertCalculations(summary.parsedCalculations)
                    }
                }
                if (summary.parsedCalculationItems.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repository.insertCalculationItems(summary.parsedCalculationItems)
                    }
                }
                if (summary.isDynamicProfitEnabledSetting != null) {
                    setDynamicProfitEnabled(summary.isDynamicProfitEnabledSetting)
                }

                _importSummary.value = summary
            } catch (e: Exception) {
                e.printStackTrace()
                triggerError(
                    module = "DynamicCostEngine",
                    operation = "importDynamicCostEngineFromExcel",
                    errorMessage = e.message ?: "Failed to import cost engine data",
                    stackTrace = e.stackTraceToString(),
                    possibleReason = "Corrupt file or parsing error."
                )
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun copyUnfinishedTasksFromPreviousDay(currentDate: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val todayVal = sdf.parse(currentDate) ?: Date()
                val cal = Calendar.getInstance()
                cal.time = todayVal
                
                var foundTasks: List<DailyTask> = emptyList()
                for (i in 1..14) {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    val prevDateStr = sdf.format(cal.time)
                    val tasks = repository.getTasksByDate(prevDateStr).first()
                    val incomplete = tasks.filter { !it.isCompleted }
                    if (incomplete.isNotEmpty()) {
                        foundTasks = incomplete
                        break
                    }
                }

                if (foundTasks.isNotEmpty()) {
                    foundTasks.forEach { task ->
                        repository.insertTask(
                            DailyTask(
                                title = task.title,
                                description = task.description,
                                taskDate = currentDate,
                                reminderTime = task.reminderTime,
                                isReminderEnabled = task.isReminderEnabled
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                triggerError("DailyTasks", "copyPrevTasks", "DatabaseError", e.message ?: "Failed to copy tasks", "Check database connection", e)
            }
        }
    }

    val allIngredients: StateFlow<List<Ingredient>> = repository.allIngredients
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allIngredientPurchases: StateFlow<List<IngredientPurchase>> = repository.allPurchases
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addIngredient(name: String, variety: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.insertIngredient(
                    Ingredient(name = name, variety = variety, category = category)
                )
            } catch (e: Exception) {
                triggerError("DynamicCost", "addIngredient", "DatabaseError", e.message ?: "Failed to add ingredient", "Check database connection", e)
            }
        }
    }

    fun updateIngredient(ingredient: Ingredient) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateIngredient(ingredient)
            } catch (e: Exception) {
                triggerError("DynamicCost", "updateIngredient", "DatabaseError", e.message ?: "Failed to update ingredient", "Check database connection", e)
            }
        }
    }

    fun deleteIngredient(ingredient: Ingredient) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteIngredient(ingredient)
            } catch (e: Exception) {
                triggerError("DynamicCost", "deleteIngredient", "DatabaseError", e.message ?: "Failed to delete ingredient", "Check database connection", e)
            }
        }
    }

    fun addPurchase(
        ingredientId: Int,
        quantity: Double,
        unit: String,
        price: Double,
        date: String,
        supplier: String? = "",
        remarks: String? = "",
        sealCost: Double = 0.0,
        printingCost: Double = 0.0,
        largeCoverDistribution: Int = 1
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.insertPurchase(
                    IngredientPurchase(
                        ingredientId = ingredientId,
                        purchaseQuantity = quantity,
                        unit = unit,
                        purchasePrice = price,
                        purchaseDate = date,
                        supplier = supplier,
                        remarks = remarks,
                        sealCost = sealCost,
                        printingCost = printingCost,
                        largeCoverDistribution = largeCoverDistribution
                    )
                )
            } catch (e: Exception) {
                triggerError("DynamicCost", "addPurchase", "DatabaseError", e.message ?: "Failed to add purchase", "Check database connection", e)
            }
        }
    }

    fun updatePurchase(purchase: IngredientPurchase) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updatePurchase(purchase)
            } catch (e: Exception) {
                triggerError("DynamicCost", "updatePurchase", "DatabaseError", e.message ?: "Failed to update purchase", "Check database connection", e)
            }
        }
    }

    fun deletePurchase(purchase: IngredientPurchase) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deletePurchase(purchase)
            } catch (e: Exception) {
                triggerError("DynamicCost", "deletePurchase", "DatabaseError", e.message ?: "Failed to delete purchase", "Check database connection", e)
            }
        }
    }

    fun saveCostCalculation(calculation: CostCalculation, items: List<CostCalculationItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveCostCalculation(calculation, items)
            } catch (e: Exception) {
                triggerError("DynamicCost", "saveCostCalculation", "DatabaseError", e.message ?: "Failed to save calculation", "Check database connection", e)
            }
        }
    }

    fun updateCostCalculation(calculation: CostCalculation, items: List<CostCalculationItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateCostCalculation(calculation, items)
            } catch (e: Exception) {
                triggerError("DynamicCost", "updateCostCalculation", "DatabaseError", e.message ?: "Failed to update calculation", "Check database connection", e)
            }
        }
    }

    fun deleteCalculation(calculation: CostCalculation) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteCalculation(calculation)
            } catch (e: Exception) {
                triggerError("DynamicCost", "deleteCalculation", "DatabaseError", e.message ?: "Failed to delete calculation", "Check database connection", e)
            }
        }
    }

    fun getCalculationItems(calculationId: Int): Flow<List<CostCalculationItem>> {
        return repository.getCalculationItems(calculationId)
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
