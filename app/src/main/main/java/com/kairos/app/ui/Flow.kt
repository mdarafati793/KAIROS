package com.kairos.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kairos.app.R
import com.kairos.app.data.Prefs
import kotlinx.coroutines.delay

@Composable
fun SplashScreen() {
    GlassBackdrop {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            var go by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { go = true }
            val s by animateFloatAsState(if (go) 1f else 0.7f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "s")
            Image(painterResource(R.drawable.kairos_logo), "KAIROS", Modifier.fillMaxWidth(0.7f).scale(s).clip(RoundedCornerShape(36.dp)))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(Prefs.name) }
    var age by remember { mutableStateOf(Prefs.age) }
    var custom by remember { mutableStateOf("") }
    var vibe by remember { mutableStateOf(Prefs.vibe.ifBlank { "Chill best friend" }) }
    val picked = remember { mutableStateListOf<String>().apply { addAll(Prefs.interests.split(",").map { it.trim() }.filter { it.isNotBlank() }) } }
    val base = listOf("Video Games", "Free Fire", "Content Creation", "Vlogs", "Music", "Cricket", "Football", "Coding & Tech", "Video Editing", "Movies & Anime", "Study & Productivity")
    val vibes = listOf("Chill best friend", "Hype motivator", "Funny roaster", "Calm & serious")

    GlassBackdrop {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.kairos_logo), null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.weight(1f))
                repeat(3) { Box(Modifier.padding(3.dp).size(if (it == step) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (it <= step) Cyan else Color.White.copy(0.25f))) }
            }
            AnimatedContent(
                step, modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 18.dp), label = "ob",
                transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) }
            ) { s ->
                Column(Modifier.glass(RoundedCornerShape(28.dp)).padding(22.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (s) {
                        0 -> {
                            Text("Let's meet 👋", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("What should I call you?", color = Color.White.copy(0.7f))
                            GlassField(name, { name = it }, "Your name")
                            GlassField(age, { age = it.filter(Char::isDigit).take(3) }, "Your age", keyboard = KeyboardType.Number)
                        }
                        1 -> {
                            Text("What are you into?", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                (base + picked.filter { it !in base }).forEach { h ->
                                    GlassChip(h, h in picked) { if (h in picked) picked.remove(h) else picked.add(h) }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassField(custom, { custom = it }, "Add custom hobby", Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                GlassButton("Add", { if (custom.isNotBlank()) { picked.add(custom.trim()); custom = "" } }, primary = false)
                            }
                        }
                        else -> {
                            Text("Pick my vibe", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("I'll talk to you like a friend who knows you.", color = Color.White.copy(0.7f))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                vibes.forEach { v -> GlassChip(v, v == vibe) { vibe = v } }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                GlassButton("Back", { if (step > 0) step-- }, primary = false, enabled = step > 0)
                GlassButton(if (step == 2) "Finish" else "Next", {
                    if (step < 2) step++ else {
                        Prefs.name = name.trim(); Prefs.age = age; Prefs.vibe = vibe
                        Prefs.interests = picked.joinToString(","); Prefs.onboarded = true
                        onDone()
                    }
                }, enabled = step != 0 || name.isNotBlank())
            }
        }
    }
}

private class SocialTask(val id: String, val title: String, val action: String, val url: String, val mark: String, val color: Color)

@Composable
fun UnlockScreen(onSkip: () -> Unit, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val tasks = remember {
        listOf(
            SocialTask("fb", "Follow on Facebook", "Follow", "https://www.facebook.com/share/19rbyRSzGW/", "f", Color(0xFF1877F2)),
            SocialTask("ig", "Follow on Instagram", "Follow", "https://www.instagram.com/sam.bros46?dmpr=MWoxcmtxbjQycHF2Yw==", "IG", Color(0xFFD62976)),
            SocialTask("yt", "Subscribe on YouTube", "Subscribe", "https://youtube.com/@sambros-o1v?si=MxGRhOb_v-s-U60c", "▶", Color(0xFFFF0000))
        )
    }
    val done = remember { mutableStateListOf<String>().apply { addAll(Prefs.doneTasks.split(",").filter { it.isNotBlank() }) } }
    var pending by remember { mutableStateOf<String?>(null) }
    var leftAt by remember { mutableLongStateOf(0L) }
    var celebrate by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && pending != null) {
                val id = pending!!
                if (System.currentTimeMillis() - leftAt >= 3000) {
                    if (id !in done) { done.add(id); Prefs.doneTasks = done.joinToString(",") }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else Toast.makeText(ctx, "Stay a little longer on the page, then come back 🙂", Toast.LENGTH_SHORT).show()
                pending = null
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(celebrate) { if (celebrate) { delay(1600); onUnlocked() } }
    val all = done.size >= tasks.size

    GlassBackdrop {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("Skip for Now", color = Color.White.copy(0.7f), modifier = Modifier.pressable { onSkip() }.padding(8.dp))
            }
            Text("Unlock Full Access 🔓", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Finish 3 quick community tasks to unlock Pro: Live Screen Vision, Auto-Tap/Scroll, Creator & CapCut Coach, Background Wake Word and Pro Coding Mode.",
                color = Color.White.copy(0.75f), modifier = Modifier.padding(vertical = 12.dp)
            )
            tasks.forEach { t ->
                val ok = t.id in done
                Row(Modifier.padding(vertical = 6.dp).fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(t.color), contentAlignment = Alignment.Center) { Text(t.mark, color = Color.White, fontWeight = FontWeight.Bold) }
                    Text(t.title, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Crossfade(ok, label = "lock") { Text(if (it) "✅" else "🔒", fontSize = 22.sp) }
                    Spacer(Modifier.width(8.dp))
                    GlassButton(if (ok) "Done" else t.action, {
                        pending = t.id; leftAt = System.currentTimeMillis()
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t.url))) } catch (e: Exception) { pending = null }
                    }, enabled = !ok, primary = !ok)
                }
            }
            Spacer(Modifier.weight(1f))
            GlassButton("Unlock Full Access Now 🚀", { Prefs.pro = true; celebrate = true }, Modifier.fillMaxWidth(), enabled = all)
        }
        AnimatedVisibility(celebrate, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), contentAlignment = Alignment.Center) {
                val s by animateFloatAsState(if (celebrate) 1f else 0.3f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow), label = "party")
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(s)) {
                    Text("🎉", fontSize = 90.sp)
                    Text("KAIROS Pro unlocked", color = Cyan, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
