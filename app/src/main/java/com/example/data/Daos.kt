package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY locationNumber ASC")
    fun getAllLocations(): Flow<List<LocationMaster>>

    @Query("SELECT * FROM locations WHERE locationNumber = :number LIMIT 1")
    suspend fun getLocationByNumber(number: String): LocationMaster?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationMaster)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(locations: List<LocationMaster>)

    @Update
    suspend fun updateLocation(location: LocationMaster)

    @Delete
    suspend fun deleteLocation(location: LocationMaster)

    @Query("DELETE FROM locations")
    suspend fun deleteAllLocations()
}

@Dao
interface ShopDao {
    @Query("SELECT * FROM shops ORDER BY shopNumber ASC")
    fun getAllShops(): Flow<List<ShopMaster>>

    @Query("SELECT * FROM shops WHERE shopNumber = :number LIMIT 1")
    suspend fun getShopByNumber(number: String): ShopMaster?

    @Query("SELECT shopNumber FROM shops ORDER BY shopNumber DESC LIMIT 1")
    suspend fun getMaxShopNumber(): String?

    @Query("SELECT shopNumber FROM shops")
    suspend fun getAllShopNumbers(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShop(shop: ShopMaster)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShops(shops: List<ShopMaster>)

    @Update
    suspend fun updateShop(shop: ShopMaster)

    @Delete
    suspend fun deleteShop(shop: ShopMaster)

    @Query("DELETE FROM shops")
    suspend fun deleteAllShops()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY id DESC")
    fun getAllProducts(): Flow<List<ProductMaster>>

    @Query("SELECT * FROM products WHERE productName = :name LIMIT 1")
    suspend fun getProductByName(name: String): ProductMaster?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductMaster): Long

    @Update
    suspend fun updateProduct(product: ProductMaster)

    @Delete
    suspend fun deleteProduct(product: ProductMaster)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}

@Dao
interface ProductPriceDao {
    @Query("SELECT * FROM product_prices WHERE productId = :productId ORDER BY priceId ASC")
    fun getPricesForProduct(productId: Int): Flow<List<ProductPrice>>

    @Query("SELECT * FROM product_prices")
    suspend fun getAllPrices(): List<ProductPrice>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrice(price: ProductPrice)

    @Update
    suspend fun updatePrice(price: ProductPrice)

    @Delete
    suspend fun deletePrice(price: ProductPrice)

    @Query("DELETE FROM product_prices WHERE productId = :productId")
    suspend fun deletePricesForProduct(productId: Int)

    @Query("DELETE FROM product_prices")
    suspend fun deleteAllPrices()
}

@Dao
interface SalesDao {
    @Query("SELECT * FROM sales ORDER BY entryDate DESC")
    fun getAllSales(): Flow<List<SalesEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSales(sales: SalesEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalesList(salesList: List<SalesEntry>)

    @Update
    suspend fun updateSales(sales: SalesEntry)

    @Update
    suspend fun updateSalesList(salesList: List<SalesEntry>)

    @Delete
    suspend fun deleteSales(sales: SalesEntry)

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()

    @Query("DELETE FROM sales WHERE sessionId = :sessionId")
    suspend fun deleteSalesBySessionId(sessionId: String)

    @Query("DELETE FROM sales WHERE id = :id")
    suspend fun deleteSalesById(id: Int)

    @Query("UPDATE sales SET shopName = :newShopName, locationNumber = :newLocationNumber WHERE shopNumber = :shopNumber")
    suspend fun updateSalesShopDetails(shopNumber: String, newShopName: String, newLocationNumber: String)

    @Query("UPDATE sales SET shopNumber = :newShopNumber WHERE shopNumber = :oldShopNumber")
    suspend fun updateSalesShopNumber(oldShopNumber: String, newShopNumber: String)
}

@Dao
interface TimetableDao {
    @Query("SELECT * FROM weekly_timetable")
    fun getAllTimetableEntries(): Flow<List<TimetableEntry>>

