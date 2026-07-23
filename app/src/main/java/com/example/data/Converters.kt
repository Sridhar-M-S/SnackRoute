package com.example.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.joinToString(",")
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(",")
    }

    @TypeConverter
    fun fromHistoryList(value: List<RemarkHistoryItem>?): String? {
        if (value == null) return null
        val array = org.json.JSONArray()
        for (item in value) {
            val obj = org.json.JSONObject()
            obj.put("id", item.id)
            obj.put("date", item.date)
            obj.put("note", item.note)
            obj.put("type", item.type)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toHistoryList(value: String?): List<RemarkHistoryItem>? {
        if (value.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<RemarkHistoryItem>()
        try {
            val array = org.json.JSONArray(value)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RemarkHistoryItem(
                        id = obj.getString("id"),
                        date = obj.getLong("date"),
                        note = obj.getString("note"),
                        type = obj.getString("type")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
