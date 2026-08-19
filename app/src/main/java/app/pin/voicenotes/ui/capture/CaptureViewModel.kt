package app.pin.voicenotes.ui.capture

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * পুরো ক্যাপচার-প্রবাহের state machine: শোনা → বোঝা → দরকার হলে প্রশ্ন → কাজ।
 * ভয়েস আর টাইপিং দুটোই একই পথে যায় (আলাদা লজিক নেই)।
 *  - সাধারণ নোট: সাথে সাথে সেভ + Undo (বাড়তি ট্যাপ নেই)
 *  - রিমাইন্ডার: সময়টা স্পষ্ট দেখিয়ে তারপর সেভ (ভুল সময়ে অ্যালার্ম আটকাতে)
 *  - অস্পষ্ট সময়: "কয়টায়?" — চটজলদি অপশন, না দিলে রিমাইন্ডার ছাড়াই নোট
 *  - খোলা/পিন/ডিলিটে একাধিক নোট মিললে ছোট লিস্ট থেকে বেছে নেওয়া
 * নোটিফিকেশন পারমিশন চাওয়া হয় প্রথম রিমাইন্ডার সেভের মুহূর্তে — আগে নয়।
 */

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pin.voicenotes.R
import app.pin.voicenotes.data.NoteEntity
import app.pin.voicenotes.data.NoteRepository
import app.pin.voicenotes.domain.parser.DayPeriod
import app.pin.voicenotes.domain.parser.IntentParser
import app.pin.voicenotes.domain.parser.VoiceIntent
import app.pin.voicenotes.reminder.AlarmScheduler
import app.pin.voicenotes.reminder.NotificationHelper
import app.pin.voicenotes.speech.SpeechController
import app.pin.voicenotes.speech.SpeechErrorKind
import app.pin.voicenotes.speech.SpeechState
import app.pin.voicenotes.ui.TimeFormat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ChooseAction { OPEN, PIN, UNPIN, DELETE }

sealed interface CaptureUi {
    data object Hidden : CaptureUi
    data object Listening : CaptureUi
    data object Processing : CaptureUi
    data class TextEntry(val prefill: String = "", val hint: String? = null) : CaptureUi

    data class ConfirmReminder(
        val heard: String,
        val content: String,
        val remindAt: LocalDateTime,
        val exactPossible: Boolean,
    ) : CaptureUi

    data class Clarify(
        val heard: String,
        val content: String,
        val date: LocalDate?,
        val period: DayPeriod?,
        val wasPast: Boolean,
    ) : CaptureUi

    data class Choose(
        val message: String,
        val candidates: List<NoteEntity>,
        val action: ChooseAction,
    ) : CaptureUi

    data class ConfirmDelete(val note: NoteEntity) : CaptureUi

    data class Failure(val message: String, val canRetryVoice: Boolean = true) : CaptureUi
}

sealed interface CaptureEvent {
    data class Snackbar(
        val message: String,
        val actionLabel: String? = null,
        val action: (() -> Unit)? = null,
    ) : CaptureEvent

    data class NavigateToNote(val id: Long) : CaptureEvent
    data class NavigateRoute(val route: String) : CaptureEvent
    data object RequestNotificationPermission : CaptureEvent
}

/**
 * One state machine for both voice and typed capture:
 * transcript -> local intent -> action, with clarification instead of guessing.
 */
