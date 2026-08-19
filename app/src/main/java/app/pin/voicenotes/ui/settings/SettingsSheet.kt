package app.pin.voicenotes.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pin.voicenotes.PinApp
import app.pin.voicenotes.R
import app.pin.voicenotes.reminder.NotificationHelper
import app.pin.voicenotes.ui.theme.PinColors
import app.pin.voicenotes.ui.theme.PinIcons

/**
 * Tiny by design: about text, truthful privacy wording, and a plain-language
 * capability readout that helps debug different Android phones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PinApp
    val speech = app.container.speech

    val voiceAvailable = speech.isRecognitionAvailable()
    val onDeviceAvailable = speech.isOnDeviceAvailable()
    val notificationsAllowed = NotificationHelper.canPostNotifications(context)
    val exactAllowed = app.container.scheduler.canScheduleExact()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PinColors.SurfaceRaised,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    PinIcons.Pin,
                    contentDescription = null,
                    tint = PinColors.Accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = PinColors.Ink,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = PinColors.InkGhost,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.privacy_statement),
                style = MaterialTheme.typography.bodyMedium,
                color = PinColors.InkFaint,
            )

            Spacer(Modifier.height(20.dp))
            StatusRow(
                label = stringResource(R.string.status_voice),
                ok = voiceAvailable,
                okText = stringResource(R.string.available),
                badText = stringResource(R.string.unavailable),
            )
            StatusRow(
                label = stringResource(R.string.status_on_device),
                ok = onDeviceAvailable,
                okText = stringResource(R.string.available),
                badText = stringResource(R.string.not_available),
            )
            StatusRow(
                label = stringResource(R.string.status_notifications),
                ok = notificationsAllowed,
                okText = stringResource(R.string.allowed),
                badText = stringResource(R.string.not_allowed),
            )
            StatusRow(
                label = stringResource(R.string.status_exact),
                ok = exactAllowed,
                okText = stringResource(R.string.allowed),
                badText = stringResource(R.string.limited),
            )

            if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                TextButton(onClick = { openExactAlarmSettings(context) }) {
                    Text(
                        stringResource(R.string.allow_exact_reminders),
                        color = PinColors.Accent,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.version_line),
                style = MaterialTheme.typography.bodySmall,
                color = PinColors.InkGhost,
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, okText: String, badText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = PinColors.Ink,
            modifier = Modifier.width(0.dp).weight(1f),
        )
        Text(
            text = if (ok) okText else badText,
            style = MaterialTheme.typography.labelLarge,
            color = if (ok) PinColors.Success else PinColors.InkGhost,
        )
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val hasAlarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager != null
        if (hasAlarmManager) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
            )
        }
    }
}
