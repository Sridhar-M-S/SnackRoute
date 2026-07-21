package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class AppRepository(
    private val database: AppDatabase,
    private val locationDao: LocationDao,
    private val shopDao: ShopDao,
    private val productDao: ProductDao,
    private val productPriceDao: ProductPriceDao,
    private val salesDao: SalesDao,
    private val timetableDao: TimetableDao,
    private val dailyTargetDao: DailyTargetDao,
    private val badgeDao: BadgeDao,
    private val errorLogDao: ErrorLogDao,
    private val dailyTaskDao: DailyTaskDao,
    private val dynamicCostDao: DynamicCostDao
) {
    // --- Error Log Queries ---
    val allErrorLogs: Flow<List<ErrorLog>> = errorLogDao.getAllErrorLogs()
    suspend fun insertErrorLog(errorLog: ErrorLog) = errorLogDao.insertErrorLog(errorLog)
    suspend fun clearErrorLogs() = errorLogDao.clearErrorLogs()

    // --- Badge Queries ---
    val allBadges: Flow<List<Badge>> = badgeDao.getAllBadges()
    val unlockedBadges: Flow<List<UserBadge>> = badgeDao.getUnlockedBadges()
    suspend fun insertBadge(badge: Badge) = badgeDao.insertBadge(badge)
    suspend fun unlockBadge(badgeId: String) = badgeDao.unlockBadge(UserBadge(badgeId, System.currentTimeMillis()))
    suspend fun revokeBadge(badgeId: String) = badgeDao.revokeBadge(badgeId)

    // --- Weekly Timetable Queries ---
    val allTimetableEntries: Flow<List<TimetableEntry>> = timetableDao.getAllTimetableEntries()
    // ... (rest of the file)
    // --- Daily Target Queries ---
    val dailyTarget: Flow<DailyTarget?> = dailyTargetDao.getDailyTarget()
    suspend fun insertDailyTarget(target: DailyTarget) = dailyTargetDao.insertDailyTarget(target)

    suspend fun updateTimetableEntry(entry: TimetableEntry) = timetableDao.updateTimetableEntry(entry)

    suspend fun initializeTimetableIfNeeded() {
        val existing = timetableDao.getDirectTimetableEntries()
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val missingDays = days.filter { day -> existing.none { it.dayOfWeek.equals(day, ignoreCase = true) } }
        
        if (missingDays.isNotEmpty()) {
            val newEntries = missingDays.map { day ->
                TimetableEntry(dayOfWeek = day)
            }
            timetableDao.insertTimetableEntries(newEntries)
        }
    }

    // --- Location Master Queries ---
    val allLocations: Flow<List<LocationMaster>> = locationDao.getAllLocations()
    suspend fun insertLocation(location: LocationMaster) = locationDao.insertLocation(location)
    suspend fun insertLocations(locations: List<LocationMaster>) = locationDao.insertLocations(locations)
    suspend fun updateLocation(location: LocationMaster) = locationDao.updateLocation(location)
    suspend fun deleteLocation(location: LocationMaster) = locationDao.deleteLocation(location)
    suspend fun deleteAllLocations() = locationDao.deleteAllLocations()
    suspend fun getLocationByNumber(number: String) = locationDao.getLocationByNumber(number)

    // --- Shop Master Queries ---
    val allShops: Flow<List<ShopMaster>> = shopDao.getAllShops()
    suspend fun insertShop(shop: ShopMaster) {
        database.withTransaction {
            shopDao.insertShop(shop)
            salesDao.updateSalesShopDetails(shop.shopNumber, shop.storeName, shop.locationNumber)
        }
    }
    suspend fun insertShops(shops: List<ShopMaster>) {
        android.util.Log.d("SnackRouteDiagnostic", "Starting Room transaction: insertShops for ${shops.size} shops")
        database.withTransaction {
            shopDao.insertShops(shops)
            shops.forEach { shop ->
                salesDao.updateSalesShopDetails(shop.shopNumber, shop.storeName, shop.locationNumber)
            }
        }
        android.util.Log.d("SnackRouteDiagnostic", "Finished Room transaction: insertShops")
    }
    suspend fun updateShop(oldShopNumber: String, shop: ShopMaster) {
        database.withTransaction {
            if (oldShopNumber != shop.shopNumber) {
                val oldRecord = shopDao.getShopByNumber(oldShopNumber)
                if (oldRecord != null) {
                    shopDao.deleteShop(oldRecord)
                }
                shopDao.insertShop(shop)
                salesDao.updateSalesShopNumber(oldShopNumber, shop.shopNumber)
            } else {
                shopDao.updateShop(shop)
            }
            salesDao.updateSalesShopDetails(shop.shopNumber, shop.storeName, shop.locationNumber)
        }
    }
    suspend fun deleteShop(shop: ShopMaster) = shopDao.deleteShop(shop)
    suspend fun deleteAllShops() = shopDao.deleteAllShops()
    suspend fun getShopByNumber(number: String) = shopDao.getShopByNumber(number)

    suspend fun generateNextShopNumber(): String {
        return try {
            val numbers = shopDao.getAllShopNumbers()
            if (numbers.isEmpty()) {
                "1"
            } else {
                val hasShopPrefix = numbers.any { it.startsWith("SHOP", ignoreCase = true) }
                val existingInts = numbers.mapNotNull { num ->
                    num.filter { it.isDigit() }.toIntOrNull()
                }.toSet()
                
                var candidate = 1
                while (existingInts.contains(candidate)) {
                    candidate++
                }
                
                if (hasShopPrefix) {
                    "SHOP" + String.format("%04d", candidate)
                } else {
                    candidate.toString()
                }
            }
        } catch (e: Exception) {
            "1"
        }
    }

    // --- Product Master Queries ---
    val allProducts: Flow<List<ProductMaster>> = productDao.getAllProducts()
    suspend fun insertProduct(product: ProductMaster) = productDao.insertProduct(product)
    suspend fun updateProduct(product: ProductMaster) = productDao.updateProduct(product)
    suspend fun deleteProductWithPrices(product: ProductMaster) {
        database.withTransaction {
            productPriceDao.deletePricesForProduct(product.id)
            productDao.deleteProduct(product)
        }
    }
    suspend fun deleteAllProducts() {
        database.withTransaction {
            productPriceDao.deleteAllPrices()
            productDao.deleteAllProducts()
        }
    }
    suspend fun getProductByName(name: String) = productDao.getProductByName(name)

    suspend fun insertProductWithPrices(product: ProductMaster, prices: List<ProductPrice>) {
        database.withTransaction {
            val productId = productDao.insertProduct(product)
            prices.forEach {
                productPriceDao.insertPrice(it.copy(productId = productId.toInt()))
            }
        }
    }

    // --- Product Price Queries ---
    fun getPricesForProduct(productId: Int) = productPriceDao.getPricesForProduct(productId)
    suspend fun getAllPrices() = productPriceDao.getAllPrices()
    suspend fun insertPrice(price: ProductPrice) = productPriceDao.insertPrice(price)
    suspend fun updatePrice(price: ProductPrice) = productPriceDao.updatePrice(price)
    suspend fun deletePrice(price: ProductPrice) = productPriceDao.deletePrice(price)
    suspend fun deletePricesForProduct(productId: Int) = productPriceDao.deletePricesForProduct(productId)

    // --- Sales Entry Queries ---
    val allSales: Flow<List<SalesEntry>> = salesDao.getAllSales()
    suspend fun insertSales(sales: SalesEntry) = salesDao.insertSales(sales)
    suspend fun insertSalesList(salesList: List<SalesEntry>) = salesDao.insertSalesList(salesList)
    suspend fun updateSales(sales: SalesEntry) = salesDao.updateSales(sales)
    suspend fun updateSalesList(salesList: List<SalesEntry>) = salesDao.updateSalesList(salesList)
    suspend fun deleteSales(sales: SalesEntry) = salesDao.deleteSales(sales)
    suspend fun deleteAllSales() = salesDao.deleteAllSales()
    suspend fun deleteSalesBySessionId(sessionId: String) = salesDao.deleteSalesBySessionId(sessionId)
    suspend fun deleteSalesById(id: Int) = salesDao.deleteSalesById(id)
    suspend fun saveSalesSession(salesList: List<SalesEntry>, oldSessionId: String?, legacyIdToDelete: Int?) {
        database.withTransaction {
            if (!oldSessionId.isNullOrEmpty()) {
                salesDao.deleteSalesBySessionId(oldSessionId)
            } else if (legacyIdToDelete != null && legacyIdToDelete != 0) {
                salesDao.deleteSalesById(legacyIdToDelete)
            }
            salesDao.insertSalesList(salesList)
        }
    }

    // --- Backup and Restore Logic ---
    fun backupDatabase(context: Context): Boolean {
        return try {
            database.close() // Close database to ensure safety

            val dbFile = context.getDatabasePath("snackroute_pro_db")
            if (!dbFile.exists()) return false

            val backupDir = File(context.filesDir, "backups")
            if (!backupDir.exists()) backupDir.mkdirs()

            val backupFile = File(backupDir, "snackroute_pro_db_backup.db")
            dbFile.copyTo(backupFile, overwrite = true)

            // Copy journaling files if they exist
            val walFile = File(dbFile.path + "-wal")
            if (walFile.exists()) {
                val backupWal = File(backupDir, "snackroute_pro_db_backup.db-wal")
                walFile.copyTo(backupWal, overwrite = true)
            }

            val shmFile = File(dbFile.path + "-shm")
            if (shmFile.exists()) {
                val backupShm = File(backupDir, "snackroute_pro_db_backup.db-shm")
                shmFile.copyTo(backupShm, overwrite = true)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun restoreDatabase(context: Context): Boolean {
        return try {
            database.close() // Close database to write safety

            val backupDir = File(context.filesDir, "backups")
            val backupFile = File(backupDir, "snackroute_pro_db_backup.db")
            if (!backupFile.exists()) return false

            val dbFile = context.getDatabasePath("snackroute_pro_db")
            backupFile.copyTo(dbFile, overwrite = true)

            // Copy journaling files if they exist
            val backupWal = File(backupDir, "snackroute_pro_db_backup.db-wal")
            val walFile = File(dbFile.path + "-wal")
            if (backupWal.exists()) {
                backupWal.copyTo(walFile, overwrite = true)
            } else if (walFile.exists()) {
                walFile.delete()
            }

            val backupShm = File(backupDir, "snackroute_pro_db_backup.db-shm")
            val shmFile = File(dbFile.path + "-shm")
            if (backupShm.exists()) {
                backupShm.copyTo(shmFile, overwrite = true)
            } else if (shmFile.exists()) {
                shmFile.delete()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Daily Task Queries & Mutations ---
    val allTasks: Flow<List<DailyTask>> = dailyTaskDao.getAllTasks()
    fun getTasksByDate(date: String): Flow<List<DailyTask>> = dailyTaskDao.getTasksByDate(date)
    val distinctTaskDates: Flow<List<String>> = dailyTaskDao.getDistinctTaskDates()

    suspend fun insertTask(task: DailyTask): Long = dailyTaskDao.insertTask(task)
    suspend fun updateTask(task: DailyTask) = dailyTaskDao.updateTask(task)
    suspend fun deleteTask(task: DailyTask) = dailyTaskDao.deleteTask(task)
    suspend fun deleteTaskById(id: Int) = dailyTaskDao.deleteTaskById(id)

    // --- Dynamic Cost calculation methods ---
    val allIngredients: Flow<List<Ingredient>> = dynamicCostDao.getAllIngredients()
    suspend fun insertIngredient(ingredient: Ingredient): Long = dynamicCostDao.insertIngredient(ingredient)
    suspend fun updateIngredient(ingredient: Ingredient) = dynamicCostDao.updateIngredient(ingredient)
    suspend fun deleteIngredient(ingredient: Ingredient) = dynamicCostDao.deleteIngredient(ingredient)

    val allPurchases: Flow<List<IngredientPurchase>> = dynamicCostDao.getAllPurchases()
    fun getPurchasesForIngredient(id: Int): Flow<List<IngredientPurchase>> = dynamicCostDao.getPurchasesForIngredient(id)
    suspend fun insertPurchase(purchase: IngredientPurchase): Long = dynamicCostDao.insertPurchase(purchase)
    suspend fun updatePurchase(purchase: IngredientPurchase) = dynamicCostDao.updatePurchase(purchase)
    suspend fun deletePurchase(purchase: IngredientPurchase) = dynamicCostDao.deletePurchase(purchase)
    suspend fun deletePurchaseById(id: Int) = dynamicCostDao.deletePurchaseById(id)

    val allCalculations: Flow<List<CostCalculation>> = dynamicCostDao.getAllCalculations()
    fun getCalculationsForProductPrice(productPriceId: Int): Flow<List<CostCalculation>> = dynamicCostDao.getCalculationsForProductPrice(productPriceId)
    
    suspend fun saveCostCalculation(calculation: CostCalculation, items: List<CostCalculationItem>) {
        database.withTransaction {
            val calcId = dynamicCostDao.insertCalculation(calculation)
            val finalItems = items.map { it.copy(costCalculationId = calcId.toInt()) }
            dynamicCostDao.insertCalculationItems(finalItems)
        }
    }

    suspend fun updateCostCalculation(calculation: CostCalculation, items: List<CostCalculationItem>) {
        database.withTransaction {
            dynamicCostDao.insertCalculation(calculation)
            dynamicCostDao.deleteCalculationItemsForCalculation(calculation.calculationId)
            val finalItems = items.map { it.copy(costCalculationId = calculation.calculationId) }
            dynamicCostDao.insertCalculationItems(finalItems)
        }
    }

    suspend fun deleteCalculation(calculation: CostCalculation) {
        database.withTransaction {
            dynamicCostDao.deleteCalculationItemsForCalculation(calculation.calculationId)
            dynamicCostDao.deleteCalculation(calculation)
        }
    }

    fun getCalculationItems(calculationId: Int): Flow<List<CostCalculationItem>> = dynamicCostDao.getCalculationItems(calculationId)
}
