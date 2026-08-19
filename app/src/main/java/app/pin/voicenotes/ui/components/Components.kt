package app.pin.voicenotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pin.voicenotes.R
import app.pin.voicenotes.data.NoteEntity
import app.pin.voicenotes.ui.TimeFormat
import app.pin.voicenotes.ui.theme.PinColors
import app.pin.voicenotes.ui.theme.PinIcons

/** Small uppercase section label — the app's quiet way of grouping content. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = PinColors.InkGhost,
        modifier = modifier,
    )
}

/**
 * The standard note row: pin state, title, body preview, reminder chip,
 * compact timestamp. Used on Home, Notes and search results.
 */
@Composable
fun NoteRow(
    note: NoteEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTogglePin: (() -> Unit)? = null,
    showBody: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isPinned) {
                    Icon(
                        imageVector = PinIcons.Pin,
                        contentDescription = stringResource(R.string.pinned),
                        tint = PinColors.Accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PinColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showBody && note.body.isNotBlank() && note.body != note.title) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = note.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PinColors.InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.reminderAt != null && note.reminderEnabled) {
                Spacer(Modifier.height(6.dp))
                ReminderChip(note.reminderAt)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = TimeFormat.compact(note.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = PinColors.InkGhost,
            )
            if (onTogglePin != null) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTogglePin()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (note.isPinned) PinIcons.Pin else PinIcons.PinOutline,
                        contentDescription = stringResource(
                            if (note.isPinned) R.string.unpin else R.string.pin
                        ),
                        tint = if (note.isPinned) PinColors.Accent else PinColors.InkGhost,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderChip(reminderAt: Long, modifier: Modifier = Modifier, fired: Boolean = false) {
    val tint = if (fired) PinColors.InkGhost else PinColors.Accent
    Row(
        modifier = modifier
            .background(
                color = tint.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = PinIcons.Bell,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = TimeFormat.reminderLabel(reminderAt),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

/** Full-bleed hairline between list rows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 20.dp)
            .background(PinColors.Outline.copy(alpha = 0.6f))
    )
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    examples: List<String> = emptyList(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = PinColors.Ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = PinColors.InkFaint,
        )
        if (examples.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            examples.forEach { example ->
                Surface(
                    color = PinColors.Surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = example },
                ) {
                    Text(
                        text = example,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PinColors.InkFaint,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

