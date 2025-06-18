package org.degreechain.common.utils

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object DateUtils {

    private val ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun now(): LocalDateTime = LocalDateTime.now()

    fun toTimestamp(dateTime: LocalDateTime): Long {
        return dateTime.toEpochSecond(ZoneOffset.UTC) * 1000
    }

    fun fromTimestamp(timestamp: Long): LocalDateTime {
        return LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC)
    }

    fun formatISO(dateTime: LocalDateTime): String {
        return dateTime.format(ISO_FORMATTER)
    }

    fun formatDisplay(dateTime: LocalDateTime): String {
        return dateTime.format(DISPLAY_FORMATTER)
    }

    fun parseISO(dateTimeString: String): LocalDateTime {
        return LocalDateTime.parse(dateTimeString, ISO_FORMATTER)
    }

    fun isExpired(expiryDate: LocalDateTime): Boolean {
        return LocalDateTime.now().isAfter(expiryDate)
    }

    fun daysUntilExpiry(expiryDate: LocalDateTime): Long {
        return ChronoUnit.DAYS.between(LocalDateTime.now(), expiryDate)
    }

    fun addDays(dateTime: LocalDateTime, days: Long): LocalDateTime {
        return dateTime.plusDays(days)
    }

    fun addYears(dateTime: LocalDateTime, years: Long): LocalDateTime {
        return dateTime.plusYears(years)
    }
}