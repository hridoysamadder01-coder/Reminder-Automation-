package app.pin.voicenotes.data

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
