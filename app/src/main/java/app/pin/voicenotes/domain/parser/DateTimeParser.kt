package app.pin.voicenotes.domain.parser

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class DayPeriod { MORNING, NOON, AFTERNOON, EVENING, NIGHT }

sealed interface DateTimeResult {
    /** An unambiguous future moment. */
    data class Resolved(val dateTime: LocalDateTime, val consumed: Set<Int>) : DateTimeResult

    /**
     * A date (and possibly a day period) without an exact time —
     * e.g. "kal shokale". The reminder flow must ask, never guess.
     */
    data class NeedsTime(
        val date: LocalDate,
        val period: DayPeriod?,
        val consumed: Set<Int>,
    ) : DateTimeResult

    /**
     * The phrase resolved to a moment that has already passed
     * (e.g. "aj shokal 8 tay" said in the evening). Never scheduled silently.
     */
    data class PastTime(val dateTime: LocalDateTime, val consumed: Set<Int>) : DateTimeResult

    data object None : DateTimeResult
}

/**
 * Focused date/time extraction over canonical tokens. Covers the common
 * Bangladesh phrasing this product actually needs; deliberately not a general
 * natural-language date library.
 */
class DateTimeParser(private val clock: Clock = Clock.systemDefaultZone()) {

    private val hourToken = Regex("^(\\d{1,2})$")
    private val colonToken = Regex("^(\\d{1,2}):(\\d{2})$")

    private val timeSuffixes = setOf("ta", "baje", "tar")
    private val trailingNoise = setOf("dike")

    fun parse(tokens: List<String>): DateTimeResult {
        val consumed = mutableSetOf<Int>()
        val now = LocalDateTime.now(clock)

        // --- Relative offsets: "10 minute por", "1 ghonta pore" -------------
        relativeOffset(tokens)?.let { (minutes, indices) ->
            consumed += indices
            return DateTimeResult.Resolved(now.plusMinutes(minutes).withSecond(0).withNano(0), consumed)
        }

        // --- Day words ------------------------------------------------------
        var dayOffset: Int? = null
        for ((i, t) in tokens.withIndex()) {
            val offset = when (t) {
                "aj" -> 0
                "kal" -> 1
                "porshu" -> 2
                else -> null
            } ?: continue
            if (dayOffset == null) dayOffset = offset
            consumed += i
        }
        // English "day after tomorrow" arrives as [day, after, kal].
        for (i in 0..tokens.size - 3) {
            if (tokens[i] == "day" && tokens[i + 1] == "after" && tokens[i + 2] == "kal") {
                dayOffset = 2
                consumed += setOf(i, i + 1, i + 2)
            }
        }

        // --- Day period -----------------------------------------------------
        var period: DayPeriod? = null
        for ((i, t) in tokens.withIndex()) {
            val p = when (t) {
                "shokal" -> DayPeriod.MORNING
                "dupur" -> DayPeriod.NOON
                "bikal" -> DayPeriod.AFTERNOON
                "shondha" -> DayPeriod.EVENING
                "rat" -> DayPeriod.NIGHT
                else -> null
            } ?: continue
            if (period == null) period = p
            consumed += i
        }

        // --- Clock time -----------------------------------------------------
        var hour: Int? = null
        var minute = 0
        var meridiem: Meridiem? = null
        var explicit24 = false

        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val colon = colonToken.matchEntire(t)
            val bare = hourToken.matchEntire(t)
            val next = tokens.getOrNull(i + 1)
            val afterNext = tokens.getOrNull(i + 2)

            if (colon != null) {
                val h = colon.groupValues[1].toInt()
                val m = colon.groupValues[2].toInt()
                if (h in 0..23 && m in 0..59) {
                    hour = h
                    minute = m
                    explicit24 = h > 12 || h == 0
                    consumed += i
                    consumeSuffixes(tokens, i, consumed)?.let { meridiem = it }
                    break
                }
            } else if (bare != null) {
                val h = bare.groupValues[1].toInt()
                val isTime = next in timeSuffixes || next == "am" || next == "pm" ||
                    (i > 0 && isPeriodToken(tokens[i - 1]))
                if (isTime && h in 0..23) {
                    hour = h
                    explicit24 = h > 12
                    consumed += i
                    consumeSuffixes(tokens, i, consumed)?.let { meridiem = it }
                    break
                }
            }
            i++
        }

        if (hour == null && period == null && dayOffset == null) return DateTimeResult.None

        val today = now.toLocalDate()

        if (hour == null) {
            // A date and/or a vague period but no clock time -> must clarify.
            val date = today.plusDays((dayOffset ?: if (periodStillAhead(period, now)) 0 else 1).toLong())
            return DateTimeResult.NeedsTime(date, period, consumed)
        }

