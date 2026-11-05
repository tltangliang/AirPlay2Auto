package io.github.jqssun.airplay.car

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.jqssun.airplay.hasLocalNetwork

/**
 * Shortcuts into the Android system settings.
 *
 * Head units often route the standard settings intents to the vendor's own (reduced) car settings
 * app, so every shortcut first tries the native AOSP settings package `com.android.settings`, then the
 * plain intent, then broader fallbacks.
 */
object SystemSettings {
    private const val NATIVE = "com.android.settings"

    /** Tries each intent in turn; returns true once one opened. */
    fun open(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                ctx.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }
        Toast.makeText(ctx, "此车机未提供该设置页面", Toast.LENGTH_SHORT).show()
        return false
    }

    /** The intent aimed at the native settings package first, then whichever app handles it. */
    private fun native(i: Intent): Array<Intent> = arrayOf(Intent(i).setPackage(NATIVE), i)

    private fun action(a: String, data: Uri? = null) = Intent(a).apply { if (data != null) this.data = data }

    private fun pkgUri(ctx: Context) = Uri.fromParts("package", ctx.packageName, null)

    private fun component(cls: String) = Intent().setComponent(ComponentName(NATIVE, cls))

    // ---------- per-app pages ----------

    fun appDetails(ctx: Context) = open(ctx,
        *native(action(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx))),
        action(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        action(Settings.ACTION_SETTINGS),
    )

    fun notifications(ctx: Context): Boolean {
        val list = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            list += native(action(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
        // Android 5-7 used these extras on the same screen
        list += Intent("android.settings.APP_NOTIFICATION_SETTINGS")
            .putExtra("app_package", ctx.packageName)
            .putExtra("app_uid", ctx.applicationInfo.uid)
        list += native(action(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx)))
        return open(ctx, *list.toTypedArray())
    }

    fun overlay(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return appDetails(ctx)
        return open(ctx,
            *native(action(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))),
            *native(action(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)),
            *native(action(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx))),
        )
    }

    @android.annotation.SuppressLint("BatteryLife")
    fun batteryOptimization(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return appDetails(ctx)
        return open(ctx,
            action(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
            *native(action(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)),
            *native(action(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx))),
        )
    }

    fun batteryOptimizationList(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return appDetails(ctx)
        return open(ctx, *native(action(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)), action(Settings.ACTION_SETTINGS))
    }

    /** Vendor autostart managers (phone-derived car ROMs ship some of these); else the app page. */
    fun autostart(ctx: Context): Boolean {
        val vendor = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
            "com.meizu.safe" to "com.meizu.safe.security.SHOW_APPSEC",
        ).map { (p, c) -> Intent().setComponent(ComponentName(p, c)) }
        return open(ctx, *vendor.toTypedArray(),
            *native(action(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx))))
    }

    // ---------- native settings menus ----------

    data class Entry(val title: String, val open: (Context) -> Boolean)

    val nativeMenus: List<Entry> = listOf(
        Entry("原生设置主页") { open(it, component("$NATIVE.Settings"), *native(action(Settings.ACTION_SETTINGS))) },
        Entry("WLAN") { open(it, *native(action(Settings.ACTION_WIFI_SETTINGS)), component("$NATIVE.Settings\$WifiSettingsActivity")) },
        Entry("热点与网络共享") { HotspotController.openSystemSettings(it); true },
        Entry("蓝牙") { open(it, *native(action(Settings.ACTION_BLUETOOTH_SETTINGS))) },
        Entry("显示") { open(it, *native(action(Settings.ACTION_DISPLAY_SETTINGS))) },
        Entry("声音") { open(it, *native(action(Settings.ACTION_SOUND_SETTINGS))) },
        Entry("应用管理") {
            open(it, *native(action(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)), *native(action(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)))
        },
        Entry("悬浮窗权限管理") { open(it, *native(action(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))) },
        Entry("电池优化名单") { batteryOptimizationList(it) },
        Entry("语言和输入法") { open(it, *native(action(Settings.ACTION_LOCALE_SETTINGS)), *native(action(Settings.ACTION_INPUT_METHOD_SETTINGS))) },
        Entry("日期和时间") { open(it, *native(action(Settings.ACTION_DATE_SETTINGS))) },
        Entry("开发者选项") { open(it, *native(action(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)), component("$NATIVE.DevelopmentSettings")) },
        Entry("关于设备") { open(it, *native(action(Settings.ACTION_DEVICE_INFO_SETTINGS))) },
    )

    // ---------- status ----------

    fun notificationsEnabled(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Android 13+ asks for notifications at runtime; older systems only allow the settings toggle. */
    val notificationsRuntime get() = Build.VERSION.SDK_INT >= 33

    /** Local network access became a runtime permission in Android 17. */
    val localNetworkRuntime get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

    fun localNetworkGranted(ctx: Context) = ctx.hasLocalNetwork()

    fun overlayGranted(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)

    fun batteryUnrestricted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    }
}
