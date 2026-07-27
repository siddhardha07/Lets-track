package com.letstrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*

data class DateRange(
    val startDate: Long,
    val endDate: Long
) {
    fun format(): String {
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return "${formatter.format(Date(startDate))} - ${formatter.format(Date(endDate))}"
    }
}

/**
 * Custom date range picker that lets user select start and end dates
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePicker(
    selectedRange: DateRange?,
    onRangeSelected: (DateRange) -> Unit,
    onDismiss: () -> Unit
) {
    var startDateMillis by remember { mutableStateOf(selectedRange?.startDate) }
    var endDateMillis by remember { mutableStateOf(selectedRange?.endDate) }
    var selectingStart by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Date Range",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date selection buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateButton(
                        label = "Start Date",
                        date = startDateMillis,
                        isSelected = selectingStart,
                        onClick = { selectingStart = true },
                        modifier = Modifier.weight(1f)
                    )
                    DateButton(
                        label = "End Date",
                        date = endDateMillis,
                        isSelected = !selectingStart,
                        onClick = { selectingStart = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date Picker
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = if (selectingStart) startDateMillis else endDateMillis
                )

                DatePicker(
                    state = datePickerState,
                    modifier = Modifier.fillMaxWidth()
                )

                // Update selected date when picker changes
                LaunchedEffect(datePickerState.selectedDateMillis) {
                    datePickerState.selectedDateMillis?.let { millis ->
                        if (selectingStart) {
                            startDateMillis = millis
                        } else {
                            endDateMillis = millis
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (startDateMillis != null && endDateMillis != null) {
                                // Ensure start is before end
                                val start = minOf(startDateMillis!!, endDateMillis!!)
                                val end = maxOf(startDateMillis!!, endDateMillis!!)
                                onRangeSelected(DateRange(start, end))
                            }
                        },
                        enabled = startDateMillis != null && endDateMillis != null
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun DateButton(
    label: String,
    date: Long?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    
    Column(
        modifier = modifier
            .background(
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) 
                MaterialTheme.colorScheme.onPrimaryContainer 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = date?.let { formatter.format(Date(it)) } ?: "Not selected",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) 
                MaterialTheme.colorScheme.onPrimaryContainer 
            else 
                MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Quick date range selector with predefined options
 */
@Composable
fun QuickDateRangeSelector(
    onRangeSelected: (DateRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Quick Select",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickRangeChip("Today", onClick = { onRangeSelected(getTodayRange()) })
            QuickRangeChip("This Week", onClick = { onRangeSelected(getThisWeekRange()) })
            QuickRangeChip("This Month", onClick = { onRangeSelected(getThisMonthRange()) })
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickRangeChip("Last 7 Days", onClick = { onRangeSelected(getLast7DaysRange()) })
            QuickRangeChip("Last 30 Days", onClick = { onRangeSelected(getLast30DaysRange()) })
            QuickRangeChip("Last 90 Days", onClick = { onRangeSelected(getLast90DaysRange()) })
        }
    }
}

@Composable
private fun QuickRangeChip(
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
    )
}

// Helper functions for quick ranges
private fun getTodayRange(): DateRange {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    val start = cal.timeInMillis
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    val end = cal.timeInMillis
    return DateRange(start, end)
}

private fun getThisWeekRange(): DateRange {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, 6)
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    val end = cal.timeInMillis
    return DateRange(start, end)
}

private fun getThisMonthRange(): DateRange {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    val start = cal.timeInMillis
    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    val end = cal.timeInMillis
    return DateRange(start, end)
}

private fun getLast7DaysRange(): DateRange {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    val end = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, -7)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    val start = cal.timeInMillis
    return DateRange(start, end)
}

private fun getLast30DaysRange(): DateRange {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    val end = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, -30)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    val start = cal.timeInMillis
    return DateRange(start, end)
}

private fun getLast90DaysRange(): DateRange {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    val end = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, -90)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    val start = cal.timeInMillis
    return DateRange(start, end)
}
