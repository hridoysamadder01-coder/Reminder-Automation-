package app.pin.voicenotes.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pin.voicenotes.data.NoteEntity
import app.pin.voicenotes.data.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val nextReminder: NoteEntity? = null,
    val pinnedCount: Int = 0,
    val pinnedPreview: List<NoteEntity> = emptyList(),
    val recent: List<NoteEntity> = emptyList(),
    val isEmpty: Boolean = false,
)

class HomeViewModel(repository: NoteRepository) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        repository.observeUpcomingReminders().map { it.firstOrNull() },
        repository.observePinnedCount(),
        repository.observePinned().map { it.take(3) },
        repository.observeRecent(5),
    ) { next, pinnedCount, pinnedPreview, recent ->
        HomeUiState(
            nextReminder = next,
            pinnedCount = pinnedCount,
            pinnedPreview = pinnedPreview,
            recent = recent,
            isEmpty = recent.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
