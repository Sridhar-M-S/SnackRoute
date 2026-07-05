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
}

@Dao
interface ShopDao {
    @Query("SELECT * FROM shops ORDER BY shopNumber ASC")
    fun getAllShops(): Flow<List<ShopMaster>>

    @Query("SELECT * FROM shops WHERE shopNumber = :number LIMIT 1")
    suspend fun getShopByNumber(number: String): ShopMaster?

    @Query("SELECT shopNumber FROM shops ORDER BY shopNumber DESC LIMIT 1")
    suspend fun getMaxShopNumber(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShop(shop: ShopMaster)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShops(shops: List<ShopMaster>)

    @Update
    suspend fun updateShop(shop: ShopMaster)

    @Delete
    suspend fun deleteShop(shop: ShopMaster)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY id DESC")
    fun getAllProducts(): Flow<List<ProductMaster>>

    @Query("SELECT * FROM products WHERE productName = :name LIMIT 1")
    suspend fun getProductByName(name: String): ProductMaster?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductMaster)

    @Update
    suspend fun updateProduct(product: ProductMaster)

    @Delete
    suspend fun deleteProduct(product: ProductMaster)
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
}
