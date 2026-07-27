package com.example.utils

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.DailyTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {
    private val TAG = "AlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive triggered with action: ${intent.action}")

        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_MY_PACKAGE_REPLACED || 
            action == AlarmScheduler.ACTION_TRIGGER_DAILY_CHECKLIST
        ) {
            // Always reschedule the next alarm
            AlarmScheduler.scheduleDailyAlarm(context)
        }

        if (action == AlarmScheduler.ACTION_TRIGGER_DAILY_CHECKLIST) {
            // Perform background processing
            val pendingResult = goAsync()
            val applicationScope = CoroutineScope(Dispatchers.IO)
            applicationScope.launch {
                try {
                    val prefs = context.getSharedPreferences("snackroute_prefs", Context.MODE_PRIVATE)
                    val enabled = prefs.getBoolean("sales_reminder_enabled", true)
                    if (!enabled) {
                        Log.d(TAG, "Reminders are disabled, skipping notification processing")
                        return@launch
                    }

                    val db = AppDatabase.getDatabase(context)

                    // 1. Daily Checklist Generation
                    val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val existingTasks = db.dailyTaskDao().getTasksByDate(todayDateStr).first()

                    var generatedCount = 0
                    if (existingTasks.isEmpty()) {
                        val todayDayName = SimpleDateFormat("EEEE", Locale.US).format(Date())
                        val timetableEntries = db.timetableDao().getDirectTimetableEntries()
                        val entry = timetableEntries.find { it.dayOfWeek.equals(todayDayName, ignoreCase = true) }

                        if (entry != null && entry.locationNumbers.isNotEmpty()) {
                            val locations = db.locationDao().getAllLocations().first().associateBy { it.locationNumber }
                            val shops = db.shopDao().getAllShops().first()

                            entry.locationNumbers.forEach { locNum ->
                                val shopsInLoc = shops.filter { it.locationNumber == locNum }
                                val locName = locations[locNum]?.locationName ?: "Location $locNum"

                                shopsInLoc.forEach { shop ->
                                    val desc = if (entry.notes.isNotEmpty()) {
                                        "Scheduled visit for today in $locName. Timetable Note: ${entry.notes}"
                                    } else {
                                        "Scheduled visit for today in $locName."
                                    }

                                    val newTask = DailyTask(
                                        title = "Visit ${shop.storeName}",
                                        description = desc,
                                        taskDate = todayDateStr,
                                        isCompleted = false
                                    )
                                    db.dailyTaskDao().insertTask(newTask)
                                    generatedCount++
                                }
                            }
                        }
                    }

                    if (generatedCount > 0) {
                        NotificationHelper.showNotification(
                            context,
                            "Today's Checklist Generated",
                            "Generated $generatedCount shop visit tasks based on your weekly timetable!"
                        )
                    }

                    // 2. Overdue Sales Reminders Notification
                    val shopsWithLastSale = db.shopDao().getShopsWithLastSale().first()
                    val locations = db.locationDao().getAllLocations().first().associateBy { it.locationNumber }
                    val notifyAfter = prefs.getInt("sales_reminder_notify_after_days", 7)
                    val keepVisible = prefs.getInt("sales_reminder_keep_visible_days", 30)

                    val now = System.currentTimeMillis()
                    val nowMidnight = getMidnight(now)
                    val oneDayMs = 24 * 60 * 60 * 1000L

                    var reminderNotifiedCount = 0

                    shopsWithLastSale.forEach { item ->
                        val shop = item.shop
                        val lastSaleDate = item.lastSaleDate
                        val lastCompletedTime = prefs.getLong("completed_reminder_shop_${shop.shopNumber}", 0L)

                        if (lastCompletedTime < lastSaleDate) {
                            val interval = shop.customReminderInterval ?: notifyAfter
                            val lastSaleMidnight = getMidnight(lastSaleDate)
                            val daysSince = ((nowMidnight - lastSaleMidnight) / oneDayMs).toInt()

                            if (daysSince == interval) {
                                val prefKey = "notified_due_${shop.shopNumber}_$lastSaleDate"
                                if (!prefs.getBoolean(prefKey, false)) {
                                    prefs.edit().putBoolean(prefKey, true).apply()

                                    val locName = locations[shop.locationNumber]?.locationName ?: "Location ${shop.locationNumber}"
                                    val title = "Sales Reminder: ${shop.storeName}"
                                    val body = "A sales reminder is due today for ${shop.storeName} in $locName."

                                    NotificationHelper.showNotification(context, title, body)
                                    reminderNotifiedCount++
                                }
                            }
                        }
                    }

                    Log.d(TAG, "Finished daily alarm task. Generated $generatedCount tasks, notified $reminderNotifiedCount overdue sales.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in AlarmReceiver: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun getMidnight(timeMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
