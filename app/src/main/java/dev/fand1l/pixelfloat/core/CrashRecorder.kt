package dev.fand1l.pixelfloat.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists the last uncaught exception to a file the debug screen can render.
 * A crash that happens while the overlay window is up would otherwise be invisible:
 * the app is not in the foreground and there is no way to read logcat on-device.
 */
class CrashRecorder(context: Context) {

    private val file = File(context.filesDir, "last_crash.txt")

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText(
                    buildString {
                        appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                        appendLine("thread=${thread.name}")
                        appendLine()
                        append(error.stackTraceToString())
                    }
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(): String? = runCatching { if (file.exists()) file.readText() else null }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }
}
