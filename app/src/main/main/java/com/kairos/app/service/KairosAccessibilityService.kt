package com.kairos.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class KairosAccessibilityService : AccessibilityService() {
    companion object { @Volatile var inst: KairosAccessibilityService? = null }

    override fun onServiceConnected() { inst = this }
    override fun onUnbind(intent: Intent?): Boolean { inst = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun pkg(): String = rootInActiveWindow?.packageName?.toString() ?: "unknown"

    fun screenText(): String {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || sb.length > 4000 || d > 25) return
            val t = n.text ?: n.contentDescription
            if (!t.isNullOrBlank()) sb.append(t).append(" | ")
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        walk(rootInActiveWindow, 0)
        return sb.toString()
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun clickByText(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(t)) {
            var x: AccessibilityNodeInfo? = n
            while (x != null) {
                if (x.isClickable) return x.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                x = x.parent
            }
        }
        return false
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 300): Boolean {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), null, null)
    }

    fun tap(x: Float, y: Float): Boolean {
        val p = Path().apply { moveTo(x, y) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null)
    }

    fun scroll(down: Boolean): Boolean {
        val m = resources.displayMetrics
        val x = m.widthPixels / 2f; val a = m.heightPixels * 0.7f; val b = m.heightPixels * 0.3f
        return if (down) swipe(x, a, x, b) else swipe(x, b, x, a)
    }

    /** On-demand screen capture for vision (Android 11+). Uses the accessibility screenshot API, so no MediaProjection prompt is needed. */
    suspend fun screenshot(): Bitmap? {
        if (Build.VERSION.SDK_INT < 30) return null
        return suspendCancellableCoroutine { cont ->
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                    val hb = r.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    cont.resume(bmp)
                }
                override fun onFailure(errorCode: Int) { cont.resume(null) }
            })
        }
    }
}
