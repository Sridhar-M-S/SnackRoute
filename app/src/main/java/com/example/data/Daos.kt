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

    @Query("""
        SELECT s.*, m.lastSaleDate 
        FROM shops s 
        INNER JOIN (
            SELECT shopNumber, MAX(entryDate) as lastSaleDate 
            FROM sales 
            GROUP BY shopNumber
        ) m ON s.shopNumber = m.shopNumber
    """)
    fun getShopsWithLastSale(): Flow<List<ShopWithLastSale>>
}

data class ShopWithLastSale(
    @Embedded val shop: ShopMaster,
    val lastSaleDate: Long
)

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

    @Query("SELECT * FROM product_prices")
    fun getAllPricesFlow(): Flow<List<ProductPrice>>

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

    @Query("SELECT * FROM sales WHERE shopNumber IN (:shopNumbers) ORDER BY entryDate DESC")
    suspend fun getSalesForShopsDirect(shopNumbers: List<String>): List<SalesEntry>
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

    @Query("SELECT * FROM cost_calculation_items")
    fun getAllCalculationItems(): Flow<List<CostCalculationItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculationItems(items: List<CostCalculationItem>)

    @Query("DELETE FROM cost_calculation_items WHERE costCalculationId = :calculationId")
    suspend fun deleteCalculationItemsForCalculation(calculationId: Int)

    // --- Direct Getters for Import/Export ---
    @Query("SELECT * FROM ingredients")
    suspend fun getAllIngredientsDirect(): List<Ingredient>

    @Query("SELECT * FROM ingredient_purchases")
    suspend fun getAllPurchasesDirect(): List<IngredientPurchase>

    @Query("SELECT * FROM cost_calculations")
    suspend fun getAllCalculationsDirect(): List<CostCalculation>

    @Query("SELECT * FROM cost_calculation_items")
    suspend fun getAllCalculationItemsDirect(): List<CostCalculationItem>

    // --- Bulk Inserts for Import/Export ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<Ingredient>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchases(purchases: List<IngredientPurchase>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculations(calculations: List<CostCalculation>)
}

@Dao
interface ShopRemarkDao {
    @Query("SELECT * FROM shop_remarks ORDER BY date DESC")
    fun getAllRemarks(): Flow<List<ShopRemark>>

    @Query("SELECT * FROM shop_remarks ORDER BY date DESC")
    suspend fun getAllRemarksDirect(): List<ShopRemark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemark(remark: ShopRemark): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemarks(remarks: List<ShopRemark>)

    @Update
    suspend fun updateRemark(remark: ShopRemark)

    @Delete
    suspend fun deleteRemark(remark: ShopRemark)

    @Query("DELETE FROM shop_remarks")
    suspend fun deleteAllRemarks()

    @Query("DELETE FROM shop_remarks WHERE id = :id")
    suspend fun deleteRemarkById(id: Int)

    @Query("SELECT * FROM shop_remarks WHERE salesEntryId = :salesId LIMIT 1")
    suspend fun getRemarkBySalesId(salesId: Int): ShopRemark?

    @Query("DELETE FROM shop_remarks WHERE salesEntryId = :salesId")
    suspend fun deleteRemarkBySalesId(salesId: Int)

    @Query("DELETE FROM shop_remarks WHERE shopNumber = :shopNumber")
    suspend fun deleteRemarksByShopNumber(shopNumber: String)
}

@Dao
interface BusinessExpenseDao {
    @Query("SELECT * FROM business_expenses ORDER BY expenseDate DESC")
    fun getAllExpenses(): Flow<List<BusinessExpense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: BusinessExpense): Long

    @Update
    suspend fun updateExpense(expense: BusinessExpense)

    @Delete
    suspend fun deleteExpense(expense: BusinessExpense)

    @Query("DELETE FROM business_expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Int)

    @Query("SELECT * FROM business_expenses")
    suspend fun getAllExpensesDirect(): List<BusinessExpense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<BusinessExpense>)

    @Query("DELETE FROM business_expenses")
    suspend fun deleteAllExpenses()
}

