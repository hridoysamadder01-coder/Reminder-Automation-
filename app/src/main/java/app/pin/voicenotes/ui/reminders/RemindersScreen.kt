package app.pin.voicenotes.ui.reminders

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pin.voicenotes.R
import app.pin.voicenotes.data.NoteEntity
import app.pin.voicenotes.data.ReminderStatus
import app.pin.voicenotes.reminder.NotificationHelper
import app.pin.voicenotes.ui.AppViewModelProvider
import app.pin.voicenotes.ui.TimeFormat
import app.pin.voicenotes.ui.components.EmptyState
import app.pin.voicenotes.ui.components.Hairline
import app.pin.voicenotes.ui.components.SectionLabel
import app.pin.voicenotes.ui.theme.PinColors
import app.pin.voicenotes.ui.theme.PinIcons

@Composable
fun RemindersScreen(
    onOpenNote: (Long) -> Unit,
    viewModel: RemindersViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Re-enabling a reminder may be the user's first — ask for notification
    // permission right here, mirroring the capture flow.
    var pendingEnable by remember { mutableStateOf<NoteEntity?>(null) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingEnable?.let { viewModel.setEnabled(it, true) }
        pendingEnable = null
    }
    val toggleWithPermission: (NoteEntity, Boolean) -> Unit = { note, enabled ->
        if (enabled && Build.VERSION.SDK_INT >= 33 &&
            !NotificationHelper.canPostNotifications(context)
        ) {
            pendingEnable = note
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setEnabled(note, enabled)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.reminders_title),
            style = MaterialTheme.typography.headlineSmall,
            color = PinColors.Ink,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )

        if (state.loaded && state.upcoming.isEmpty() && state.past.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.reminders_empty_title),
                subtitle = stringResource(R.string.reminders_empty_subtitle),
                examples = listOf("“কাল সকাল ১০টায় মনে করাইস”"),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.upcoming.isNotEmpty()) {
                    item {
                        SectionLabel(
                            stringResource(R.string.upcoming),
                            Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                        )
                    }
                    items(state.upcoming, key = { "u${it.id}" }) { note ->
                        ReminderRow(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            onToggle = { enabled -> toggleWithPermission(note, enabled) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
                if (state.past.isNotEmpty()) {
                    item {
                        SectionLabel(
                            stringResource(R.string.earlier),
                            Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                        )
                    }
                    items(state.past, key = { "d${it.id}" }) { note ->
                        Column {
                            Hairline()
                            PastReminderRow(note = note, onClick = { onOpenNote(note.id) })
                        }
                    }
                }
                item { Spacer(Modifier.height(140.dp)) }
            }
        }
    }
}

@Composable
private fun ReminderRow(
    note: NoteEntity,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (note.reminderEnabled) PinIcons.Bell else PinIcons.BellOff,
            contentDescription = null,
            tint = if (note.reminderEnabled) PinColors.Accent else PinColors.InkGhost,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (note.reminderEnabled) PinColors.Ink else PinColors.InkFaint,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = TimeFormat.reminderLabel(note.reminderAt ?: 0L),
                style = MaterialTheme.typography.bodySmall,
                color = if (note.reminderEnabled) PinColors.Accent else PinColors.InkGhost,
            )
        }
        Switch(
            checked = note.reminderEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PinColors.Accent,
                checkedThumbColor = PinColors.OnAccent,
                uncheckedTrackColor = PinColors.SurfaceHigh,
                uncheckedThumbColor = PinColors.InkGhost,
                uncheckedBorderColor = PinColors.Outline,
            ),
        )
    }
}

@Composable
private fun PastReminderRow(note: NoteEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            PinIcons.BellOff,
            contentDescription = null,
            tint = PinColors.InkGhost,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                color = PinColors.InkFaint,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = TimeFormat.full(note.reminderAt ?: 0L),
                style = MaterialTheme.typography.bodySmall,
                color = PinColors.InkGhost,
            )
        }
        Text(
            text = when (note.reminderStatus) {
                ReminderStatus.FIRED -> stringResource(R.string.status_done)
                ReminderStatus.MISSED -> stringResource(R.string.status_missed)
                ReminderStatus.CANCELLED -> stringResource(R.string.status_off)
                else -> ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (note.reminderStatus == ReminderStatus.MISSED) PinColors.Danger
            else PinColors.InkGhost,
        )
    }
}
