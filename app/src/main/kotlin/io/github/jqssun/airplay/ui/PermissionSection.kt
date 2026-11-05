package io.github.jqssun.airplay.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.jqssun.airplay.car.SystemSettings

private enum class PermState { GRANTED, DENIED, MANUAL }

/** Permissions the receiver relies on, each with its live state and a jump into system settings. */
@Composable
fun PermissionSection() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // coming back from a settings page re-reads every state
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val notifRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
        if (!ok) SystemSettings.notifications(ctx)
    }
    val localNetRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
        if (!ok) SystemSettings.appDetails(ctx)
    }

    // reading `tick` makes every state below recompute after resume
    val t = tick
    val localNet = remember(t) { SystemSettings.localNetworkGranted(ctx) }
    val notif = remember(t) { SystemSettings.notificationsEnabled(ctx) }
    val overlay = remember(t) { SystemSettings.overlayGranted(ctx) }
    val battery = remember(t) { SystemSettings.batteryUnrestricted(ctx) }

    if (SystemSettings.localNetworkRuntime) {
        PermItem(
            Icons.Default.Devices, "附近设备 / 本地网络", "必需：iPhone 需要通过局域网发现并连接车机",
            if (localNet) PermState.GRANTED else PermState.DENIED,
        ) { localNetRequest.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }
    }
    PermItem(
        Icons.Default.Notifications, "通知", "用于前台服务常驻通知和音乐播放控制，可保持后台不被清理",
        if (notif) PermState.GRANTED else PermState.DENIED,
    ) {
        if (SystemSettings.notificationsRuntime && !notif) notifRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        else SystemSettings.notifications(ctx)
    }
    PermItem(
        Icons.Default.PictureInPicture, "显示在其他应用上层", "用于开机自动打开界面、手机连接时自动弹出投屏画面",
        if (overlay) PermState.GRANTED else PermState.DENIED,
    ) { SystemSettings.overlay(ctx) }
    PermItem(
        Icons.Default.BatteryChargingFull, "忽略电池优化", "防止车机系统在后台休眠或清理接收器",
        if (battery) PermState.GRANTED else PermState.DENIED,
    ) { SystemSettings.batteryOptimization(ctx) }
    PermItem(
        Icons.Default.RestartAlt, "自启动 / 后台运行", "部分定制系统需在“自启动管理”或应用详情中允许，系统无法自动检测",
        PermState.MANUAL,
    ) { SystemSettings.autostart(ctx) }
    PermItem(
        Icons.Default.Apps, "应用详情（全部权限）", "在系统应用详情中查看和管理本应用的所有权限",
        null,
    ) { SystemSettings.appDetails(ctx) }
}

/** Grid of shortcuts into the native Android settings, for head units whose launcher hides them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NativeSettingsSection() {
    val ctx = LocalContext.current
    Text(
        "优先打开安卓原生设置（com.android.settings）；车机未提供时自动改用系统默认的设置页面。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    )
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SystemSettings.nativeMenus.forEach { e ->
            OutlinedButton(onClick = { e.open(ctx) }, modifier = Modifier.dpadFocus()) { Text(e.title) }
        }
    }
}

@Composable
private fun PermItem(
    icon: ImageVector,
    title: String,
    description: String,
    state: PermState?,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title)
                if (state != null) {
                    Spacer(Modifier.width(8.dp))
                    StatusChip(state)
                }
            }
        },
        supportingContent = { Text(description) },
        trailingContent = {
            val label = when (state) {
                PermState.GRANTED -> "管理"
                PermState.DENIED -> "去授权"
                PermState.MANUAL, null -> "打开"
            }
            if (state == PermState.DENIED) {
                Button(onClick = onClick, modifier = Modifier.dpadFocus()) { Text(label) }
            } else {
                FilledTonalButton(onClick = onClick, modifier = Modifier.dpadFocus()) { Text(label) }
            }
        }
    )
}

@Composable
private fun StatusChip(state: PermState) {
    val (text, bg, fg) = when (state) {
        PermState.GRANTED -> Triple("已开启", Color(0xFF1E7D34), Color.White)
        PermState.DENIED -> Triple("未开启", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
        PermState.MANUAL -> Triple("请手动确认", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    }
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
