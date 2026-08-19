package app.pin.voicenotes.reminder

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * রিমাইন্ডার নোটিফিকেশন: high-importance চ্যানেল, সাউন্ড + ভাইব্রেশন।
 * নোটিফিকেশনে ট্যাপ করলে MainActivity-তে নোটের id পাঠানো হয় —
 * ঠিক সেই নোটটাই খোলে, অ্যাপ বন্ধ/ব্যাকগ্রাউন্ড/খোলা যে অবস্থাতেই থাকুক।
 */

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.pin.voicenotes.MainActivity
import app.pin.voicenotes.R
import app.pin.voicenotes.data.NoteEntity

object NotificationHelper {

    const val CHANNEL_REMINDERS = "reminders"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_REMINDERS) != null) return

        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun showReminder(context: Context, note: NoteEntity) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)

        // Tapping the notification opens exactly this note. singleTask activity
        // + intent extra keeps one navigation stack in every app state.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_NOTE
            putExtra(MainActivity.EXTRA_NOTE_ID, note.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            note.id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle(note.title)
            .setContentText(note.body.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.body.take(600)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(note.id.toInt(), notification)
    }
}
