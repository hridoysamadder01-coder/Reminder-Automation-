package app.pin.voicenotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import app.pin.voicenotes.ui.PinRoot
import app.pin.voicenotes.ui.theme.PinTheme

class MainActivity : ComponentActivity() {

    /** Note requested by a tapped reminder notification; consumed by PinRoot. */
    private val requestedNoteId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeOpenIntent(intent)
        setContent {
            PinTheme {
                PinRoot(
                    requestedNoteId = requestedNoteId.value,
                    onNoteRequestConsumed = { requestedNoteId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeOpenIntent(intent)
    }

    private fun consumeOpenIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_NOTE) {
            val id = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
            if (id > 0) requestedNoteId.value = id
        }
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_OPEN_NOTE = "app.pin.voicenotes.action.OPEN_NOTE"
        const val EXTRA_NOTE_ID = "note_id"
    }
}
