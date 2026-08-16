package com.example.utils

import java.net.URLDecoder

object LocationUtils {

    fun extractCoordinates(text: String, resolvedUrl: String? = null): Pair<Double, Double>? {
        if (text.isBlank()) return null
        val urlToParse = resolvedUrl ?: text
        val decoded = try {
            URLDecoder.decode(urlToParse, "UTF-8")
        } catch (e: Exception) {
            urlToParse
        }

        // 1. Try to find @lat,lng format
        val atPattern = Regex("@(-?\\d{1,3}\\.\\d+)\\s*,\\s*(-?\\d{1,3}\\.\\d+)")
        atPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (isValid(lat, lng)) return Pair(lat!!, lng!!)
        }

        // 2. Google Maps !3d<lat>!4d<lng> data format
        val data3d4dPattern = Regex("!3d(-?\\d{1,3}\\.\\d+)!4d(-?\\d{1,3}\\.\\d+)")
        data3d4dPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (isValid(lat, lng)) return Pair(lat!!, lng!!)
        }

        // 3. Parameter pattern e.g. q=lat,lng or query=lat,lng or destination=... or loc:... or ll=...
        val paramPattern = Regex("(?:[?&](?:q|query|daddr|saddr|ll|cbll|destination|center|loc:?)=)(-?\\d{1,3}\\.\\d+)[\\s,+](-?\\d{1,3}\\.\\d+)")
        paramPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (isValid(lat, lng)) return Pair(lat!!, lng!!)
        }

        // 4. Path pattern: e.g. /place/lat,lng or /dir/lat,lng or /search/lat,lng
        val pathPattern = Regex("/(?:place|dir|search|maps)/(-?\\d{1,3}\\.\\d+)[\\s,+](-?\\d{1,3}\\.\\d+)")
        pathPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (isValid(lat, lng)) return Pair(lat!!, lng!!)
        }

        // 5. DMS format: e.g. 12°58'17.8"N 77°35'40.4"E
        fun parseDMS(deg: String, min: String, sec: String, dir: String): Double? {
            val d = deg.toDoubleOrNull() ?: return null
            val m = min.toDoubleOrNull() ?: 0.0
            val s = sec.toDoubleOrNull() ?: 0.0
            var decimal = d + (m / 60.0) + (s / 3600.0)
            if (dir.equals("S", ignoreCase = true) || dir.equals("W", ignoreCase = true)) {
                decimal = -decimal
            }
            return decimal
        }

        val dmsRegex = Regex("(\\d+)[°\\s]+(\\d+)[\\'\\s]+(\\d+(?:\\.\\d+)?)\"?\\s*([NSEWnsew])")
        val dmsMatches = dmsRegex.findAll(decoded).toList()
        if (dmsMatches.size >= 2) {
            val lat = parseDMS(dmsMatches[0].groupValues[1], dmsMatches[0].groupValues[2], dmsMatches[0].groupValues[3], dmsMatches[0].groupValues[4])
            val lng = parseDMS(dmsMatches[1].groupValues[1], dmsMatches[1].groupValues[2], dmsMatches[1].groupValues[3], dmsMatches[1].groupValues[4])
            if (isValid(lat, lng)) return Pair(lat!!, lng!!)
        }

        // 6. Generic decimal pair: e.g. "12.971598, 77.594562" or "12.971598+77.594562"
        val genericPattern = Regex("(-?\\d{1,3}\\.\\d{3,})[\\s,+](-?\\d{1,3}\\.\\d{3,})")
        genericPattern.find(decoded)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (isValid(lat, lng)) return Pair(lat!!, lng!!)
        }

        return null
    }

    private fun isValid(lat: Double?, lng: Double?): Boolean {
        if (lat == null || lng == null) return false
        if (lat == 0.0 && lng == 0.0) return false
        return lat in -90.0..90.0 && lng in -180.0..180.0
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
