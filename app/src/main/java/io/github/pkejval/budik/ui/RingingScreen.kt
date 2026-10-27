package io.github.pkejval.budik.ui

import android.os.Build
import android.text.format.DateFormat
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
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
import io.github.pkejval.budik.R
import io.github.pkejval.budik.data.ClockWeight
import io.github.pkejval.budik.data.DismissMethod
import io.github.pkejval.budik.data.RingingAppearance
import io.github.pkejval.budik.data.RingingStyle
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class RingingColors(val top: Color, val middle: Color, val accent: Color)

@Composable
fun ringingColors(style: RingingStyle): RingingColors {
    if (style != RingingStyle.SYSTEM) return RingingColors(Color(style.top), Color(style.middle), Color(style.accent))
    val context = LocalContext.current
    val scheme = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else darkColorScheme()
    }
    return RingingColors(scheme.background, scheme.primaryContainer, scheme.primary)
}

/** The full-screen alarm UI; also rendered scaled down as a preview in settings. */
@Composable
fun RingingScreen(
    label: String,
    dismissMethod: DismissMethod,
    appearance: RingingAppearance,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Keep content clear of system bars; off for the settings preview. */
    systemBarsPadding: Boolean = true,
) {
    val colors = ringingColors(appearance.style)
    val transition = rememberInfiniteTransition(label = "ringing")
    val glowAnim by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Reverse), label = "glow",
    )
    val pulseAnim by transition.animateFloat(
        1f, 1.25f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    val glow = if (appearance.animated) glowAnim else 0.5f
    val pulse = if (appearance.animated) pulseAnim else 1.1f

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val context = LocalContext.current
    val timePattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
    val weight = when (appearance.clockWeight) {
        ClockWeight.THIN -> FontWeight.Thin
        ClockWeight.LIGHT -> FontWeight.Light
        ClockWeight.BOLD -> FontWeight.Bold
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.top, colors.middle, lerp(colors.middle, colors.accent, 0.35f + 0.4f * glow))))
            .then(if (systemBarsPadding) Modifier.safeDrawingPadding() else Modifier)
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
                        .background(colors.accent.copy(alpha = 0.5f), CircleShape),
                )
                Box(
                    Modifier.size(76.dp).background(Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Alarm, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(now.format(DateTimeFormatter.ofPattern(timePattern)), color = Color.White, fontSize = 96.sp, fontWeight = weight)
            if (appearance.showDate) {
                Text(
                    now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 18.sp,
                )
            }
            if (label.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Text(label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            }
        }

        val hint = stringResource(
            when (dismissMethod) {
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
            if (dismissMethod != DismissMethod.SLIDE_HORIZONTAL) {
                Text(hint, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
            }
            DismissControl(dismissMethod, hint, onDismiss)
        }
    }
}
