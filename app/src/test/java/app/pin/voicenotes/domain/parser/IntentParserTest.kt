package app.pin.voicenotes.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Spec-driven coverage of the local command engine. All clocks are fixed in
 * Asia/Dhaka — the primary audience timezone.
 */
class IntentParserTest {

    private val dhaka: ZoneId = ZoneId.of("Asia/Dhaka")

    /** Wednesday 2026-08-19, 09:00 in Dhaka. */
    private val morningClock: Clock = Clock.fixed(
        LocalDateTime.of(2026, 8, 19, 9, 0).atZone(dhaka).toInstant(), dhaka
    )

    /** Same day, 22:00 in Dhaka — for rollover / past-time behavior. */
    private val nightClock: Clock = Clock.fixed(
        LocalDateTime.of(2026, 8, 19, 22, 0).atZone(dhaka).toInstant(), dhaka
    )

    private val today: LocalDate = LocalDate.of(2026, 8, 19)
    private val parser = IntentParser(morningClock)

    // ---- Create note -------------------------------------------------------

    @Test
    fun `banglish create note`() {
        val intent = parser.parse("ei idea ta likhe rakh")
        val note = intent as VoiceIntent.CreateNote
        assertTrue(note.content.contains("idea"))
        assertEquals(Confidence.HIGH, note.confidence)
        assertFalse(note.content.contains("likhe"))
        assertFalse(note.content.contains("rakh"))
    }

    @Test
    fun `bangla create note`() {
        val intent = parser.parse("এই আইডিয়াটা লিখে রাখ")
        val note = intent as VoiceIntent.CreateNote
        assertTrue(note.content.contains("আইডিয়া"))
        assertEquals(Confidence.HIGH, note.confidence)
    }

    @Test
    fun `save koira rakh variant`() {
        val intent = parser.parse("ei idea ta save koira rakh")
        val note = intent as VoiceIntent.CreateNote
        assertTrue(note.content.contains("idea"))
        assertEquals(Confidence.HIGH, note.confidence)
    }

    @Test
    fun `plain speech defaults to note with medium confidence`() {
        val intent = parser.parse("dokan theke chal ante hobe")
        val note = intent as VoiceIntent.CreateNote
        assertEquals("dokan theke chal ante hobe", note.content)
        assertEquals(Confidence.MEDIUM, note.confidence)
    }

    // ---- Reminders ---------------------------------------------------------

    @Test
    fun `banglish reminder with time`() {
        val intent = parser.parse("kal 10 tay mone korais dada re call dite hobe")
        val reminder = intent as VoiceIntent.CreateNoteWithReminder
        assertEquals(LocalDateTime.of(today.plusDays(1), java.time.LocalTime.of(10, 0)), reminder.remindAt)
        assertTrue(reminder.content.contains("dada"))
        assertTrue(reminder.content.contains("call"))
        assertFalse(reminder.content.contains("mone"))
        assertFalse(reminder.content.contains("kal"))
        assertFalse(reminder.content.contains("10"))
    }

    @Test
    fun `bangla reminder with bengali numerals`() {
        val intent = parser.parse("কাল সকাল ১০টায় কৃষ্ণ দাদাকে ফোন দিতে হবে মনে করাইস")
        val reminder = intent as VoiceIntent.CreateNoteWithReminder
        assertEquals(LocalDateTime.of(today.plusDays(1), java.time.LocalTime.of(10, 0)), reminder.remindAt)
        assertTrue(reminder.content.contains("কৃষ্ণ"))
        assertTrue(reminder.content.contains("ফোন"))
        assertFalse(reminder.content.contains("মনে"))
    }

    @Test
    fun `mixed bangla english reminder`() {
        val intent = parser.parse("কাল 10 tay client meeting reminder dis")
        val reminder = intent as VoiceIntent.CreateNoteWithReminder
        assertEquals(LocalDateTime.of(today.plusDays(1), java.time.LocalTime.of(10, 0)), reminder.remindAt)
        assertTrue(reminder.content.contains("client meeting"))
    }

