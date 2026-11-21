package com.kairos.app

enum class OrbState { Idle, Listening, Thinking, Speaking }
enum class Mode { GENERAL, CODE, CREATOR }
data class ChatMsg(val id: Long = System.nanoTime(), val user: Boolean, val text: String, val attach: List<String> = emptyList())

class KairosApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        com.kairos.app.data.Prefs.init(this)
    }
}
