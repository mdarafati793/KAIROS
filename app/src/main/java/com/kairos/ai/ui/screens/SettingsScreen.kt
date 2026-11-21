package com.kairos.ai.ui.screens

import android.annotation.SuppressLint; import android.content.*; import android.net.Uri; import android.os.PowerManager; import android.provider.Settings
import androidx.compose.foundation.*; import androidx.compose.foundation.layout.*; import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack; import androidx.compose.material.icons.rounded.*; import androidx.compose.material3.*
import androidx.compose.runtime.*; import androidx.compose.ui.*; import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight; import androidx.compose.ui.text.input.PasswordVisualTransformation; import androidx.compose.ui.unit.dp
import androidx.lifecycle.*; import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kairos.ai.KairosApplication; import com.kairos.ai.accessibility.AccessibilityBridge
import com.kairos.ai.data.model.McpAppType; import com.kairos.ai.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, onBack: () -> Unit, onOpenMemorySummary: () -> Unit) {
    val ctx = LocalContext.current; val app = ctx.applicationContext as KairosApplication; val owner = LocalLifecycleOwner.current
    val caps by viewModel.capabilities.collectAsState(); val savedInstr by viewModel.customInstructions.collectAsState()
    val savedUrl by viewModel.composioUrl.collectAsState(); val savedKey by viewModel.composioApiKey.collectAsState()
    val connectors by viewModel.connectors.collectAsState(); val accLive by viewModel.accessibilityLive.collectAsState()

    var geminiKey by remember(caps.geminiApiKey) { mutableStateOf(caps.geminiApiKey) }
    var model by remember(caps.primaryModel) { mutableStateOf(caps.primaryModel) }
    var orKey by remember(caps.openRouterApiKey) { mutableStateOf(caps.openRouterApiKey) }
    var orModel by remember(caps.openRouterFallbackModel) { mutableStateOf(caps.openRouterFallbackModel) }
    var instr by remember(savedInstr) { mutableStateOf(savedInstr) }
    var mcpUrl by remember(savedUrl) { mutableStateOf(savedUrl) }; var mcpKey by remember(savedKey) { mutableStateOf(savedKey) }
    var accConnected by remember { mutableStateOf(AccessibilityBridge.isConnected(ctx)) }
    var ignoringBat by remember { mutableStateOf((ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(ctx.packageName) == true) }

    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                accConnected = AccessibilityBridge.isConnected(ctx)
                ignoringBat = (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
            }
        }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Scaffold(topBar = { TopAppBar(navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }, title = { Text("KAIROS Settings") }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.AccessibilityNew, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Accessibility & Resilience", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    val status = when { accConnected && accLive -> "CONNECTED (Live + System Enabled)"; AccessibilityBridge.isEnabledInSettings(ctx) -> "ENABLED (Waiting for Binder)"; else -> "DISCONNECTED" }
                    Text("Status: $status", color = if (accConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Button({ ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.fillMaxWidth()) { Text("Open Accessibility Settings") }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.BatteryChargingFull, null, tint = MaterialTheme.colorScheme.secondary)
                        Text("Fix Background Kill / Battery Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(if (ignoringBat) "Unrestricted battery active. On Xiaomi/Samsung/OnePlus, also enable OEM Autostart & lock app in Recents." else "Grant Unrestricted Battery + OEM Autostart so Android never kills the Accessibility service.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton({ openBatteryWhitelist(ctx) }, Modifier.fillMaxWidth()) { Text("Fix Background Kill / Battery Settings") }
                }
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI Providers & Auto Fallback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(geminiKey, { geminiKey = it }, label = { Text("Gemini API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(model, { model = it }, label = { Text("Primary Gemini Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(orKey, { orKey = it }, label = { Text("OpenRouter API Key (Fallback)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(orModel, { orModel = it }, label = { Text("OpenRouter Fallback Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button({ app.capabilityManager.setGeminiApiKey(geminiKey); app.capabilityManager.setPrimaryModel(model); app.capabilityManager.setOpenRouterConfig(orKey, orModel) }, Modifier.fillMaxWidth()) { Text("Save Provider Keys") }
                }
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Capabilities Manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    ToggleRow("Web Search Grounding", "Enable Google Search grounding", caps.webSearchEnabled) { app.capabilityManager.setWebSearch(it) }
                    ToggleRow("Code & File Execution", "Enable Gemini code execution", caps.codeExecutionEnabled) { app.capabilityManager.setCodeExecution(it) }
                    ToggleRow("Auto Model Fallback", "Switch to OpenRouter on block/error", caps.autoModelFallback) { app.capabilityManager.setAutoModelFallback(it) }
                    ToggleRow("Auto Memory", "Extract facts into Long-Term Memory", caps.autoMemoryEnabled) { app.capabilityManager.setAutoMemory(it) }
                }
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Personal Intelligence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(instr, { instr = it }, label = { Text("Custom Instructions") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button({ viewModel.setCustomInstructions(instr) }, Modifier.weight(1f)) { Text("Save") }
                        OutlinedButton(onOpenMemorySummary, Modifier.weight(1f)) { Text("Memory Summary") }
                    }
                }
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connected Apps (Composio MCP)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(mcpUrl, { mcpUrl = it }, label = { Text("Composio MCP Server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(mcpKey, { mcpKey = it }, label = { Text("Composio API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton({ viewModel.saveComposioConfig(mcpUrl, mcpKey) }, Modifier.fillMaxWidth()) { Text("Save MCP Credentials") }
                    HorizontalDivider()
                    McpAppType.entries.forEach { appType ->
                        val st = connectors.firstOrNull { it.id == appType.id }
                        val sub = when { st?.isConnected == true -> "Connected • ${st.toolCount} tools"; !st?.lastError.isNullOrBlank() -> "Error: ${st?.lastError}"; else -> "Disconnected" }
                        ToggleRow(appType.displayName, sub, st?.isConnected == true) { on -> viewModel.toggleMcpApp(appType, on, mcpUrl, mcpKey) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@SuppressLint("BatteryLife")
private fun openBatteryWhitelist(ctx: Context) {
    runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
