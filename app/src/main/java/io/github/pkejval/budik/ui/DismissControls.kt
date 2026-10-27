package io.github.pkejval.budik.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pkejval.budik.data.DismissMethod
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ThumbSize = 72.dp
private const val CONFIRM_FRACTION = 0.85f

@Composable
fun DismissControl(method: DismissMethod, label: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    when (method) {
        DismissMethod.SLIDE_HORIZONTAL -> SlideToConfirm(
            Orientation.Horizontal, label, Icons.AutoMirrored.Filled.ArrowForward, onDismiss,
            modifier.fillMaxWidth().height(ThumbSize + 16.dp),
        )
        DismissMethod.SLIDE_VERTICAL -> SlideToConfirm(
            Orientation.Vertical, label, Icons.Filled.KeyboardArrowUp, onDismiss,
            modifier.width(ThumbSize + 16.dp).height(300.dp),
        )
        DismissMethod.BUTTON -> LargeFloatingActionButton(
            onClick = onDismiss,
            modifier = modifier.size(120.dp),
            shape = CircleShape,
            containerColor = Color.White,
            contentColor = Color(0xFF1E1B4B),
        ) {
            Icon(Icons.Filled.AlarmOff, contentDescription = label, modifier = Modifier.size(48.dp))
        }
    }
}

/** A track with a thumb that must be dragged to the far end (right, or up) to confirm. */
@Composable
private fun SlideToConfirm(
    orientation: Orientation,
    label: String,
    icon: ImageVector,
    onConfirm: () -> Unit,
    modifier: Modifier,
) {
    val horizontal = orientation == Orientation.Horizontal
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var trackPx by remember { mutableIntStateOf(0) }
    val maxPx = (trackPx - with(density) { (ThumbSize + 16.dp).toPx() }).coerceAtLeast(1f)
    val progress = (offset.value / maxPx).coerceIn(0f, 1f)

    val dragState = rememberDraggableState { delta ->
        // Vertical drags grow upwards, i.e. with negative deltas.
        val d = if (horizontal) delta else -delta
        scope.launch { offset.snapTo((offset.value + d).coerceIn(0f, maxPx)) }
    }

    Box(
        modifier
            .onSizeChanged { trackPx = if (horizontal) it.width else it.height }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f + 0.2f * progress))
            .semantics {
                role = Role.Button
                onClick(label) { onConfirm(); true }
            }
            .draggable(
                state = dragState,
                orientation = orientation,
                onDragStopped = {
                    if (offset.value >= maxPx * CONFIRM_FRACTION) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        offset.animateTo(maxPx)
                        onConfirm()
                    } else {
                        offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                },
            ),
        contentAlignment = if (horizontal) Alignment.CenterStart else Alignment.BottomCenter,
    ) {
        // The vertical track is too narrow for text; the caller shows the hint next to it.
        if (horizontal) {
            Text(
                label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = ThumbSize)
                    .alpha(1f - progress),
            )
        }
        Box(
            Modifier
                .padding(8.dp)
                .offset {
                    val px = offset.value.roundToInt()
                    if (horizontal) IntOffset(px, 0) else IntOffset(0, -px)
                }
                .size(ThumbSize)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF1E1B4B), modifier = Modifier.size(36.dp))
        }
    }
}
