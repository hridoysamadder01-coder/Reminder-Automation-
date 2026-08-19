package app.pin.voicenotes.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Human-friendly local formatting. Everything renders in the device zone. */
object TimeFormat {

    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val fullFmt = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a", Locale.ENGLISH)

    fun local(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    /** "Today · 10:00 AM" / "Tomorrow · 10:00 AM" / "Mon, 24 Aug · 2:00 PM" */
    fun reminderLabel(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val dt = local(millis, zone)
        val today = LocalDate.now(zone)
        val prefix = when (dt.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> dt.format(dayFmt)
        }
        return "$prefix · ${dt.format(timeFmt)}"
    }

    fun reminderLabel(dt: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): String {
        val today = LocalDate.now(zone)
        val prefix = when (dt.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> dt.format(dayFmt)
        }
        return "$prefix · ${dt.format(timeFmt)}"
    }

    /** Compact note timestamp: time if today, "Yesterday", else "24 Aug". */
    fun compact(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val dt = local(millis, zone)
        val today = LocalDate.now(zone)
        return when (dt.toLocalDate()) {
            today -> dt.format(timeFmt)
            today.minusDays(1) -> "Yesterday"
            else -> dt.format(dateFmt)
        }
    }

    fun full(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        local(millis, zone).format(fullFmt)

    fun timeOnly(dt: LocalDateTime): String = dt.format(timeFmt)
}