    @Query("SELECT * FROM weekly_timetable")
    suspend fun getDirectTimetableEntries(): List<TimetableEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimetableEntry(entry: TimetableEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimetableEntries(entries: List<TimetableEntry>)

    @Update
    suspend fun updateTimetableEntry(entry: TimetableEntry)

    @Query("DELETE FROM weekly_timetable")
    suspend fun deleteAllTimetableEntries()
}

@Dao
interface DailyTargetDao {
    @Query("SELECT * FROM daily_targets WHERE id = 1 LIMIT 1")
    fun getDailyTarget(): Flow<DailyTarget?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyTarget(target: DailyTarget)
}

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badges")
    fun getAllBadges(): Flow<List<Badge>>

    @Query("SELECT * FROM user_badges")
    fun getUnlockedBadges(): Flow<List<UserBadge>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadge(badge: Badge)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun unlockBadge(userBadge: UserBadge)

    @Query("DELETE FROM user_badges WHERE badgeId = :badgeId")
    suspend fun revokeBadge(badgeId: String)
}

@Dao
interface ErrorLogDao {
    @Query("SELECT * FROM error_logs ORDER BY timestamp DESC")
    fun getAllErrorLogs(): Flow<List<ErrorLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertErrorLog(errorLog: ErrorLog)

    @Query("DELETE FROM error_logs")
    suspend fun clearErrorLogs()
}

@Dao
interface DailyTaskDao {
    @Query("SELECT * FROM daily_tasks ORDER BY id ASC")
    fun getAllTasks(): Flow<List<DailyTask>>

    @Query("SELECT * FROM daily_tasks WHERE taskDate = :date ORDER BY id ASC")
    fun getTasksByDate(date: String): Flow<List<DailyTask>>

    @Query("SELECT DISTINCT taskDate FROM daily_tasks ORDER BY taskDate DESC")
    fun getDistinctTaskDates(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DailyTask): Long

    @Update
    suspend fun updateTask(task: DailyTask)

    @Delete
    suspend fun deleteTask(task: DailyTask)

    @Query("DELETE FROM daily_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)
}

@Dao
interface DynamicCostDao {
    // --- Ingredient ---
    @Query("SELECT * FROM ingredients ORDER BY name ASC, variety ASC")
    fun getAllIngredients(): Flow<List<Ingredient>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredient(ingredient: Ingredient): Long

    @Update
    suspend fun updateIngredient(ingredient: Ingredient)

    @Delete
    suspend fun deleteIngredient(ingredient: Ingredient)

    // --- Ingredient Purchase ---
    @Query("SELECT * FROM ingredient_purchases ORDER BY purchaseDate DESC, purchaseId DESC")
    fun getAllPurchases(): Flow<List<IngredientPurchase>>

    @Query("SELECT * FROM ingredient_purchases WHERE ingredientId = :ingredientId ORDER BY purchaseDate DESC, purchaseId DESC")
    fun getPurchasesForIngredient(ingredientId: Int): Flow<List<IngredientPurchase>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: IngredientPurchase): Long

    @Update
    suspend fun updatePurchase(purchase: IngredientPurchase)

    @Delete
    suspend fun deletePurchase(purchase: IngredientPurchase)

    @Query("DELETE FROM ingredient_purchases WHERE purchaseId = :id")
    suspend fun deletePurchaseById(id: Int)

    // --- Cost Calculation ---
    @Query("SELECT * FROM cost_calculations ORDER BY calculationId DESC")
    fun getAllCalculations(): Flow<List<CostCalculation>>

    @Query("SELECT * FROM cost_calculations WHERE productPriceId = :productPriceId ORDER BY version DESC")
    fun getCalculationsForProductPrice(productPriceId: Int): Flow<List<CostCalculation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculation(calculation: CostCalculation): Long

    @Delete
    suspend fun deleteCalculation(calculation: CostCalculation)

    // --- Cost Calculation Item ---
    @Query("SELECT * FROM cost_calculation_items WHERE costCalculationId = :calculationId")
    fun getCalculationItems(calculationId: Int): Flow<List<CostCalculationItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculationItems(items: List<CostCalculationItem>)

    @Query("DELETE FROM cost_calculation_items WHERE costCalculationId = :calculationId")
    suspend fun deleteCalculationItemsForCalculation(calculationId: Int)
}



