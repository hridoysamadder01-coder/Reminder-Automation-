package app.pin.voicenotes.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pin.voicenotes.data.NoteEntity
import app.pin.voicenotes.data.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

enum class NotesFilter { ALL, PINNED, REMINDERS, TODAY }

data class NotesUiState(
    val pinned: List<NoteEntity> = emptyList(),
    val others: List<NoteEntity> = emptyList(),
    val isSearching: Boolean = false,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NotesViewModel(private val repository: NoteRepository) : ViewModel() {

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(NotesFilter.ALL)

    // Re-runs the search whenever notes change too, so pin toggles and
    // deletes reflect immediately inside live search results.
    private val searchResults = combine(
        query.debounce(150),
        repository.observeAll(),
    ) { q, _ -> q }
        .flatMapLatest { q -> flow { emit(if (q.isBlank()) null else repository.search(q)) } }

    val state: StateFlow<NotesUiState> = combine(
        repository.observeAll(),
        filter,
        searchResults,
    ) { all, filter, search ->
        if (search != null) {
            NotesUiState(pinned = emptyList(), others = search, isSearching = true, loaded = true)
        } else {
            val filtered = when (filter) {
                NotesFilter.ALL -> all
                NotesFilter.PINNED -> all.filter { it.isPinned }
                NotesFilter.REMINDERS -> all.filter { it.reminderAt != null && it.reminderEnabled }
                NotesFilter.TODAY -> {
                    val zone = ZoneId.systemDefault()
                    val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
                    all.filter { it.createdAt >= start }
                }
            }
            NotesUiState(
                pinned = if (filter == NotesFilter.ALL) filtered.filter { it.isPinned } else emptyList(),
                others = if (filter == NotesFilter.ALL) filtered.filter { !it.isPinned } else filtered,
                isSearching = false,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setQuery(q: String) {
        query.value = q
    }

    fun setFilter(f: NotesFilter) {
        filter.value = f
        if (f != NotesFilter.ALL) query.value = ""
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            repository.setPinned(note.id, !note.isPinned)
        }
    }
}
