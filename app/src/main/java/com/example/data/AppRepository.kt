package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class AppRepository(
    private val database: AppDatabase,
    private val locationDao: LocationDao,
    private val shopDao: ShopDao,
    private val productDao: ProductDao,
    private val salesDao: SalesDao
) {
    // --- Location Master Queries ---
    val allLocations: Flow<List<LocationMaster>> = locationDao.getAllLocations()
    suspend fun insertLocation(location: LocationMaster) = locationDao.insertLocation(location)
    suspend fun insertLocations(locations: List<LocationMaster>) = locationDao.insertLocations(locations)
    suspend fun updateLocation(location: LocationMaster) = locationDao.updateLocation(location)
    suspend fun deleteLocation(location: LocationMaster) = locationDao.deleteLocation(location)
    suspend fun getLocationByNumber(number: String) = locationDao.getLocationByNumber(number)

    // --- Shop Master Queries ---
    val allShops: Flow<List<ShopMaster>> = shopDao.getAllShops()
    suspend fun insertShop(shop: ShopMaster) = shopDao.insertShop(shop)
    suspend fun insertShops(shops: List<ShopMaster>) = shopDao.insertShops(shops)
    suspend fun updateShop(shop: ShopMaster) = shopDao.updateShop(shop)
    suspend fun deleteShop(shop: ShopMaster) = shopDao.deleteShop(shop)
    suspend fun getShopByNumber(number: String) = shopDao.getShopByNumber(number)

    suspend fun generateNextShopNumber(): String {
        val maxShop = shopDao.getMaxShopNumber() ?: return "SHOP0001"
        return try {
            val numStr = maxShop.removePrefix("SHOP")
            val nextNum = numStr.toInt() + 1
            "SHOP" + String.format("%04d", nextNum)
        } catch (e: Exception) {
            "SHOP0001"
        }
    }

    // --- Product Master Queries ---
    val allProducts: Flow<List<ProductMaster>> = productDao.getAllProducts()
    suspend fun insertProduct(product: ProductMaster) = productDao.insertProduct(product)
    suspend fun updateProduct(product: ProductMaster) = productDao.updateProduct(product)
    suspend fun deleteProduct(product: ProductMaster) = productDao.deleteProduct(product)
    suspend fun getProductByName(name: String) = productDao.getProductByName(name)

    // --- Sales Entry Queries ---
    val allSales: Flow<List<SalesEntry>> = salesDao.getAllSales()
    suspend fun insertSales(sales: SalesEntry) = salesDao.insertSales(sales)
    suspend fun insertSalesList(salesList: List<SalesEntry>) = salesDao.insertSalesList(salesList)
    suspend fun updateSales(sales: SalesEntry) = salesDao.updateSales(sales)
    suspend fun deleteSales(sales: SalesEntry) = salesDao.deleteSales(sales)

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
}
