package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SalesEntry
import com.example.ui.AppViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class CalendarDay(
    val dayNumber: Int,
    val dateMillis: Long,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasSales: Boolean,
    val totalSalesAmount: Double,
    val totalPacketsSold: Int,
    val salesCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesCalendarScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onDateSelected: (dateMillis: Long, year: Int, month: Int) -> Unit
) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val viewedYear by viewModel.calendarViewedYear.collectAsStateWithLifecycle()
    val viewedMonth by viewModel.calendarViewedMonth.collectAsStateWithLifecycle()
    val selectedDateMillis by viewModel.calendarSelectedDateMillis.collectAsStateWithLifecycle()

    val todayCal = remember { Calendar.getInstance() }
    val todayYear = remember { todayCal.get(Calendar.YEAR) }
    val todayMonth = remember { todayCal.get(Calendar.MONTH) }
    val todayDay = remember { todayCal.get(Calendar.DAY_OF_MONTH) }

    // Map sales by "yyyy-MM-dd" for fast lookups
    val salesByDay = remember(sales) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sales.groupBy { sale ->
            sdf.format(Date(sale.entryDate))
        }
    }

    fun isSameDay(millis1: Long, millis2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = millis1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = millis2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    // Build the grid days for the viewed month
    val calendarDays = remember(viewedYear, viewedMonth, salesByDay, selectedDateMillis) {
        val days = mutableListOf<CalendarDay>()
        val targetSelectedMillis = selectedDateMillis

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, viewedYear)
            set(Calendar.MONTH, viewedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Previous month padding
        val prevCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, viewedYear)
            set(Calendar.MONTH, viewedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        val daysInPrevMonth = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val prevDaysCount = firstDayOfWeek - Calendar.SUNDAY

        for (i in (daysInPrevMonth - prevDaysCount + 1)..daysInPrevMonth) {
            val padCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, prevCal.get(Calendar.YEAR))
                set(Calendar.MONTH, prevCal.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(padCal.time)
            val daySales = salesByDay[key] ?: emptyList()
            val isSelected = targetSelectedMillis != null && isSameDay(padCal.timeInMillis, targetSelectedMillis)
            days.add(
                CalendarDay(
                    dayNumber = i,
                    dateMillis = padCal.timeInMillis,
                    isCurrentMonth = false,
                    isToday = false,
                    isSelected = isSelected,
                    hasSales = daySales.isNotEmpty(),
                    totalSalesAmount = daySales.sumOf { it.totalAmount },
                    totalPacketsSold = daySales.sumOf { it.packetsSold },
                    salesCount = daySales.size
                )
            )
        }

        // Current month days
        for (day in 1..daysInMonth) {
            val currentCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, viewedYear)
                set(Calendar.MONTH, viewedMonth)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(currentCal.time)
            val daySales = salesByDay[key] ?: emptyList()
            val isToday = (viewedYear == todayYear && viewedMonth == todayMonth && day == todayDay)
            val isSelected = targetSelectedMillis != null && isSameDay(currentCal.timeInMillis, targetSelectedMillis)

            days.add(
                CalendarDay(
                    dayNumber = day,
                    dateMillis = currentCal.timeInMillis,
                    isCurrentMonth = true,
                    isToday = isToday,
                    isSelected = isSelected,
                    hasSales = daySales.isNotEmpty(),
                    totalSalesAmount = daySales.sumOf { it.totalAmount },
                    totalPacketsSold = daySales.sumOf { it.packetsSold },
                    salesCount = daySales.size
                )
            )
        }

        // Trailing padding to make full 7-column rows
        val remaining = (7 - (days.size % 7)) % 7
        for (i in 1..remaining) {
            val nextCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, viewedYear)
                set(Calendar.MONTH, viewedMonth)
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(nextCal.time)
            val daySales = salesByDay[key] ?: emptyList()
            val isSelected = targetSelectedMillis != null && isSameDay(nextCal.timeInMillis, targetSelectedMillis)
            days.add(
                CalendarDay(
                    dayNumber = i,
                    dateMillis = nextCal.timeInMillis,
                    isCurrentMonth = false,
                    isToday = false,
                    isSelected = isSelected,
                    hasSales = daySales.isNotEmpty(),
                    totalSalesAmount = daySales.sumOf { it.totalAmount },
                    totalPacketsSold = daySales.sumOf { it.packetsSold },
                    salesCount = daySales.size
                )
            )
        }

        days
    }

    // Monthly summary metrics
    val monthSalesStats = remember(calendarDays) {
        val currentMonthDays = calendarDays.filter { it.isCurrentMonth }
        val activeDaysCount = currentMonthDays.count { it.hasSales }
        val totalRevenue = currentMonthDays.sumOf { it.totalSalesAmount }
        val totalPackets = currentMonthDays.sumOf { it.totalPacketsSold }
        Triple(totalRevenue, activeDaysCount, totalPackets)
    }

    val monthName = remember(viewedYear, viewedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, viewedYear)
            set(Calendar.MONTH, viewedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }

    val isCurrentMonthViewed = (viewedYear == todayYear && viewedMonth == todayMonth)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sales Calendar", fontWeight = FontWeight.Bold)
                        Text(
                            "Monthly sales activity & drilldown",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("calendar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (!isCurrentMonthViewed) {
                        TextButton(
                            onClick = {
                                viewModel.setCalendarMonth(todayYear, todayMonth)
                            },
                            modifier = Modifier.testTag("calendar_jump_today_action")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Today,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Today", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- Month Navigation Card ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (viewedMonth == 0) {
                                viewModel.setCalendarMonth(viewedYear - 1, 11)
                            } else {
                                viewModel.setCalendarMonth(viewedYear, viewedMonth - 1)
                            }
                        },
                        modifier = Modifier.size(40.dp).testTag("calendar_prev_month")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Month",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = monthName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            if (viewedMonth == 11) {
                                viewModel.setCalendarMonth(viewedYear + 1, 0)
                            } else {
                                viewModel.setCalendarMonth(viewedYear, viewedMonth + 1)
                            }
                        },
                        modifier = Modifier.size(40.dp).testTag("calendar_next_month")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Month",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // --- Monthly KPI Metrics Strip ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Month Total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${NumberFormat.getNumberInstance(Locale.US).format(monthSalesStats.first.toInt())}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Sales Days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${monthSalesStats.second} active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Volume",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${monthSalesStats.third} pkts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // --- Weekday Headers ---
            val weekDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekDays.forEach { dayName ->
                    Text(
                        text = dayName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (dayName == "Sun") MaterialTheme.colorScheme.error.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- Calendar Days Grid ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    userScrollEnabled = false
                ) {
                    items(calendarDays) { day ->
                        CalendarDayCell(
                            day = day,
                            onClick = {
                                onDateSelected(day.dateMillis, viewedYear, viewedMonth)
                            }
                        )
                    }
                }
            }

            // --- Info Hint Banner ---
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = "Tap any date to view sales in Sales Master. The back arrow in Sales Master will return directly here with this month and date preserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    onClick: () -> Unit
) {
    val isHighlighted = day.hasSales
    val isCurrentMonth = day.isCurrentMonth
    val isToday = day.isToday
    val isSelected = day.isSelected

    val backgroundColor = when {
        !isCurrentMonth -> Color.Transparent
        isSelected && isHighlighted -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        isHighlighted -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }

    val contentColor = when {
        !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        isSelected && isHighlighted -> MaterialTheme.colorScheme.onPrimaryContainer
        isHighlighted -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val border = when {
        isSelected -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        isToday -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
        isHighlighted -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        else -> null
    }

    Surface(
        modifier = Modifier
            .aspectRatio(0.88f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = isCurrentMonth) { onClick() }
            .testTag("calendar_day_${day.dayNumber}"),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = border,
        tonalElevation = if (isSelected) 4.dp else if (isHighlighted) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Day number header with today dot / selection check
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = day.dayNumber.toString(),
                    fontSize = if (isSelected) 13.sp else 12.sp,
                    fontWeight = if (isSelected || isToday || isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else contentColor,
                    textAlign = TextAlign.Center
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                } else if (isToday && isCurrentMonth) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                    )
                }
            }

            // Sales Amount Display
            if (isHighlighted && isCurrentMonth) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val formattedAmount = formatCompactCurrency(day.totalSalesAmount)
                    Text(
                        text = formattedAmount,
                        fontSize = if (formattedAmount.length > 6) 9.sp else 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${day.totalPacketsSold}p",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

private fun formatCompactCurrency(amount: Double): String {
    return if (amount >= 100000.0) {
        val lakhs = amount / 100000.0
        "₹%.1fL".format(Locale.US, lakhs)
    } else if (amount >= 10000.0) {
        val thousands = amount / 1000.0
        "₹%.1fk".format(Locale.US, thousands)
    } else if (amount >= 1000.0) {
        "₹" + NumberFormat.getNumberInstance(Locale.US).format(amount.toInt())
    } else {
        "₹${amount.toInt()}"
    }
}
