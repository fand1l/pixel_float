package dev.fand1l.pixelfloat.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fand1l.pixelfloat.R
import dev.fand1l.pixelfloat.data.settings.IslandGeometry
import dev.fand1l.pixelfloat.data.settings.OverlayWindowType
import dev.fand1l.pixelfloat.graph
import dev.fand1l.pixelfloat.overlay.OverlayGeometry
import dev.fand1l.pixelfloat.permission.AccessibilityAccess
import dev.fand1l.pixelfloat.theme.PixelFloatTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Stage 1b control panel. Not the real UI — onboarding, calibration, whitelist, settings
 * and history come later. It exists to answer, on device: do the pills land beside the
 * cutout, does the chosen window type behave as the AOSP layer table predicts, and does
 * the gap between the pills still pass touches through.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelFloatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    StageScreen(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun StageScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()

    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessibilityGranted by remember { mutableStateOf(AccessibilityAccess.isGranted(context)) }
    LifecycleResumeEffect(Unit) {
        canDrawOverlays = Settings.canDrawOverlays(context)
        accessibilityGranted = AccessibilityAccess.isGranted(context)
        graph.overlay.refreshCutout()
        onPauseOrDispose { }
    }

    val isShown by graph.overlay.isShown.collectAsStateWithLifecycle()
    val activeType by graph.overlay.activeType.collectAsStateWithLifecycle()
    val usingFallback by graph.overlay.usingFallbackHost.collectAsStateWithLifecycle()
    val frames by graph.overlay.framesSinceShow.collectAsStateWithLifecycle()
    val cutout by graph.overlay.cutout.collectAsStateWithLifecycle()
    val windowVisibility by graph.overlay.windowVisibility.collectAsStateWithLifecycle()
    val lastError by graph.overlay.lastError.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val logLines by graph.debugLog.lines.collectAsStateWithLifecycle()
    val listenerConnected by graph.serviceState.listenerConnected.collectAsStateWithLifecycle()
    val accessibilityConnected by graph.serviceState.accessibilityConnected.collectAsStateWithLifecycle()

    val crash = remember { graph.crashRecorder.read() }
    var crashVisible by remember { mutableStateOf(crash != null) }

    fun updateIsland(transform: (IslandGeometry) -> IslandGeometry) {
        scope.launch { graph.settings.update { it.copy(island = transform(it.island)) } }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.stage_banner),
            style = MaterialTheme.typography.titleMedium,
        )

        SectionCard(title = stringResource(R.string.permissions_title)) {
            Text("${stringResource(R.string.permission_overlay_title)}: " + if (canDrawOverlays) "✓" else "✗")
            Text(
                "${stringResource(R.string.permission_accessibility_title)}: " +
                    (if (accessibilityGranted) "✓" else "✗") +
                    " · " + (if (accessibilityConnected) stringResource(R.string.service_bound)
                    else stringResource(R.string.service_unbound))
            )
            Text(
                "${stringResource(R.string.permission_listener_title)}: " +
                    if (listenerConnected) stringResource(R.string.service_bound)
                    else stringResource(R.string.service_unbound)
            )
            Text(
                text = stringResource(R.string.restricted_settings_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!canDrawOverlays) {
                    Button(onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }) { Text(stringResource(R.string.permission_overlay_title)) }
                }
                OutlinedButton(onClick = { context.startActivity(AccessibilityAccess.settingsIntent()) }) {
                    Text(stringResource(R.string.permission_accessibility_title))
                }
                OutlinedButton(onClick = { context.startActivity(AccessibilityAccess.appInfoIntent(context)) }) {
                    Text(stringResource(R.string.app_info))
                }
            }
        }

        SectionCard(title = stringResource(R.string.window_type_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayWindowType.entries.forEach { type ->
                    FilterChip(
                        selected = settings.overlayWindowType == type,
                        onClick = {
                            scope.launch { graph.settings.update { it.copy(overlayWindowType = type) } }
                        },
                        label = {
                            Text(
                                when (type) {
                                    OverlayWindowType.ACCESSIBILITY_OVERLAY -> "accessibility (31)"
                                    OverlayWindowType.APPLICATION_OVERLAY -> "app overlay (11)"
                                }
                            )
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.window_type_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            if (usingFallback) {
                Text(
                    text = stringResource(R.string.window_type_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SectionCard(title = stringResource(R.string.island_title)) {
            Button(onClick = { graph.overlay.toggle() }) {
                Text(
                    if (isShown) stringResource(R.string.island_hide)
                    else stringResource(R.string.island_show)
                )
            }
            Text(
                text = "attached: $isShown · host: ${activeType ?: "—"} · frames: $frames · " +
                    "visibility: ${windowVisibility?.let(::visibilityName) ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            if (isShown && frames == 0) {
                Text(
                    text = stringResource(R.string.no_frames_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            lastError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = stringResource(R.string.stage_1b_checks),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard(title = stringResource(R.string.geometry_title)) {
            val island = settings.island
            DpSlider(stringResource(R.string.geometry_gap), island.gapDp, 0f..200f) { v ->
                updateIsland { it.copy(gapDp = v) }
            }
            DpSlider(stringResource(R.string.geometry_offset_y), island.offsetYDp, 0f..120f) { v ->
                updateIsland { it.copy(offsetYDp = v) }
            }
            DpSlider(stringResource(R.string.geometry_height), island.heightDp, 8f..64f) { v ->
                updateIsland { it.copy(heightDp = v) }
            }
            DpSlider(stringResource(R.string.geometry_left_width), island.leftWidthDp, 8f..240f) { v ->
                updateIsland { it.copy(leftWidthDp = v) }
            }
            DpSlider(stringResource(R.string.geometry_right_width), island.rightWidthDp, 8f..240f) { v ->
                updateIsland { it.copy(rightWidthDp = v) }
            }
            DpSlider(stringResource(R.string.geometry_center_offset), island.centerOffsetXDp, -60f..60f) { v ->
                updateIsland { it.copy(centerOffsetXDp = v) }
            }
            DpSlider(stringResource(R.string.geometry_corner), island.cornerDp, 0f..40f) { v ->
                updateIsland { it.copy(cornerDp = v) }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = island.rightPillVisible,
                    onCheckedChange = { checked -> updateIsland { it.copy(rightPillVisible = checked) } },
                )
                Text(stringResource(R.string.geometry_right_visible))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = cutout?.activeRect != null,
                    onClick = {
                        cutout?.let { info ->
                            updateIsland { OverlayGeometry.seedFrom(info, it) }
                        }
                    },
                ) { Text(stringResource(R.string.geometry_seed)) }
                OutlinedButton(onClick = { updateIsland { IslandGeometry() } }) {
                    Text(stringResource(R.string.reset))
                }
            }
        }

        SectionCard(title = stringResource(R.string.cutout_title)) {
            Text(
                text = cutout?.describe() ?: stringResource(R.string.cutout_none),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            OutlinedButton(onClick = { graph.overlay.refreshCutout() }) {
                Text(stringResource(R.string.refresh))
            }
        }

        if (crashVisible && crash != null) {
            SectionCard(title = stringResource(R.string.crash_title)) {
                Text(
                    text = crash.take(2000),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                OutlinedButton(onClick = {
                    graph.crashRecorder.clear()
                    crashVisible = false
                }) { Text(stringResource(R.string.crash_clear)) }
            }
        }

        SectionCard(title = stringResource(R.string.log_title)) {
            OutlinedButton(onClick = { graph.debugLog.clear() }) {
                Text(stringResource(R.string.log_clear))
            }
            if (logLines.isEmpty()) {
                Text(stringResource(R.string.log_empty))
            } else {
                // A plain Column, not a LazyColumn: this screen is already inside a
                // verticalScroll, and nesting a lazy list in it crashes with an infinite
                // height constraint.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    logLines.asReversed().take(MAX_VISIBLE_LOG_LINES).forEach { line ->
                        Text(
                            text = line.format(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            content()
        }
    }
}

/**
 * Commits on release, never per frame: writing to DataStore on every drag frame rewrites
 * and fsyncs the whole settings file.
 */
@Composable
private fun DpSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text(
            text = "$label: ${local.roundToInt()} dp",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = local,
            onValueChange = { local = it },
            valueRange = range,
            onValueChangeFinished = { onCommit(local) },
        )
    }
}

private const val MAX_VISIBLE_LOG_LINES = 80

private fun visibilityName(visibility: Int): String = when (visibility) {
    View.VISIBLE -> "VISIBLE"
    View.INVISIBLE -> "INVISIBLE"
    View.GONE -> "GONE"
    else -> visibility.toString()
}
