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
    val notes: String? = null
) {
    val startingDateFormatted: String
        get() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(startingDate))
}

@Entity(tableName = "products")
data class ProductMaster(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productName: String,
    val productCategory: String,
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
