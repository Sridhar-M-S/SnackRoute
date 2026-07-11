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

    @Delete
    suspend fun deleteSales(sales: SalesEntry)

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()

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
}

