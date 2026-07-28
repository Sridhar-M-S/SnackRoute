package com.example.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    private const val ALARM_REQ_CODE = 1001
    const val ACTION_TRIGGER_DAILY_CHECKLIST = "com.example.ACTION_TRIGGER_DAILY_CHECKLIST"

    fun parseTime(timeStr: String): Pair<Int, Int> {
        val clean = timeStr.trim().replace("\\s+".toRegex(), " ").uppercase()
        val isPm = clean.endsWith("PM")
        val isAm = clean.endsWith("AM")
        val timePart = if (isPm || isAm) {
            if (clean.length > 2) {
                clean.substring(0, clean.length - 2).trim()
            } else {
                clean
            }
        } else {
            clean
        }
        val parts = timePart.split(":")
        var hour = parts.getOrNull(0)?.toIntOrNull() ?: 20
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        if (isPm) {
            if (hour < 12) hour += 12
        } else if (isAm) {
            if (hour == 12) hour = 0
        }
        return Pair(hour, minute)
    }

    fun formatTo12Hour(hour: Int, minute: Int): String {
        val suffix = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(java.util.Locale.US, "%02d:%02d %s", displayHour, minute, suffix)
    }

    fun scheduleDailyAlarm(context: Context) {
        val prefs = context.getSharedPreferences("snackroute_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("sales_reminder_enabled", true)
        val timeStr = prefs.getString("sales_reminder_time", "20:00") ?: "20:00"

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager not available")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_DAILY_CHECKLIST
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Cancel existing alarm first to prevent duplicates or stale times
        alarmManager.cancel(pendingIntent)

        if (!enabled) {
            Log.d(TAG, "Notification disabled, cancelled existing alarms")
            return
        }

        val (hour, minute) = parseTime(timeStr)

        val nowMs = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
        
        Log.d(TAG, "Calculating trigger time. Configured: $timeStr (Parsed Hour: $hour, Minute: $minute)")
        Log.d(TAG, "Current time: ${sdf.format(java.util.Date(nowMs))} ($nowMs ms)")

        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val originalTargetMs = calendar.timeInMillis
        Log.d(TAG, "Initial target time (today): ${sdf.format(java.util.Date(originalTargetMs))} ($originalTargetMs ms)")

        // If time is in the past, set for tomorrow
        if (calendar.timeInMillis <= nowMs) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            Log.d(TAG, "Target time is in the past. Rolled forward to tomorrow: ${sdf.format(java.util.Date(calendar.timeInMillis))} (${calendar.timeInMillis} ms)")
        } else {
            Log.d(TAG, "Target time is in the future. Kept for today: ${sdf.format(java.util.Date(calendar.timeInMillis))} (${calendar.timeInMillis} ms)")
        }

        val finalTriggerMs = calendar.timeInMillis
        val delaySec = (finalTriggerMs - nowMs) / 1000.0
        Log.d(TAG, "Final scheduled trigger time: ${sdf.format(java.util.Date(finalTriggerMs))} in $delaySec seconds ($finalTriggerMs ms)")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact alarm for $timeStr using setExactAndAllowWhileIdle")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled alarm for $timeStr using setAndAllowWhileIdle (exact alarms not permitted)")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm for $timeStr using setExactAndAllowWhileIdle")
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled alarm for $timeStr using set")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling exact alarm: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled alarm for $timeStr using fallback setAndAllowWhileIdle after SecurityException")
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled alarm for $timeStr using fallback set after SecurityException")
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Exception during fallback scheduling: ${ex.message}", ex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception scheduling alarm: ${e.message}", e)
        }
    }
}
