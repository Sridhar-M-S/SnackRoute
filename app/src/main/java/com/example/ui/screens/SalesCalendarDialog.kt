package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SalesEntry
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
    val hasSales: Boolean,
    val totalSalesAmount: Double,
    val totalPacketsSold: Int,
    val salesCount: Int
)

@Composable
fun SalesCalendarDialog(
    sales: List<SalesEntry>,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    // Current viewed year and month (0-indexed)
    val todayCal = remember { Calendar.getInstance() }
    val todayYear = remember { todayCal.get(Calendar.YEAR) }
    val todayMonth = remember { todayCal.get(Calendar.MONTH) }
    val todayDay = remember { todayCal.get(Calendar.DAY_OF_MONTH) }

    var viewedYear by remember { mutableIntStateOf(todayYear) }
    var viewedMonth by remember { mutableIntStateOf(todayMonth) }

    // Map sales by "yyyy-MM-dd" for fast lookups
    val salesByDay = remember(sales) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sales.groupBy { sale ->
            sdf.format(Date(sale.entryDate))
        }
    }

    // Build the grid days for the viewed month
    val calendarDays = remember(viewedYear, viewedMonth, salesByDay) {
        val days = mutableListOf<CalendarDay>()

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, viewedYear)
            set(Calendar.MONTH, viewedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Day of week for 1st day (Calendar.SUNDAY = 1, MONDAY = 2, ...)
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
            days.add(
                CalendarDay(
                    dayNumber = i,
                    dateMillis = padCal.timeInMillis,
                    isCurrentMonth = false,
                    isToday = false,
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

            days.add(
                CalendarDay(
                    dayNumber = day,
                    dateMillis = currentCal.timeInMillis,
                    isCurrentMonth = true,
                    isToday = isToday,
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
            days.add(
                CalendarDay(
                    dayNumber = i,
                    dateMillis = nextCal.timeInMillis,
                    isCurrentMonth = false,
                    isToday = false,
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 480.dp)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- Top Header ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Sales Calendar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Daily sales activity at a glance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("close_calendar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // --- Month Navigation Row ---
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (viewedMonth == 0) {
                                    viewedMonth = 11
                                    viewedYear -= 1
                                } else {
                                    viewedMonth -= 1
                                }
                            },
                            modifier = Modifier.size(36.dp).testTag("calendar_prev_month")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Month",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = monthName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!isCurrentMonthViewed) {
                                TextButton(
                                    onClick = {
                                        viewedYear = todayYear
                                        viewedMonth = todayMonth
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Today,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Today", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                if (viewedMonth == 11) {
                                    viewedMonth = 0
                                    viewedYear += 1
                                } else {
                                    viewedMonth += 1
                                }
                            },
                            modifier = Modifier.size(36.dp).testTag("calendar_next_month")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Month",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // --- Monthly KPI Strip ---
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(24.dp)
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
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(24.dp)
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
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // --- Day of Week Names ---
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
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (dayName == "Sun") MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // --- Calendar Grid ---
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    userScrollEnabled = false
                ) {
                    items(calendarDays) { day ->
                        CalendarDayCell(
                            day = day,
                            onClick = {
                                onDateSelected(day.dateMillis)
                            }
                        )
                    }
                }

                // --- Footer Hint ---
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = "Tap any date with sales to view records in Sales Master",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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

    val backgroundColor = when {
        !isCurrentMonth -> Color.Transparent
        isHighlighted -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }

    val contentColor = when {
        !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        isHighlighted -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val border = when {
        isToday -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isHighlighted -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        else -> null
    }

    Surface(
        modifier = Modifier
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = isCurrentMonth) { onClick() }
            .testTag("calendar_day_${day.dayNumber}"),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = border,
        tonalElevation = if (isHighlighted) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Day number header with today dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = day.dayNumber.toString(),
                    fontSize = 12.sp,
                    fontWeight = if (isToday || isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    color = contentColor,
                    textAlign = TextAlign.Center
                )
                if (isToday && isCurrentMonth) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
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
