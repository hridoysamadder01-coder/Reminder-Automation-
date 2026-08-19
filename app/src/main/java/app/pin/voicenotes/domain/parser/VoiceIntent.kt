package app.pin.voicenotes.domain.parser

import java.time.LocalDate
import java.time.LocalDateTime

enum class Confidence { HIGH, MEDIUM }

/**
 * Everything the local intent engine can decide to do. Deterministic —
 * no model, no network. Reminders always attach to a note (the note body is
 * what the reminder is about), so "create reminder" and "create note with
 * reminder" are one intent here.
 */
sealed interface VoiceIntent {

    data class CreateNote(
        val content: String,
        val confidence: Confidence,
    ) : VoiceIntent

    data class CreateNoteWithReminder(
        val content: String,
        val remindAt: LocalDateTime,
    ) : VoiceIntent

    /**
     * A reminder was requested but the time is materially ambiguous
     * ("kal shokale mone korais") or already passed. The UI must ask —
     * never guess.
     */
    data class NeedsTimeClarification(
        val content: String,
        val date: LocalDate?,
        val period: DayPeriod?,
        val wasPast: Boolean = false,
    ) : VoiceIntent

    data class SearchNotes(val query: String) : VoiceIntent

    data class OpenNote(
        val query: String,
        val pinnedOnly: Boolean = false,
        /** 0 = today, 1 = "kalker" (yesterday's note or tomorrow's reminder), 2 = porshu. */
        val dayOffset: Int? = null,
        val latest: Boolean = false,
    ) : VoiceIntent

    /** Empty query = pin the contextual note (open note, else most recent). */
    data class PinNote(val query: String) : VoiceIntent

    data class UnpinNote(val query: String) : VoiceIntent

    data object ShowPinned : VoiceIntent

    data object ShowTodayNotes : VoiceIntent

    data class ShowReminders(val todayOnly: Boolean) : VoiceIntent

    data class DeleteNote(val query: String) : VoiceIntent

    data class Unknown(val raw: String) : VoiceIntent
}
