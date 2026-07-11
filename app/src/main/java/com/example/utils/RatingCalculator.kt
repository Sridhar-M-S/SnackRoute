package com.example.utils

import com.example.data.SalesEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ShopAnalytics(
    val currentRating: Float,
    val totalSalesTransactions: Int,
    val totalPacketsPurchased: Int,
    val totalRevenue: Double,
    val totalProfit: Double,
    val firstPurchaseDate: Long?,
    val lastPurchaseDate: Long?,
    val daysSinceLastPurchase: Long,
    val averagePurchasesPerMonth: Double
) {
    val ratingDescription: String
        get() = when {
            currentRating >= 4.8f -> "Excellent Customer"
            currentRating >= 4.3f -> "Very Regular Customer"
            currentRating >= 3.8f -> "Regular Customer"
            currentRating >= 2.8f -> "Average Customer"
            currentRating >= 1.8f -> "Low Activity Customer"
            else -> "Inactive Customer"
        }

    val firstPurchaseDateFormatted: String
        get() = firstPurchaseDate?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "N/A"

    val lastPurchaseDateFormatted: String
        get() = lastPurchaseDate?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "N/A"
}

object RatingCalculator {
    fun calculateAnalytics(salesForShop: List<SalesEntry>): ShopAnalytics {
        val totalSalesTransactions = salesForShop.size
        if (totalSalesTransactions == 0) {
            return ShopAnalytics(
                currentRating = 1.0f,
                totalSalesTransactions = 0,
                totalPacketsPurchased = 0,
                totalRevenue = 0.0,
                totalProfit = 0.0,
                firstPurchaseDate = null,
                lastPurchaseDate = null,
                daysSinceLastPurchase = 999L,
                averagePurchasesPerMonth = 0.0
            )
        }

        val totalPacketsPurchased = salesForShop.sumOf { it.packetsSold }
        val totalRevenue = salesForShop.sumOf { it.totalAmount }
        val totalProfit = salesForShop.sumOf { it.totalProfit }

        val firstPurchaseDate = salesForShop.minOfOrNull { it.entryDate }
        val lastPurchaseDate = salesForShop.maxOfOrNull { it.entryDate }

        val daysSinceLastPurchase = if (lastPurchaseDate != null) {
            val diff = System.currentTimeMillis() - lastPurchaseDate
            (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(0L)
        } else {
            999L
        }

        // Average Purchases Per Month
        val daysActive = if (firstPurchaseDate != null && lastPurchaseDate != null) {
            val diff = lastPurchaseDate - firstPurchaseDate
            (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(1L)
        } else {
            1L
        }
        val monthsActive = (daysActive / 30.0).coerceAtLeast(1.0)
        val averagePurchasesPerMonth = totalSalesTransactions / monthsActive

        // Calculate rating factors
        // 1. Purchase Frequency (max 1.0 points)
        // 1 transaction = 0.1 points, up to 10 transactions = 1.0 points
        val scoreSalesCount = (totalSalesTransactions * 0.1f).coerceAtMost(1.0f)

        // 2. Total Packets Purchased (max 1.0 points)
        // Every 10 packets = 0.1 points, up to 100 packets = 1.0 points
        val scorePackets = (totalPacketsPurchased * 0.01f).coerceAtMost(1.0f)

        // 3. Total Revenue (max 1.0 points)
        // Every ₹1000 = 0.1 points, up to ₹10000 = 1.0 points
        val scoreRevenue = (totalRevenue.toFloat() * 0.0001f).coerceAtMost(1.0f)

        // 4. Total Profit (max 0.5 points)
        // Every ₹500 profit = 0.1 points, up to ₹2500 profit = 0.5 points
        val scoreProfit = (totalProfit.toFloat() * 0.0002f).coerceAtMost(0.5f)

        // 5. Purchase Frequency & Consistency (max 0.5 points)
        // avgPurchasesPerMonth * 0.1f, up to 0.5f
        val scoreFrequency = (averagePurchasesPerMonth.toFloat() * 0.1f).coerceAtMost(0.5f)

        // 6. Recency (max 1.0 points multiplier/decay)
        val scoreRecency = when {
            daysSinceLastPurchase <= 7 -> 1.0f
            daysSinceLastPurchase <= 30 -> 0.8f
            daysSinceLastPurchase <= 90 -> 0.5f
            else -> 0.2f
        }

        val totalScore = scoreSalesCount + scorePackets + scoreRevenue + scoreProfit + scoreFrequency
        val calculated = 1.0f + totalScore * scoreRecency

        // Format to 1 decimal place and clamp to 1.0 - 5.0
        val finalRating = (Math.round(calculated * 10f) / 10f).coerceIn(1.0f, 5.0f)

        return ShopAnalytics(
            currentRating = finalRating,
            totalSalesTransactions = totalSalesTransactions,
            totalPacketsPurchased = totalPacketsPurchased,
            totalRevenue = totalRevenue,
            totalProfit = totalProfit,
            firstPurchaseDate = firstPurchaseDate,
            lastPurchaseDate = lastPurchaseDate,
            daysSinceLastPurchase = daysSinceLastPurchase,
            averagePurchasesPerMonth = averagePurchasesPerMonth
        )
    }
}
