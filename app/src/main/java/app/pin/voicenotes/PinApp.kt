package app.pin.voicenotes

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * অ্যাপের সব যন্ত্রাংশের এক জায়গায় জন্ম: ডাটাবেস, রিপোজিটরি, অ্যালার্ম,
 * পার্সার, স্পিচ। DI ফ্রেমওয়ার্ক নেই — অ্যাপ ছোট, সরল হাতে-জোড়াই যথেষ্ট।
 */

import android.app.Application
import app.pin.voicenotes.data.NoteRepository
import app.pin.voicenotes.data.PinDatabase
import app.pin.voicenotes.domain.parser.IntentParser
import app.pin.voicenotes.reminder.AlarmScheduler
import app.pin.voicenotes.speech.AndroidSpeechController
import app.pin.voicenotes.speech.SpeechController

/**
 * Plain manual wiring — the app is small enough that a DI framework would be
 * ceremony. Everything here is created lazily and lives for the process.
 */
class AppContainer(private val app: Application) {
    val database: PinDatabase by lazy { PinDatabase.get(app) }
    val scheduler: AlarmScheduler by lazy { AlarmScheduler(app) }
    val repository: NoteRepository by lazy { NoteRepository(database.noteDao(), scheduler) }
    val intentParser: IntentParser by lazy { IntentParser() }
    val speech: SpeechController by lazy { AndroidSpeechController(app) }
}

class PinApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
