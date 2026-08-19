package app.pin.voicenotes.ui.reminders

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * রিমাইন্ডার তালিকার ViewModel। "এখন" সময়টা প্রতি ৩০ সেকেন্ডে রিফ্রেশ হয় —
 * নাহলে একবার খোলা স্ক্রিনে বাজতে-যাওয়া রিমাইন্ডার Upcoming থেকে Past-এ
 * সরত না। বন্ধ-করা (কিন্তু ভবিষ্যতের) রিমাইন্ডারও তালিকায় থাকে,
 * যাতে সুইচ দিয়ে আবার চালু করা যায়।
 */

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pin.voicenotes.data.NoteEntity
import app.pin.voicenotes.data.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RemindersUiState(
    val upcoming: List<NoteEntity> = emptyList(),
    val past: List<NoteEntity> = emptyList(),
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModel(private val repository: NoteRepository) : ViewModel() {

    /** Re-emits "now" periodically so lists move across the fired boundary. */
    private fun clockTicks(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val state: StateFlow<RemindersUiState> = clockTicks().flatMapLatest { now ->
        combine(
            repository.observeUpcomingReminders(now),
            repository.observePastReminders(now),
        ) { upcoming, past ->
            RemindersUiState(upcoming = upcoming, past = past, loaded = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    fun setEnabled(note: NoteEntity, enabled: Boolean) {
        viewModelScope.launch {
            repository.setReminderEnabled(note.id, enabled)
        }
    }
}
