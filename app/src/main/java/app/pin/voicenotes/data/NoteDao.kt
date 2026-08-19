package app.pin.voicenotes.data

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * ডাটাবেসের সব প্রশ্ন (query) এখানে। Flow ফেরত দেওয়া query-গুলো লাইভ —
 * ডেটা বদলালে স্ক্রিন নিজে নিজে রিফ্রেশ হয়। search() একসাথে আসল লেখা
 * আর normalized লেখা দুটোতেই খোঁজে।
 */

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: Long): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isPinned = 1 ORDER BY updatedAt DESC")
    fun observePinned(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isPinned = 1 ORDER BY updatedAt DESC")
    suspend fun pinnedNotes(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE isPinned = 1")
    fun observePinnedCount(): Flow<Int>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC LIMIT 1")
    suspend fun mostRecent(): NoteEntity?

    @Query("SELECT * FROM notes WHERE createdAt BETWEEN :from AND :until ORDER BY createdAt DESC")
    suspend fun createdBetween(from: Long, until: Long): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE createdAt BETWEEN :from AND :until ORDER BY createdAt DESC")
    fun observeCreatedBetween(from: Long, until: Long): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE reminderEnabled = 1 AND reminderAt IS NOT NULL " +
            "AND reminderAt > :now ORDER BY reminderAt ASC"
    )
    fun observeUpcomingReminders(now: Long): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE reminderAt IS NOT NULL AND reminderAt <= :now " +
            "AND reminderStatus != 'NONE' ORDER BY reminderAt DESC LIMIT :limit"
    )
    fun observePastReminders(now: Long, limit: Int): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE reminderEnabled = 1 AND reminderAt IS NOT NULL " +
            "AND reminderAt > :now"
    )
    suspend fun futureEnabledReminders(now: Long): List<NoteEntity>

    @Query(
        "SELECT * FROM notes WHERE reminderEnabled = 1 AND reminderAt IS NOT NULL " +
            "AND reminderAt <= :now AND reminderStatus = 'SCHEDULED'"
    )
    suspend fun overdueScheduledReminders(now: Long): List<NoteEntity>

    @Query(
        "SELECT * FROM notes WHERE reminderEnabled = 1 AND reminderAt IS NOT NULL " +
            "AND reminderAt BETWEEN :from AND :until ORDER BY reminderAt ASC"
    )
    suspend fun remindersBetween(from: Long, until: Long): List<NoteEntity>

    @Query(
        "SELECT * FROM notes WHERE title LIKE '%' || :raw || '%' " +
            "OR body LIKE '%' || :raw || '%' " +
            "OR originalTranscript LIKE '%' || :raw || '%' " +
            "OR searchText LIKE '%' || :normalized || '%' " +
            "ORDER BY isPinned DESC, updatedAt DESC LIMIT 50"
    )
    suspend fun search(raw: String, normalized: String): List<NoteEntity>
}
