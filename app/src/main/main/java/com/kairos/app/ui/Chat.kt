package com.kairos.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.ChatMsg
import com.kairos.app.Mode

class Pickers(val image: () -> Unit, val video: () -> Unit, val file: () -> Unit, val upload: () -> Unit)

@Composable
fun ChatScreen(vm: KairosVM, base: Mode, bottomPad: Dp, pickers: Pickers, ensureMic: (() -> Unit) -> Unit, onLocked: () -> Unit, creatorAllowed: Boolean) {
    var creator by remember { mutableStateOf(false) }
    val mode = if (base == Mode.GENERAL && creator) Mode.CREATOR else base
    val list = if (base == Mode.CODE) vm.code else vm.chat
    var text by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val state = rememberLazyListState()
    LaunchedEffect(list.size, list.lastOrNull()?.text) { if (list.isNotEmpty()) state.animateScrollToItem(list.lastIndex) }

    Column(Modifier.fillMaxSize().imePadding().padding(bottom = bottomPad)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (base == Mode.CODE) "Code Assistant" else "Chat", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
            if (base == Mode.GENERAL) GlassChip(if (creatorAllowed) "🎬 Creator" else "🔒 Creator", creator) {
                if (creatorAllowed) creator = !creator else onLocked()
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) item {
                Text(
                    if (base == Mode.CODE) "Paste code, ask for a fix, or describe an Android feature." else "Ask anything — বাংলা, English or Banglish.",
                    color = Color.White.copy(0.5f), modifier = Modifier.padding(24.dp)
                )
            }
            items(list, key = { it.id }) { Bubble(it) }
        }
        if (vm.attachments.isNotEmpty()) LazyRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.attachments.toList()) { a ->
                Box(Modifier.glass(RoundedCornerShape(14.dp)).padding(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (a.preview != null) Image(a.preview.asImageBitmap(), null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                        else Icon(Icons.Rounded.AttachFile, null, tint = Cyan)
                        Text(a.name.take(14), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
                        Icon(Icons.Rounded.Close, "Remove", tint = Color.White, modifier = Modifier.size(20.dp).pressable { vm.attachments.remove(a) })
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), strength = 0.12f).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val rot by animateFloatAsState(if (menu) 45f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "plus")
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(0.1f)).pressable { menu = !menu }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, "Attach", tint = Cyan, modifier = Modifier.rotate(rot))
                }
                GlassField(text, { text = it }, "Ask anything…", Modifier.weight(1f).padding(horizontal = 6.dp), singleLine = false)
                Icon(Icons.Rounded.Mic, "Mic", tint = Color.White, modifier = Modifier.size(42.dp).padding(9.dp).pressable { ensureMic { vm.dictate { text = it } } })
                Box(Modifier.size(42.dp).clip(CircleShape).background(SendBrush).pressable(!vm.busy) { vm.send(text, mode); text = ""; menu = false }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                menu,
                modifier = Modifier.align(Alignment.TopStart).offset(y = (-176).dp),
                enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), transformOrigin = TransformOrigin(0f, 1f)) + fadeIn() + expandVertically(),
                exit = scaleOut(transformOrigin = TransformOrigin(0f, 1f)) + fadeOut()
            ) {
                Column(Modifier.glass(RoundedCornerShape(22.dp), strength = 0.16f).padding(8.dp)) {
                    MenuRow(Icons.Rounded.Image, "Image") { menu = false; pickers.image() }
                    MenuRow(Icons.Rounded.Videocam, "Video") { menu = false; pickers.video() }
                    MenuRow(Icons.Rounded.AttachFile, "File") { menu = false; pickers.file() }
                    MenuRow(Icons.Rounded.CloudUpload, "Upload") { menu = false; pickers.upload() }
                }
            }
        }
    }
}

private val SendBrush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Blue, Cyan))

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.width(150.dp).pressable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Cyan); Spacer(Modifier.width(12.dp)); Text(label, color = Color.White)
    }
}

@Composable
fun Bubble(m: ChatMsg) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val shape = RoundedCornerShape(22.dp, 22.dp, if (m.user) 22.dp else 6.dp, if (m.user) 6.dp else 22.dp)
    AnimatedVisibility(shown, enter = fadeIn(tween(250)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 4 }) {
        Box(Modifier.fillMaxWidth(), contentAlignment = if (m.user) Alignment.CenterEnd else Alignment.CenterStart) {
            Column(
                Modifier.widthIn(max = 330.dp).glass(shape, tint = if (m.user) Blue else Color(0xFF7FB8FF), strength = if (m.user) 0.18f else 0.09f).padding(14.dp)
            ) {
                if (m.attach.isNotEmpty()) Text("📎 " + m.attach.joinToString(), color = Ice, fontSize = 12.sp)
                if (m.text.isEmpty() && !m.user) Typing() else MessageText(m.text)
            }
        }
    }
}

@Composable
private fun Typing() {
    val inf = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        for (i in 0..2) {
            val a by inf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(Cyan))
        }
    }
}

@Composable
private fun MessageText(text: String) {
    text.split("```").forEachIndexed { i, p ->
        if (i % 2 == 1) {
            val nl = p.indexOf('\n')
            val lang = if (nl >= 0) p.substring(0, nl).trim() else ""
            CodeCard(lang, (if (nl >= 0) p.substring(nl + 1) else p).trimEnd())
        } else if (p.isNotBlank()) Text(p.trim(), color = Color.White, fontSize = 16.sp, lineHeight = 22.sp)
    }
}

private val KW = Regex("\\b(fun|val|var|class|object|if|else|for|while|return|import|package|def|function|const|let|public|private|static|void|int|new|when|in|is|null|true|false|try|catch|from|print)\\b")
private val NUM = Regex("\\b\\d+(\\.\\d+)?\\b")
private val STR = Regex("\"[^\"\\n]*\"|'[^'\\n]*'")
private val COM = Regex("//.*|#.*")

private fun highlight(code: String): AnnotatedString = buildAnnotatedString {
    append(code)
    fun paint(re: Regex, c: Color) = re.findAll(code).forEach { addStyle(SpanStyle(color = c), it.range.first, it.range.last + 1) }
    paint(KW, Color(0xFFB794FF)); paint(NUM, Color(0xFFFFB86B)); paint(STR, Color(0xFF7CE3A1)); paint(COM, Color(0xFF7A869A))
}

@Composable
private fun CodeCard(lang: String, code: String) {
    val clip = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xCC03060F))) {
        Row(Modifier.fillMaxWidth().background(Color.White.copy(0.07f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lang.ifBlank { "code" }, color = Cyan, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ContentCopy, "Copy", tint = if (copied) Color(0xFF7CE3A1) else Color.White, modifier = Modifier.size(18.dp).pressable { clip.setText(AnnotatedString(code)); copied = true })
        }
        Text(highlight(code), color = Color(0xFFE6EEFF), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp))
    }
}
