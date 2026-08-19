package app.pin.voicenotes.ui.detail

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * নোটের বিস্তারিত পাতার ViewModel। ডিলিট আর তার Undo চালানো হয় appScope-এ
 * (viewModelScope-এ নয়) — কারণ ডিলিটের পর পাতা বন্ধ হয়ে ViewModel মরে যায়,
 * তখন Undo চাপলে মরা scope-এ কিছুই চলত না, নোট ফেরত আসত না।
 */

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pin.voicenotes.data.NoteEntity
import app.pin.voicenotes.data.NoteRepository
import app.pin.voicenotes.reminder.AlarmScheduler
import app.pin.voicenotes.ui.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

sealed interface DetailEvent {
    data object Deleted : DetailEvent
    data class Snackbar(
        val message: String,
        val actionLabel: String? = null,
        val action: (() -> Unit)? = null,
    ) : DetailEvent
}

class DetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: NoteRepository,
    private val alarmScheduler: AlarmScheduler,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val noteId: Long = checkNotNull(savedStateHandle["noteId"])

    val note: StateFlow<NoteEntity?> = repository.observeNote(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = MutableSharedFlow<DetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DetailEvent> = _events

    fun exactAlarmAllowed(): Boolean = alarmScheduler.canScheduleExact()

    fun togglePin() {
        val current = note.value ?: return
        viewModelScope.launch {
            repository.setPinned(noteId, !current.isPinned)
        }
    }

    fun saveEdits(title: String, body: String) {
        viewModelScope.launch {
            repository.updateContent(noteId, title, body)
        }
    }

    fun setReminder(at: LocalDateTime) {
        viewModelScope.launch {
            repository.setReminder(noteId, at)
        }
    }

    fun cancelReminder() {
        val current = note.value ?: return
        val previousAt = current.reminderAt
        viewModelScope.launch {
            repository.cancelReminder(noteId)
            _events.tryEmit(
                DetailEvent.Snackbar(
                    message = "Reminder removed",
                    actionLabel = "Undo",
                    action = {
                        // appScope: the user may undo after leaving this screen.
                        if (previousAt != null && previousAt > System.currentTimeMillis()) {
                            appScope.launch {
                                repository.setReminder(noteId, TimeFormat.local(previousAt))
                            }
                        }
                    },
                )
            )
        }
    }

    fun deleteNote() {
        val snapshot = note.value ?: return
        // The whole delete + undo pair lives on appScope: the Deleted event
        // pops this screen and clears the ViewModel, and the snackbar's Undo
        // fires well after that.
        appScope.launch {
            repository.deleteNote(noteId)
            _events.tryEmit(DetailEvent.Deleted)
            _events.tryEmit(
                DetailEvent.Snackbar(
                    message = "Note deleted",
                    actionLabel = "Undo",
                    action = { appScope.launch { repository.restoreNote(snapshot) } },
                )
            )
        }
    }
}
