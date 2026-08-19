package app.pin.voicenotes.ui.notes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pin.voicenotes.R
import app.pin.voicenotes.ui.AppViewModelProvider
import app.pin.voicenotes.ui.components.EmptyState
import app.pin.voicenotes.ui.components.Hairline
import app.pin.voicenotes.ui.components.NoteRow
import app.pin.voicenotes.ui.components.SectionLabel
import app.pin.voicenotes.ui.theme.PinColors
import app.pin.voicenotes.ui.theme.PinIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onOpenNote: (Long) -> Unit,
    initialQuery: String? = null,
    initialFilter: String? = null,
    viewModel: NotesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()

    // Apply navigation arguments exactly once per entry — returning from a
    // note must not clobber a filter or query the user changed since.
    var navArgsApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!navArgsApplied) {
            navArgsApplied = true
            initialQuery?.takeIf { it.isNotBlank() }?.let { viewModel.setQuery(it) }
            when (initialFilter) {
                "pinned" -> viewModel.setFilter(NotesFilter.PINNED)
                "today" -> viewModel.setFilter(NotesFilter.TODAY)
                "reminders" -> viewModel.setFilter(NotesFilter.REMINDERS)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.notes_title),
            style = MaterialTheme.typography.headlineSmall,
            color = PinColors.Ink,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = {
                Icon(
                    PinIcons.Search,
                    contentDescription = null,
                    tint = PinColors.InkGhost,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PinColors.Accent,
                unfocusedBorderColor = PinColors.Outline,
                cursorColor = PinColors.Accent,
                focusedContainerColor = PinColors.Surface,
                unfocusedContainerColor = PinColors.Surface,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NotesFilterChip(
                selected = filter == NotesFilter.ALL,
                label = stringResource(R.string.filter_all),
                onClick = { viewModel.setFilter(NotesFilter.ALL) },
            )
            NotesFilterChip(
                selected = filter == NotesFilter.PINNED,
                label = stringResource(R.string.filter_pinned),
                onClick = { viewModel.setFilter(NotesFilter.PINNED) },
                leading = {
                    Icon(
                        PinIcons.Pin,
                        contentDescription = null,
                        modifier = Modifier.width(12.dp),
                    )
                },
            )
            NotesFilterChip(
                selected = filter == NotesFilter.REMINDERS,
                label = stringResource(R.string.filter_reminders),
                onClick = { viewModel.setFilter(NotesFilter.REMINDERS) },
            )
            if (filter == NotesFilter.TODAY) {
                NotesFilterChip(
                    selected = true,
                    label = stringResource(R.string.filter_today),
                    onClick = { viewModel.setFilter(NotesFilter.ALL) },
                )
            }
        }

        val isEmpty = state.loaded && state.pinned.isEmpty() && state.others.isEmpty()
        if (isEmpty) {
            EmptyState(
                title = if (state.isSearching) stringResource(R.string.search_empty_title)
                else stringResource(R.string.notes_empty_title),
                subtitle = if (state.isSearching) stringResource(R.string.search_empty_subtitle)
                else stringResource(R.string.notes_empty_subtitle),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.pinned.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                PinIcons.Pin,
                                contentDescription = null,
                                tint = PinColors.Accent,
                                modifier = Modifier.width(13.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            SectionLabel(stringResource(R.string.pinned))
                        }
                    }
                    items(state.pinned, key = { "p${it.id}" }) { note ->
                        NoteRow(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            onTogglePin = { viewModel.togglePin(note) },
                        )
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }
                if (state.others.isNotEmpty() && state.pinned.isNotEmpty()) {
                    item {
                        SectionLabel(
                            stringResource(R.string.recent),
                            Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                        )
                    }
                }
                itemsIndexedWithDividers(state.others) { note ->
                    NoteRow(
                        note = note,
                        onClick = { onOpenNote(note.id) },
                        onTogglePin = { viewModel.togglePin(note) },
                    )
                }
                item { Spacer(Modifier.height(140.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leading,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PinColors.Accent.copy(alpha = 0.16f),
            selectedLabelColor = PinColors.Accent,
            selectedLeadingIconColor = PinColors.Accent,
            labelColor = PinColors.InkFaint,
            iconColor = PinColors.InkGhost,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = PinColors.Outline,
            selectedBorderColor = PinColors.Accent.copy(alpha = 0.4f),
        ),
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedWithDividers(
    notes: List<app.pin.voicenotes.data.NoteEntity>,
    row: @Composable (app.pin.voicenotes.data.NoteEntity) -> Unit,
) {
    notes.forEachIndexed { index, note ->
        item(key = note.id) {
            Column {
                if (index > 0) Hairline()
                row(note)
            }
        }
    }
}
