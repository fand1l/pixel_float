package dev.fand1l.pixelfloat.overlay

/**
 * A rectangle in window-local pixels.
 *
 * Deliberately NOT android.graphics.Rect: the geometry layer must be plain Kotlin so it
 * runs in local JVM unit tests. In a unit test the platform Rect is a stub — its
 * constructor silently leaves every field at 0 and its methods throw "not mocked" — so
 * using it here would make the geometry both untestable and quietly wrong under test.
 * Conversion to the platform types happens only at the WindowManager boundary.
 */
data class PillRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    override fun toString(): String = "[$left,$top → $right,$bottom]"
}
