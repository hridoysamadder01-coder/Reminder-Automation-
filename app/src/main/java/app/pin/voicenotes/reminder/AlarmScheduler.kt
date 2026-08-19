package app.pin.voicenotes.reminder

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * AlarmManager দিয়ে রিমাইন্ডার বসানো/বাতিল। Android 12+-এ exact alarm
 * পারমিশন ইউজার কেড়ে নিলে ~১০ মিনিটের window-এ fallback — রিমাইন্ডার
 * হারায় না, শুধু সেকেন্ড-নিখুঁত থাকে না। প্রতিটা নোটের জন্য একটাই
 * PendingIntent (requestCode = নোটের id) — তাই একই নোটে নতুন সময় দিলে
 * পুরনো অ্যালার্ম নিজেই বদলে যায়, ডুপ্লিকেট বাজে না।
 */

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.pin.voicenotes.data.ReminderScheduling

/**
 * AlarmManager-backed reminder scheduling.
 *
 * Exact alarms: on Android 12+ SCHEDULE_EXACT_ALARM is user-revocable, so we
 * check [AlarmManager.canScheduleExactAlarms] at every schedule and fall back
 * to a short inexact window when not allowed — the reminder still arrives,
 * just not to the second. setExactAndAllowWhileIdle keeps Doze from holding
 * the reminder back.
 */
class AlarmScheduler(private val context: Context) : ReminderScheduling {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(noteId: Long, atEpochMillis: Long) {
        val pi = pendingIntent(noteId)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMillis, pi)
        } else {
            // ~10 minute delivery window when exact access is off.
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                atEpochMillis,
                10 * 60 * 1000L,
                pi
            )
        }
    }

    override fun cancel(noteId: Long) {
        alarmManager.cancel(pendingIntent(noteId))
    }

    override fun dismissNotification(noteId: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        nm.cancel(noteId.toInt())
    }

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private fun pendingIntent(noteId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
            putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId)
        }
        // One PendingIntent per note: same request code + UPDATE_CURRENT means
        // re-scheduling a note's reminder replaces the old alarm (no duplicates).
        return PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
