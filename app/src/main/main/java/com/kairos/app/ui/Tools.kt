package com.kairos.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(bottomPad: Dp, onAsk: (String) -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val items by dao.items().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(false) }
    var topic by remember { mutableStateOf("") }

    // Pomodoro
    var focus by remember { mutableStateOf(true) }
    var left by remember { mutableIntStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running, focus) {
        while (running && left > 0) { delay(1000); left-- }
        if (running && left == 0) { focus = !focus; left = if (focus) 25 * 60 else 5 * 60 }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = bottomPad + 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Tools", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        item {
            Column(Modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (focus) "🍅 Pomodoro · Focus" else "☕ Pomodoro · Break", color = Ice)
                Text("%02d:%02d".format(left / 60, left % 60), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(if (running) "Pause" else "Start", { running = !running }, icon = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow)
                    GlassButton("Reset", { running = false; focus = true; left = 25 * 60 }, primary = false)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📚 Quick topic summary", color = Ice)
                GlassField(topic, { topic = it }, "Topic (e.g. Newton's laws)")
                GlassButton("Summarize", { if (topic.isNotBlank()) { onAsk("Give me a quick study summary of: $topic"); topic = "" } }, enabled = topic.isNotBlank())
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Reminders · Tasks · Dates · Study", color = Ice, modifier = Modifier.weight(1f))
                GlassButton("Add", { dialog = true }, icon = Icons.Rounded.Add)
            }
        }
        if (items.isEmpty()) item { Text("Nothing yet. Add a reminder, task, birthday or study subject.", color = Color.White.copy(0.5f)) }
        items(items, key = { it.id }) { it ->
            Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), strength = 0.08f).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (it.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = Cyan,
                    modifier = Modifier.size(30.dp).pressable { scope.launch(Dispatchers.IO) { dao.upd(it.copy(done = !it.done)) } })
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(it.title, color = Color.White)
                    Text(it.kind + if (it.time > 0) " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.time)) else "", color = Color.White.copy(0.5f), fontSize = 12.sp)
                }
                Icon(Icons.Rounded.Delete, "Delete", tint = Color.White.copy(0.7f), modifier = Modifier.size(28.dp).pressable {
                    scope.launch(Dispatchers.IO) { Reminders.cancel(ctx, it.id); dao.del(it) }
                })
            }
        }
        item {
            Column(Modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🎬 Creator mode", color = Ice)
                Text("Open any funny video or CapCut project, tap the floating orb and say “give me a viral title and hashtags for this video”. You can also attach a video or image in Chat with the Creator chip on.", color = Color.White.copy(0.75f), fontSize = 14.sp)
            }
        }
    }

    if (dialog) AddItemDialog(onDismiss = { dialog = false }) { kind, title, amount, unit ->
        dialog = false
        val at = if (kind == "study") 0L else System.currentTimeMillis() + amount * when (unit) { "min" -> 60_000L; "hr" -> 3_600_000L; else -> 86_400_000L }
        scope.launch(Dispatchers.IO) {
            val id = dao.add(Item(kind = kind, title = title, time = at))
            if (at > 0) Reminders.schedule(ctx, id, title, at)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddItemDialog(onDismiss: () -> Unit, onAdd: (String, String, Long, String) -> Unit) {
    var kind by remember { mutableStateOf("reminder") }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("30") }
    var unit by remember { mutableStateOf("min") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Navy,
        title = { Text("New item", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("reminder", "task", "date", "study").forEach { k -> GlassChip(k, k == kind) { kind = k } }
                }
                GlassField(title, { title = it }, if (kind == "study") "Subject / topic" else "Title")
                if (kind != "study") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("In", color = Color.White)
                        GlassField(amount, { amount = it.filter(Char::isDigit).take(4) }, "30", Modifier.width(80.dp), keyboard = KeyboardType.Number)
                        listOf("min", "hr", "day").forEach { u -> GlassChip(u, u == unit) { unit = u } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onAdd(kind, title.trim(), amount.toLongOrNull() ?: 30L, unit) }) { Text("Add", color = Cyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(0.7f)) } }
    )
}
