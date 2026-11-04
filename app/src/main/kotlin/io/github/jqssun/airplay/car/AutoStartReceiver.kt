package io.github.jqssun.airplay.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.get

/**
 * Starts the AirPlay receiver on boot, quick-boot / ACC-on, app update, and from the keep-alive
 * watchdog alarm.
 */
class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        Log.i("AutoStartReceiver", "received $action")
        if (!prefs[Prefs.BOOT_AUTO_START]) return

        val isBoot = action != CarKeepAlive.ACTION_WATCHDOG && action != Intent.ACTION_MY_PACKAGE_REPLACED
        if (isBoot) CarKeepAlive.setUserStopped(context, false)
        if (CarKeepAlive.userStopped(context)) return
        if (action == CarKeepAlive.ACTION_WATCHDOG && !prefs[Prefs.KEEP_ALIVE]) return

        CarKeepAlive.startService(context, if (isBoot) prefs[Prefs.BOOT_DELAY_S] else 0, fromBoot = isBoot)
        if (isBoot && prefs[Prefs.BOOT_LAUNCH_UI]) CarKeepAlive.launchUi(context)
        CarKeepAlive.schedule(context)
    }
}
