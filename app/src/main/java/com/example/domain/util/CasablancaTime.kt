package com.example.domain.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object CasablancaTime {
    val timeZone: TimeZone = TimeZone.getTimeZone("Africa/Casablanca")

    fun formatDateTime(epochMillis: Long, isArabic: Boolean = false): String {
        val locale = if (isArabic) Locale("ar", "MA") else Locale.FRANCE
        val sdf = SimpleDateFormat("dd/MM HH:mm", locale).apply {
            timeZone = this@CasablancaTime.timeZone
        }
        return "${sdf.format(Date(epochMillis))} (MA)"
    }

    fun formatShortDate(epochMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM", Locale.ENGLISH).apply {
            timeZone = this@CasablancaTime.timeZone
        }
        return sdf.format(Date(epochMillis))
    }

    fun daysElapsed(fromMillis: Long, nowMillis: Long = System.currentTimeMillis()): Long {
        if (nowMillis <= fromMillis) return 0L
        return TimeUnit.MILLISECONDS.toDays(nowMillis - fromMillis)
    }

    fun hoursElapsed(fromMillis: Long, toMillis: Long): Double {
        if (toMillis <= fromMillis) return 0.0
        return (toMillis - fromMillis).toDouble() / TimeUnit.HOURS.toMillis(1).toDouble()
    }
}
