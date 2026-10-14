package com.smsfraud.detector.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Date/time utilities for display formatting and TTL calculations.
 */
object DateUtil {

    private val displayFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private val shortFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun formatTimestamp(epochMillis: Long): String = displayFormat.format(Date(epochMillis))

    fun formatShort(epochMillis: Long): String = shortFormat.format(Date(epochMillis))

    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun daysAgoMillis(days: Int): Long =
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())

    /** Check if a timestamp is within the 24-hour restore window. */
    fun isWithinRestoreWindow(deletedAt: Long): Boolean =
        System.currentTimeMillis() - deletedAt < TimeUnit.HOURS.toMillis(24)
}