@Dao
interface ProductCostDao {
    @Query("SELECT * FROM product_cost_ingredients ORDER BY name ASC")
    fun getAllIngredients(): Flow<List<ProductCostIngredient>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredient(ingredient: ProductCostIngredient): Long

    @Update
    suspend fun updateIngredient(ingredient: ProductCostIngredient)

    @Delete
    suspend fun deleteIngredient(ingredient: ProductCostIngredient)

    @Query("SELECT * FROM product_cost_calculations ORDER BY date DESC")
    fun getAllCalculations(): Flow<List<ProductCostCalculation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculation(calculation: ProductCostCalculation): Long

    @Delete
    suspend fun deleteCalculation(calculation: ProductCostCalculation)

    // Direct methods for Export/Import
    @Query("SELECT * FROM product_cost_ingredients")
    suspend fun getAllIngredientsDirect(): List<ProductCostIngredient>

    @Query("SELECT * FROM product_cost_calculations")
    suspend fun getAllCalculationsDirect(): List<ProductCostCalculation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<ProductCostIngredient>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculations(calculations: List<ProductCostCalculation>)

    @Query("DELETE FROM product_cost_ingredients")
    suspend fun deleteAllIngredients()

    @Query("DELETE FROM product_cost_calculations")
    suspend fun deleteAllCalculations()
}

@Dao
interface SalesTargetDao {
    @Query("SELECT * FROM sales_target_items WHERE targetDate = :date ORDER BY id ASC")
    fun getTargetsForDate(date: String): Flow<List<SalesTargetItem>>

    @Query("SELECT * FROM sales_target_items ORDER BY id ASC")
    fun getAllTargets(): Flow<List<SalesTargetItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargetItem(item: SalesTargetItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargetItems(items: List<SalesTargetItem>)

    @Update
    suspend fun updateTargetItem(item: SalesTargetItem)

    @Delete
    suspend fun deleteTargetItem(item: SalesTargetItem)

    @Query("DELETE FROM sales_target_items WHERE id = :id")
    suspend fun deleteTargetItemById(id: Int)

    @Query("DELETE FROM sales_target_items WHERE targetDate = :date")
    suspend fun deleteTargetsForDate(date: String)

    @Query("DELETE FROM sales_target_items")
    suspend fun deleteAllTargets()

    @Query("SELECT * FROM sales_target_items")
    suspend fun getAllTargetsDirect(): List<SalesTargetItem>
}

@Dao
interface PaymentInvoiceDao {
    @Query("SELECT * FROM payment_invoices ORDER BY invoiceDate DESC, id DESC")
    fun getAllInvoices(): Flow<List<PaymentInvoice>>

    @Query("SELECT * FROM payment_invoices ORDER BY invoiceDate DESC, id DESC")
    suspend fun getAllInvoicesDirect(): List<PaymentInvoice>

    @Query("SELECT * FROM payment_invoices WHERE id = :id LIMIT 1")
    suspend fun getInvoiceById(id: Int): PaymentInvoice?

    @Query("SELECT * FROM payment_invoices WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getInvoiceByNumber(invoiceNumber: String): PaymentInvoice?

    @Query("SELECT * FROM payment_invoices WHERE shopNumber = :shopNumber ORDER BY invoiceDate DESC")
    fun getInvoicesForShop(shopNumber: String): Flow<List<PaymentInvoice>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: PaymentInvoice): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoices(invoices: List<PaymentInvoice>)

    @Update
    suspend fun updateInvoice(invoice: PaymentInvoice)

    @Delete
    suspend fun deleteInvoice(invoice: PaymentInvoice)

    @Query("DELETE FROM payment_invoices WHERE id = :id")
    suspend fun deleteInvoiceById(id: Int)

    @Query("DELETE FROM payment_invoices")
    suspend fun deleteAllInvoices()
}






