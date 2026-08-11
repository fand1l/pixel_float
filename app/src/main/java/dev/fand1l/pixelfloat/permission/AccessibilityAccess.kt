package dev.fand1l.pixelfloat.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import dev.fand1l.pixelfloat.service.PixelFloatAccessibilityService

/**
 * Whether the accessibility grant exists, which is a different question from whether the
 * service is currently connected. The gap between the two is exactly what goes wrong after
 * an APK update, so the UI shows both.
 */
object AccessibilityAccess {

    fun isGranted(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val component = ComponentName(context, PixelFloatAccessibilityService::class.java)
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.let { info ->
                ComponentName(info.packageName, info.name) == component
            } == true }
    }

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Both the notification-listener and the accessibility toggles are inert for an APK
     * installed outside Play until "Allow restricted settings" is used in App info. This
     * deep link is the shortest path to that screen.
     */
    fun appInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
