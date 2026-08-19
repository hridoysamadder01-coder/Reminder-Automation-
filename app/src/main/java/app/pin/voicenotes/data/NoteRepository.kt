package app.pin.voicenotes.data

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * ডাটাবেস আর অ্যালার্মের মাঝের সমন্বয়কারী। নোট সেভ/এডিট/ডিলিট, পিন,
 * রিমাইন্ডার সেট/বাতিল, Undo-র জন্য restore, সার্চ র‍্যাংকিং — সব এখানে।
 * রিবুটের পর restoreReminders() ভবিষ্যতেরগুলো আবার বসায়, আর ফোন বন্ধ থাকা
 * অবস্থায় পার হয়ে যাওয়াগুলো Missed মার্ক করে — দেরিতে কখনো বাজায় না।
 */

import app.pin.voicenotes.domain.parser.TextNormalizer
import app.pin.voicenotes.domain.parser.TitleGenerator
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Abstraction over AlarmManager so the repository stays JVM-testable. */
interface ReminderScheduling {
    fun schedule(noteId: Long, atEpochMillis: Long)
    fun cancel(noteId: Long)
}

class NoteRepository(
    private val dao: NoteDao,
    private val scheduler: ReminderScheduling,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    // ---- Observation -------------------------------------------------------

    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()
    fun observePinned(): Flow<List<NoteEntity>> = dao.observePinned()
    fun observePinnedCount(): Flow<Int> = dao.observePinnedCount()
    fun observeRecent(limit: Int = 6): Flow<List<NoteEntity>> = dao.observeRecent(limit)
    fun observeNote(id: Long): Flow<NoteEntity?> = dao.observeNote(id)

    fun observeTodayNotes(): Flow<List<NoteEntity>> {
        val (from, until) = dayBounds(LocalDate.now(zone))
        return dao.observeCreatedBetween(from, until)
    }

    fun observeUpcomingReminders(now: Long = System.currentTimeMillis()) =
        dao.observeUpcomingReminders(now)

    fun observePastReminders(now: Long = System.currentTimeMillis(), limit: Int = 30) =
        dao.observePastReminders(now, limit)

    // ---- Create / update ---------------------------------------------------

    suspend fun createNote(
        content: String,
        transcript: String,
        remindAt: LocalDateTime? = null,
    ): NoteEntity {
        val now = System.currentTimeMillis()
        // A plain note with no extractable content keeps the raw transcript;
        // a bare reminder ("কাল ১০টায় মনে করাইস") gets a clean placeholder
        // title instead of echoing the command words.
        val body = content.trim().ifEmpty { if (remindAt == null) transcript.trim() else "" }
        val reminderMillis = remindAt?.atZone(zone)?.toInstant()?.toEpochMilli()
        val entity = NoteEntity(
            title = TitleGenerator.generate(
                body,
                fallback = if (remindAt != null) "Reminder" else "Note"
            ),
            body = body,
            originalTranscript = transcript.trim(),
            searchText = searchTextOf(body, transcript),
            createdAt = now,
            updatedAt = now,
            reminderAt = reminderMillis,
            reminderEnabled = reminderMillis != null,
            reminderStatus = if (reminderMillis != null) ReminderStatus.SCHEDULED else ReminderStatus.NONE,
        )
        val id = dao.insert(entity)
        if (reminderMillis != null) scheduler.schedule(id, reminderMillis)
        return entity.copy(id = id)
    }

    suspend fun updateContent(id: Long, title: String, body: String) {
        val note = dao.getNote(id) ?: return
        dao.update(
            note.copy(
                title = title.trim().ifEmpty { TitleGenerator.generate(body) },
                body = body.trim(),
                searchText = searchTextOf(body, note.originalTranscript),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun getNote(id: Long): NoteEntity? = dao.getNote(id)

    // ---- Pin ---------------------------------------------------------------

    suspend fun setPinned(id: Long, pinned: Boolean) {
        val note = dao.getNote(id) ?: return
        dao.update(note.copy(isPinned = pinned, updatedAt = System.currentTimeMillis()))
    }

    // ---- Reminders ---------------------------------------------------------

    suspend fun setReminder(id: Long, at: LocalDateTime) {
        val note = dao.getNote(id) ?: return
        val millis = at.atZone(zone).toInstant().toEpochMilli()
        dao.update(
            note.copy(
                reminderAt = millis,
                reminderEnabled = true,
                reminderStatus = ReminderStatus.SCHEDULED,
                updatedAt = System.currentTimeMillis(),
            )
        )
        scheduler.schedule(id, millis)
    }

    suspend fun cancelReminder(id: Long) {
        val note = dao.getNote(id) ?: return
        scheduler.cancel(id)
        dao.update(
            note.copy(
                reminderEnabled = false,
                reminderStatus = if (note.reminderAt != null) ReminderStatus.CANCELLED else ReminderStatus.NONE,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun setReminderEnabled(id: Long, enabled: Boolean) {
        val note = dao.getNote(id) ?: return
        val at = note.reminderAt ?: return
        if (enabled && at > System.currentTimeMillis()) {
            dao.update(note.copy(reminderEnabled = true, reminderStatus = ReminderStatus.SCHEDULED))
            scheduler.schedule(id, at)
        } else {
            scheduler.cancel(id)
            dao.update(note.copy(reminderEnabled = false, reminderStatus = ReminderStatus.CANCELLED))
        }
    }

    /** Called by the notification path when a reminder actually fires. */
    suspend fun markReminderFired(id: Long) {
        val note = dao.getNote(id) ?: return
        dao.update(note.copy(reminderStatus = ReminderStatus.FIRED, reminderEnabled = false))
    }

    /**
     * Boot / time-change recovery: re-arm future reminders, mark the ones
     * that silently expired while the device was off as missed.
     */
    suspend fun restoreReminders() {
        val now = System.currentTimeMillis()
        for (note in dao.futureEnabledReminders(now)) {
            scheduler.schedule(note.id, note.reminderAt!!)
        }
        for (note in dao.overdueScheduledReminders(now)) {
            dao.update(note.copy(reminderStatus = ReminderStatus.MISSED, reminderEnabled = false))
        }
    }

    // ---- Delete ------------------------------------------------------------

    suspend fun deleteNote(id: Long) {
        val note = dao.getNote(id) ?: return
        scheduler.cancel(id)
        dao.delete(note)
    }

    /** Restores a just-deleted note (Undo). Reschedules a still-future reminder. */
    suspend fun restoreNote(note: NoteEntity): Long {
        val id = dao.insert(note.copy(id = 0))
        if (note.reminderEnabled && note.reminderAt != null &&
            note.reminderAt > System.currentTimeMillis()
        ) {
            scheduler.schedule(id, note.reminderAt)
        }
        return id
    }

    // ---- Search / voice open ----------------------------------------------

    suspend fun search(query: String): List<NoteEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val normalized = normalizedQuery(trimmed)
        val results = dao.search(trimmed, normalized)
        return results.sortedByDescending { score(it, trimmed, normalized) }
    }

    /**
     * Voice "open note" resolution. Returns candidates best-first; the caller
     * opens a single strong match directly and shows a short list otherwise.
     */
    suspend fun findNotesForOpen(
        query: String,
        pinnedOnly: Boolean,
        dayOffset: Int?,
        latest: Boolean,
    ): List<NoteEntity> {
        if (pinnedOnly) {
            val pinned = dao.pinnedNotes()
            return if (query.isBlank()) pinned
            else pinned.sortedByDescending { score(it, query, normalizedQuery(query)) }
        }
        if (query.isNotBlank()) return search(query)

        val today = LocalDate.now(zone)
        return when (dayOffset) {
            0 -> {
                val (from, until) = dayBounds(today)
                val notes = dao.createdBetween(from, until)
                if (latest) notes.take(1) else notes
            }
            1, 2 -> {
                // "kalker note" — the note from N days ago, or the note whose
                // reminder is N days ahead. Offer both, created-first.
                val off = dayOffset.toLong()
                val (pastFrom, pastUntil) = dayBounds(today.minusDays(off))
                val (nextFrom, nextUntil) = dayBounds(today.plusDays(off))
                dao.createdBetween(pastFrom, pastUntil) + dao.remindersBetween(nextFrom, nextUntil)
            }
            else -> listOfNotNull(dao.mostRecent())
        }
    }

    suspend fun mostRecentNote(): NoteEntity? = dao.mostRecent()

    // ---- Helpers -----------------------------------------------------------

    private fun searchTextOf(body: String, transcript: String): String {
        val combined = "$body $transcript".trim()
        return TextNormalizer.tokenize(combined).canonical.joinToString(" ")
    }

    private fun normalizedQuery(query: String): String =
        TextNormalizer.tokenize(query).canonical.joinToString(" ")

    private fun score(note: NoteEntity, raw: String, normalized: String): Int {
        val rawLower = raw.lowercase()
        val title = note.title.lowercase()
        val body = note.body.lowercase()
        var s = 0
        if (title.startsWith(rawLower)) s += 4
        if (title.contains(rawLower)) s += 3
        if (note.searchText.contains(normalized)) s += 2
        if (body.contains(rawLower)) s += 2
        if (note.originalTranscript.lowercase().contains(rawLower)) s += 1
        if (note.isPinned) s += 1
        return s
    }

    private fun dayBounds(date: LocalDate): Pair<Long, Long> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start to end
    }
}
