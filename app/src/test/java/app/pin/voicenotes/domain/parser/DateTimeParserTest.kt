package app.pin.voicenotes.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DateTimeParserTest {

    private val dhaka: ZoneId = ZoneId.of("Asia/Dhaka")
    private val today: LocalDate = LocalDate.of(2026, 8, 19)

    private fun clockAt(hour: Int, minute: Int = 0): Clock = Clock.fixed(
        LocalDateTime.of(2026, 8, 19, hour, minute).atZone(dhaka).toInstant(), dhaka
    )

    private fun parse(text: String, clock: Clock = clockAt(9)): DateTimeResult =
        DateTimeParser(clock).parse(TextNormalizer.tokenize(text).canonical)

    private fun resolved(text: String, clock: Clock = clockAt(9)): LocalDateTime =
        (parse(text, clock) as DateTimeResult.Resolved).dateTime

    // ---- Relative days -----------------------------------------------------

    @Test
    fun `tomorrow morning bare hour`() {
        assertEquals(LocalDateTime.of(today.plusDays(1), LocalTime.of(10, 0)), resolved("kal 10 tay"))
    }

    @Test
    fun `english tomorrow`() {
        assertEquals(
            LocalDateTime.of(today.plusDays(1), LocalTime.of(9, 0)),
            resolved("tomorrow 9 am")
        )
    }

    @Test
    fun `day after tomorrow english`() {
        assertEquals(
            LocalDateTime.of(today.plusDays(2), LocalTime.of(14, 0)),
            resolved("day after tomorrow 2 pm")
        )
    }

    @Test
    fun `porshu bengali`() {
        assertEquals(
            LocalDateTime.of(today.plusDays(2), LocalTime.of(14, 0)),
            resolved("পরশু দুপুর ২টায়")
        )
    }

    // ---- Periods resolve am-pm --------------------------------------------

    @Test
    fun `night nine is 21`() {
        assertEquals(LocalDateTime.of(today, LocalTime.of(21, 0)), resolved("aj rat 9 tay"))
    }

    @Test
    fun `evening seven`() {
        assertEquals(
            LocalDateTime.of(today, LocalTime.of(19, 0)),
            resolved("aj shondha 7 tay")
        )
    }

    @Test
    fun `night twelve crosses midnight`() {
        assertEquals(
            LocalDateTime.of(today.plusDays(1), LocalTime.of(0, 0)),
            resolved("aj rat 12 tay")
        )
    }

    @Test
    fun `noon twelve stays noon`() {
        assertEquals(
            LocalDateTime.of(today.plusDays(1), LocalTime.of(12, 0)),
            resolved("kal dupur 12 tay")
        )
    }

    @Test
    fun `bare afternoon hour reads pm`() {
        assertEquals(
            LocalDateTime.of(today.plusDays(1), LocalTime.of(14, 0)),
            resolved("kal 2 tay")
        )
    }

    // ---- Bengali numerals and formats -------------------------------------

    @Test
    fun `bengali digits with colon`() {
        assertEquals(
            LocalDateTime.of(today.plusDays(1), LocalTime.of(10, 30)),
            resolved("কাল সকাল ১০:৩০")
        )
    }

    @Test
    fun `attached suffix splits`() {
        assertEquals(
            LocalDateTime.of(today.plusDays(1), LocalTime.of(8, 0)),
            resolved("কাল সকাল ৮টায়")
        )
    }

    @Test
    fun `baje form`() {
        assertEquals(
            LocalDateTime.of(today.plusDays(1), LocalTime.of(10, 0)),
            resolved("kal shokal 10 baje")
        )
    }

    @Test
    fun `twenty four hour colon`() {
        assertEquals(
            LocalDateTime.of(today, LocalTime.of(17, 30)),
            resolved("aj 17:30")
        )
    }

    // ---- Ambiguity and safety ---------------------------------------------

    @Test
    fun `period without hour needs time`() {
        val result = parse("kal shokale")
        val needs = result as DateTimeResult.NeedsTime
        assertEquals(today.plusDays(1), needs.date)
        assertEquals(DayPeriod.MORNING, needs.period)
    }

    @Test
    fun `bare number without suffix is not a time`() {
        val result = parse("kal 10")
        assertTrue(result is DateTimeResult.NeedsTime)
    }

    @Test
    fun `invalid hour ignored`() {
        val result = parse("kal 25 tay")
        assertTrue(result is DateTimeResult.NeedsTime)
    }

    @Test
    fun `explicit past time flagged not scheduled`() {
        val result = parse("aj shokal 8 tay", clockAt(20))
        assertTrue(result is DateTimeResult.PastTime)
    }

    @Test
    fun `never resolves into the past`() {
        val result = parse("kal 10 tay", clockAt(23, 59))
        val dt = (result as DateTimeResult.Resolved).dateTime
        assertTrue(dt.isAfter(LocalDateTime.of(2026, 8, 19, 23, 59)))
    }

    @Test
    fun `dayless time rolls forward at midnight boundary`() {
        // 00:30 at night: "10 tay" should mean 10:00 the same (new) day.
        val clock = Clock.fixed(
            LocalDateTime.of(2026, 8, 20, 0, 30).atZone(dhaka).toInstant(), dhaka
        )
        assertEquals(
            LocalDateTime.of(LocalDate.of(2026, 8, 20), LocalTime.of(10, 0)),
            resolved("10 tay", clock)
        )
    }

    @Test
    fun `relative hour offset`() {
        assertEquals(
            LocalDateTime.of(today, LocalTime.of(10, 0)),
            resolved("1 ghonta por")
        )
    }

    @Test
    fun `no datetime returns none`() {
        assertTrue(parse("dada re call dite hobe") is DateTimeResult.None)
    }
}
