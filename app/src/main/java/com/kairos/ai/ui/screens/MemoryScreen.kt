package com.kairos.ai.ui.screens

import androidx.compose.foundation.layout.*; import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons; import androidx.compose.material.icons.automirrored.rounded.ArrowBack; import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*; import androidx.compose.runtime.*; import androidx.compose.ui.*; import androidx.compose.ui.text.font.FontWeight; import androidx.compose.ui.unit.dp
import com.kairos.ai.data.local.MemoryEntity; import com.kairos.ai.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val memories by viewModel.memories.collectAsState()
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }; var showDlg by remember { mutableStateOf(false) }
    var fact by remember { mutableStateOf("") }; var cat by remember { mutableStateOf("Preference") }
    Scaffold(
        topBar = { TopAppBar(navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }, title = { Text("Long-Term Memory (${memories.size})") }) },
        floatingActionButton = { FloatingActionButton({ editing = null; fact = ""; cat = "Preference"; showDlg = true }) { Icon(Icons.Rounded.Add, "Add") } }
    ) { pad ->
        if (memories.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("No saved memories yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp)); Text("KAIROS extracts durable preferences automatically, or tap + to add.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(memories, key = { it.id }) { m ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            SuggestionChip(onClick = {}, label = { Text(m.category) })
                            Row {
                                IconButton({ editing = m; fact = m.fact; cat = m.category; showDlg = true }) { Icon(Icons.Rounded.Edit, "Edit") }
                                IconButton({ viewModel.deleteMemory(m.id) }) { Icon(Icons.Rounded.Delete, "Delete") }
                            }
                        }
                        Text(m.fact, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
    if (showDlg) AlertDialog(onDismissRequest = { showDlg = false }, title = { Text(if (editing == null) "Add Memory" else "Edit Memory") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(cat, { cat = it }, label = { Text("Category") }, singleLine = true); OutlinedTextField(fact, { fact = it }, label = { Text("Fact") }, minLines = 3) } },
        confirmButton = { TextButton({ viewModel.saveMemory(editing?.id, fact, cat); showDlg = false }) { Text("Save") } },
        dismissButton = { TextButton({ showDlg = false }) { Text("Cancel") } })
}
