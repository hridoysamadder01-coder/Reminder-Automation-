package app.pin.voicenotes.data

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * Room ডাটাবেস (schema v1)। destructive migration ইচ্ছা করেই নেই —
 * ভবিষ্যতে স্কিমা বদলালে ডেটা রেখে migration লিখতে হবে, মুছে ফেলা চলবে না।
 * নোট হারানো মানে এই অ্যাপের মূল প্রতিশ্রুতিই ভাঙা।
 */

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun toReminderStatus(value: String): ReminderStatus = ReminderStatus.valueOf(value)

    @TypeConverter
    fun fromReminderStatus(status: ReminderStatus): String = status.name
}

/**
 * Schema v1. Migration policy: additive migrations only; destructive
 * migration is never enabled — losing notes defeats the product.
 */
@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class PinDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var instance: PinDatabase? = null

        fun get(context: Context): PinDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PinDatabase::class.java,
                    "pin-notes.db"
                ).build().also { instance = it }
            }
    }
}
