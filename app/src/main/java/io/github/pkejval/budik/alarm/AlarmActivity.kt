package io.github.pkejval.budik.alarm

import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.pkejval.budik.R
import io.github.pkejval.budik.data.Alarm
import io.github.pkejval.budik.data.DismissMethod
import io.github.pkejval.budik.ui.DismissControl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        // The alarm can only be stopped by the chosen dismiss gesture.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        lifecycleScope.launch {
            // The service publishes the alarm right after loading it; close if nothing rings.
            withTimeoutOrNull(5_000) { AlarmService.ringing.first { it != null } } ?: return@launch finish()
            AlarmService.ringing.first { it == null }
            finish()
        }

        setContent {
            var alarm by remember { mutableStateOf<Alarm?>(null) }
            LaunchedEffect(Unit) { AlarmService.ringing.collect { if (it != null) alarm = it } }
            alarm?.let { a ->
                RingingScreen(a) { startService(AlarmService.dismissIntent(this)) }
            } ?: Box(Modifier.fillMaxSize().background(Night))
        }
    }
}

private val Night = Color(0xFF0F0C29)
private val Dusk = Color(0xFF302B63)
private val Dawn = Color(0xFFF97316)

@Composable
private fun RingingScreen(alarm: Alarm, onDismiss: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "ringing")
    val glow by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Reverse), label = "glow",
    )
    val pulse by transition.animateFloat(
        1f, 1.25f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val context = LocalContext.current
    val timePattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Night, Dusk, lerp(Dusk, Dawn, 0.35f + 0.4f * glow))))
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(96.dp)
                        .scale(pulse)
                        .graphicsLayer { alpha = 1.6f - pulse }
                        .background(Dawn.copy(alpha = 0.5f), CircleShape),
                )
                Box(
                    Modifier.size(76.dp).background(Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Alarm, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                now.format(DateTimeFormatter.ofPattern(timePattern)),
                color = Color.White,
                fontSize = 96.sp,
                fontWeight = FontWeight.Thin,
            )
            Text(
                now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 18.sp,
            )
            if (alarm.label.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Text(alarm.label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            }
        }

        val hint = stringResource(
            when (alarm.dismissMethod) {
                DismissMethod.SLIDE_HORIZONTAL -> R.string.slide_to_dismiss
                DismissMethod.SLIDE_VERTICAL -> R.string.slide_up_to_dismiss
                DismissMethod.BUTTON -> R.string.dismiss
            },
        )
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (alarm.dismissMethod != DismissMethod.SLIDE_HORIZONTAL) {
                Text(hint, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
            }
            DismissControl(alarm.dismissMethod, hint, onDismiss)
        }
    }
}
