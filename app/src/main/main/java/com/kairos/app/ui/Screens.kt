package com.kairos.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.OrbState
import com.kairos.app.R
import com.kairos.app.data.Prefs

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home), Chat("Chat", Icons.Rounded.Chat), Voice("Voice", Icons.Rounded.Mic),
    Code("Code", Icons.Rounded.Code), Tools("Tools", Icons.Rounded.Build)
}

@Composable
fun orbStateOf(vm: KairosVM): Pair<OrbState, Float> {
    val vs by vm.voice.state.collectAsState()
    val lv by vm.voice.level.collectAsState()
    return (if (vm.thinking && vs == OrbState.Idle) OrbState.Thinking else vs) to lv
}

@Composable
fun BottomNav(tab: Tab, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = 18.dp, vertical = 12.dp).glass(RoundedCornerShape(32.dp), strength = 0.14f).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.values().forEach { t ->
            val sel = t == tab
            val tint by animateColorAsState(if (sel) Cyan else Color.White.copy(0.6f), tween(250), label = "nav")
            val locked = t == Tab.Code && !Prefs.pro
            Row(
                Modifier.weight(if (sel) 1.7f else 1f).animateContentSize(spring(Spring.DampingRatioLowBouncy)).clip(RoundedCornerShape(24.dp))
                    .background(if (sel) Color.White.copy(0.12f) else Color.Transparent).pressable { onTab(t) }.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (locked) Icons.Rounded.Lock else t.icon, t.label, tint = tint, modifier = Modifier.size(22.dp))
                AnimatedVisibility(sel) { Text(t.label, color = tint, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp), maxLines = 1) }
            }
        }
    }
}

@Composable
fun HomeScreen(vm: KairosVM, bottomPad: Dp, goto: (Tab) -> Unit, onSettings: () -> Unit, onCreator: () -> Unit) {
    val (st, lv) = orbStateOf(vm)
    val name = Prefs.name
    Column(Modifier.fillMaxSize().padding(bottom = bottomPad).padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.kairos_logo), "KAIROS", Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
            Text("  KAIROS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Settings, "Settings", tint = Color.White, modifier = Modifier.size(40.dp).padding(8.dp).pressable { onSettings() })
        }
        Spacer(Modifier.weight(0.6f))
        KairosOrb(st, lv, Modifier.fillMaxWidth(0.72f).pressable { goto(Tab.Voice) })
        Spacer(Modifier.height(4.dp))
        Column(Modifier.glass(RoundedCornerShape(26.dp)).padding(horizontal = 24.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (name.isBlank()) "Hello" else "Hello, $name", color = Ice, fontSize = 14.sp)
            Text("How can I help you today?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), strength = 0.12f).pressable { goto(Tab.Chat) }.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Mic, null, tint = Color.White)
            Text("Ask anything…", color = Color.White.copy(0.65f), modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            Icon(Icons.Rounded.GraphicEq, null, tint = Cyan)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard("Voice", Icons.Rounded.Mic, false, Modifier.weight(1f)) { goto(Tab.Voice) }
            FeatureCard("Chat", Icons.Rounded.Chat, false, Modifier.weight(1f)) { goto(Tab.Chat) }
            FeatureCard("Code", Icons.Rounded.Code, !Prefs.pro, Modifier.weight(1f)) { goto(Tab.Code) }
            FeatureCard("Tools", Icons.Rounded.Build, false, Modifier.weight(1f)) { goto(Tab.Tools) }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.glass(RoundedCornerShape(24.dp), strength = 0.08f).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassChip("⏰ Reminder", false) { goto(Tab.Tools) }
            GlassChip(if (Prefs.pro) "🎬 Creator" else "🔒 Creator", false) { onCreator() }
        }
        Spacer(Modifier.weight(0.4f))
    }
}

@Composable
private fun FeatureCard(label: String, icon: ImageVector, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.glass(RoundedCornerShape(22.dp)).pressable(onClick = onClick).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (locked) Icons.Rounded.Lock else icon, label, tint = Cyan, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
fun VoiceScreen(vm: KairosVM, bottomPad: Dp, ensureMic: (() -> Unit) -> Unit) {
    val (st, lv) = orbStateOf(vm)
    val lastReply = vm.chat.lastOrNull { !it.user }?.text ?: ""
    val inf = rememberInfiniteTransition(label = "mic")
    val glow by inf.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "g")
    Column(Modifier.fillMaxSize().padding(bottom = bottomPad).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.3f))
        KairosOrb(st, lv, Modifier.fillMaxWidth(0.9f))
        Waveform(if (st == OrbState.Speaking) 0.55f else lv, st == OrbState.Listening || st == OrbState.Speaking, Modifier.fillMaxWidth().height(56.dp))
        Text(
            when (st) { OrbState.Idle -> "Tap the mic and talk"; OrbState.Listening -> "Listening…"; OrbState.Thinking -> "Thinking…"; OrbState.Speaking -> "Speaking — tap to stop" },
            color = Ice, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp)
        )
        AnimatedContent(if (vm.heard.isNotBlank() && st != OrbState.Speaking) vm.heard else lastReply.take(220), label = "txt") { t ->
            Text(t, color = Color.White.copy(0.85f), textAlign = TextAlign.Center, fontSize = 16.sp, maxLines = 5, modifier = Modifier.heightIn(min = 90.dp))
        }
        Spacer(Modifier.weight(0.4f))
        val active = st == OrbState.Listening
        Box(
            Modifier.size(84.dp).scale(if (active) glow else 1f).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Blue, Cyan))).border(2.dp, Color.White.copy(0.5f), CircleShape)
                .pressable { ensureMic { vm.toggleVoice() } },
            contentAlignment = Alignment.Center
        ) { Icon(if (st == OrbState.Idle || st == OrbState.Thinking) Icons.Rounded.Mic else Icons.Rounded.Stop, "Mic", tint = Color.White, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun LockedPanel(title: String, onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Column(Modifier.glass(RoundedCornerShape(28.dp)).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Lock, null, tint = Cyan, modifier = Modifier.size(40.dp))
            Text("$title is a Pro feature", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text("Complete the 3 quick community tasks to unlock it.", color = Color.White.copy(0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
            GlassButton("Unlock Full Access 🔓", onUnlock)
        }
    }
}
