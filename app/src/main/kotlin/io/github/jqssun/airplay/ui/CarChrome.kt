package io.github.jqssun.airplay.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlin.math.max

/**
 * Space to keep clear of the head unit's own status bar and dock.
 *
 * Many head units draw their status bar as an overlay that reports no window insets, so the app
 * would draw underneath it. Unless the user sets a value, the top uses the larger of the reported
 * inset and the system's status_bar_height resource.
 */
@SuppressLint("DiscouragedApi", "InternalInsetResource")
@Composable
fun carChromePadding(viewModel: MainViewModel): PaddingValues {
    val topPref by viewModel.pref(Prefs.INSET_TOP_DP).collectAsState()
    val bottomPref by viewModel.pref(Prefs.INSET_BOTTOM_DP).collectAsState()
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val ctx = LocalContext.current
    val statusBarRes = remember {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) ctx.resources.getDimensionPixelSize(id) else 0
    }
    val sysTop = WindowInsets.statusBars.getTop(density)
    val sysBottom = WindowInsets.navigationBars.getBottom(density)
    val safe = WindowInsets.safeDrawing
    return with(density) {
        PaddingValues(
            start = safe.getLeft(density, dir).toDp(),
            end = safe.getRight(density, dir).toDp(),
            top = if (topPref >= 0) topPref.dp else max(sysTop, statusBarRes).toDp(),
            bottom = if (bottomPref >= 0) bottomPref.dp else max(sysBottom, safe.getBottom(density)).toDp(),
        )
    }
}

/**
 * Enlarges the whole UI on wide, low-density car screens, where the default sizing makes text,
 * icons and buttons tiny. `UI_SCALE` 0 = automatic, otherwise a percentage.
 */
@Composable
fun CarUiScale(viewModel: MainViewModel, content: @Composable () -> Unit) {
    val pref by viewModel.pref(Prefs.UI_SCALE).collectAsState()
    val conf = LocalConfiguration.current
    val base = LocalDensity.current
    val scale = if (pref > 0) pref / 100f else autoUiScale(conf)
    if (scale == 1f) { content(); return }
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale), content = content)
}

/** Aim for roughly 440dp of usable height on landscape screens that are much taller than that. */
fun autoUiScale(conf: Configuration): Float {
    val w = conf.screenWidthDp
    val h = conf.screenHeightDp
    if (w <= h || w < 900) return 1f
    return (h / 440f).coerceIn(1f, 1.75f)
}

/** Effective percentage shown in settings for the automatic mode. */
fun autoUiScalePercent(conf: Configuration) = (autoUiScale(conf) * 100).toInt()

