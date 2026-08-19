package com.example.data

import org.json.JSONObject

data class UnexportedChange(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val category: String, // "Sales", "Locations", "Shops", "Products", "Expenses", "Daily Tasks", "Shop Remarks", "Cost Engine", "Weekly Timetable", "Sales Targets"
    val changeType: String, // "ADD", "UPDATE", "DELETE", "IMPORT"
    val count: Int = 1,
    val summarySentence: String, // e.g. "1 new location added: LOC001 - Downtown", "3 sales added for SHOP0001"
    val detailText: String? = null
) {
    fun toJsonString(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("timestamp", timestamp)
        json.put("category", category)
        json.put("changeType", changeType)
        json.put("count", count)
        json.put("summarySentence", summarySentence)
        if (detailText != null) {
            json.put("detailText", detailText)
        }
        return json.toString()
    }

    companion object {
        fun fromJsonString(str: String): UnexportedChange? {
            return try {
                val json = JSONObject(str)
                UnexportedChange(
                    id = json.optString("id", java.util.UUID.randomUUID().toString()),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    category = json.optString("category", "General"),
                    changeType = json.optString("changeType", "UPDATE"),
                    count = json.optInt("count", 1),
                    summarySentence = json.optString("summarySentence", "Data updated"),
                    detailText = if (json.has("detailText")) json.optString("detailText") else null
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class ExportCategorySummary(
    val category: String,
    val totalCount: Int,
    val headlineSentence: String,
    val items: List<UnexportedChange>
)
