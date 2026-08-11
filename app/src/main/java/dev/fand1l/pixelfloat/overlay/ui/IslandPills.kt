package dev.fand1l.pixelfloat.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import dev.fand1l.pixelfloat.overlay.IslandLayout
import dev.fand1l.pixelfloat.overlay.PillRect

/**
 * Stage 1b island: two static pills flanking the cutout, with the gap left clear. No
 * springs and no notification content yet — the point is to prove the pills land beside
 * the cutout on the chosen window type, and that the gap still passes touches through.
 *
 * [onFrame] fires for the first few produced frames. A counter stuck at 0 while the window
 * is attached means the composition never rendered, which is what a lifecycle stopped at
 * CREATED looks like: no crash, no log line, just nothing.
 */
@Composable
fun IslandPills(
    layout: IslandLayout?,
    onFrame: () -> Unit,
    onTap: () -> Unit,
) {
    if (layout == null) return

    LaunchedEffect(Unit) {
        repeat(FRAME_PROBES) {
            withFrameNanos { }
            onFrame()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Pill(rect = layout.left, cornerRadiusPx = layout.cornerRadiusPx, onTap = onTap)
        // When the right pill is hidden its space stays reserved in the geometry — the
        // expand animation needs a stable origin — but nothing is drawn there.
        if (layout.rightPillVisible) {
            Pill(rect = layout.right, cornerRadiusPx = layout.cornerRadiusPx, onTap = onTap)
        }
    }
}

@Composable
private fun Pill(rect: PillRect, cornerRadiusPx: Float, onTap: () -> Unit) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { IntOffset(rect.left, rect.top) }
            .size(
                width = with(density) { rect.width.toDp() },
                height = with(density) { rect.height.toDp() },
            )
            .clip(RoundedCornerShape(with(density) { cornerRadiusPx.toDp() }))
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
    )
}

private const val FRAME_PROBES = 3
