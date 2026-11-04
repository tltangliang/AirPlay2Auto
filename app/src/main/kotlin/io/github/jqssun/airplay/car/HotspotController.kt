package io.github.jqssun.airplay.car

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.provider.Settings
import android.util.Log
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Car head-unit hotspot control.
 *
 * Android exposes no public API for a normal app to configure or toggle the tethering hotspot, so
 * this tries, in order, every route that is known to work on some firmware:
 *  - root shell (`cmd wifi start-softap`, Android 11+), when the user allows it and `su` exists;
 *  - Android 6.0-7.1: WifiManager.setWifiApConfiguration / setWifiApEnabled (needs WRITE_SETTINGS);
 *  - Android 8.0-10: IConnectivityManager.startTethering via reflection (needs WRITE_SETTINGS);
 *  - Android 11+: TetheringManager.startTethering via reflection (WRITE_SETTINGS or system app);
 *  - setting SSID/password on 8.0+ only works when the head unit grants this app system rights
 *    (OVERRIDE_WIFI_CONFIG, e.g. installed as a system / priv-app) or via root on 11+.
 * Everything else falls back to opening the system hotspot page.
 */
object HotspotController {
    private const val TAG = "CarHotspot"
    private const val TETHERING_WIFI = 0
    const val AP_STATE_DISABLED = 11
    const val AP_STATE_ENABLED = 13
    const val ACTION_AP_STATE = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    const val ACTION_TETHER_STATE = "android.net.conn.TETHER_STATE_CHANGED"

    data class Config(val ssid: String, val pass: String, val band5g: Boolean)
    data class Outcome(val ok: Boolean, val message: String)

    enum class ApState { ON, OFF, TRANSITION, UNKNOWN }

    private val main = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { main.post(it) }

    // ---------- hidden API access ----------

    @Volatile private var exempted = false

    /** Lift non-SDK restrictions for this process (Android 9+), so the reflection below can run. */
    fun exemptHiddenApis() {
        if (exempted || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        exempted = runCatching { org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("") }
            .onFailure { Log.w(TAG, "hidden api exemption failed", it) }
            .getOrDefault(false)
    }

    // ---------- state ----------

    fun canWriteSettings(ctx: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(ctx)

    private fun wifi(ctx: Context) = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun apState(ctx: Context): ApState {
        exemptHiddenApis()
        val s = runCatching {
            wifi(ctx).javaClass.getMethod("getWifiApState").invoke(wifi(ctx)) as Int
        }.getOrNull()
        if (s != null) return when (s) {
            AP_STATE_ENABLED -> ApState.ON
            AP_STATE_DISABLED, 14 -> ApState.OFF
            10, 12 -> ApState.TRANSITION
            else -> ApState.UNKNOWN
        }
        // fall back to looking for a softap interface with an address
        return if (hotspotAddresses().isNotEmpty()) ApState.ON else ApState.UNKNOWN
    }

    private val AP_IFACE = Regex("^(ap\\d+|swlan\\d+|softap\\d*|wlan[1-9]|wigig\\d+|uap\\d+)$")

    /** IPv4 addresses on interfaces that look like a hotspot (ap0, swlan0, wlan1 ...). */
    fun hotspotAddresses(): List<Pair<String, String>> = allAddresses().filter { AP_IFACE.matches(it.first) }

    /** Every non-loopback IPv4 address, as (interface, address). */
    fun allAddresses(): List<Pair<String, String>> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { ni.name to it.hostAddress!! } }
    }.getOrDefault(emptyList())

