package app.pin.voicenotes.reminder

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * অ্যালার্ম বাজার মুহূর্তে এটা চলে: ডাটাবেস থেকে নোট পড়ে, নোটটা এখনো আছে
 * আর রিমাইন্ডার চালু আছে কি না দেখে, তবেই নোটিফিকেশন দেখায় ও FIRED মার্ক করে।
 * ডিলিট/বন্ধ করা রিমাইন্ডার চুপ থাকে। goAsync() দরকার কারণ ডাটাবেস পড়া
 * ব্যাকগ্রাউন্ড থ্রেডে হয়।
 */

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pin.voicenotes.PinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId <= 0) return

        val repository = (context.applicationContext as PinApp).container.repository
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val note = repository.getNote(noteId)
                // Deleted or disabled since scheduling -> stay silent.
                if (note != null && note.reminderEnabled && note.reminderAt != null) {
                    NotificationHelper.showReminder(context, note)
                    repository.markReminderFired(noteId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "app.pin.voicenotes.action.REMINDER"
        const val EXTRA_NOTE_ID = "note_id"
    }
}
