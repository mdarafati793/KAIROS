package com.kairos.ai.ui.screens

import android.Manifest; import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult; import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*; import androidx.camera.lifecycle.ProcessCameraProvider; import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*; import androidx.compose.foundation.background; import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*; import androidx.compose.material.icons.Icons; import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*; import androidx.compose.material3.*; import androidx.compose.runtime.*
import androidx.compose.ui.*; import androidx.compose.ui.draw.*; import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight; import androidx.compose.ui.text.style.TextAlign; import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView; import androidx.core.content.ContextCompat; import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel; import com.kairos.ai.ui.viewmodel.*
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVoiceScreen(onBack: () -> Unit, vm: LiveVoiceViewModel = viewModel()) {
    val ctx = LocalContext.current; val owner = LocalLifecycleOwner.current
    val phase by vm.phase.collectAsState(); val transcript by vm.transcript.collectAsState()
    val reply by vm.aiReply.collectAsState(); val db by vm.currentDb.collectAsState()
    val vision by vm.visionEnabled.collectAsState(); val status by vm.statusText.collectAsState()
    var hasAudio by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var hasCam by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        hasAudio = r[Manifest.permission.RECORD_AUDIO] ?: hasAudio; hasCam = r[Manifest.permission.CAMERA] ?: hasCam
    }
    LaunchedEffect(Unit) { if (!hasAudio) permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) }
    DisposableEffect(Unit) { onDispose { vm.stopSession() } }
    val scale by rememberInfiniteTransition("p").animateFloat(1f, if (phase == VoicePhase.IDLE) 1f else 1.18f, infiniteRepeatable(tween(900), RepeatMode.Reverse), "s")

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            title = { Text("Live Voice & Guided Vision") },
            actions = { Text("${db.toInt()} dB", style = MaterialTheme.typography.labelMedium, color = if (db > 60.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp)) }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            if (vision && hasCam) {
                val exec = remember { Executors.newSingleThreadExecutor() }
                DisposableEffect(Unit) { onDispose { exec.shutdown() } }
                AndroidView(modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(20.dp)), factory = { c ->
                    PreviewView(c).also { pv ->
                        val fut = ProcessCameraProvider.getInstance(c)
                        fut.addListener({
                            val cp = fut.get()
                            val prev = Preview.Builder().build().also { it.surfaceProvider = pv.surfaceProvider }
                            val ana = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
                                .also { it.setAnalyzer(exec) { p -> vm.onCameraFrame(p) } }
                            runCatching { cp.unbindAll(); cp.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, prev, ana) }
                        }, ContextCompat.getMainExecutor(c))
                    }
                })
            } else Spacer(Modifier.height(20.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp).scale(scale).clip(CircleShape).background(
                    when (phase) { VoicePhase.LISTENING -> MaterialTheme.colorScheme.primaryContainer; VoicePhase.THINKING -> MaterialTheme.colorScheme.tertiaryContainer; VoicePhase.SPEAKING -> MaterialTheme.colorScheme.secondaryContainer; VoicePhase.IDLE -> MaterialTheme.colorScheme.surfaceVariant }
                )) { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp)) }
                Spacer(Modifier.height(14.dp)); Text(status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                if (transcript.isNotBlank()) Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("You: $transcript", Modifier.padding(12.dp)) }
                if (reply.isNotBlank()) Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(0.6f), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(reply, maxLines = 6, modifier = Modifier.padding(12.dp)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                FilledTonalIconButton(onClick = { if (!hasCam) permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) else vm.toggleVision(!vision) }, modifier = Modifier.size(58.dp)) {
                    Icon(if (vision) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Guided Vision")
                }
                FilledIconButton(
                    onClick = { if (!hasAudio) permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) else if (phase == VoicePhase.IDLE) vm.startSession() else vm.stopSession() },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (phase == VoicePhase.IDLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
                    modifier = Modifier.size(72.dp)
                ) { Icon(if (phase == VoicePhase.IDLE) Icons.Rounded.Mic else Icons.Rounded.Stop, "Mic", Modifier.size(32.dp)) }
            }
        }
    }
}