    @Test
    fun `day after tomorrow noon`() {
        val intent = parser.parse("porshu dupur 2 tay mone korai dio")
        val reminder = intent as VoiceIntent.CreateNoteWithReminder
        assertEquals(LocalDateTime.of(today.plusDays(2), java.time.LocalTime.of(14, 0)), reminder.remindAt)
    }

    @Test
    fun `tonight with rate spelling`() {
        val intent = parser.parse("aj rate 9 tay dawat ase mone korais")
        val reminder = intent as VoiceIntent.CreateNoteWithReminder
        assertEquals(LocalDateTime.of(today, java.time.LocalTime.of(21, 0)), reminder.remindAt)
        assertTrue(reminder.content.contains("dawat"))
    }

    @Test
    fun `combined note and reminder command`() {
        val intent = parser.parse("amar kal 10 tay dada re call dite hobe, likhe rakh ar mone korais")
        val reminder = intent as VoiceIntent.CreateNoteWithReminder
        assertEquals(LocalDateTime.of(today.plusDays(1), java.time.LocalTime.of(10, 0)), reminder.remindAt)
        assertTrue(reminder.content.contains("dada re call dite hobe"))
        assertFalse(reminder.content.startsWith("amar"))
        assertFalse(reminder.content.contains("likhe"))
    }

    @Test
    fun `bare reminder keeps empty content`() {
        val intent = parser.parse("কাল সকাল ১০টায় মনে করাইস")
        val reminder = intent as VoiceIntent.CreateNoteWithReminder
        assertEquals(LocalDateTime.of(today.plusDays(1), java.time.LocalTime.of(10, 0)), reminder.remindAt)
        assertTrue(reminder.content.isBlank())
    }

    @Test
    fun `relative minutes reminder`() {
        val intent = parser.parse("10 minute por cha banate mone korais")
        val reminder = intent as VoiceIntent.CreateNoteWithReminder
        assertEquals(LocalDateTime.of(today, java.time.LocalTime.of(9, 10)), reminder.remindAt)
    }

    // ---- Ambiguity: never guess -------------------------------------------

    @Test
    fun `vague morning requires clarification`() {
        val intent = parser.parse("kal shokale mone korais")
        val clarify = intent as VoiceIntent.NeedsTimeClarification
        assertEquals(today.plusDays(1), clarify.date)
        assertEquals(DayPeriod.MORNING, clarify.period)
        assertFalse(clarify.wasPast)
    }

    @Test
    fun `day without time requires clarification`() {
        val intent = parser.parse("kal mone korais dada re call dite hobe")
        val clarify = intent as VoiceIntent.NeedsTimeClarification
        assertEquals(today.plusDays(1), clarify.date)
        assertEquals(null, clarify.period)
        assertTrue(clarify.content.contains("dada"))
    }

    @Test
    fun `reminder with no time at all requires clarification`() {
        val intent = parser.parse("dada re call dite hobe mone korais")
        assertTrue(intent is VoiceIntent.NeedsTimeClarification)
    }

    @Test
    fun `explicit today time already passed is flagged`() {
        val nightParser = IntentParser(nightClock)
        val intent = nightParser.parse("aj rate 9 tay mone korais")
        val clarify = intent as VoiceIntent.NeedsTimeClarification
        assertTrue(clarify.wasPast)
    }

    @Test
    fun `time without day rolls to tomorrow when passed`() {
        val nightParser = IntentParser(nightClock)
        val intent = nightParser.parse("10 tay mone korais osudh khete hobe")
        val reminder = intent as VoiceIntent.CreateNoteWithReminder
        assertEquals(LocalDateTime.of(today.plusDays(1), java.time.LocalTime.of(10, 0)), reminder.remindAt)
    }

    // ---- Pin ---------------------------------------------------------------

    @Test
    fun `pin current note`() {
        val intent = parser.parse("ei note ta pin kor")
        val pin = intent as VoiceIntent.PinNote
        assertTrue(pin.query.isBlank())
    }

