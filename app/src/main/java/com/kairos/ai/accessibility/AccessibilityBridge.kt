package com.kairos.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import kotlinx.coroutines.flow.*
import java.lang.ref.WeakReference

object AccessibilityBridge {
    private val lock = Any()
    @Volatile private var serviceRef: WeakReference<KairosAccessibilityService>? = null
    private val _liveConnected = MutableStateFlow(false)
    val liveConnected: StateFlow<Boolean> = _liveConnected.asStateFlow()

    fun attach(s: KairosAccessibilityService) = synchronized(lock) { serviceRef = WeakReference(s); _liveConnected.value = true }
    fun detach(s: KairosAccessibilityService) = synchronized(lock) {
        if (serviceRef?.get() == null || serviceRef?.get() === s) { serviceRef = null; _liveConnected.value = false }
    }
    fun getService(): KairosAccessibilityService? = synchronized(lock) { serviceRef?.get().also { if (it == null) _liveConnected.value = false } }

    fun isEnabledInSettings(ctx: Context): Boolean {
        if (Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cn = ComponentName(ctx.packageName, KairosAccessibilityService::class.java.name)
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        while (splitter.hasNext()) {
            val item = splitter.next()
            if (item.equals(cn.flattenToString(), true) || item.equals(cn.flattenToShortString(), true)) return true
        }
        return false
    }

    fun isConnected(ctx: Context): Boolean {
        val live = getService() != null
        _liveConnected.value = live
        return live && isEnabledInSettings(ctx)
    }

    fun refreshState(ctx: Context) = isConnected(ctx)
    fun captureScreenContext(): String = getService()?.extractScreenSummary() ?: "Accessibility service is not connected."
    fun performBack(): Boolean = getService()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true
    fun performHome(): Boolean = getService()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true
    fun clickElementByText(text: String): Boolean = getService()?.clickNodeByText(text) == true
}
