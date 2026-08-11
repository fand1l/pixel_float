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

/**
 * Stage 1a island: one static pill. No springs, no notification content — the point is to
 * prove the window lands where it should and that touches behave.
 *
 * [onFrame] fires for the first few produced frames. If the counter it feeds stays at 0
 * while the window is attached, the composition never rendered — which is what a lifecycle
 * stuck at CREATED looks like, since it throws nothing and logs nothing.
 */
@Composable
fun IslandPill(
    layout: IslandLayout?,
    onFrame: () -> Unit,
    onTap: () -> Unit,
) {
    if (layout == null) return

    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        repeat(FRAME_PROBES) {
            withFrameNanos { }
            onFrame()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(layout.pill.left, layout.pill.top) }
                .size(
                    width = with(density) { layout.pill.width.toDp() },
                    height = with(density) { layout.pill.height.toDp() },
                )
                .clip(RoundedCornerShape(with(density) { layout.cornerRadiusPx.toDp() }))
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                }
        )
    }
}

private const val FRAME_PROBES = 3
