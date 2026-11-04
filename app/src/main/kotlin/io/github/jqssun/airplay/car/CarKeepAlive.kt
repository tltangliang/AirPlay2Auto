package io.github.jqssun.airplay.car

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.jqssun.airplay.MainActivity
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.get
import io.github.jqssun.airplay.service.AirPlayService

object CarKeepAlive {
    private const val TAG = "CarKeepAlive"
    private const val KEY_USER_STOPPED = "car_user_stopped"
    const val ACTION_WATCHDOG = "io.github.carairplay.WATCHDOG"
    private const val WATCHDOG_INTERVAL_MS = 10 * 60 * 1000L

    /** ACC-on style broadcasts seen on Chinese head units; harmless where unused. */
    val BUILTIN_ACC_ACTIONS = listOf(
        "android.intent.action.ACC_ON",
        "android.intent.action.ACTION_ACC_ON",
        "com.android.action.ACC_ON",
        "com.fyt.boot.ACCON",
        "com.microntek.bootcheck",
        "com.ts.main.uiaccon",
        "com.nwd.action.ACTION_ACC_ON",
        "autochips.intent.action.QB_POWERON",
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON",
    )

    fun accActions(prefs: SharedPreferences): List<String> =
        BUILTIN_ACC_ACTIONS + prefs[Prefs.CUSTOM_ACC_ACTIONS].split(',', '，', ';', '\n')
            .map { it.trim() }.filter { it.isNotEmpty() }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    fun userStopped(ctx: Context) = prefs(ctx).getBoolean(KEY_USER_STOPPED, false)
    fun setUserStopped(ctx: Context, v: Boolean) = prefs(ctx).edit { putBoolean(KEY_USER_STOPPED, v) }

    /** Start the receiver service from a broadcast context. */
    fun startService(ctx: Context, delaySec: Int, fromBoot: Boolean) {
        val i = Intent(ctx, AirPlayService::class.java)
            .setAction(AirPlayService.ACTION_START_SERVER)
            .putExtra(AirPlayService.EXTRA_DELAY_S, delaySec)
            .putExtra(AirPlayService.EXTRA_FROM_BOOT, fromBoot)
        try {
            ContextCompat.startForegroundService(ctx, i)
        } catch (e: Exception) {
            // Android 12+ may refuse a background FGS start outside of exempt broadcasts
            Log.w(TAG, "cannot start service: $e")
        }
    }

    fun launchUi(ctx: Context) {
        val p = prefs(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(ctx)) {
            Log.w(TAG, "no overlay permission, cannot open UI from background")
            return
        }
        try {
            ctx.startActivity(Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(MainActivity.EXTRA_BOOT_LAUNCH, p[Prefs.BOOT_LAUNCH_BACKGROUND]))
        } catch (e: Exception) {
            Log.w(TAG, "cannot open UI: $e")
        }
    }

    /** Inexact repeating alarm that re-launches the receiver if the system killed it. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, AutoStartReceiver::class.java).setAction(ACTION_WATCHDOG),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (!prefs(ctx)[Prefs.KEEP_ALIVE]) { am.cancel(pi); return }
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS, WATCHDOG_INTERVAL_MS, pi)
    }

    fun ignoringBatteryOptimizations(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    @android.annotation.SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intents = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
        )
        for (i in intents) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: ActivityNotFoundException) {} catch (_: SecurityException) {}
        }
    }

    fun openOverlaySettings(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun canDrawOverlays(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)
}