        val (resolvedHour, dayBump) = resolveHour(hour, minute, meridiem, period, explicit24)
        if (resolvedHour !in 0..23) return DateTimeResult.None
        val time = LocalTime.of(resolvedHour, minute)

        return if (dayOffset != null) {
            val dateTime = LocalDateTime.of(today.plusDays(dayOffset.toLong() + dayBump), time)
            if (dateTime.isAfter(now)) DateTimeResult.Resolved(dateTime, consumed)
            else DateTimeResult.PastTime(dateTime, consumed)
        } else {
            // No explicit day: take today, roll forward to tomorrow if passed.
            var dateTime = LocalDateTime.of(today.plusDays(dayBump.toLong()), time)
            if (!dateTime.isAfter(now)) dateTime = dateTime.plusDays(1)
            DateTimeResult.Resolved(dateTime, consumed)
        }
    }

    private enum class Meridiem { AM, PM }

    /** Consumes "ta"/"baje"/am/pm/"dike" tokens after a matched hour at [at]. */
    private fun consumeSuffixes(tokens: List<String>, at: Int, consumed: MutableSet<Int>): Meridiem? {
        var meridiem: Meridiem? = null
        var j = at + 1
        while (j < tokens.size) {
            when (tokens[j]) {
                in timeSuffixes, in trailingNoise -> consumed += j
                "am" -> { meridiem = Meridiem.AM; consumed += j }
                "pm" -> { meridiem = Meridiem.PM; consumed += j }
                else -> return meridiem
            }
            j++
        }
        return meridiem
    }

    private fun isPeriodToken(t: String) = t in setOf("shokal", "dupur", "bikal", "shondha", "rat")

    /**
     * Turns a spoken 12-hour figure into a 24-hour value.
     * Returns hour plus a day bump (rat 12 ta / rat 1 ta land after midnight).
     */
    private fun resolveHour(
        hour: Int,
        minute: Int,
        meridiem: Meridiem?,
        period: DayPeriod?,
        explicit24: Boolean,
    ): Pair<Int, Int> {
        if (explicit24) return hour to 0
        if (meridiem != null) {
            return when (meridiem) {
                Meridiem.AM -> (if (hour == 12) 0 else hour) to 0
                Meridiem.PM -> (if (hour == 12) 12 else hour + 12) to 0
            }
        }
        return when (period) {
            DayPeriod.MORNING -> hour to 0
            DayPeriod.NOON -> (if (hour == 12) 12 else if (hour in 1..4) hour + 12 else hour) to 0
            DayPeriod.AFTERNOON, DayPeriod.EVENING ->
                (if (hour == 12) 12 else if (hour in 1..11) hour + 12 else hour) to 0
            DayPeriod.NIGHT -> when (hour) {
                12 -> 0 to 1      // "rat 12 ta" -> midnight entering the next day
                in 1..3 -> hour to 1  // "rat 2 ta" -> 2 AM after that night
                else -> (hour + 12) to 0
            }
            null -> when (hour) {
                // Bare hour ("kal 10 tay"): daytime reading. 7-11 -> morning,
                // 12 -> noon, 1-6 -> afternoon/evening.
                in 7..11 -> hour to 0
                12 -> 12 to 0
                in 1..6 -> (hour + 12) to 0
                else -> hour to 0
            }
        }
    }

    /** For "aj shokale ..." style phrases: is the vague period still ahead today? */
    private fun periodStillAhead(period: DayPeriod?, now: LocalDateTime): Boolean {
        period ?: return true
        val endHour = when (period) {
            DayPeriod.MORNING -> 11
            DayPeriod.NOON -> 15
            DayPeriod.AFTERNOON -> 18
            DayPeriod.EVENING -> 20
            DayPeriod.NIGHT -> 23
        }
        return now.hour < endHour
    }

    /** Matches "N minute por" / "N ghonta pore" anywhere in the token list. */
    private fun relativeOffset(tokens: List<String>): Pair<Long, Set<Int>>? {
        for (i in 0..tokens.size - 3) {
            val n = hourTokenValue(tokens[i]) ?: continue
            val unit = tokens[i + 1]
            val por = tokens[i + 2]
            if (por != "por") continue
            val minutes = when (unit) {
                "minute" -> n.toLong()
                "ghonta" -> n.toLong() * 60
                else -> continue
            }
            if (minutes in 1..(7 * 24 * 60)) return minutes to setOf(i, i + 1, i + 2)
        }
        return null
    }

    private fun hourTokenValue(t: String): Int? =
        Regex("^(\\d{1,3})$").matchEntire(t)?.groupValues?.get(1)?.toInt()
}