class CaptureViewModel(
    private val app: Application,
    private val repository: NoteRepository,
    private val parser: IntentParser,
    val speech: SpeechController,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    private val _ui = MutableStateFlow<CaptureUi>(CaptureUi.Hidden)
    val ui: StateFlow<CaptureUi> = _ui

    private val _events = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CaptureEvent> = _events

    /** Reminder save waiting for the notification-permission result. */
    private var pendingReminderSave: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            speech.state.collect { state ->
                when (state) {
                    is SpeechState.Result -> {
                        speech.reset()
                        // A late result after the user dismissed the sheet must
                        // not silently create a note.
                        if (_ui.value is CaptureUi.Listening || _ui.value is CaptureUi.Processing) {
                            handleTranscript(state.text)
                        }
                    }
                    is SpeechState.Error -> {
                        if (_ui.value is CaptureUi.Listening || _ui.value is CaptureUi.Processing) {
                            speech.reset()
                            _ui.value = CaptureUi.Failure(errorMessage(state.kind))
                        }
                    }
                    is SpeechState.Processing ->
                        if (_ui.value is CaptureUi.Listening) _ui.value = CaptureUi.Processing
                    else -> Unit
                }
            }
        }
    }

    // ---- Entry points ------------------------------------------------------

    fun startVoice() {
        if (!speech.isRecognitionAvailable()) {
            _ui.value = CaptureUi.TextEntry(
                hint = app.getString(R.string.speech_unavailable)
            )
            return
        }
        _ui.value = CaptureUi.Listening
        speech.startListening()
    }

    fun startText(prefill: String = "") {
        speech.cancel()
        _ui.value = CaptureUi.TextEntry(prefill)
    }

    fun onMicPermissionDenied() {
        speech.cancel()
        _ui.value = CaptureUi.TextEntry(
            hint = app.getString(R.string.mic_denied)
        )
    }

    fun finishListening() = speech.stopListening()

    fun dismiss() {
        speech.cancel()
        pendingReminderSave = null
        _ui.value = CaptureUi.Hidden
    }

    fun submitText(text: String) {
        if (text.isBlank()) return
        handleTranscript(text.trim())
    }

    // ---- Intent handling ---------------------------------------------------

    private fun handleTranscript(text: String) {
        _ui.value = CaptureUi.Processing
        viewModelScope.launch {
            when (val intent = parser.parse(text)) {
                is VoiceIntent.CreateNote -> saveNote(intent.content, text)

                is VoiceIntent.CreateNoteWithReminder -> _ui.value = CaptureUi.ConfirmReminder(
                    heard = text,
                    content = intent.content,
                    remindAt = intent.remindAt,
                    exactPossible = alarmScheduler.canScheduleExact(),
                )

                is VoiceIntent.NeedsTimeClarification -> _ui.value = CaptureUi.Clarify(
                    heard = text,
                    content = intent.content,
                    date = intent.date,
                    period = intent.period,
                    wasPast = intent.wasPast,
                )

                is VoiceIntent.SearchNotes -> {
                    navigate("notes?query=${android.net.Uri.encode(intent.query)}")
                }

                is VoiceIntent.OpenNote -> openNote(intent)

                is VoiceIntent.PinNote -> pinByQuery(intent.query, pinned = true)
                is VoiceIntent.UnpinNote -> pinByQuery(intent.query, pinned = false)

                VoiceIntent.ShowPinned -> navigate("notes?filter=pinned")
                VoiceIntent.ShowTodayNotes -> navigate("notes?filter=today")
                is VoiceIntent.ShowReminders -> navigate("reminders")

                is VoiceIntent.DeleteNote -> deleteByQuery(intent.query)

                is VoiceIntent.Unknown -> _ui.value = CaptureUi.Failure(
                    app.getString(R.string.did_not_understand)
                )
            }
        }
    }

    private suspend fun saveNote(content: String, transcript: String) {
        val note = repository.createNote(content, transcript)
        _ui.value = CaptureUi.Hidden
        emit(
            CaptureEvent.Snackbar(
                message = app.getString(R.string.note_saved),
                actionLabel = app.getString(R.string.undo),
                action = { viewModelScope.launch { repository.deleteNote(note.id) } },
            )
        )
    }

    // ---- Reminder confirmation --------------------------------------------

    fun adjustReminderTime(newAt: LocalDateTime) {
        val current = _ui.value
        if (current is CaptureUi.ConfirmReminder) {
            _ui.value = current.copy(remindAt = newAt)
        }
    }

    fun saveReminder() {
        val current = _ui.value as? CaptureUi.ConfirmReminder ?: return
        val doSave = {
            viewModelScope.launch {
                val note = repository.createNote(current.content, current.heard, current.remindAt)
                _ui.value = CaptureUi.Hidden
                val label = TimeFormat.reminderLabel(current.remindAt)
                emit(
                    CaptureEvent.Snackbar(
                        message = app.getString(
                            R.string.reminder_set_for, label
                        ),
                        actionLabel = app.getString(R.string.undo),
                        action = { viewModelScope.launch { repository.deleteNote(note.id) } },
                    )
                )
                if (!NotificationHelper.canPostNotifications(app)) {
                    emit(
                        CaptureEvent.Snackbar(
                            app.getString(R.string.notifications_off_warning)
                        )
                    )
                }
            }
            Unit
        }
        if (!NotificationHelper.canPostNotifications(app)) {
            pendingReminderSave = doSave
            emit(CaptureEvent.RequestNotificationPermission)
        } else {
            doSave()
        }
    }

    /** Called with the notification-permission result; saves either way. */
    fun onNotificationPermissionResult() {
        pendingReminderSave?.invoke()
        pendingReminderSave = null
    }

    // ---- Clarification -----------------------------------------------------

    fun clarifyTime(time: LocalTime) {
        val current = _ui.value as? CaptureUi.Clarify ?: return
        val now = LocalDateTime.now()
        val date = current.date ?: run {
            val candidate = LocalDateTime.of(LocalDate.now(), time)
            if (candidate.isAfter(now)) LocalDate.now() else LocalDate.now().plusDays(1)
        }
        var at = LocalDateTime.of(date, time)
        if (!at.isAfter(now)) at = at.plusDays(1)
        _ui.value = CaptureUi.ConfirmReminder(
            heard = current.heard,
            content = current.content,
            remindAt = at,
            exactPossible = alarmScheduler.canScheduleExact(),
        )
    }

    fun clarifySaveWithoutReminder() {
        val current = _ui.value as? CaptureUi.Clarify ?: return
        viewModelScope.launch {
            val note = repository.createNote(current.content, current.heard)
            _ui.value = CaptureUi.Hidden
            emit(
                CaptureEvent.Snackbar(
                    message = app.getString(R.string.saved_without_reminder),
                    actionLabel = app.getString(R.string.undo),
                    action = { viewModelScope.launch { repository.deleteNote(note.id) } },
                )
            )
        }
    }

    // ---- Open / pin / delete by voice --------------------------------------

    private suspend fun openNote(intent: VoiceIntent.OpenNote) {
        val candidates = repository.findNotesForOpen(
            intent.query, intent.pinnedOnly, intent.dayOffset, intent.latest
        )
        when {
            candidates.isEmpty() -> _ui.value = CaptureUi.Failure(
                app.getString(R.string.no_note_match)
            )
            candidates.size == 1 || intent.latest -> {
                _ui.value = CaptureUi.Hidden
                emit(CaptureEvent.NavigateToNote(candidates.first().id))
            }
            else -> _ui.value = CaptureUi.Choose(
                message = app.getString(R.string.which_note),
                candidates = candidates.take(5),
                action = ChooseAction.OPEN,
            )
        }
    }

    private suspend fun pinByQuery(query: String, pinned: Boolean) {
        val candidates: List<NoteEntity> = if (query.isBlank()) {
            if (pinned) listOfNotNull(repository.mostRecentNote())
            else repository.findNotesForOpen("", pinnedOnly = true, dayOffset = null, latest = false)
        } else {
            repository.search(query).let { if (pinned) it else it.filter { n -> n.isPinned } }
        }
        when {
            candidates.isEmpty() -> _ui.value = CaptureUi.Failure(
                app.getString(R.string.no_note_match)
            )
            candidates.size == 1 || query.isBlank() -> applyPin(candidates.first(), pinned)
            else -> _ui.value = CaptureUi.Choose(
                message = app.getString(
                    if (pinned) R.string.which_note_pin
                    else R.string.which_note_unpin
                ),
                candidates = candidates.take(5),
                action = if (pinned) ChooseAction.PIN else ChooseAction.UNPIN,
            )
        }
    }

    private suspend fun deleteByQuery(query: String) {
        if (query.isBlank()) {
            _ui.value = CaptureUi.Failure(
                app.getString(R.string.no_note_match)
            )
            return
        }
        val candidates = repository.search(query)
        when {
            candidates.isEmpty() -> _ui.value = CaptureUi.Failure(
                app.getString(R.string.no_note_match)
            )
            candidates.size == 1 -> _ui.value = CaptureUi.ConfirmDelete(candidates.first())
            else -> _ui.value = CaptureUi.Choose(
                message = app.getString(R.string.which_note_delete),
                candidates = candidates.take(5),
                action = ChooseAction.DELETE,
            )
        }
    }

    fun chooseCandidate(note: NoteEntity) {
        val current = _ui.value as? CaptureUi.Choose ?: return
        viewModelScope.launch {
            when (current.action) {
                ChooseAction.OPEN -> {
                    _ui.value = CaptureUi.Hidden
                    emit(CaptureEvent.NavigateToNote(note.id))
                }
                ChooseAction.PIN -> applyPin(note, true)
                ChooseAction.UNPIN -> applyPin(note, false)
                ChooseAction.DELETE -> _ui.value = CaptureUi.ConfirmDelete(note)
            }
        }
    }

    fun confirmDelete() {
        val current = _ui.value as? CaptureUi.ConfirmDelete ?: return
        viewModelScope.launch {
            val snapshot = current.note
            repository.deleteNote(snapshot.id)
            _ui.value = CaptureUi.Hidden
            emit(
                CaptureEvent.Snackbar(
                    message = app.getString(R.string.note_deleted),
                    actionLabel = app.getString(R.string.undo),
                    action = { viewModelScope.launch { repository.restoreNote(snapshot) } },
                )
            )
        }
    }

    private suspend fun applyPin(note: NoteEntity, pinned: Boolean) {
        repository.setPinned(note.id, pinned)
        _ui.value = CaptureUi.Hidden
        emit(
            CaptureEvent.Snackbar(
                message = app.getString(
                    if (pinned) R.string.note_pinned
                    else R.string.note_unpinned,
                    note.title
                ),
                actionLabel = app.getString(R.string.undo),
                action = { viewModelScope.launch { repository.setPinned(note.id, !pinned) } },
            )
        )
    }

    // ---- Helpers -----------------------------------------------------------

    private fun navigate(route: String) {
        _ui.value = CaptureUi.Hidden
        emit(CaptureEvent.NavigateRoute(route))
    }

    private fun emit(event: CaptureEvent) {
        _events.tryEmit(event)
    }

    private fun errorMessage(kind: SpeechErrorKind): String = app.getString(
        when (kind) {
            SpeechErrorKind.NO_MATCH -> R.string.speech_no_match
            SpeechErrorKind.PERMISSION -> R.string.mic_denied
            SpeechErrorKind.UNAVAILABLE -> R.string.speech_unavailable
            SpeechErrorKind.NETWORK -> R.string.speech_network
            SpeechErrorKind.BUSY -> R.string.speech_busy
            SpeechErrorKind.OTHER -> R.string.speech_generic_error
        }
    )
}
