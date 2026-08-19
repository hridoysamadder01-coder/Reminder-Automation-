package app.pin.voicenotes.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReminderStatus { NONE, SCHEDULED, FIRED, MISSED, CANCELLED }

@Entity(
    tableName = "notes",
    indices = [Index("isPinned"), Index("reminderAt"), Index("createdAt")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val originalTranscript: String,
    /** Canonicalized title+body used for Banglish-insensitive local search. */
    val searchText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val reminderAt: Long? = null,
    val reminderEnabled: Boolean = false,
    val reminderStatus: ReminderStatus = ReminderStatus.NONE,
)
