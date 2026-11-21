package com.kairos.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class KairosAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); AccessibilityBridge.attach(this) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) { if (AccessibilityBridge.getService() == null) AccessibilityBridge.attach(this) }
    override fun onInterrupt() {}
    override fun onUnbind(i: Intent?): Boolean { AccessibilityBridge.detach(this); return super.onUnbind(i) }
    override fun onDestroy() { AccessibilityBridge.detach(this); super.onDestroy() }

    fun extractScreenSummary(maxLines: Int = 60): String {
        val root = rootInActiveWindow ?: return "No active window available."
        val lines = mutableListOf("Active App: ${root.packageName ?: "unknown"}")
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || lines.size >= maxLines) return
            val t = n.text?.toString()?.trim().orEmpty()
            val desc = n.contentDescription?.toString()?.trim().orEmpty()
            val lbl = when {
                t.isNotEmpty() && desc.isNotEmpty() && t != desc -> "$t ($desc)"
                t.isNotEmpty() -> t
                desc.isNotEmpty() -> desc
                else -> ""
            }
            if (lbl.isNotEmpty()) lines.add("${"  ".repeat(d.coerceAtMost(4))}- $lbl${if (n.isClickable) " [clickable]" else ""}")
            for (i in 0 until n.childCount) {
                if (lines.size >= maxLines) break
                val c = n.getChild(i); walk(c, d + 1)
                @Suppress("DEPRECATION") c?.recycle()
            }
        }
        try { walk(root, 0) } finally { @Suppress("DEPRECATION") root.recycle() }
        return lines.joinToString("\n")
    }

    fun clickNodeByText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            for (n in root.findAccessibilityNodeInfosByText(target)) {
                var c: AccessibilityNodeInfo? = n
                while (c != null) {
                    if (c.isClickable && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    c = c.parent
                }
            }
            false
        } finally { @Suppress("DEPRECATION") root.recycle() }
    }
}
