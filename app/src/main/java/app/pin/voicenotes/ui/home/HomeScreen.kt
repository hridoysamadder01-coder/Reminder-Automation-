package app.pin.voicenotes.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pin.voicenotes.R
import app.pin.voicenotes.ui.AppViewModelProvider
import app.pin.voicenotes.ui.TimeFormat
import app.pin.voicenotes.ui.components.EmptyState
import app.pin.voicenotes.ui.components.Hairline
import app.pin.voicenotes.ui.components.NoteRow
import app.pin.voicenotes.ui.components.SectionLabel
import app.pin.voicenotes.ui.theme.PinColors
import app.pin.voicenotes.ui.theme.PinIcons
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenNote: (Long) -> Unit,
    onOpenPinned: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsState()
    val now = remember { LocalDateTime.now() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        // ---- Top: date + settings -----------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = now.format(
                    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = PinColors.InkGhost,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(
                    PinIcons.Info,
                    contentDescription = stringResource(R.string.about),
                    tint = PinColors.InkGhost,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // ---- Greeting ------------------------------------------------------
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text(
                text = greeting(now.hour),
                style = MaterialTheme.typography.labelLarge,
                color = PinColors.Accent,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_prompt),
                style = MaterialTheme.typography.displaySmall,
                color = PinColors.Ink,
            )
            Spacer(Modifier.height(14.dp))
            RotatingExample()
        }

        if (state.isEmpty) {
            EmptyState(
                title = stringResource(R.string.empty_title),
                subtitle = stringResource(R.string.empty_subtitle),
                examples = listOf(
                    "“কাল সকাল ১০টায় আমাকে মনে করাইস”",
                    "“এই idea টা লিখে রাখ”",
                    "“pinned note gula dekhaw”",
                ),
            )
        } else {
            // ---- Up next ---------------------------------------------------
            state.nextReminder?.let { next ->
                SectionLabel(
                    stringResource(R.string.up_next),
                    Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
                Surface(
                    onClick = { onOpenNote(next.id) },
                    color = PinColors.SurfaceRaised,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            PinIcons.Bell,
                            contentDescription = null,
                            tint = PinColors.Accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = next.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = PinColors.Ink,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = TimeFormat.reminderLabel(next.reminderAt ?: 0L),
                                style = MaterialTheme.typography.bodySmall,
                                color = PinColors.Accent,
                            )
                        }
                        Icon(
                            PinIcons.ChevronRight,
                            contentDescription = null,
                            tint = PinColors.InkGhost,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // ---- Pinned ----------------------------------------------------
            if (state.pinnedCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        PinIcons.Pin,
                        contentDescription = null,
                        tint = PinColors.Accent,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    SectionLabel(stringResource(R.string.pinned))
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.view_all),
                        style = MaterialTheme.typography.labelMedium,
                        color = PinColors.InkFaint,
                        modifier = Modifier
                            .clickable(onClick = onOpenPinned)
                            .padding(4.dp),
                    )
                }
                state.pinnedPreview.forEach { note ->
                    NoteRow(note = note, onClick = { onOpenNote(note.id) }, showBody = false)
                }
                Spacer(Modifier.height(16.dp))
            }

            // ---- Recent ----------------------------------------------------
            if (state.recent.isNotEmpty()) {
                SectionLabel(
                    stringResource(R.string.recent),
                    Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
                state.recent.forEachIndexed { index, note ->
                    if (index > 0) Hairline()
                    NoteRow(note = note, onClick = { onOpenNote(note.id) })
                }
            }
        }

        // Space so content is never hidden behind the bottom bar / mic.
        Spacer(Modifier.height(120.dp))
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning."
    in 12..16 -> "Good afternoon."
    in 17..20 -> "Good evening."
    else -> "Late night thought?"
}

/** Rotating Banglish/Bangla example hints under the prompt. */
@Composable
private fun RotatingExample() {
    val examples = listOf(
        "“কাল ১০টায় আমাকে মনে করাইস…”",
        "“ei idea ta likhe rakh…”",
        "“porshu dupur 2 tay mone korai dio…”",
        "“pinned note gula dekhaw…”",
    )
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            index = (index + 1) % examples.size
        }
    }
    Text(
        text = examples[index],
        style = MaterialTheme.typography.bodyMedium,
        color = PinColors.InkGhost,
    )
}

