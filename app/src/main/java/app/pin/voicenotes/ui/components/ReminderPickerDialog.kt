package app.pin.voicenotes.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.pin.voicenotes.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Two-step reminder picker: date, then time. Confirms with a full
 * [LocalDateTime] so callers never juggle partial state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPickerDialog(
    initial: LocalDateTime,
    onConfirm: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val step = remember { mutableStateOf(0) }
    val pickedDate = remember { mutableStateOf(initial.toLocalDate()) }

    if (step.value == 0) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initial.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val millis = dateState.selectedDateMillis
                    if (millis != null) {
                        pickedDate.value = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    step.value = 1
                }) { Text(stringResource(R.string.next)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.pick_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(
                        LocalDateTime.of(
                            pickedDate.value,
                            LocalTime.of(timeState.hour, timeState.minute)
                        )
                    )
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = { step.value = 0 }) { Text(stringResource(R.string.back)) }
            },
        )
    }
}

/** Simple date guard so a picked moment in the past rolls to the next day. */
fun LocalDateTime.ensureFuture(): LocalDateTime =
    if (isAfter(LocalDateTime.now())) this
    else LocalDateTime.of(LocalDate.now().plusDays(1), toLocalTime())
