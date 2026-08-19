package app.pin.voicenotes.ui.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pin.voicenotes.R
import app.pin.voicenotes.reminder.NotificationHelper
import app.pin.voicenotes.ui.AppViewModelProvider
import app.pin.voicenotes.ui.TimeFormat
import app.pin.voicenotes.ui.components.ReminderPickerDialog
import app.pin.voicenotes.ui.components.SectionLabel
import app.pin.voicenotes.ui.components.ensureFuture
import app.pin.voicenotes.ui.theme.PinColors
import app.pin.voicenotes.ui.theme.PinIcons
import java.time.LocalDateTime

@Composable
fun NoteDetailScreen(
    onBack: () -> Unit,
    onShowSnackbar: (String, String?, (() -> Unit)?) -> Unit,
    viewModel: DetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val note by viewModel.note.collectAsState()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    var editing by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }

    // Setting a reminder here must ask for notification permission the same
    // way the voice flow does — this may be the user's first reminder ever.
    var pendingReminderAt by remember { mutableStateOf<LocalDateTime?>(null) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingReminderAt?.let { viewModel.setReminder(it) }
        pendingReminderAt = null
        if (!granted) {
            onShowSnackbar(context.getString(R.string.notifications_off_warning), null, null)
        }
    }
    val setReminderWithPermission: (LocalDateTime) -> Unit = { at ->
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canPostNotifications(context)) {
            pendingReminderAt = at
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setReminder(at)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailEvent.Deleted -> onBack()
                is DetailEvent.Snackbar ->
                    onShowSnackbar(event.message, event.actionLabel, event.action)
            }
        }
    }

    // A stale deep link (deleted note) must not strand the user on a blank
    // screen. Deleting from this screen pops via the Deleted event instead.
    var everLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(note) {
        if (note != null) {
            everLoaded = true
        } else if (!everLoaded) {
            kotlinx.coroutines.delay(250)
            if (viewModel.note.value == null) onBack()
        }
    }

    val current = note ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        // ---- Top bar -------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    PinIcons.ChevronRight,
                    contentDescription = stringResource(R.string.back),
                    tint = PinColors.InkFaint,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(180f),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.togglePin()
            }) {
                Icon(
                    imageVector = if (current.isPinned) PinIcons.Pin else PinIcons.PinOutline,
                    contentDescription = stringResource(
                        if (current.isPinned) R.string.unpin else R.string.pin
                    ),
                    tint = if (current.isPinned) PinColors.Accent else PinColors.InkGhost,
                )
            }
            IconButton(onClick = { editing = !editing }) {
                Icon(
                    PinIcons.Edit,
                    contentDescription = stringResource(R.string.edit),
                    tint = if (editing) PinColors.Accent else PinColors.InkGhost,
                )
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    PinIcons.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = PinColors.InkGhost,
                )
            }
        }

        if (editing) {
            EditPane(
                initialTitle = current.title,
                initialBody = current.body,
                onSave = { title, body ->
                    viewModel.saveEdits(title, body)
                    editing = false
                },
                onCancel = { editing = false },
            )
        } else {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = PinColors.Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.created_at, TimeFormat.full(current.createdAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = PinColors.InkGhost,
                )
                if (current.body.isNotBlank() && current.body != current.title) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = current.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PinColors.Ink,
                    )
                }

                Spacer(Modifier.height(28.dp))

                // ---- Reminder surface -------------------------------------
                SectionLabel(stringResource(R.string.reminder_label))
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = { showReminderPicker = true },
                    color = PinColors.SurfaceRaised,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (current.reminderEnabled && current.reminderAt != null)
                                PinIcons.Bell else PinIcons.BellOff,
                            contentDescription = null,
                            tint = if (current.reminderEnabled && current.reminderAt != null)
                                PinColors.Accent else PinColors.InkGhost,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            if (current.reminderAt != null && current.reminderEnabled) {
                                Text(
                                    text = TimeFormat.reminderLabel(current.reminderAt),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PinColors.Accent,
                                )
                                if (!viewModel.exactAlarmAllowed()) {
                                    Text(
                                        text = stringResource(R.string.exact_alarm_off_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PinColors.InkGhost,
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.add_reminder),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PinColors.InkFaint,
                                )
                            }
                        }
                        if (current.reminderAt != null && current.reminderEnabled) {
                            Text(
                                text = stringResource(R.string.remove),
                                style = MaterialTheme.typography.labelLarge,
                                color = PinColors.InkGhost,
                                modifier = Modifier
                                    .clickable { viewModel.cancelReminder() }
                                    .padding(6.dp),
                            )
                        }
                    }
                }

                // ---- Original transcript ----------------------------------
                if (current.originalTranscript.isNotBlank() &&
                    current.originalTranscript != current.body
                ) {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel(stringResource(R.string.heard))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "“${current.originalTranscript}”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PinColors.InkGhost,
                    )
                }

                Spacer(Modifier.height(140.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = PinColors.SurfaceRaised,
            title = {
                Text(stringResource(R.string.delete_question), color = PinColors.Ink)
            },
            text = {
                Text(current.title, color = PinColors.InkFaint)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteNote()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PinColors.Danger,
                        contentColor = Color.White,
                    ),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = PinColors.InkFaint)
                }
            },
        )
    }

    if (showReminderPicker) {
        ReminderPickerDialog(
            initial = current.reminderAt?.let { TimeFormat.local(it) }
                ?: LocalDateTime.now().plusHours(1).withMinute(0),
            onConfirm = {
                setReminderWithPermission(it.ensureFuture())
                showReminderPicker = false
            },
            onDismiss = { showReminderPicker = false },
        )
    }
}

@Composable
private fun EditPane(
    initialTitle: String,
    initialBody: String,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var body by rememberSaveable { mutableStateOf(initialBody) }

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.title_label)) },
            modifier = Modifier.fillMaxWidth(),
            colors = editFieldColors(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text(stringResource(R.string.body_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            colors = editFieldColors(),
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = PinColors.InkFaint)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onSave(title, body) },
                enabled = body.isNotBlank() || title.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PinColors.Accent,
    unfocusedBorderColor = PinColors.Outline,
    cursorColor = PinColors.Accent,
    focusedLabelColor = PinColors.Accent,
    unfocusedLabelColor = PinColors.InkGhost,
)
