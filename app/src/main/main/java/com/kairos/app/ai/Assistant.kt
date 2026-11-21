package com.kairos.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.kairos.app.Mode
import com.kairos.app.data.AppDb
import com.kairos.app.data.Memo
import com.kairos.app.data.Prefs
import com.kairos.app.service.KairosAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream

/** One pipeline used by the app UI and by the floating-orb service: local fast-path → (optional screen context) → streaming cloud brain. */
object Assistant {
    private val SCREEN = Regex("screen|স্ক্রিন|this video|এই ভিডিও|what is this|viral title|hashtag|হ্যাশট্যাগ|caption|এখানে কি|look at|দেখ", RegexOption.IGNORE_CASE)

    private val CREATOR = Regex("title|hashtag|viral|caption|হ্যাশট্যাগ|ক্যাপশন|টাইটেল|capcut|hook", RegexOption.IGNORE_CASE)

    fun toB64(b: Bitmap, max: Int = 1024): String {
        val s = max.toFloat() / maxOf(b.width, b.height)
        val bm = if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true) else b
        val o = ByteArrayOutputStream(); bm.compress(Bitmap.CompressFormat.JPEG, 80, o)
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
    }

    fun run(ctx: Context, text: String, mode: Mode, images: List<String> = emptyList(), allowScreen: Boolean = false): Flow<String> = flow {
        val app = ctx.applicationContext
        val dao = AppDb.get(app).dao()
        val hist = dao.lastMemos(10).reversed().map { it.role to it.text }.dropWhile { it.first != "user" }
        dao.addMemo(Memo(role = "user", text = text))

        LocalIntentRouter.handle(app, text)?.let {
            emit(it); dao.addMemo(Memo(role = "model", text = it)); return@flow
        }

        val imgs = images.toMutableList()
        var prompt = text
        if (allowScreen && SCREEN.containsMatchIn(text)) {
            if (!Prefs.pro) { emit(LocalIntentRouter.LOCKED); return@flow }
            val svc = KairosAccessibilityService.inst
            if (svc == null) { emit("Turn on the KAIROS Accessibility Service in Settings so I can see your screen."); return@flow }
            svc.screenshot()?.let { imgs.add(toB64(it)) }
            prompt += "\n\n[Screen context — app: ${svc.pkg()}]\n" + svc.screenText().take(3000)
        }
        val sb = StringBuilder()
        Brain.stream(hist, prompt, imgs, if (mode == Mode.GENERAL && allowScreen && CREATOR.containsMatchIn(text)) Mode.CREATOR else mode)
            .collect { sb.append(it); emit(it) }
        dao.addMemo(Memo(role = "model", text = sb.toString()))
    }.catch { emit("⚠️ ${it.message}") }.flowOn(Dispatchers.IO)
}
