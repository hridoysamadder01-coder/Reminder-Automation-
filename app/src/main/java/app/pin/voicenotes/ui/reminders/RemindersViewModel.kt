package app.pin.voicenotes.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pin.voicenotes.data.NoteEntity
import app.pin.voicenotes.data.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RemindersUiState(
    val upcoming: List<NoteEntity> = emptyList(),
    val past: List<NoteEntity> = emptyList(),
    val loaded: Boolean = false,
)

class RemindersViewModel(private val repository: NoteRepository) : ViewModel() {

    val state: StateFlow<RemindersUiState> = combine(
        repository.observeUpcomingReminders(),
        repository.observePastReminders(),
    ) { upcoming, past ->
        RemindersUiState(upcoming = upcoming, past = past, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    fun setEnabled(note: NoteEntity, enabled: Boolean) {
        viewModelScope.launch {
            repository.setReminderEnabled(note.id, enabled)
        }
    }
}
