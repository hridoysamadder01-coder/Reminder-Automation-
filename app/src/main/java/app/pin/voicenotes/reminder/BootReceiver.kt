package app.pin.voicenotes.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pin.voicenotes.PinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alarms do not survive reboot; the notes do (Room). Re-arm every future
 * enabled reminder after boot, app update, or clock/timezone change, and mark
 * reminders that expired while the device was off as missed.
 */
class BootReceiver : BroadcastReceiver() {

    private val handledActions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in handledActions) return
        val repository = (context.applicationContext as PinApp).container.repository
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.restoreReminders()
            } finally {
                pending.finish()
            }
        }
    }
}
