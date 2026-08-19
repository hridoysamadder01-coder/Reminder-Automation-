package app.pin.voicenotes.ui.capture

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pin.voicenotes.R
import app.pin.voicenotes.domain.parser.DayPeriod
import app.pin.voicenotes.speech.SpeechState
import app.pin.voicenotes.ui.TimeFormat
import app.pin.voicenotes.ui.components.NoteRow
import app.pin.voicenotes.ui.components.ReminderPickerDialog
import app.pin.voicenotes.ui.components.SectionLabel
import app.pin.voicenotes.ui.components.ensureFuture
import app.pin.voicenotes.ui.theme.PinColors
import app.pin.voicenotes.ui.theme.PinIcons
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The capture surface: one bottom sheet hosting every state of the
 * listen → understand → confirm/clarify → act flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(viewModel: CaptureViewModel) {
    val ui by viewModel.ui.collectAsState()
    if (ui is CaptureUi.Hidden) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { viewModel.dismiss() },
        sheetState = sheetState,
        containerColor = PinColors.SurfaceRaised,
        dragHandle = null,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .animateContentSize(animationSpec = tween(200))
        ) {
            when (val state = ui) {
                is CaptureUi.Hidden -> Unit
                is CaptureUi.Listening -> ListeningPane(viewModel)
                is CaptureUi.Processing -> ProcessingPane()
                is CaptureUi.TextEntry -> TextEntryPane(viewModel, state)
                is CaptureUi.ConfirmReminder -> ConfirmReminderPane(viewModel, state)
                is CaptureUi.Clarify -> ClarifyPane(viewModel, state)
                is CaptureUi.Choose -> ChoosePane(viewModel, state)
                is CaptureUi.ConfirmDelete -> ConfirmDeletePane(viewModel, state)
                is CaptureUi.Failure -> FailurePane(viewModel, state)
            }
        }
    }
}

// ---- Listening -------------------------------------------------------------

@Composable
private fun ListeningPane(viewModel: CaptureViewModel) {
    val speechState by viewModel.speech.state.collectAsState()
    val level by viewModel.speech.level.collectAsState()
    val partial = (speechState as? SpeechState.Listening)?.partial.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.listening),
            style = MaterialTheme.typography.labelMedium,
            color = PinColors.Accent,
        )
        Spacer(Modifier.height(20.dp))
        LevelBars(level)
        Spacer(Modifier.height(20.dp))
        Text(
            text = partial.ifEmpty { stringResource(R.string.listening_hint) },
            style = MaterialTheme.typography.bodyLarge,
            color = if (partial.isEmpty()) PinColors.InkGhost else PinColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.startText() }) {
                Icon(
                    PinIcons.Keyboard,
                    contentDescription = stringResource(R.string.type_instead),
                    tint = PinColors.InkGhost,
                )
            }
            Spacer(Modifier.width(24.dp))
            // Big stop control — finishes listening and hands off to the recognizer.
            Surface(
                onClick = { viewModel.finishListening() },
                shape = CircleShape,
                color = PinColors.Accent,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        PinIcons.Check,
                        contentDescription = stringResource(R.string.done_listening),
                        tint = PinColors.OnAccent,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = { viewModel.dismiss() }) {
                Icon(
                    PinIcons.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = PinColors.InkGhost,
                )
            }
        }
    }
}

/** Restrained level meter driven by the real microphone RMS. */
@Composable
private fun LevelBars(level: Float) {
    val weights = listOf(0.45f, 0.75f, 1f, 0.75f, 0.45f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(44.dp),
    ) {
        weights.forEach { w ->
            val target = (8 + 36 * level * w).coerceAtLeast(8f)
            val animated by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(120),
                label = "bar",
            )
            Box(
                Modifier
                    .width(5.dp)
                    .height(animated.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(PinColors.Accent)
            )
        }
    }
}

// ---- Processing ------------------------------------------------------------

@Composable
private fun ProcessingPane() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = PinColors.Accent,
            strokeWidth = 3.dp,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.understanding),
            style = MaterialTheme.typography.bodyMedium,
            color = PinColors.InkFaint,
        )
    }
}

// ---- Text entry ------------------------------------------------------------

