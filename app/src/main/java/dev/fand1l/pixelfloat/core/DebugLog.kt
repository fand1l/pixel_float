package dev.fand1l.pixelfloat.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory diagnostics ring.
 *
 * There is no adb on this project: READ_LOGS is not grantable on-device, so logcat is
 * unreachable and the GitHub Actions log only covers the build. Every catch block in the
 * overlay writes here, and the debug screen renders it. Without this, "nothing happened"
 * is indistinguishable from a missing permission, a bad window token, an off-screen
 * geometry, or a swallowed exception.
 */
class DebugLog(private val capacity: Int = 250) {

    data class Line(val timestamp: Long, val tag: String, val message: String) {
        fun format(): String =
            SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp)) + "  $tag: $message"
    }

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    /** Set by the graph so lines also survive a process restart in Room. */
    @Volatile
    var sink: ((Line) -> Unit)? = null

    fun i(tag: String, message: String) = add(Log.INFO, tag, message)

    fun w(tag: String, message: String, error: Throwable? = null) =
        add(Log.WARN, tag, if (error == null) message else "$message — ${error.summary()}")

    fun clear() {
        _lines.value = emptyList()
    }

    private fun add(level: Int, tag: String, message: String) {
        Log.println(level, TAG, "$tag: $message")
        val line = Line(System.currentTimeMillis(), tag, message)
        _lines.update { (it + line).takeLast(capacity) }
        sink?.invoke(line)
    }

    private fun Throwable.summary(): String =
        "${javaClass.simpleName}: ${message ?: "no message"}"

    private companion object {
        const val TAG = "PixelFloat"
    }
}
