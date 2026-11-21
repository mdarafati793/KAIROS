package com.kairos.app.service

import android.animation.ValueAnimator
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import com.kairos.app.MainActivity
import com.kairos.app.Mode
import com.kairos.app.OrbState
import com.kairos.app.R
import com.kairos.app.ai.Assistant
import com.kairos.app.data.Prefs
import com.kairos.app.voice.*
import kotlinx.coroutines.*
import kotlin.math.sin

class OrbView(c: Context) : View(c) {
    var state = OrbState.Idle
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var ph = 0f
    private val an = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 2200; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { ph = it.animatedValue as Float; invalidate() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); an.start() }
    override fun onDetachedFromWindow() { an.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val base = width / 2f
        val k = when (state) { OrbState.Idle -> 0.05f; OrbState.Listening -> 0.15f; OrbState.Thinking -> 0.10f; OrbState.Speaking -> 0.12f }
        val r = base * 0.58f * (1f + k * sin(ph * (if (state == OrbState.Thinking) 3f else 1f)))
        val col = if (state == OrbState.Thinking) 0xFF9B6BFF.toInt() else 0xFF00E5FF.toInt()
        p.shader = RadialGradient(cx, cy, base, intArrayOf((col and 0x00FFFFFF) or 0x88000000.toInt(), 0x00000000), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, base, p)
        p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.4f, intArrayOf(0xFFFFFFFF.toInt(), col, 0xFF2979FF.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p)
        p.shader = null
    }
}

class KairosCoreService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wm: WindowManager
    private lateinit var voice: Voice
    private var orb: OrbView? = null
    private var porcupine: PorcupineManager? = null
    private var wakeSr: SpeechRecognizer? = null
    private var wakeLoop = false
    private var busy = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        voice = Voice(this)
        scope.launch { voice.state.collect { orb?.state = it } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("kairos_core", "KAIROS is listening", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "kairos_core").setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("KAIROS").setContentText("Tap the orb or say “Hey KAIROS”")
            .setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1, n)
        addOrb()
        startWake()
        return START_STICKY
    }

    private fun addOrb() {
        if (orb != null || !Settings.canDrawOverlays(this)) return
        val size = (72 * resources.displayMetrics.density).toInt()
        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 400 }
        val v = OrbView(this).apply { alpha = 0.9f }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (Math.abs(dx) + Math.abs(dy) > 12) moved = true
                    lp.x = ox + dx.toInt(); lp.y = oy + dy.toInt(); wm.updateViewLayout(v, lp)
                }
                MotionEvent.ACTION_UP -> if (!moved) onOrbTap()
            }
            true
        }
        wm.addView(v, lp); orb = v
    }

    private fun onOrbTap() {
        if (voice.state.value == OrbState.Speaking) voice.stopSpeaking() else converse()
    }

    /** Listen → think → speak, without leaving whatever app/game is on screen. */
    private fun converse() {
        if (busy) return
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        busy = true
        pauseWake()
        voice.listen(
            onFinal = { text ->
                orb?.state = OrbState.Thinking
                scope.launch {
                    val sp = SentenceSpeaker(voice)
                    Assistant.run(this@KairosCoreService, text, Mode.GENERAL, allowScreen = true).collect { sp.feed(it) }
                    sp.flush()
                    busy = false; resumeWake()
                }
            },
            fail = { busy = false; resumeWake() }
        )
    }

    // ---- wake word ----
    private fun startWake() {
        if (!Prefs.wake || !Prefs.pro) return
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val ppn = assets.list("")?.contains("hey_kairos.ppn") == true
        if (ppn && Prefs.pvKey.isNotBlank()) {
            try {
                porcupine = PorcupineManager.Builder().setAccessKey(Prefs.pvKey).setKeywordPath("hey_kairos.ppn")
                    .setSensitivity(0.7f).build(this, PorcupineManagerCallback { main.post { converse() } })
                porcupine?.start()
                return
            } catch (e: Exception) { porcupine = null }
        }
        wakeLoop = true
        listenWake()
    }

    private fun pauseWake() { porcupine?.stop(); wakeSr?.destroy(); wakeSr = null }
    private fun resumeWake() { try { porcupine?.start() } catch (e: Exception) {}; if (wakeLoop) listenWake() }

    /** Fallback: low-power continuous recogniser that triggers on "Hey KAIROS" (Android speech services may beep between cycles). */
    private fun listenWake() {
        if (!wakeLoop || busy || !SpeechRecognizer.isRecognitionAvailable(this)) return
        wakeSr?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        wakeSr = r
        val re = Regex("kairos|kyros|কাইরোস|কায়রোস|কাইরস")
        r.setRecognitionListener(object : SimpleListener() {
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.joinToString(" ")?.lowercase() ?: ""
                if (re.containsMatchIn(t)) converse() else main.postDelayed({ listenWake() }, 300)
            }
            override fun onError(error: Int) { main.postDelayed({ listenWake() }, 1500) }
        })
        r.startListening(speechIntent())
    }

    override fun onDestroy() {
        wakeLoop = false
        scope.cancel()
        try { porcupine?.stop(); porcupine?.delete() } catch (e: Exception) {}
        wakeSr?.destroy()
        orb?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        voice.release()
        super.onDestroy()
    }
}