@Composable
private fun TextEntryPane(viewModel: CaptureViewModel, state: CaptureUi.TextEntry) {
    var text by rememberSaveable(state) { mutableStateOf(state.prefill) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        if (state.hint != null) {
            Text(
                text = state.hint,
                style = MaterialTheme.typography.bodyMedium,
                color = PinColors.InkFaint,
            )
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.text_placeholder)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PinColors.Accent,
                unfocusedBorderColor = PinColors.Outline,
                cursorColor = PinColors.Accent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.submitText(text) }),
            minLines = 2,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { viewModel.dismiss() }) {
                Text(stringResource(R.string.cancel), color = PinColors.InkFaint)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { viewModel.submitText(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

// ---- Confirm reminder ------------------------------------------------------

@Composable
private fun ConfirmReminderPane(viewModel: CaptureViewModel, state: CaptureUi.ConfirmReminder) {
    var showPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        SectionLabel(stringResource(R.string.heard))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "“${state.heard}”",
            style = MaterialTheme.typography.bodyMedium,
            color = PinColors.InkFaint,
        )
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.note_label))
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.content.ifBlank { stringResource(R.string.reminder_placeholder) },
            style = MaterialTheme.typography.titleMedium,
            color = PinColors.Ink,
        )
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.reminder_label))
        Spacer(Modifier.height(6.dp))
        Surface(
            onClick = { showPicker = true },
            color = PinColors.SurfaceHigh,
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    PinIcons.Bell,
                    contentDescription = null,
                    tint = PinColors.Accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = TimeFormat.reminderLabel(state.remindAt),
                    style = MaterialTheme.typography.titleMedium,
                    color = PinColors.Accent,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    PinIcons.Edit,
                    contentDescription = stringResource(R.string.change_time),
                    tint = PinColors.InkGhost,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (!state.exactPossible) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.exact_alarm_off_hint),
                style = MaterialTheme.typography.bodySmall,
                color = PinColors.InkGhost,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { viewModel.dismiss() }) {
                Text(stringResource(R.string.cancel), color = PinColors.InkFaint)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { viewModel.startText(state.heard) }) {
                Text(stringResource(R.string.edit))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { viewModel.saveReminder() }) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (showPicker) {
        ReminderPickerDialog(
            initial = state.remindAt,
            onConfirm = {
                viewModel.adjustReminderTime(it.ensureFuture())
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

// ---- Clarify ---------------------------------------------------------------

@Composable
private fun ClarifyPane(viewModel: CaptureViewModel, state: CaptureUi.Clarify) {
    var showPicker by remember { mutableStateOf(false) }

    val question = when {
        state.wasPast -> stringResource(R.string.clarify_past)
        state.period != null -> stringResource(
            R.string.clarify_period,
            periodLabel(state.period)
        )
        else -> stringResource(R.string.clarify_when)
    }

    val quickHours: List<LocalTime> = when (state.period) {
        DayPeriod.MORNING -> listOf(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0))
        DayPeriod.NOON -> listOf(LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(14, 0))
        DayPeriod.AFTERNOON -> listOf(LocalTime.of(15, 0), LocalTime.of(16, 0), LocalTime.of(17, 0))
        DayPeriod.EVENING -> listOf(LocalTime.of(18, 0), LocalTime.of(19, 0), LocalTime.of(20, 0))
        DayPeriod.NIGHT -> listOf(LocalTime.of(20, 0), LocalTime.of(21, 0), LocalTime.of(22, 0))
        null -> listOf(LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(18, 0))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        if (state.content.isNotBlank()) {
            SectionLabel(stringResource(R.string.note_label))
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.content,
                style = MaterialTheme.typography.titleMedium,
                color = PinColors.Ink,
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = question,
            style = MaterialTheme.typography.headlineSmall,
            color = PinColors.Ink,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickHours.forEach { time ->
                Surface(
                    onClick = { viewModel.clarifyTime(time) },
                    color = PinColors.SurfaceHigh,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = TimeFormat.timeOnly(LocalDateTime.now().withHour(time.hour).withMinute(time.minute)),
                        style = MaterialTheme.typography.titleMedium,
                        color = PinColors.Accent,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Surface(
                onClick = { showPicker = true },
                color = PinColors.SurfaceHigh,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.choose_time),
                    style = MaterialTheme.typography.titleMedium,
                    color = PinColors.InkFaint,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { viewModel.clarifySaveWithoutReminder() }) {
                Text(stringResource(R.string.save_without_reminder), color = PinColors.InkFaint)
            }
            TextButton(onClick = { viewModel.dismiss() }) {
                Text(stringResource(R.string.cancel), color = PinColors.InkGhost)
            }
        }
    }

    if (showPicker) {
        ReminderPickerDialog(
            initial = LocalDateTime.now().plusHours(1).withMinute(0),
            onConfirm = {
                showPicker = false
                viewModel.clarifyTime(it.toLocalTime())
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun periodLabel(period: DayPeriod): String = stringResource(
    when (period) {
        DayPeriod.MORNING -> R.string.period_morning
        DayPeriod.NOON -> R.string.period_noon
        DayPeriod.AFTERNOON -> R.string.period_afternoon
        DayPeriod.EVENING -> R.string.period_evening
        DayPeriod.NIGHT -> R.string.period_night
    }
)

// ---- Choose ----------------------------------------------------------------

@Composable
private fun ChoosePane(viewModel: CaptureViewModel, state: CaptureUi.Choose) {
    Column(modifier = Modifier.padding(vertical = 20.dp)) {
        Text(
            text = state.message,
            style = MaterialTheme.typography.titleLarge,
            color = PinColors.Ink,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
        state.candidates.forEach { note ->
            NoteRow(
                note = note,
                onClick = { viewModel.chooseCandidate(note) },
                showBody = true,
            )
        }
        TextButton(
            onClick = { viewModel.dismiss() },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.cancel), color = PinColors.InkFaint)
        }
    }
}

// ---- Confirm delete --------------------------------------------------------

@Composable
private fun ConfirmDeletePane(viewModel: CaptureViewModel, state: CaptureUi.ConfirmDelete) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.delete_question),
            style = MaterialTheme.typography.titleLarge,
            color = PinColors.Ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.note.title,
            style = MaterialTheme.typography.titleMedium,
            color = PinColors.InkFaint,
        )
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { viewModel.dismiss() }) {
                Text(stringResource(R.string.cancel), color = PinColors.InkFaint)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { viewModel.confirmDelete() },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = PinColors.Danger,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                ),
            ) {
                Text(stringResource(R.string.delete))
            }
        }
    }
}

// ---- Failure ---------------------------------------------------------------

@Composable
private fun FailurePane(viewModel: CaptureViewModel, state: CaptureUi.Failure) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.message,
            style = MaterialTheme.typography.titleMedium,
            color = PinColors.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row {
            if (state.canRetryVoice) {
                OutlinedButton(onClick = { viewModel.startVoice() }) {
                    Text(stringResource(R.string.try_again))
                }
                Spacer(Modifier.width(12.dp))
            }
            Button(onClick = { viewModel.startText() }) {
                Text(stringResource(R.string.type_instead))
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { viewModel.dismiss() }) {
            Text(stringResource(R.string.cancel), color = PinColors.InkGhost)
        }
    }
}
