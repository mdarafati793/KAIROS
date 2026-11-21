package com.kairos.ai.ui.screens

import android.Manifest; import android.net.Uri; import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult; import androidx.activity.result.PickVisualMediaRequest; import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*; import androidx.compose.foundation.layout.*; import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*; import androidx.compose.material.icons.Icons; import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*; import androidx.compose.runtime.*; import androidx.compose.ui.*; import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight; import androidx.compose.ui.text.style.TextOverflow; import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider; import com.kairos.ai.data.local.*; import com.kairos.ai.ui.components.MessageBubble; import com.kairos.ai.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch; import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onOpenSettings: () -> Unit, onOpenLiveVoice: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed); val listState = rememberLazyListState(); val snack = remember { SnackbarHostState() }
    val sessions by viewModel.sessions.collectAsState(); val sid by viewModel.activeSessionId.collectAsState()
    val messages by viewModel.messages.collectAsState(); val draft by viewModel.draftInput.collectAsState()
    val pendingAtts by viewModel.pendingAttachments.collectAsState(); val streamingText by viewModel.streamingText.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState(); val notice by viewModel.statusNotice.collectAsState()
    val caps by viewModel.capabilities.collectAsState()
    var showAttach by remember { mutableStateOf(false) }; var renameTarget by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var renameText by remember { mutableStateOf("") }; var camUri by remember { mutableStateOf<Uri?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { u -> viewModel.addAttachmentFromUri(u, "image/jpeg") } }
    val docPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> viewModel.addAttachmentFromUri(u) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) camUri?.let { viewModel.addAttachmentFromUri(it, "image/jpeg") } }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(ctx.cacheDir, "captures").apply { mkdirs() }.let { File(it, "c_${System.currentTimeMillis()}.jpg") }); camUri = u; camera.launch(u) }
        else Toast.makeText(ctx, "Camera permission required.", Toast.LENGTH_SHORT).show()
    }

    val isNearBottom by remember { derivedStateOf { val info = listState.layoutInfo; info.totalItemsCount == 0 || (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 2 } }
    val showScrollFab by remember { derivedStateOf { listState.layoutInfo.totalItemsCount > 0 && !isNearBottom } }
    LaunchedEffect(messages.size, streamingText.length) { val n = messages.size + if (isStreaming) 1 else 0; if (n > 0 && isNearBottom) listState.animateScrollToItem(n - 1) }
    LaunchedEffect(Unit) { viewModel.scrollToBottomEvent.collect { val n = messages.size + if (isStreaming) 1 else 0; if (n > 0) listState.scrollToItem(n - 1) } }
    LaunchedEffect(notice) { notice?.takeIf { it.isNotBlank() }?.let { snack.showSnackbar(it); viewModel.clearStatusNotice() } }

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet(Modifier.width(300.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("KAIROS Chats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { viewModel.createNewSession(); scope.launch { drawerState.close() } }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("New Chat")
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                items(sessions, key = { it.id }) { s ->
                    NavigationDrawerItem(
                        selected = s.id == sid,
                        onClick = { viewModel.selectSession(s.id); scope.launch { drawerState.close() } },
                        label = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        badge = {
                            Row {
                                IconButton({ renameTarget = s; renameText = s.title }, Modifier.size(28.dp)) { Icon(Icons.Rounded.Edit, "Rename", Modifier.size(16.dp)) }
                                IconButton({ viewModel.deleteSession(s.id) }, Modifier.size(28.dp)) { Icon(Icons.Rounded.Delete, "Delete", Modifier.size(16.dp)) }
                            }
                        }
                    )
                }
            }
        }
    }) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton({ scope.launch { drawerState.open() } }) { Icon(Icons.Rounded.Menu, "Sessions") } },
                    title = {
                        Column {
                            Text(sessions.firstOrNull { it.id == sid }?.title ?: "KAIROS v2.0", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(caps.primaryModel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(onOpenLiveVoice) { Icon(Icons.Rounded.GraphicEq, "Live Voice") }
                        IconButton(onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                    }
                )
            },
            bottomBar = {
                Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        if (pendingAtts.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                items(pendingAtts, key = { it.id }) { a ->
                                    InputChip(selected = true, onClick = { viewModel.removePendingAttachment(a.id) }, label = { Text(a.fileName, maxLines = 1) },
                                        trailingIcon = { Icon(Icons.Rounded.Close, "Remove", Modifier.size(16.dp)) })
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                            IconButton({ showAttach = true }) { Icon(Icons.Rounded.AttachFile, "Attach") }
                            OutlinedTextField(value = draft, onValueChange = { viewModel.updateDraftInput(it) }, placeholder = { Text("Ask KAIROS anything...") },
                                maxLines = 5, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(6.dp))
                            if (isStreaming) Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                                IconButton({ viewModel.stopGeneration() }) { Icon(Icons.Rounded.Stop, "Stop", tint = MaterialTheme.colorScheme.onErrorContainer) }
                            } else Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                IconButton(onClick = { viewModel.sendMessage() }, enabled = draft.isNotBlank() || pendingAtts.isNotEmpty()) {
                                    Icon(Icons.Rounded.Send, "Send", tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                if (messages.isEmpty() && !isStreaming) {
                    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(12.dp)); Text("KAIROS v2.0", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp)); Text("Streaming AI • Multimodal • MCP Connectors • Live Voice", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 72.dp)) {
                        items(messages, key = { it.id }) { m -> MessageBubble(m, false) { t -> scope.launch { snack.showSnackbar(t) } } }
                        if (isStreaming) item(key = "streaming") { MessageBubble(ChatMessageEntity("streaming", sid.orEmpty(), "model", streamingText, timestamp = System.currentTimeMillis()), true) }
                    }
                }
                AnimatedVisibility(visible = showScrollFab, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
                    ExtendedFloatingActionButton(
                        onClick = { scope.launch { val n = messages.size + if (isStreaming) 1 else 0; if (n > 0) listState.animateScrollToItem(n - 1) } },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(50), icon = { Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp)) }, text = { Text("Scroll to Bottom ↓") }
                    )
                }
            }
        }
    }
    if (showAttach) ModalBottomSheet(onDismissRequest = { showAttach = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add Multimodal Attachment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton({ showAttach = false; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(12.dp)); Text("Pick Image from Gallery", Modifier.weight(1f)) }
            TextButton({ showAttach = false; camPerm.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CameraAlt, null); Spacer(Modifier.width(12.dp)); Text("Capture Camera Photo", Modifier.weight(1f)) }
            TextButton({ showAttach = false; docPick.launch(arrayOf("application/pdf", "text/*", "audio/*", "*/*")) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AttachFile, null); Spacer(Modifier.width(12.dp)); Text("Attach Document or Audio", Modifier.weight(1f)) }
        }
    }
    renameTarget?.let { target ->
        AlertDialog(onDismissRequest = { renameTarget = null }, title = { Text("Rename Chat") },
            text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true, label = { Text("Title") }) },
            confirmButton = { TextButton({ viewModel.renameSession(target.id, renameText); renameTarget = null }) { Text("Save") } },
            dismissButton = { TextButton({ renameTarget = null }) { Text("Cancel") } })
    }
}
