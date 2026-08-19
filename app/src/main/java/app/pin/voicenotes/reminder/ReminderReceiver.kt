package app.pin.voicenotes.reminder

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
