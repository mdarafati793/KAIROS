package com.kairos.ai.ui.components
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*

sealed interface MdBlock { data class Text(val body: String) : MdBlock; data class Code(val lang: String, val code: String) : MdBlock }

fun parseMd(input: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>(); val txt = StringBuilder(); val code = StringBuilder()
    var inCode = false; var lang = ""
    for (line in input.lines()) {
        val t = line.trimStart()
        if (t.startsWith("```")) {
            if (!inCode) { if (txt.isNotEmpty()) { out.add(MdBlock.Text(txt.toString().trimEnd())); txt.clear() }; inCode = true; lang = t.removePrefix("```").trim() }
            else { out.add(MdBlock.Code(lang, code.toString().trimEnd())); code.clear(); inCode = false; lang = "" }
        } else if (inCode) code.appendLine(line) else txt.appendLine(line)
    }
    if (inCode && code.isNotEmpty()) out.add(MdBlock.Code(lang, code.toString().trimEnd()))
    else if (txt.isNotEmpty()) out.add(MdBlock.Text(txt.toString().trimEnd()))
    return out
}

@Composable
fun RichMarkdownContent(markdown: String, textColor: Color, isStreaming: Boolean = false) {
    val blocks = remember(markdown, isStreaming) { parseMd(if (isStreaming) "$markdown▌" else markdown) }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { b ->
                when (b) {
                    is MdBlock.Text -> MdParagraphs(b.body, textColor)
                    is MdBlock.Code -> CodeCard(b.lang, b.code)
                }
            }
        }
    }
}

@Composable
private fun MdParagraphs(text: String, baseColor: Color) {
    val accent = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        text.lines().forEach { raw ->
            val t = raw.trimStart()
            when {
                t.startsWith("### ") -> Text(inlineMd(t.drop(4), baseColor, codeBg), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = accent))
                t.startsWith("## ") -> Text(inlineMd(t.drop(3), baseColor, codeBg), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = baseColor))
                t.startsWith("# ") -> Text(inlineMd(t.drop(2), baseColor, codeBg), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = baseColor))
                t.startsWith("- ") || t.startsWith("* ") -> Row {
                    Text("•  ", color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(inlineMd(t.drop(2), baseColor, codeBg), style = MaterialTheme.typography.bodyMedium, color = baseColor)
                }
                Regex("^\\d+\\.\\s+.*").matches(t) -> {
                    val idx = t.indexOf('.')
                    Row {
                        Text("${t.substring(0, idx + 1)} ", color = accent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(inlineMd(t.substring(idx + 1).trimStart(), baseColor, codeBg), style = MaterialTheme.typography.bodyMedium, color = baseColor)
                    }
                }
                t == "---" || t == "***" -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                else -> Text(inlineMd(raw, baseColor, codeBg), style = MaterialTheme.typography.bodyMedium, color = baseColor)
            }
        }
    }
}

private fun inlineMd(text: String, base: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = base)) { append(text.substring(i + 2, end)) }; i = end + 2 }
                else { append(text[i]); i++ }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = base, fontSize = 13.sp)) { append(" ${text.substring(i + 1, end)} ") }; i = end + 1 }
                else { append(text[i]); i++ }
            }
            text[i] == '*' && (i + 1 >= text.length || text[i + 1] != '*') -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) { withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = base)) { append(text.substring(i + 1, end)) }; i = end + 1 }
                else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

@Composable
fun CodeCard(language: String, code: String) {
    val clip = LocalClipboardManager.current; val ctx = LocalContext.current
    val highlighted = remember(code) { highlightSyntax(code) }
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF0D1117), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().background(Color(0xFF161B22)).padding(horizontal = 12.dp, vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(language.ifBlank { "code" }.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = Color(0xFF8B949E), fontWeight = FontWeight.Bold))
                TextButton(onClick = { clip.setText(AnnotatedString(code)); Toast.makeText(ctx, "Code copied!", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Rounded.ContentCopy, "Copy Code", tint = Color(0xFF79C0FF), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp)); Text("Copy Code", style = MaterialTheme.typography.labelSmall, color = Color(0xFF79C0FF))
                }
            }
            Text(highlighted, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp))
        }
    }
}

private val KW = setOf("fun","val","var","class","object","interface","return","if","else","when","for","while","suspend","import","package","private","override","data","sealed","def","async","await","const","let","function","try","catch","true","false","null")
private fun highlightSyntax(code: String): AnnotatedString = buildAnnotatedString {
    val def = Color(0xFFE6EDF3); val kw = Color(0xFFFF7B72); val str = Color(0xFFA5D6FF); val cmt = Color(0xFF8B949E); val num = Color(0xFF79C0FF)
    code.lines().forEachIndexed { idx, line ->
        val t = line.trimStart()
        if (t.startsWith("//") || t.startsWith("#")) withStyle(SpanStyle(color = cmt, fontStyle = FontStyle.Italic)) { append(line) }
        else {
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' || c == '\'' -> { val e = line.indexOf(c, i + 1); val se = if (e != -1) e + 1 else line.length; withStyle(SpanStyle(color = str)) { append(line.substring(i, se)) }; i = se }
                    c.isDigit() -> { var j = i; while (j < line.length && (line[j].isDigit() || line[j] == '.')) j++; withStyle(SpanStyle(color = num)) { append(line.substring(i, j)) }; i = j }
                    c.isLetter() || c == '_' -> { var j = i; while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++; val w = line.substring(i, j); withStyle(SpanStyle(color = if (w in KW) kw else def)) { append(w) }; i = j }
                    else -> { withStyle(SpanStyle(color = def)) { append(c) }; i++ }
                }
            }
        }
        if (idx < code.lines().lastIndex) append("\n")
    }
}
