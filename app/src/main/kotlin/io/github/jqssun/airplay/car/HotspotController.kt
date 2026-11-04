package io.github.jqssun.airplay.car

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Read-only view of the head unit's hotspot. The hotspot itself is configured and switched in the
 * system settings, which is the only approach that works the same on every head unit.
 */
object HotspotController {
    enum class ApState { ON, OFF, TRANSITION, UNKNOWN }

    private val AP_IFACE = Regex("^(ap\\d+|swlan\\d+|softap\\d*|wlan[1-9]|wigig\\d+|uap\\d+)$")

    fun apState(ctx: Context): ApState {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // getWifiApState is not public SDK; treat any failure as unknown and fall back to interfaces
        val s = runCatching { wm.javaClass.getMethod("getWifiApState").invoke(wm) as Int }.getOrNull()
        return when (s) {
            13 -> ApState.ON
            11, 14 -> ApState.OFF
            10, 12 -> ApState.TRANSITION
            else -> if (hotspotAddresses().isNotEmpty()) ApState.ON else ApState.UNKNOWN
        }
    }

    /** IPv4 addresses on interfaces that look like a hotspot (ap0, swlan0, wlan1 ...). */
    fun hotspotAddresses(): List<Pair<String, String>> = allAddresses().filter { AP_IFACE.matches(it.first) }

    /** Every non-loopback IPv4 address, as (interface, address). */
    fun allAddresses(): List<Pair<String, String>> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { ni.name to it.hostAddress!! } }
    }.getOrDefault(emptyList())

    /** Opens the system hotspot page, falling back to broader settings screens on unusual ROMs. */
    fun openSystemSettings(ctx: Context) {
        val intents = listOf(
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$WifiTetherSettingsActivity")),
            Intent("android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in intents) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
            catch (_: ActivityNotFoundException) {} catch (_: SecurityException) {}
        }
    }

    fun openWifiSettings(ctx: Context) {
        try { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { openSystemSettings(ctx) }
    }
}
