package com.kairos.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kairos.app.data.Prefs
import com.kairos.app.ui.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: KairosVM by viewModels()
    private var pendingMic: (() -> Unit)? = null
    private val micL = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) pendingMic?.invoke(); pendingMic = null }
    private val notifL = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val imgL = registerForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> vm.addUri(u, "image") } }
    private val vidL = registerForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> vm.addUri(u, "video") } }
    private val fileL = registerForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> vm.addUri(u, "file") } }
    private val upL = registerForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> vm.addUri(u, "file") } }

    private fun ensureMic(then: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) then()
        else { pendingMic = then; micL.launch(Manifest.permission.RECORD_AUDIO) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifL.launch(Manifest.permission.POST_NOTIFICATIONS)
        val pickers = Pickers({ imgL.launch("image/*") }, { vidL.launch("video/*") }, { fileL.launch("text/*") }, { upL.launch("*/*") })
        setContent { KairosTheme { KairosRoot(vm, pickers, ::ensureMic) } }
    }
}

@Composable
fun KairosRoot(vm: KairosVM, pickers: Pickers, ensureMic: (() -> Unit) -> Unit) {
    var stage by remember { mutableStateOf(if (!Prefs.onboarded) "onboard" else if (!Prefs.pro && !Prefs.skipped) "unlock" else "main") }
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1300); splash = false }

    Crossfade(if (splash) "splash" else stage, label = "stage") { s ->
        when (s) {
            "splash" -> SplashScreen()
            "onboard" -> OnboardingScreen { stage = "unlock" }
            "unlock" -> UnlockScreen(onSkip = { Prefs.skipped = true; stage = "main" }, onUnlocked = { stage = "main" })
            else -> MainShell(vm, pickers, ensureMic) { stage = "unlock" }
        }
    }
}

@Composable
private fun MainShell(vm: KairosVM, pickers: Pickers, ensureMic: (() -> Unit) -> Unit, openUnlock: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var settings by remember { mutableStateOf(false) }
    var lockedDlg by remember { mutableStateOf(false) }
    val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val navPad = if (imeUp) 0.dp else 84.dp

    if (settings) { SettingsScreen(vm, { settings = false }, openUnlock, ensureMic); return }

    GlassBackdrop {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            AnimatedContent(
                tab, label = "tab",
                transitionSpec = { (fadeIn(tween(260)) + scaleIn(initialScale = 0.96f)) togetherWith (fadeOut(tween(160)) + scaleOut(targetScale = 1.03f)) }
            ) { t ->
                when (t) {
                    Tab.Home -> HomeScreen(vm, navPad, { tab = it }, { settings = true }, { if (Prefs.pro) tab = Tab.Chat else lockedDlg = true })
                    Tab.Chat -> ChatScreen(vm, Mode.GENERAL, navPad, pickers, ensureMic, { lockedDlg = true }, Prefs.pro)
                    Tab.Voice -> VoiceScreen(vm, navPad, ensureMic)
                    Tab.Code -> if (Prefs.pro) ChatScreen(vm, Mode.CODE, navPad, pickers, ensureMic, { lockedDlg = true }, true) else LockedPanel("Pro Coding Mode", openUnlock)
                    Tab.Tools -> ToolsScreen(navPad) { q -> tab = Tab.Chat; vm.send(q, Mode.GENERAL) }
                }
            }
            AnimatedVisibility(!imeUp, Modifier.align(Alignment.BottomCenter), enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                BottomNav(tab, { tab = it })
            }
        }
    }

    if (lockedDlg) AlertDialog(
        onDismissRequest = { lockedDlg = false }, containerColor = Navy,
        title = { Text("🔒 Pro feature", color = Color.White) },
        text = { Text("Complete the remaining unlock tasks to use this.", color = Color.White.copy(0.8f)) },
        confirmButton = { TextButton({ lockedDlg = false; openUnlock() }) { Text("Unlock now", color = Cyan) } },
        dismissButton = { TextButton({ lockedDlg = false }) { Text("Later", color = Color.White.copy(0.7f)) } }
    )
}
