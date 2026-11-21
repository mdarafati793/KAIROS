package com.kairos.ai.ui.components
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kairos.ai.data.local.ChatMessageEntity
import com.kairos.ai.data.model.ChatAttachment
import kotlinx.serialization.json.Json

private val bJson = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(message: ChatMessageEntity, isStreaming: Boolean = false, onCopied: ((String) -> Unit)? = null) {
    val isUser = message.role == "user"
    val clip = LocalClipboardManager.current; val ctx = LocalContext.current
    val atts = remember(message.attachmentsJson) { runCatching { bJson.decodeFromString<List<ChatAttachment>>(message.attachmentsJson) }.getOrDefault(emptyList()) }
    val bg = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        if (!isUser) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                Text(if (isStreaming) "KAIROS • streaming" else "KAIROS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Surface(shape = RoundedCornerShape(18.dp, 18.dp, if (isUser) 4.dp else 18.dp, if (isUser) 18.dp else 4.dp), color = bg, modifier = Modifier.widthIn(max = 340.dp)) {
            Column(Modifier.padding(14.dp)) {
                if (atts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        atts.forEach { a ->
                            if (a.mimeType.startsWith("image/") && a.uriString.isNotBlank()) {
                                AsyncImage(model = Uri.parse(a.uriString), contentDescription = a.fileName, contentScale = ContentScale.Crop, modifier = Modifier.size(100.dp).clip(RoundedCornerShape(10.dp)))
                            } else {
                                AssistChip(onClick = {}, label = { Text(a.fileName, maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.Description, null, Modifier.size(16.dp)) })
                            }
                        }
                    }
                }
                if (message.content.isNotBlank() || isStreaming) RichMarkdownContent(message.content, fg, isStreaming)
            }
        }
        if (!isStreaming && message.content.isNotBlank()) {
            IconButton(onClick = {
                clip.setText(AnnotatedString(message.content))
                if (onCopied != null) onCopied("Copied!") else Toast.makeText(ctx, "Copied!", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.ContentCopy, "Copy message", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            }
        }
    }
}
