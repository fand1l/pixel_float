package dev.fand1l.pixelfloat.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

/** The only file allowed to name window flag literals. */
object OverlayLayoutParams {

    fun create(host: OverlayHost, heightPx: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams().apply {
            type = host.windowType
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = heightPx

            // FLAG_NOT_TOUCH_MODAL is set EXPLICITLY even though FLAG_NOT_FOCUSABLE implies
            // it: when the window is made focusable for inline reply, the implication is
            // silently lost, and a top strip without it swallows every touch on the whole
            // screen — the phone looks frozen.
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

            // The window must be allowed to extend into the cutout area.
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

            // Without this the status bar inset would shrink the frame away from the top edge.
            fitInsetsTypes = 0

            // Pinned at y=0 and non-resizable: nothing should pan for the IME.
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING

            // Names the window in `adb shell dumpsys window windows` — the only remote
            // diagnostic available for this project.
            title = WINDOW_TITLE
        }

    const val WINDOW_TITLE = "PixelFloat:island"
}
