package com.kairos.app.ai

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import com.kairos.app.data.Prefs
import com.kairos.app.service.KairosAccessibilityService

/** Tier-1 local fast path (<100ms): device commands never touch the network. Returns null if the text isn't a local command. */
object LocalIntentRouter {
    const val LOCKED = "🔒 This is a Pro feature. Complete the 3 unlock tasks (Settings → Unlock) to use it."
    private const val BN = "০১২৩৪৫৬৭৮৯"

    private fun norm(s: String): String {
        var t = s.lowercase().trim().trimEnd('.', '!', '?', '।')
        BN.forEachIndexed { i, ch -> t = t.replace(ch, '0' + i) }
        t = t.replace(Regex("^(hey|hi|ok|okay)?\\s*(kairos|কাইরোস)[,\\s]*"), "")
        return t.trim()
    }

    private inline fun gesture(block: (KairosAccessibilityService) -> String): String {
        if (!Prefs.pro) return LOCKED
        val s = KairosAccessibilityService.inst ?: return "Turn on the KAIROS Accessibility Service in Settings first."
        return block(s)
    }

    fun handle(c: Context, raw: String): String? {
        val t = norm(raw)
        if (t.isEmpty()) return null

        if (Regex("flash ?light|torch|ফ্ল্যাশ ?লাইট|টর্চ").containsMatchIn(t))
            return torch(c, !Regex("off|বন্ধ").containsMatchIn(t))

        if (Regex("volume|ভলিউম|louder|quieter|mute").containsMatchIn(t)) {
            val am = c.getSystemService(AudioManager::class.java)
            val dir = when {
                Regex("up|louder|increase|বাড়া|বেশি").containsMatchIn(t) -> AudioManager.ADJUST_RAISE
                Regex("mute|মিউট").containsMatchIn(t) -> AudioManager.ADJUST_MUTE
                else -> AudioManager.ADJUST_LOWER
            }
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
            return "Done ✅"
        }

        if (Regex("timer|টাইমার").containsMatchIn(t)) {
            val m = Regex("(\\d+)\\s*(hour|hr|ঘণ্টা|minute|min|মিনিট|second|sec|সেকেন্ড)?").find(t)
            if (m != null) {
                val n = m.groupValues[1].toInt()
                val u = m.groupValues[2]
                val secs = when { u.startsWith("h") || u == "ঘণ্টা" -> n * 3600; u.startsWith("s") || u == "সেকেন্ড" -> n; else -> n * 60 }
                return start(c, Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, secs).putExtra(AlarmClock.EXTRA_SKIP_UI, true), "Timer set for $n ${if (u.isBlank()) "min" else u} ⏱️")
            }
        }

        if (Regex("alarm|অ্যালার্ম|এলার্ম").containsMatchIn(t)) {
            val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|এএম|পিএম)?").find(t)
            if (m != null) {
                var h = m.groupValues[1].toInt()
                val min = m.groupValues[2].ifBlank { "0" }.toInt()
                val ap = m.groupValues[3]
                if ((ap == "pm" || ap == "পিএম") && h < 12) h += 12
                if ((ap == "am" || ap == "এএম") && h == 12) h = 0
                if (h in 0..23)
                    return start(c, Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, min).putExtra(AlarmClock.EXTRA_SKIP_UI, true), "Alarm set for %02d:%02d ⏰".format(h, min))
            }
        }

        if (Regex("wi-?fi|ওয়াই ?ফাই").containsMatchIn(t))
            return start(c, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), "Opening Wi-Fi panel")
        if (Regex("bluetooth|ব্লুটুথ").containsMatchIn(t))
            return start(c, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Opening Bluetooth settings")

        // YouTube
        Regex("^play (.+?)(?: on youtube)?$").find(t)?.let { return youtube(c, it.groupValues[1]) }
        Regex("^(.+?)\\s*(?:গানটা |গান )?(?:চালাও|বাজাও|ছাড়ো)(?: ইউটিউবে)?$").find(t)?.let { return youtube(c, it.groupValues[1]) }

        // Open app
        Regex("^open (.+)$").find(t)?.let { return launchApp(c, it.groupValues[1]) }
        Regex("^(.+?)\\s*(?:খোলো|খুলো|ওপেন কর(?:ো)?|চালু কর(?:ো)?)$").find(t)?.let { return launchApp(c, it.groupValues[1]) }

        // Screen automation (Pro)
        if (Regex("^(go )?back$|পেছনে যাও|^ব্যাক$").containsMatchIn(t)) return gesture { it.back(); "Back ✅" }
        if (Regex("^(go )?home$|হোমে যাও|হোম স্ক্রিন").containsMatchIn(t)) return gesture { it.home(); "Home ✅" }
        if (Regex("recent apps|^recents?$|রিসেন্ট").containsMatchIn(t)) return gesture { it.recents(); "Recents ✅" }
        if (Regex("^close( this)?( app)?$|অ্যাপ বন্ধ কর").containsMatchIn(t)) return gesture { it.home(); "Sent the app to the background ✅" }
        if (Regex("scroll down|নিচে স্ক্রল").containsMatchIn(t)) return gesture { it.scroll(true); "Scrolling down ✅" }
        if (Regex("scroll up|উপরে স্ক্রল").containsMatchIn(t)) return gesture { it.scroll(false); "Scrolling up ✅" }
        Regex("^(?:tap|click|press)(?: on)? (.+)$").find(t)?.let { m -> return gesture { if (it.clickByText(m.groupValues[1])) "Tapped “${m.groupValues[1]}” ✅" else "I couldn't find “${m.groupValues[1]}” on screen." } }
        return null
    }

    private fun start(c: Context, i: Intent, ok: String): String = try {
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ok
    } catch (e: Exception) { "That action isn't available on this phone." }

    private fun torch(c: Context, on: Boolean): String = try {
        val cm = c.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        if (id == null) "No flashlight found." else { cm.setTorchMode(id, on); if (on) "Flashlight on 🔦" else "Flashlight off" }
    } catch (e: Exception) { "Couldn't toggle the flashlight." }

    private fun youtube(c: Context, q: String): String {
        return try {
            c.startActivity(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Playing “$q” on YouTube ▶️"
        } catch (e: Exception) {
            start(c, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))), "Searching “$q” on YouTube ▶️")
        }
    }

    private fun launchApp(c: Context, q: String): String {
        val pm = c.packageManager
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val ql = q.lowercase().trim()
        fun label(r: android.content.pm.ResolveInfo) = r.loadLabel(pm).toString().lowercase()
        val hit = list.firstOrNull { label(it) == ql } ?: list.firstOrNull { label(it).contains(ql) }
            ?: list.firstOrNull { label(it).length > 2 && ql.contains(label(it)) }
        val pkg = hit?.activityInfo?.packageName ?: return "I couldn't find an app called “$q”."
        val li = pm.getLaunchIntentForPackage(pkg) ?: return "I can't open that app."
        c.startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Opening ${hit.loadLabel(pm)} ✅"
    }
}
