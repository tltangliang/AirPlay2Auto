package io.github.jqssun.airplay.ui

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.car.CarKeepAlive
import io.github.jqssun.airplay.car.SystemSettings
import io.github.jqssun.airplay.car.HotspotController
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarScreen(viewModel: MainViewModel) {
    val ctx = LocalContext.current

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
    val musicAutoFs by viewModel.pref(Prefs.MUSIC_AUTO_FULLSCREEN).collectAsState()
    val mirrorAutoFs by viewModel.pref(Prefs.AUTO_FULLSCREEN).collectAsState()
    val mirrorScale by viewModel.pref(Prefs.MIRROR_SCALE).collectAsState()
    val pipEnabled by viewModel.pref(Prefs.PIP_ENABLED).collectAsState()
    val uiScale by viewModel.pref(Prefs.UI_SCALE).collectAsState()
    val insetTop by viewModel.pref(Prefs.INSET_TOP_DP).collectAsState()
    val insetBottom by viewModel.pref(Prefs.INSET_BOTTOM_DP).collectAsState()
    val musicBuffer by viewModel.pref(Prefs.MUSIC_BUFFER_MS).collectAsState()
    val fixedVolume by viewModel.pref(Prefs.FIXED_VOLUME).collectAsState()
    val advMirroring by viewModel.pref(Prefs.ADVERTISE_MIRRORING).collectAsState()
    val advVideo by viewModel.pref(Prefs.ADVERTISE_VIDEO).collectAsState()
    val autoScale = autoUiScalePercent(androidx.compose.ui.platform.LocalConfiguration.current)


    // live status, refreshed periodically and on resume
    var apState by remember { mutableStateOf(HotspotController.ApState.UNKNOWN) }
    var addresses by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var canOverlay by remember { mutableStateOf(CarKeepAlive.canDrawOverlays(ctx)) }
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
            canOverlay = CarKeepAlive.canDrawOverlays(ctx)
            delay(3000)
        }
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
                    },
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

        // ---------- permissions ----------
        CarHeader("系统权限")
        PermissionSection()

        // ---------- hotspot (system settings only) ----------
        CarHeader("车机热点")
        Text(
            "热点名称、密码和开关请在车机系统设置中完成。热点开启后，本应用会自动在热点上重新广播 AirPlay。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { HotspotController.openSystemSettings(ctx) }) {
                Icon(Icons.Default.WifiTethering, null); Spacer(Modifier.width(6.dp)); Text("打开系统热点设置")
            }
            OutlinedButton(onClick = { HotspotController.openWifiSettings(ctx) }) {
                Icon(Icons.Default.Wifi, null); Spacer(Modifier.width(6.dp)); Text("Wi-Fi 设置")
            }
        }

        // ---------- receiver features ----------
        CarHeader("接收功能")
        ListItem(
            headlineContent = { Text("AirPlay 音乐") },
            supportingContent = { Text("始终开启：在手机的 AirPlay 音频输出中选择本车机播放音乐") },
            trailingContent = { Switch(checked = true, onCheckedChange = null, enabled = false) }
        )
        CarSwitch("屏幕镜像", "在控制中心「屏幕镜像」中显示本车机；默认关闭", advMirroring) {
            viewModel.set(Prefs.ADVERTISE_MIRRORING, it)
        }
        CarSwitch("AirPlay 视频", "允许把视频 App 中的视频投到车机；关闭时视频只以声音播放。默认关闭", advVideo) {
            viewModel.set(Prefs.ADVERTISE_VIDEO, it)
        }

        // ---------- display ----------
        CarHeader("投屏显示")
        if (advMirroring) CarSwitch("镜像连接时自动全屏", "iPhone 开始屏幕镜像后直接全屏显示；也可点画面或全屏按钮进入", mirrorAutoFs) {
            viewModel.set(Prefs.AUTO_FULLSCREEN, it)
        }
        CarSwitch("音乐播放时自动全屏", "播放音乐时直接显示全屏封面和控制；也可点右上角全屏按钮进入", musicAutoFs) {
            viewModel.set(Prefs.MUSIC_AUTO_FULLSCREEN, it)
        }
        CarSwitch(
            "画中画小窗",
            "离开应用时缩成小窗继续显示。很多车机系统无法操作画中画小窗，会出现点不动、回不到应用的黑框，默认关闭；确认车机支持再开启",
            pipEnabled
        ) { viewModel.set(Prefs.PIP_ENABLED, it) }
        if (advMirroring) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("全屏镜像画面比例")
            Text("宽屏车机可选“拉伸”或“裁剪”去掉黑边；全屏时也可点右上角按钮切换",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("BEST_FIT" to "适应", "STRETCH" to "拉伸", "CROP" to "裁剪").forEach { (v, label) ->
                    FilterChip(selected = mirrorScale == v, onClick = { viewModel.set(Prefs.MIRROR_SCALE, v) }, label = { Text(label) })
                }
            }
        }

        // ---------- interface ----------
        CarHeader("界面")
        ChipSetting(
            "界面缩放", "放大文字、图标和按钮；自动模式按车机屏幕高度计算（当前约 $autoScale%）",
            uiScale, listOf(0 to "自动", 100 to "100%", 150 to "150%", 200 to "200%", 250 to "250%", 300 to "300%", 350 to "350%")
        ) { viewModel.set(Prefs.UI_SCALE, it) }
        ChipSetting(
            "顶部避让", "界面被车机状态栏遮挡时加大；自动模式读取系统状态栏高度",
            insetTop, listOf(-1 to "自动", 0 to "0", 24 to "24", 36 to "36", 48 to "48", 64 to "64", 80 to "80")
        ) { viewModel.set(Prefs.INSET_TOP_DP, it) }
        ChipSetting(
            "底部避让", "界面被车机底部快捷栏遮挡时加大",
            insetBottom, listOf(-1 to "自动", 0 to "0", 24 to "24", 36 to "36", 48 to "48", 64 to "64", 80 to "80")
        ) { viewModel.set(Prefs.INSET_BOTTOM_DP, it) }

        // ---------- audio ----------
        CarHeader("音频")
        CarSwitch("由车机控制音量", "忽略手机端音量调节，始终以最大音量输出，音量用车机旋钮或方向盘按键调节", fixedVolume) {
            viewModel.set(Prefs.FIXED_VOLUME, it)
        }
        ChipSetting(
            "音乐缓冲（延迟）", "缓冲越大越不容易断流，开始播放会相应延迟；默认 2 秒，与常见 AirPlay 音箱一致。屏幕镜像不受影响，仍用低延迟缓冲保证音画同步",
            musicBuffer, listOf(0 to "自动", 500 to "0.5秒", 1000 to "1秒", 1500 to "1.5秒", 2000 to "2秒")
        ) { viewModel.set(Prefs.MUSIC_BUFFER_MS, it) }

        // ---------- autostart ----------
        CarHeader("自动启动")
        CarSwitch("开机 / ACC 上电自动启动", "车机开机或点火后自动在后台开启 AirPlay 接收", bootStart) {
            viewModel.set(Prefs.BOOT_AUTO_START, it)
        }
        DelayField(bootDelay) { viewModel.set(Prefs.BOOT_DELAY_S, it) }
        CarSwitch("开机后打开本应用界面", if (canOverlay) "开机自动打开界面" else "需要“显示在其他应用上层”权限（点击开关去授权）", bootUi && canOverlay) {
            viewModel.set(Prefs.BOOT_LAUNCH_UI, it)
            if (it && !canOverlay) SystemSettings.overlay(ctx)
        }
        if (bootUi) CarSwitch("打开后自动退到后台", "开机打开界面 2.5 秒后返回桌面，不影响导航", bootBg) {
            viewModel.set(Prefs.BOOT_LAUNCH_BACKGROUND, it)
        }
        CarSwitch("打开应用时自动开始接收", "进入本应用即启动 AirPlay 服务", appAutoStart) {
            viewModel.set(Prefs.AUTO_START, it)
        }
        CarSwitch("手机连接时自动弹出画面", if (canOverlay) "iPhone 投屏时自动切到全屏画面" else "需要“显示在其他应用上层”权限（点击开关去授权）", launchOnConnect && canOverlay) {
            viewModel.set(Prefs.LAUNCH_ON_CONNECT, it)
            if (it && !canOverlay) SystemSettings.overlay(ctx)
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

        // ---------- native settings ----------
        CarHeader("安卓原生设置")
        NativeSettingsSection()
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSetting(title: String, description: String, value: Int, options: List<Pair<Int, String>>, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, label) ->
                FilterChip(selected = value == v, onClick = { onSelect(v) }, label = { Text(label) }, modifier = Modifier.dpadFocus())
            }
        }
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

