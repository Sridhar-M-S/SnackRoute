package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "locations")
data class LocationMaster(
    @PrimaryKey val locationNumber: String, // e.g. LOC001
    val locationName: String
)

@Entity(tableName = "shops")
data class ShopMaster(
    @PrimaryKey val shopNumber: String, // SHOP0001, SHOP0002...
    val locationNumber: String,
    val storeName: String,
    val storeImage: String? = null,
    val rating: Float = 0f,
    val score: Int = 0,
    val startingDate: Long = System.currentTimeMillis(),
    val googleMapLink: String? = null,
    val mobileNumber: String? = null,
    val notes: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coordinateStatus: String? = null, // "Valid", "Invalid", "Pending"
    val lastCoordinateUpdate: Long? = null,
    val coordinateError: String? = null
) {
    val startingDateFormatted: String
        get() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(startingDate))

    val lastCoordinateUpdateFormatted: String?
        get() = lastCoordinateUpdate?.let { SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(it)) }
}

@Entity(tableName = "products")
data class ProductMaster(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productName: String,
    val productCategory: String,
    val status: String = "Active" // Active, Inactive
)

@Entity(tableName = "product_prices")
data class ProductPrice(
    @PrimaryKey(autoGenerate = true) val priceId: Int = 0,
    val productId: Int,
    val sellingPrice: Double,
    val profitPerPacket: Double,
    val status: String = "Active" // Active, Inactive
)

@Entity(tableName = "sales")
data class SalesEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val entryDate: Long = System.currentTimeMillis(),
    val shopNumber: String,
    val shopName: String,
    val locationNumber: String,
    val productName: String,
    val packetsGiven: Int,
    val packetsReturned: Int = 0,
    val packetsSold: Int, // Given - Returned
    val ratePerPacket: Double,
    val totalAmount: Double, // Packets Sold * Rate
    val profitPerPacket: Double,
    val totalProfit: Double, // Packets Sold * Profit Per Packet
    val status: String, // Paid, Pending, Partially Paid
    val remarks: String? = null
) {
    val entryDateFormatted: String
        get() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(entryDate))
}

@Entity(tableName = "weekly_timetable")
data class TimetableEntry(
    @PrimaryKey val dayOfWeek: String, // "Monday", "Tuesday", etc.
    val locationNumbers: List<String> = emptyList(), // Store as list of location numbers
    val notes: String = ""
)

@Entity(tableName = "daily_targets")
data class DailyTarget(
    @PrimaryKey val id: Int = 1,
    val packetTarget: Int,
    val salesAmountTarget: Double,
    val profitTarget: Double
)

@Entity(tableName = "badges")
data class Badge(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val iconName: String
)

@Entity(tableName = "user_badges")
data class UserBadge(
    @PrimaryKey val badgeId: String,
    val unlockedAt: Long
)

