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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fand1l.pixelfloat.R
import dev.fand1l.pixelfloat.data.settings.PillGeometry
import dev.fand1l.pixelfloat.graph
import dev.fand1l.pixelfloat.theme.PixelFloatTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Stage 1a control panel. This is not the real UI — onboarding, calibration, whitelist,
 * settings and history come later. Right now it exists to answer four questions on device:
 * does the window appear, does it appear in the right place, does it break touches, and
 * what does the display actually report about its cutout.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelFloatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    StageOneScreen(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun StageOneScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()

    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleResumeEffect(Unit) {
        canDrawOverlays = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }

    val isShown by graph.overlay.isShown.collectAsStateWithLifecycle()
    val frames by graph.overlay.framesSinceShow.collectAsStateWithLifecycle()
    val cutout by graph.overlay.cutout.collectAsStateWithLifecycle()
    val windowVisibility by graph.overlay.windowVisibility.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val logLines by graph.debugLog.lines.collectAsStateWithLifecycle()
    val listenerConnected by graph.serviceState.listenerConnected.collectAsStateWithLifecycle()
    val accessibilityConnected by graph.serviceState.accessibilityConnected.collectAsStateWithLifecycle()

    val crash = remember { graph.crashRecorder.read() }
    var crashVisible by remember { mutableStateOf(crash != null) }

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

        SectionCard(title = stringResource(R.string.permission_overlay_title)) {
            Text(
                text = if (canDrawOverlays) {
                    stringResource(R.string.permission_overlay_granted)
                } else {
                    stringResource(R.string.permission_overlay_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!canDrawOverlays) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }
                ) { Text(stringResource(R.string.permission_overlay_action)) }
            }
        }

        SectionCard(title = "Island") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = canDrawOverlays,
                    onClick = { graph.overlay.toggle() },
                ) {
                    Text(
                        if (isShown) stringResource(R.string.island_hide)
                        else stringResource(R.string.island_show)
                    )
                }
            }
            Text(
                text = "attached: $isShown · frames: $frames · visibility: " +
                    (windowVisibility?.let { visibilityName(it) } ?: "—"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            if (isShown && frames == 0) {
                Text(
                    text = "0 frames while attached → the composition never rendered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.check_touches),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard(title = stringResource(R.string.geometry_title)) {
            val pill = settings.pill
            DpSlider(stringResource(R.string.geometry_width), pill.widthDp, 24f..320f) { value ->
                scope.launch { graph.settings.update { it.copy(pill = it.pill.copy(widthDp = value)) } }
            }
            DpSlider(stringResource(R.string.geometry_height), pill.heightDp, 8f..64f) { value ->
                scope.launch { graph.settings.update { it.copy(pill = it.pill.copy(heightDp = value)) } }
            }
            DpSlider(stringResource(R.string.geometry_offset_x), pill.offsetXDp, -200f..200f) { value ->
                scope.launch { graph.settings.update { it.copy(pill = it.pill.copy(offsetXDp = value)) } }
            }
            DpSlider(stringResource(R.string.geometry_offset_y), pill.offsetYDp, 0f..120f) { value ->
                scope.launch { graph.settings.update { it.copy(pill = it.pill.copy(offsetYDp = value)) } }
            }
            DpSlider(stringResource(R.string.geometry_corner), pill.cornerDp, 0f..40f) { value ->
                scope.launch { graph.settings.update { it.copy(pill = it.pill.copy(cornerDp = value)) } }
            }
            OutlinedButton(
                onClick = {
                    scope.launch { graph.settings.update { it.copy(pill = PillGeometry()) } }
                }
            ) { Text("Reset") }
        }

        SectionCard(title = stringResource(R.string.cutout_title)) {
            Text(
                text = cutout?.describe() ?: stringResource(R.string.cutout_none),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }

        SectionCard(title = stringResource(R.string.services_title)) {
            val bound = stringResource(R.string.service_bound)
            val unbound = stringResource(R.string.service_unbound)
            Text("notification listener: ${if (listenerConnected) bound else unbound}")
            Text("accessibility: ${if (accessibilityConnected) bound else unbound}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    }
                ) { Text("Notification access") }
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                ) { Text("Accessibility") }
            }
        }

        if (crashVisible && crash != null) {
            SectionCard(title = stringResource(R.string.crash_title)) {
                Text(
                    text = crash.take(2000),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                OutlinedButton(
                    onClick = {
                        graph.crashRecorder.clear()
                        crashVisible = false
                    }
                ) { Text(stringResource(R.string.crash_clear)) }
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
