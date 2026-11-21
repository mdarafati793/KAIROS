package com.kairos.ai
import android.os.Bundle
import androidx.activity.ComponentActivity; import androidx.activity.compose.setContent; import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.*; import androidx.lifecycle.compose.LocalLifecycleOwner; import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.kairos.ai.ui.screens.*; import com.kairos.ai.ui.theme.KairosTheme; import com.kairos.ai.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            KairosTheme {
                val nav = rememberNavController(); val vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory); val owner = LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.onAppResumed() }
                    owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
                }
                NavHost(nav, startDestination = "chat") {
                    composable("chat") { ChatScreen(vm, { nav.navigate("settings") }, { nav.navigate("live_voice") }) }
                    composable("settings") { SettingsScreen(vm, { nav.popBackStack() }, { nav.navigate("memory_summary") }) }
                    composable("memory_summary") { MemoryScreen(vm, { nav.popBackStack() }) }
                    composable("live_voice") { LiveVoiceScreen({ nav.popBackStack() }) }
                }
            }
        }
    }
}
