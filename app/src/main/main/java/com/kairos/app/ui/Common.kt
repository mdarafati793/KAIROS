package com.kairos.app.ui

import android.app.ActivityManager
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.OrbState
import kotlin.math.PI
import kotlin.math.sin

val Obsidian = Color(0xFF05070F)
val Navy = Color(0xFF0A1330)
val Indigo = Color(0xFF14123A)
val Cyan = Color(0xFF00E5FF)
val Blue = Color(0xFF2979FF)
val Violet = Color(0xFF8B5CFF)
val Ice = Color(0xFFBFF6FF)
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun KairosTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val reduce = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
            ctx.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    }
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Cyan, secondary = Blue, tertiary = Violet, background = Obsidian, surface = Navy, onSurface = Color.White, onBackground = Color.White)
    ) { CompositionLocalProvider(LocalReduceMotion provides reduce) { content() } }
}

/** Layered translucent "liquid glass": soft gradient body, specular top edge, prismatic rim, depth shadow. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), tint: Color = Color(0xFF7FB8FF), strength: Float = 0.10f): Modifier = this
    .shadow(14.dp, shape, ambientColor = Color(0xFF00172E), spotColor = Color(0xFF001A3A))
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = strength + 0.07f), tint.copy(alpha = strength * 0.5f))))
    .drawBehind {
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(0.55f), Color.Transparent)),
            Offset(size.width * 0.12f, 1.5f), Offset(size.width * 0.88f, 1.5f), strokeWidth = 2f
        )
    }
    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.5f), Cyan.copy(0.12f), Color.White.copy(0.04f), Violet.copy(0.3f))), shape)

fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) 0.94f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    this.scale(s).pointerInput(enabled) {
        detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { if (enabled) onClick() })
    }
}

@Composable
fun GlassBackdrop(content: @Composable BoxScope.() -> Unit) {
    val inf = rememberInfiniteTransition(label = "bg")
    val t by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "t")
    val rm = LocalReduceMotion.current
    val tt = if (rm) 0f else t
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Obsidian, Navy, Indigo, Obsidian)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            fun blob(c: Color, x: Float, y: Float, r: Float) =
                drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(x, y), r), r, Offset(x, y))
            blob(Blue.copy(0.22f), w * (0.2f + 0.08f * sin(tt)), h * (0.18f + 0.05f * sin(tt * 1.3f)), w * 0.7f)
            blob(Violet.copy(0.16f), w * (0.85f - 0.07f * sin(tt * 0.8f)), h * (0.55f + 0.06f * sin(tt)), w * 0.65f)
            blob(Cyan.copy(0.12f), w * (0.4f + 0.1f * sin(tt * 0.6f)), h * (0.92f - 0.04f * sin(tt)), w * 0.8f)
        }
        content()
    }
}

@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, icon: ImageVector? = null) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.alpha(if (enabled) 1f else 0.45f).pressable(enabled, onClick).clip(shape)
            .background(
                if (primary) Brush.horizontalGradient(listOf(Blue.copy(0.85f), Cyan.copy(0.75f)))
                else Brush.horizontalGradient(listOf(Color.White.copy(0.14f), Color.White.copy(0.06f)))
            )
            .border(1.dp, Color.White.copy(0.3f), shape).padding(horizontal = 22.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "chip")
    Box(
        Modifier.pressable(onClick = onClick).clip(shape)
            .background(Brush.horizontalGradient(listOf(Blue.copy(0.55f * a + 0.06f), Cyan.copy(0.4f * a + 0.04f))))
            .border(1.dp, Color.White.copy(0.15f + 0.4f * a), shape).padding(horizontal = 16.dp, vertical = 9.dp)
    ) { Text(text, color = Color.White, fontSize = 14.sp) }
}

@Composable
fun GlassField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, secret: Boolean = false, singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text) {
    var focus by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (focus) 1f else 0f, tween(250), label = "focus")
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.clip(shape).background(Color.White.copy(0.06f)).border(1.dp, Cyan.copy(0.15f + 0.6f * glow), shape).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(hint, color = Color.White.copy(0.4f))
        BasicTextField(
            value, onChange, singleLine = singleLine, maxLines = if (singleLine) 1 else 5,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Cyan),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focus = it.isFocused }
        )
    }
}

@Composable
fun KairosOrb(state: OrbState, level: Float, modifier: Modifier = Modifier) {
    val rm = LocalReduceMotion.current
    val inf = rememberInfiniteTransition(label = "orb")
    val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(if (rm) 6000 else 3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val wave by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "w")
    val speedTarget = when (state) { OrbState.Idle -> 8f; OrbState.Listening -> 22f; OrbState.Thinking -> 70f; OrbState.Speaking -> 36f }
    val speed by animateFloatAsState(speedTarget, spring(stiffness = Spring.StiffnessLow), label = "s")
    val live by rememberUpdatedState(speed)
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(rm) {
        var last = 0L
        while (true) withFrameNanos { n -> if (last != 0L && !rm) angle = (angle + (n - last) / 1e9f * live) % 360f; last = n }
    }
    val amp by animateFloatAsState(
        when (state) { OrbState.Idle -> 0.1f; OrbState.Listening -> 0.25f + level * 0.75f; OrbState.Thinking -> 0.45f; OrbState.Speaking -> 0.5f + 0.3f * sin(wave) },
        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium), label = "a"
    )
    val hue by animateColorAsState(if (state == OrbState.Thinking) Violet else Cyan, tween(600), label = "h")
    Canvas(modifier.aspectRatio(1f)) {
        val c = center; val base = size.minDimension / 2f
        val r = base * 0.58f * (1f + 0.05f * breathe + 0.10f * amp)
        drawCircle(Brush.radialGradient(listOf(hue.copy(0.40f * (0.6f + amp)), Blue.copy(0.14f), Color.Transparent), c, base), base, c)
        drawCircle(
            Brush.radialGradient(0f to Color.White.copy(0.85f), 0.35f to hue.copy(0.65f), 1f to Blue.copy(0.35f), center = Offset(c.x - r * 0.3f, c.y - r * 0.35f), radius = r * 1.5f),
            r, c
        )
        for (i in 0..2) {
            rotate(angle * (1f + i * 0.6f) * (if (i == 1) -1f else 1f), c) {
                val rr = r * (0.78f + 0.10f * i + 0.04f * amp * sin(wave + i))
                drawCircle(
                    Brush.sweepGradient(listOf(Color.Transparent, hue.copy(0.9f), Violet.copy(0.7f), Color.Transparent), c),
                    rr, c, style = Stroke(r * (0.05f + 0.02f * i))
                )
            }
        }
        drawCircle(Color.White.copy(0.35f), r, c, style = Stroke(1.5.dp.toPx()))
        drawOval(Color.White.copy(0.22f), Offset(c.x - r * 0.55f, c.y - r * 0.85f), Size(r * 0.9f, r * 0.35f))
    }
}

@Composable
fun Waveform(level: Float, active: Boolean, modifier: Modifier = Modifier, color: Color = Cyan) {
    val inf = rememberInfiniteTransition(label = "wf")
    val t by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "t")
    Canvas(modifier) {
        val n = 28; val gap = size.width / n
        for (i in 0 until n) {
            val env = sin(PI * i / (n - 1)).toFloat()
            val h = size.height * (0.08f + if (active) (0.15f + 0.8f * level) * env * (0.5f + 0.5f * sin(t + i * 0.7f)) else 0.04f)
            drawRoundRect(color.copy(0.85f), Offset(i * gap + gap * 0.2f, (size.height - h) / 2f), Size(gap * 0.6f, h), CornerRadius(gap))
        }
    }
}