    /** Reads the saved system hotspot SSID/password where the firmware allows it. */
    @Suppress("DEPRECATION")
    fun readSystemConfig(ctx: Context): Config? {
        exemptHiddenApis()
        val wm = wifi(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val c = wm.javaClass.getMethod("getSoftApConfiguration").invoke(wm)!!
                val ssid = runCatching { c.javaClass.getMethod("getSsid").invoke(c) as String? }.getOrNull()
                val pass = runCatching { c.javaClass.getMethod("getPassphrase").invoke(c) as String? }.getOrNull()
                val band = runCatching { c.javaClass.getMethod("getBand").invoke(c) as Int }.getOrDefault(1)
                if (ssid != null) return Config(ssid.trim('"'), pass ?: "", band and 2 != 0 && band and 1 == 0)
            }
        }
        return runCatching {
            val c = wm.javaClass.getMethod("getWifiApConfiguration").invoke(wm) as WifiConfiguration
            val band = runCatching { WifiConfiguration::class.java.getField("apBand").getInt(c) }.getOrDefault(0)
            Config(c.SSID?.trim('"') ?: return null, c.preSharedKey ?: "", band == 1)
        }.getOrNull()
    }

    // ---------- configure ----------

    /** Saves SSID/password/band into the system hotspot configuration. */
    @Suppress("DEPRECATION")
    fun applyConfig(ctx: Context, cfg: Config, allowRoot: Boolean): Outcome {
        exemptHiddenApis()
        val errors = mutableListOf<String>()
        val wm = wifi(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val builderCls = Class.forName("android.net.wifi.SoftApConfiguration\$Builder")
                val builder = builderCls.getConstructor().newInstance()
                builderCls.getMethod("setSsid", String::class.java).invoke(builder, cfg.ssid)
                // SECURITY_TYPE_WPA2_PSK = 1
                builderCls.getMethod("setPassphrase", String::class.java, Int::class.javaPrimitiveType).invoke(builder, cfg.pass, 1)
                // BAND_2GHZ = 1, BAND_5GHZ = 2
                runCatching { builderCls.getMethod("setBand", Int::class.javaPrimitiveType).invoke(builder, if (cfg.band5g) 2 else 1) }
                val conf = builderCls.getMethod("build").invoke(builder)
                val ok = wm.javaClass.getMethod("setSoftApConfiguration", conf!!.javaClass).invoke(wm, conf) as Boolean
                if (ok) return Outcome(true, "已写入系统热点配置")
                errors += "setSoftApConfiguration 返回失败"
            } catch (e: Throwable) {
                errors += "setSoftApConfiguration: ${describe(e)}"
            }
            if (allowRoot && RootShell.available()) {
                return Outcome(true, "无系统权限写入配置；将通过 Root 以此配置开启热点")
            }
        } else {
            try {
                val c = WifiConfiguration().apply {
                    SSID = cfg.ssid
                    preSharedKey = cfg.pass
                    allowedKeyManagement.set(4) // WPA2_PSK
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    runCatching { WifiConfiguration::class.java.getField("apBand").setInt(this, if (cfg.band5g) 1 else 0) }
                }
                val ok = wm.javaClass.getMethod("setWifiApConfiguration", WifiConfiguration::class.java).invoke(wm, c) as Boolean
                if (ok) return Outcome(true, "已写入系统热点配置")
                errors += "setWifiApConfiguration 返回失败"
            } catch (e: Throwable) {
                errors += "setWifiApConfiguration: ${describe(e)}"
            }
        }
        Log.w(TAG, "applyConfig failed: $errors")
        return Outcome(false, "此车机不允许普通应用修改热点名称/密码（需要系统权限或 Root）。请在系统热点设置中填写相同信息。\n${errors.joinToString("; ")}")
    }

    // ---------- start / stop ----------

    fun start(ctx: Context, cfg: Config?, allowRoot: Boolean, done: (Outcome) -> Unit) {
        exemptHiddenApis()
        val errors = mutableListOf<String>()
        if (allowRoot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && cfg != null && RootShell.available()) {
            val band = if (cfg.band5g) "5" else "2"
            val r = RootShell.run("cmd wifi start-softap ${RootShell.quote(cfg.ssid)} wpa2 ${RootShell.quote(cfg.pass)} -b $band")
            if (r.ok) { done(Outcome(true, "已通过 Root 开启热点")); return }
            errors += "root: ${r.output.take(160)}"
        }
        if (!canWriteSettings(ctx)) errors += "未授予“修改系统设置”权限"
        val sdk = Build.VERSION.SDK_INT
        try {
            when {
                sdk >= Build.VERSION_CODES.R -> if (startTetheringModule(ctx, done)) return
                sdk >= Build.VERSION_CODES.O -> if (startTetheringLegacy(ctx, done)) return
                else -> {
                    if (startTetheringConnectivity24(ctx, done)) return
                    if (setWifiApEnabled(ctx, cfg, true)) { done(Outcome(true, "已开启热点")); return }
                }
            }
        } catch (e: Throwable) {
            errors += describe(e)
        }
        // last resort on any version: legacy toggle
        if (sdk < Build.VERSION_CODES.R && runCatching { setWifiApEnabled(ctx, cfg, true) }.getOrDefault(false)) {
            done(Outcome(true, "已开启热点")); return
        }
        done(Outcome(false, "无法自动开启热点：${errors.joinToString("; ")}\n请用“打开系统热点设置”手动开启。"))
    }

    fun stop(ctx: Context, allowRoot: Boolean): Outcome {
        exemptHiddenApis()
        val sdk = Build.VERSION.SDK_INT
        val errors = mutableListOf<String>()
        try {
            if (sdk >= Build.VERSION_CODES.R) {
                val tm = ctx.getSystemService("tethering")!!
                tm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType).invoke(tm, TETHERING_WIFI)
                return Outcome(true, "已关闭热点")
            } else if (sdk >= Build.VERSION_CODES.O) {
                val svc = connectivityBinder(ctx)
                runCatching {
                    svc.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType, String::class.java)
                        .invoke(svc, TETHERING_WIFI, ctx.packageName)
                }.recoverCatching {
                    svc.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType).invoke(svc, TETHERING_WIFI)
                }.getOrThrow()
                return Outcome(true, "已关闭热点")
            } else if (setWifiApEnabled(ctx, null, false)) return Outcome(true, "已关闭热点")
        } catch (e: Throwable) {
            errors += describe(e)
        }
        if (allowRoot && RootShell.available()) {
            val r = RootShell.run(if (sdk >= Build.VERSION_CODES.R) "cmd wifi stop-softap" else "svc wifi disable")
            if (r.ok) return Outcome(true, "已通过 Root 关闭热点")
        }
        return Outcome(false, "无法关闭热点：${errors.joinToString("; ")}")
    }

    @Suppress("DEPRECATION")
    private fun setWifiApEnabled(ctx: Context, cfg: Config?, enable: Boolean): Boolean {
        val wm = wifi(ctx)
        val conf = cfg?.let {
            WifiConfiguration().apply {
                SSID = it.ssid; preSharedKey = it.pass
                allowedKeyManagement.set(4)
                allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
            }
        }
        if (enable && wm.isWifiEnabled) runCatching { wm.isWifiEnabled = false }
        return wm.javaClass.getMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
            .invoke(wm, conf, enable) as Boolean
    }

    /** Android 7.x: ConnectivityManager.startTethering(int, boolean, OnStartTetheringCallback). */
    private fun startTetheringConnectivity24(ctx: Context, done: (Outcome) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return runCatching {
            val svc = connectivityBinder(ctx)
            svc.javaClass.getMethod("startTethering", Int::class.javaPrimitiveType, ResultReceiver::class.java,
                Boolean::class.javaPrimitiveType).invoke(svc, TETHERING_WIFI, resultReceiver(done), false)
            true
        }.getOrDefault(false)
    }

    /** Android 8.0-10: IConnectivityManager.startTethering(type, ResultReceiver, showUi[, callerPkg]). */
    private fun startTetheringLegacy(ctx: Context, done: (Outcome) -> Unit): Boolean {
        val svc = connectivityBinder(ctx)
        val owners = listOfNotNull(runCatching { Class.forName("android.net.IConnectivityManager") }.getOrNull(), svc.javaClass)
        val rr = resultReceiver(done)
        for (owner in owners) {
            runCatching {
                owner.getMethod("startTethering", Int::class.javaPrimitiveType, ResultReceiver::class.java,
                    Boolean::class.javaPrimitiveType, String::class.java)
                    .apply { isAccessible = true }.invoke(svc, TETHERING_WIFI, rr, false, ctx.packageName)
                return true
            }.onFailure { if (it is InvocationTargetException) throw it.targetException }
            runCatching {
                owner.getMethod("startTethering", Int::class.javaPrimitiveType, ResultReceiver::class.java,
                    Boolean::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(svc, TETHERING_WIFI, rr, false)
                return true
            }.onFailure { if (it is InvocationTargetException) throw it.targetException }
        }
        return false
    }

    /** Android 11+: TetheringManager.startTethering(TetheringRequest, Executor, StartTetheringCallback). */
    @SuppressLint("WrongConstant")
    private fun startTetheringModule(ctx: Context, done: (Outcome) -> Unit): Boolean {
        val tm = ctx.getSystemService("tethering") ?: return false
        val reqBuilderCls = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
        val builder = reqBuilderCls.getConstructor(Int::class.javaPrimitiveType).newInstance(TETHERING_WIFI)
        runCatching { reqBuilderCls.getMethod("setShouldShowEntitlementUi", Boolean::class.javaPrimitiveType).invoke(builder, false) }
        val request = reqBuilderCls.getMethod("build").invoke(builder)
        val cbCls = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
        val cb = Proxy.newProxyInstance(cbCls.classLoader, arrayOf(cbCls)) { _, m, args ->
            when (m.name) {
                "onTetheringStarted" -> done(Outcome(true, "已开启热点"))
                "onTetheringFailed" -> done(Outcome(false, "系统拒绝开启热点（错误码 ${args?.getOrNull(0)}）。请确认已授予“修改系统设置”，或手动开启。"))
                "hashCode" -> return@newProxyInstance System.identityHashCode(this)
                "equals" -> return@newProxyInstance args?.getOrNull(0) === this
                "toString" -> return@newProxyInstance "StartTetheringCallback"
            }
            null
        }
        tm.javaClass.getMethod("startTethering", request!!.javaClass, Executor::class.java, cbCls)
            .invoke(tm, request, mainExecutor, cb)
        return true
    }

    private fun resultReceiver(done: (Outcome) -> Unit) = object : ResultReceiver(main) {
        override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
            done(if (resultCode == 0) Outcome(true, "已开启热点")
                 else Outcome(false, "系统拒绝开启热点（错误码 $resultCode）。请确认已授予“修改系统设置”，或手动开启。"))
        }
    }

    private fun connectivityBinder(ctx: Context): Any {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        runCatching {
            val f = ConnectivityManager::class.java.getDeclaredField("mService").apply { isAccessible = true }
            f.get(cm)?.let { return it }
        }
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, Context.CONNECTIVITY_SERVICE) as IBinder
        return Class.forName("android.net.IConnectivityManager\$Stub").getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
    }

    // ---------- local-only hotspot ----------

    @Volatile private var lohReservation: WifiManager.LocalOnlyHotspotReservation? = null

    val localOnlyActive get() = lohReservation != null

    /** Temporary hotspot with system generated credentials; lives as long as this app process. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun startLocalOnly(ctx: Context, done: (Outcome) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { done(Outcome(false, "需要 Android 8.0 及以上")); return }
        if (lohReservation != null) { done(Outcome(true, describeLoh(lohReservation!!))); return }
        try {
            wifi(ctx).startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    lohReservation = reservation
                    done(Outcome(true, describeLoh(reservation)))
                }
                override fun onStopped() { lohReservation = null; done(Outcome(false, "临时热点已停止")) }
                override fun onFailed(reason: Int) {
                    done(Outcome(false, "临时热点开启失败（$reason）。若已开启系统热点，请先关闭；并确认已授予定位/附近设备权限。"))
                }
            }, main)
        } catch (e: Throwable) {
            done(Outcome(false, "临时热点开启失败：${describe(e)}"))
        }
    }

    fun stopLocalOnly() { lohReservation?.close(); lohReservation = null }

    @Suppress("DEPRECATION")
    private fun describeLoh(r: WifiManager.LocalOnlyHotspotReservation): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val c = r.softApConfiguration
            val ssid = if (Build.VERSION.SDK_INT >= 33) c.wifiSsid?.toString()?.trim('"') else c.ssid
            return "临时热点已开启\n名称：$ssid\n密码：${c.passphrase}"
        }
        val c = r.wifiConfiguration
        return "临时热点已开启\n名称：${c?.SSID}\n密码：${c?.preSharedKey}"
    }

    // ---------- system UI ----------

    fun openSystemSettings(ctx: Context) {
        val intents = listOf(
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$WifiTetherSettingsActivity")),
            Intent("android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in intents) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: ActivityNotFoundException) {} catch (_: SecurityException) {}
        }
    }

    fun openWriteSettingsPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun describe(e: Throwable): String {
        val t = (e as? InvocationTargetException)?.targetException ?: e
        return t.javaClass.simpleName + (t.message?.let { ": ${it.take(120)}" } ?: "")
    }
}

/** Minimal `su` helper for rooted head units. */
object RootShell {
    data class Result(val ok: Boolean, val output: String)

    @Volatile private var cached: Boolean? = null

    fun available(): Boolean = cached ?: (listOf("/system/xbin/su", "/system/bin/su", "/sbin/su", "/su/bin/su",
        "/system/sbin/su", "/vendor/bin/su", "/data/adb/magisk").any { File(it).exists() } && run("id").output.contains("uid=0"))
        .also { cached = it }

    fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    fun run(cmd: String): Result = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val finished = p.waitFor(8, TimeUnit.SECONDS)
        if (!finished) p.destroy()
        Result(finished && p.exitValue() == 0 && !out.contains("Exception", ignoreCase = false), out.trim())
    } catch (e: Exception) {
        Result(false, e.toString())
    }
}
