package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AppRepository(
        db,
        db.locationDao(),
        db.shopDao(),
        db.productDao(),
        db.salesDao()
    )

    // --- Core Database Flows ---
    val locations: StateFlow<List<LocationMaster>> = repository.allLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shops: StateFlow<List<ShopMaster>> = repository.allShops
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductMaster>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales: StateFlow<List<SalesEntry>> = repository.allSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Next Shop Number State ---
    private val _nextShopNumber = MutableStateFlow("SHOP0001")
    val nextShopNumber: StateFlow<String> = _nextShopNumber.asStateFlow()

    init {
        refreshNextShopNumber()
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

    fun addShop(shop: ShopMaster) = viewModelScope.launch {
        repository.insertShop(shop)
        refreshNextShopNumber()
    }

    fun updateShop(shop: ShopMaster) = viewModelScope.launch {
        repository.updateShop(shop)
        refreshNextShopNumber()
    }

    fun deleteShop(shop: ShopMaster) = viewModelScope.launch {
        repository.deleteShop(shop)
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
                    repository.insertShops(summary.parsedShops)
                    refreshNextShopNumber()
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
                val summary = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.utils.Exporter.importSales(context, uri, currentShops, currentProducts)
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

    fun addProduct(product: ProductMaster) = viewModelScope.launch {
        repository.insertProduct(product)
    }

    fun updateProduct(product: ProductMaster) = viewModelScope.launch {
        repository.updateProduct(product)
    }

    fun deleteProduct(product: ProductMaster) = viewModelScope.launch {
        repository.deleteProduct(product)
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
        return repository.backupDatabase(context)
    }

    fun restoreData(context: Context): Boolean {
        return repository.restoreDatabase(context)
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
}

enum class SuggestionType {
    INFO, SUCCESS, WARNING
}

data class BusinessSuggestion(
    val title: String,
    val message: String,
    val type: SuggestionType
)
