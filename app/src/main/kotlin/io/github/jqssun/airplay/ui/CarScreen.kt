package io.github.jqssun.airplay.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.appSettingsIntent
import io.github.jqssun.airplay.car.CarKeepAlive
import io.github.jqssun.airplay.car.HotspotController
import io.github.jqssun.airplay.car.RootShell
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarScreen(viewModel: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val ssidPref by viewModel.pref(Prefs.HOTSPOT_SSID).collectAsState()
    val passPref by viewModel.pref(Prefs.HOTSPOT_PASS).collectAsState()
    val band5g by viewModel.pref(Prefs.HOTSPOT_5G).collectAsState()
    val hotspotAuto by viewModel.pref(Prefs.HOTSPOT_AUTO_ENABLE).collectAsState()
    val applyConfig by viewModel.pref(Prefs.HOTSPOT_APPLY_CONFIG).collectAsState()
    val useRoot by viewModel.pref(Prefs.HOTSPOT_USE_ROOT).collectAsState()
    val bootStart by viewModel.pref(Prefs.BOOT_AUTO_START).collectAsState()
    val bootDelay by viewModel.pref(Prefs.BOOT_DELAY_S).collectAsState()
    val bootUi by viewModel.pref(Prefs.BOOT_LAUNCH_UI).collectAsState()
    val bootBg by viewModel.pref(Prefs.BOOT_LAUNCH_BACKGROUND).collectAsState()
    val accWake by viewModel.pref(Prefs.ACC_WAKE_START).collectAsState()
    val accActions by viewModel.pref(Prefs.CUSTOM_ACC_ACTIONS).collectAsState()
    val keepAlive by viewModel.pref(Prefs.KEEP_ALIVE).collectAsState()
    val readvertise by viewModel.pref(Prefs.READVERTISE_ON_NETWORK).collectAsState()
    val appAutoStart by viewModel.pref(Prefs.AUTO_START).collectAsState()
    val launchOnConnect by viewModel.pref(Prefs.LAUNCH_ON_CONNECT).collectAsState()
    val keepScreenOn by viewModel.pref(Prefs.KEEP_SCREEN_ON).collectAsState()
    val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()

    var ssid by remember(ssidPref) { mutableStateOf(ssidPref) }
    var pass by remember(passPref) { mutableStateOf(passPref) }
    var showPass by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // live status, refreshed periodically and on resume
    var apState by remember { mutableStateOf(HotspotController.ApState.UNKNOWN) }
    var addresses by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var canWrite by remember { mutableStateOf(HotspotController.canWriteSettings(ctx)) }
    var canOverlay by remember { mutableStateOf(CarKeepAlive.canDrawOverlays(ctx)) }
    var batteryOk by remember { mutableStateOf(CarKeepAlive.ignoringBatteryOptimizations(ctx)) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(tick) {
        while (true) {
            val (s, a) = withContext(Dispatchers.IO) { HotspotController.apState(ctx) to HotspotController.allAddresses() }
            apState = s; addresses = a
            canWrite = HotspotController.canWriteSettings(ctx)
            canOverlay = CarKeepAlive.canDrawOverlays(ctx)
            batteryOk = CarKeepAlive.ignoringBatteryOptimizations(ctx)
            delay(3000)
        }
    }

    fun cfg() = HotspotController.Config(ssid.trim(), pass, band5g)
    fun validate(): String? = when {
        ssid.isBlank() -> "热点名称不能为空"
        ssid.toByteArray().size > 32 -> "热点名称过长（最多 32 字节）"
        pass.length !in 8..63 -> "密码需 8~63 位"
        else -> null
    }
    fun save(): Boolean {
        validate()?.let { message = it; return false }
        viewModel.set(Prefs.HOTSPOT_SSID, ssid.trim())
        viewModel.set(Prefs.HOTSPOT_PASS, pass)
        return true
    }

    val locationPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        HotspotController.startLocalOnly(ctx) { message = it.message; tick++ }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)
    ) {
        // ---------- status ----------
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiTethering, null)
                    Spacer(Modifier.width(8.dp))
                    Text("车机热点：" + when (apState) {
                        HotspotController.ApState.ON -> "已开启"
                        HotspotController.ApState.OFF -> "已关闭"
                        HotspotController.ApState.TRANSITION -> "切换中…"
                        HotspotController.ApState.UNKNOWN -> "未知"
                    } + if (HotspotController.localOnlyActive) "（临时热点运行中）" else "",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text("AirPlay 名称：$serverName", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (addresses.isEmpty()) "当前没有可用的网络地址（请开启热点或连接 Wi-Fi）"
                    else "网络地址：" + addresses.joinToString("  ") { "${it.first} ${it.second}" },
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text("用法：手机连接车机热点（或与车机连同一 Wi-Fi）→ 控制中心 → 屏幕镜像 / AirPlay → 选择“$serverName”。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---------- hotspot ----------
        CarHeader("热点设置")
        if (!canWrite) {
            PermissionRow("需要“修改系统设置”权限", "用于自动开关车机热点（Android 6.0+）", "去授权") {
                HotspotController.openWriteSettingsPermission(ctx)
            }
        }
        OutlinedTextField(
            value = ssid, onValueChange = { ssid = it }, singleLine = true,
            label = { Text("热点名称 (SSID)") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it }, singleLine = true,
            label = { Text("热点密码（8~63 位）") },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        CarSwitch("使用 5GHz 频段", "画面更流畅；部分车机或旧手机不支持 5GHz 时请关闭", band5g) {
            viewModel.set(Prefs.HOTSPOT_5G, it)
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(enabled = !busy, onClick = {
                if (!save()) return@Button
                busy = true
                scope.launch {
                    val c = cfg()
                    val msgs = mutableListOf<String>()
                    withContext(Dispatchers.IO) {
                        if (applyConfig) msgs += HotspotController.applyConfig(ctx, c, useRoot).message
                        if (HotspotController.apState(ctx) == HotspotController.ApState.ON) {
                            // restart so a new SSID/password takes effect
                            HotspotController.stop(ctx, useRoot)
                            delay(2000)
                        }
                    }
                    withContext(Dispatchers.IO) {
                        HotspotController.start(ctx, c, useRoot) { r ->
                            msgs += r.message
                            message = msgs.joinToString("\n")
                            busy = false
                            tick++
                        }
                    }
                    if (busy) { delay(8000); if (busy) { busy = false; message = (msgs + "已发送开启请求，请查看状态").joinToString("\n") } }
                }
            }) {
                Icon(Icons.Default.WifiTethering, null); Spacer(Modifier.width(6.dp)); Text("保存并开启热点")
            }
            OutlinedButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    message = withContext(Dispatchers.IO) { HotspotController.stop(ctx, useRoot).message }
                    busy = false; tick++
                }
            }) { Text("关闭热点") }
            OutlinedButton(onClick = {
                if (save()) scope.launch {
                    message = withContext(Dispatchers.IO) { HotspotController.applyConfig(ctx, cfg(), useRoot).message }
                }
            }) { Text("仅保存配置") }
            OutlinedButton(onClick = {
                scope.launch {
                    val c = withContext(Dispatchers.IO) { HotspotController.readSystemConfig(ctx) }
                    if (c == null) message = "无法读取系统热点配置（此系统不允许）"
                    else {
                        ssid = c.ssid; if (c.pass.isNotEmpty()) pass = c.pass
                        message = "已读取系统热点配置：${c.ssid}"
                    }
                }
            }) { Text("读取系统配置") }
            OutlinedButton(onClick = { HotspotController.openSystemSettings(ctx) }) {
                Icon(Icons.Default.Settings, null); Spacer(Modifier.width(6.dp)); Text("系统热点设置")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!HotspotController.localOnlyActive) {
                    OutlinedButton(onClick = {
                        val perms = if (Build.VERSION.SDK_INT >= 33)
                            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION)
                        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        locationPerms.launch(perms)
                    }) { Text("开启临时热点（随机名称和密码）") }
                } else {
                    OutlinedButton(onClick = { HotspotController.stopLocalOnly(); message = "临时热点已关闭"; tick++ }) {
                        Text("关闭临时热点")
                    }
                }
            }
        }

        message?.let {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
        }

        CarSwitch("启动时自动开启热点", "开机 / ACC 上电自动启动接收器时，同时开启车机热点", hotspotAuto) {
            viewModel.set(Prefs.HOTSPOT_AUTO_ENABLE, it)
        }
        CarSwitch("开启前写入名称和密码", "把上面的名称/密码写入系统热点（需系统权限或 Root，否则使用系统里已保存的热点）", applyConfig) {
            viewModel.set(Prefs.HOTSPOT_APPLY_CONFIG, it)
        }
        CarSwitch(
            "使用 Root（已 Root 的车机）",
            when (rootOk) { true -> "已检测到 Root"; false -> "未检测到 Root 权限"; null -> "Android 11+ 可通过 Root 设置并开启热点" },
            useRoot
        ) { on ->
            viewModel.set(Prefs.HOTSPOT_USE_ROOT, on)
            if (on) scope.launch { rootOk = withContext(Dispatchers.IO) { RootShell.available() } }
        }

        // ---------- autostart ----------
        CarHeader("自动启动")
        CarSwitch("开机 / ACC 上电自动启动", "车机开机或点火后自动在后台开启 AirPlay 接收", bootStart) {
            viewModel.set(Prefs.BOOT_AUTO_START, it)
        }
        DelayField(bootDelay) { viewModel.set(Prefs.BOOT_DELAY_S, it) }
        CarSwitch("开机后打开本应用界面", if (canOverlay) "开机自动打开界面" else "需要“显示在其他应用上层”权限（点击开关去授权）", bootUi && canOverlay) {
            viewModel.set(Prefs.BOOT_LAUNCH_UI, it)
            if (it && !canOverlay) CarKeepAlive.openOverlaySettings(ctx)
        }
        if (bootUi) CarSwitch("打开后自动退到后台", "开机打开界面 2.5 秒后返回桌面，不影响导航", bootBg) {
            viewModel.set(Prefs.BOOT_LAUNCH_BACKGROUND, it)
        }
        CarSwitch("打开应用时自动开始接收", "进入本应用即启动 AirPlay 服务", appAutoStart) {
            viewModel.set(Prefs.AUTO_START, it)
        }
        CarSwitch("手机连接时自动弹出画面", if (canOverlay) "iPhone 投屏时自动切到全屏画面" else "需要“显示在其他应用上层”权限（点击开关去授权）", launchOnConnect && canOverlay) {
            viewModel.set(Prefs.LAUNCH_ON_CONNECT, it)
            if (it && !canOverlay) CarKeepAlive.openOverlaySettings(ctx)
        }
        CarSwitch("休眠唤醒后自动恢复", "适配“假关机/休眠”车机：亮屏、解锁、ACC 广播时检查并恢复接收", accWake) {
            viewModel.set(Prefs.ACC_WAKE_START, it)
        }
        AccActionsField(accActions) { viewModel.set(Prefs.CUSTOM_ACC_ACTIONS, it) }
        CarSwitch("后台保活", "前台服务 + 定时检查，被系统清理后自动重新拉起", keepAlive) {
            viewModel.set(Prefs.KEEP_ALIVE, it)
            CarKeepAlive.schedule(ctx)
        }
        CarSwitch("网络变化时重新广播", "热点开启 / Wi-Fi 切换后自动重新发布 AirPlay，避免手机搜不到", readvertise) {
            viewModel.set(Prefs.READVERTISE_ON_NETWORK, it)
        }
        CarSwitch("投屏时屏幕常亮", "防止车机投屏时熄屏", keepScreenOn) {
            viewModel.set(Prefs.KEEP_SCREEN_ON, it)
        }
        if (!batteryOk) {
            PermissionRow("建议关闭电池优化", "防止车机系统在后台杀掉接收器", "去设置") {
                CarKeepAlive.requestIgnoreBatteryOptimizations(ctx)
            }
        }
        PermissionRow("自启动 / 后台运行权限", "部分车机系统（如定制 ROM）需在应用详情或“自启动管理”中允许", "应用详情") {
            ctx.startActivity(ctx.appSettingsIntent().addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CarHeader(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun CarSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.dpadFocus(RectangleShape).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) }
    )
}

@Composable
private fun PermissionRow(title: String, description: String, action: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.tertiary) },
        trailingContent = { FilledTonalButton(onClick = onClick) { Text(action) } }
    )
}

@Composable
private fun DelayField(value: Int, onCommit: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("启动延迟（秒）")
            Text("等待车机 Wi-Fi/热点就绪后再启动，默认 8 秒", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = text, singleLine = true,
            onValueChange = { t -> text = t.filter(Char::isDigit).take(3); text.toIntOrNull()?.let { if (it in 0..300) onCommit(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp)
        )
    }
}

@Composable
private fun AccActionsField(value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, singleLine = false,
        label = { Text("自定义 ACC 上电广播（可选，多个用逗号分隔）") },
        placeholder = { Text("例如 com.xxx.action.ACC_ON") },
        trailingIcon = { TextButton(onClick = { onCommit(text.trim()) }) { Text("保存") } },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

