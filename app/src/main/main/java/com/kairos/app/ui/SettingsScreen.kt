package com.kairos.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kairos.app.ai.Brain
import com.kairos.app.data.Prefs
import com.kairos.app.service.KairosCoreService
import kotlinx.coroutines.launch

private fun accessibilityOn(c: Context): Boolean =
    (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(c.packageName)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: KairosVM, onBack: () -> Unit, onUnlock: () -> Unit, ensureMic: (() -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    var gk by remember { mutableStateOf(Prefs.geminiKey) }
    var ork by remember { mutableStateOf(Prefs.orKey) }
    var pvk by remember { mutableStateOf(Prefs.pvKey) }
    var show by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(Prefs.model) }
    var custom by remember { mutableStateOf(Prefs.customModel) }
    var lang by remember { mutableStateOf(Prefs.lang) }
    var wake by remember { mutableStateOf(Prefs.wake) }
    var speak by remember { mutableStateOf(Prefs.speak) }
    var pitch by remember { mutableFloatStateOf(Prefs.pitch) }
    var rate by remember { mutableFloatStateOf(Prefs.rate) }
    var testMsg by remember { mutableStateOf("") }
    var orbOn by remember { mutableStateOf(false) }

    val models = listOf(
        "Ultra-fast voice & daily (default)" to "gemini-3.5-flash-lite",
        "Fast & balanced" to "gemini-3.8-flash",
        "Smart multimodal & Creator" to "gemini-4-hydrogen",
        "Deep reasoning & complex coding" to "gemini-4-carbon"
    )

    GlassBackdrop {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(40.dp).padding(8.dp).pressable { onBack() })
                Text("Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            Section("Gemini AI brain") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassField(gk, { gk = it; Prefs.geminiKey = it.trim() }, "Google Gemini API key", Modifier.weight(1f), secret = !show)
                    Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show/Hide", tint = Color.White, modifier = Modifier.size(44.dp).padding(10.dp).pressable { show = !show })
                }
                GlassField(ork, { ork = it; Prefs.orKey = it.trim() }, "OpenRouter key (optional fallback)", secret = !show)
                GlassButton("Test API connection", { testMsg = "Testing…"; scope.launch { testMsg = Brain.test() } }, primary = false)
                if (testMsg.isNotBlank()) Text(testMsg, color = Ice, fontSize = 14.sp)
            }

            Section("Model") {
                models.forEach { (label, id) ->
                    Row(Modifier.fillMaxWidth().pressable { model = id; Prefs.model = id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(model == id && custom.isBlank(), { model = id; Prefs.model = id }, colors = RadioButtonDefaults.colors(selectedColor = Cyan))
                        Column { Text(label, color = Color.White); Text(id, color = Color.White.copy(0.5f), fontSize = 12.sp) }
                    }
                }
                GlassField(custom, { custom = it; Prefs.customModel = it.trim() }, "Custom model ID (overrides the above)")
            }

            Section("Voice & wake word") {
                ToggleRow("Speak replies aloud", speak) { speak = it; Prefs.speak = it }
                ToggleRow(if (Prefs.pro) "“Hey KAIROS” background listening" else "🔒 “Hey KAIROS” background listening", wake && Prefs.pro) {
                    if (!Prefs.pro) onUnlock() else { wake = it; Prefs.wake = it }
                }
                GlassField(pvk, { pvk = it; Prefs.pvKey = it.trim() }, "Picovoice AccessKey (for hey_kairos.ppn)", secret = !show)
                Text("Male voice pitch ${"%.2f".format(pitch)}", color = Color.White)
                Slider(pitch, { pitch = it; Prefs.pitch = it }, valueRange = 0.5f..1.3f, colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan))
                Text("Speech speed ${"%.2f".format(rate)}", color = Color.White)
                Slider(rate, { rate = it; Prefs.rate = it }, valueRange = 0.6f..1.6f, colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan))
                GlassButton("Preview voice", { vm.voice.speak("Hey ${Prefs.name.ifBlank { "friend" }}, KAIROS here. কেমন আছো?") }, primary = false, icon = Icons.Rounded.PlayArrow)
                Text("Language", color = Color.White)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto" to "Auto (বাংলা + English)", "bn" to "Bangla priority", "en" to "English priority").forEach { (k, l) ->
                        GlassChip(l, lang == k) { lang = k; Prefs.lang = k }
                    }
                }
            }

            Section("Floating orb & background service") {
                GlassButton(if (orbOn) "Stop KAIROS orb" else "Start KAIROS orb", {
                    ensureMic {
                        if (!orbOn) {
                            if (!Settings.canDrawOverlays(ctx)) ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                            else { ContextCompat.startForegroundService(ctx, Intent(ctx, KairosCoreService::class.java)); orbOn = true }
                        } else { ctx.stopService(Intent(ctx, KairosCoreService::class.java)); orbOn = false }
                    }
                }, icon = Icons.Rounded.GraphicEq)
                Text("Tap the orb over any app, or say “Hey KAIROS” (Pro), and talk without leaving the screen.", color = Color.White.copy(0.65f), fontSize = 13.sp)
            }

            Section("Permissions") {
                key(tick) {
                    val mic = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    PermRow("Microphone", mic) { ensureMic {} }
                    PermRow("Display over other apps", Settings.canDrawOverlays(ctx)) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                    }
                    PermRow("Accessibility service", accessibilityOn(ctx)) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    PermRow("Battery optimization exemption", ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)) {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                    }
                }
            }

            Section("Pro access") {
                Text(if (Prefs.pro) "✅ Pro unlocked" else "🔒 Pro features locked", color = Color.White)
                if (!Prefs.pro) GlassButton("Unlock Full Access 🔓", onUnlock)
                GlassButton("Clear chat memory", { vm.clearChat() }, primary = false, icon = Icons.Rounded.Delete)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Ice, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Cyan, checkedThumbColor = Color.White))
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable { onClick() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Text(if (ok) "Granted ✅" else "Missing ⚠️", color = if (ok) Color(0xFF7CE3A1) else Color(0xFFFFC857), fontSize = 13.sp)
    }
}