    @Test
    fun `pin by name`() {
        val intent = parser.parse("PharmacyOS note ta pin kor")
        val pin = intent as VoiceIntent.PinNote
        assertEquals("PharmacyOS", pin.query)
    }

    @Test
    fun `unpin`() {
        val intent = parser.parse("ei note ta pin khule daw")
        assertTrue(intent is VoiceIntent.UnpinNote)
    }

    @Test
    fun `show pinned`() {
        assertTrue(parser.parse("pinned note gula dekhaw") is VoiceIntent.ShowPinned)
        assertTrue(parser.parse("pin kora note gula dekhao") is VoiceIntent.ShowPinned)
    }

    // ---- Open / show / search / delete ------------------------------------

    @Test
    fun `open note by keyword`() {
        val intent = parser.parse("PharmacyOS note ta khol")
        val open = intent as VoiceIntent.OpenNote
        assertEquals("PharmacyOS", open.query)
    }

    @Test
    fun `open in bangla`() {
        val intent = parser.parse("কৃষ্ণ দাদার note টা খোল")
        val open = intent as VoiceIntent.OpenNote
        assertTrue(open.query.contains("কৃষ্ণ"))
    }

    @Test
    fun `todays notes`() {
        assertTrue(parser.parse("ajker note gula dekhao") is VoiceIntent.ShowTodayNotes)
    }

    @Test
    fun `todays last note opens directly`() {
        val intent = parser.parse("ajker shesh note khol")
        val open = intent as VoiceIntent.OpenNote
        assertEquals(0, open.dayOffset)
        assertTrue(open.latest)
    }

    @Test
    fun `yesterdays note filter`() {
        val intent = parser.parse("kalker note khol")
        val open = intent as VoiceIntent.OpenNote
        assertEquals(1, open.dayOffset)
    }

    @Test
    fun `show reminders`() {
        val intent = parser.parse("ajker reminder dekhao")
        val show = intent as VoiceIntent.ShowReminders
        assertTrue(show.todayOnly)
    }

    @Test
    fun `show all reminders`() {
        val intent = parser.parse("reminder gula dekhaw")
        val show = intent as VoiceIntent.ShowReminders
        assertFalse(show.todayOnly)
    }

    @Test
    fun `delete by name`() {
        val intent = parser.parse("PharmacyOS note ta delete kor")
        val delete = intent as VoiceIntent.DeleteNote
        assertEquals("PharmacyOS", delete.query)
    }

    @Test
    fun `bangla delete`() {
        val intent = parser.parse("ওই note টা মুছে ফেল")
        assertTrue(intent is VoiceIntent.DeleteNote)
    }

    // ---- Robustness --------------------------------------------------------

    @Test
    fun `empty input is unknown`() {
        assertTrue(parser.parse("   ") is VoiceIntent.Unknown)
    }

    @Test
    fun `taka amount is not a time`() {
        val intent = parser.parse("dokane 500 taka dite hobe likhe rakh")
        val note = intent as VoiceIntent.CreateNote
        assertTrue(note.content.contains("500"))
    }

    @Test
    fun `messy sentence keeps meaning`() {
        val intent = parser.parse(
            "ওই যে কালকে দাদার লগে যেই PharmacyOS এর কথা আছিল " +
                "ওইটা সকাল ১০টার দিকে আবার কথা কইতে হবে, এইটা লিখে রাখ আর আমারে মনে করাইস"
        )
        val reminder = intent as VoiceIntent.CreateNoteWithReminder
        assertEquals(
            LocalDateTime.of(today.plusDays(1), java.time.LocalTime.of(10, 0)),
            reminder.remindAt
        )
        assertTrue(reminder.content.contains("PharmacyOS"))
        assertFalse(reminder.content.contains("লিখে রাখ"))
        assertFalse(reminder.content.contains("মনে করাইস"))
    }

    @Test
    fun `note keeps its time words when no reminder asked`() {
        val intent = parser.parse("kal 10 tay meeting ase likhe rakh")
        val note = intent as VoiceIntent.CreateNote
        assertTrue(note.content.contains("kal"))
        assertTrue(note.content.contains("10"))
    }
}
