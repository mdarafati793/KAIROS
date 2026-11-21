package com.kairos.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Prefs {
    private lateinit var sp: SharedPreferences
    private lateinit var sec: SharedPreferences

    fun init(c: Context) {
        if (::sec.isInitialized) return
        sp = c.getSharedPreferences("kairos", Context.MODE_PRIVATE)
        val mk = MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        sec = EncryptedSharedPreferences.create(
            c, "kairos_secure", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun s(k: String, d: String = "") = sp.getString(k, d) ?: d
    private fun p(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun b(k: String, d: Boolean = false) = sp.getBoolean(k, d)
    private fun pb(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var name: String get() = s("name"); set(v) = p("name", v)
    var age: String get() = s("age"); set(v) = p("age", v)
    var interests: String get() = s("interests"); set(v) = p("interests", v)
    var vibe: String get() = s("vibe"); set(v) = p("vibe", v)
    var onboarded: Boolean get() = b("onboarded"); set(v) = pb("onboarded", v)
    var skipped: Boolean get() = b("skipped"); set(v) = pb("skipped", v)
    var pro: Boolean get() = b("pro"); set(v) = pb("pro", v)
    var doneTasks: String get() = s("done"); set(v) = p("done", v)
    var model: String get() = s("model", "gemini-3.5-flash-lite"); set(v) = p("model", v)
    var customModel: String get() = s("cmodel"); set(v) = p("cmodel", v)
    var lang: String get() = s("lang", "auto"); set(v) = p("lang", v)
    var wake: Boolean get() = b("wake"); set(v) = pb("wake", v)
    var speak: Boolean get() = b("speak", true); set(v) = pb("speak", v)
    var pitch: Float get() = sp.getFloat("pitch", 0.8f); set(v) = sp.edit().putFloat("pitch", v).apply()
    var rate: Float get() = sp.getFloat("rate", 1.0f); set(v) = sp.edit().putFloat("rate", v).apply()

    var geminiKey: String get() = sec.getString("gk", "") ?: ""; set(v) = sec.edit().putString("gk", v).apply()
    var orKey: String get() = sec.getString("ork", "") ?: ""; set(v) = sec.edit().putString("ork", v).apply()
    var pvKey: String get() = sec.getString("pvk", "") ?: ""; set(v) = sec.edit().putString("pvk", v).apply()

    fun userContext(): String = buildString {
        append("USER PROFILE — name: ").append(name.ifBlank { "unknown" })
        if (age.isNotBlank()) append(", age: ").append(age)
        if (interests.isNotBlank()) append(", interests: ").append(interests)
        if (vibe.isNotBlank()) append(", preferred vibe: ").append(vibe)
        append(". Talk like a friend who truly knows them.")
    }
}
