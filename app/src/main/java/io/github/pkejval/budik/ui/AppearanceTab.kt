package io.github.pkejval.budik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.pkejval.budik.R
import io.github.pkejval.budik.data.ClockWeight
import io.github.pkejval.budik.data.DismissMethod
import io.github.pkejval.budik.data.RingingAppearance
import io.github.pkejval.budik.data.RingingStyle

private const val PREVIEW_SCALE = 0.45f

@Composable
fun AppearanceTab(appearance: RingingAppearance, onChange: (RingingAppearance) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Preview(appearance) }
        item {
            Column(Modifier.fillMaxWidth()) {
                Label(R.string.ring_style)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RingingStyle.entries.forEach { style ->
                        StyleSwatch(style, selected = appearance.style == style) { onChange(appearance.copy(style = style)) }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                Label(R.string.clock_weight)
                val weights = listOf(
                    ClockWeight.THIN to R.string.weight_thin,
                    ClockWeight.LIGHT to R.string.weight_light,
                    ClockWeight.BOLD to R.string.weight_bold,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    weights.forEachIndexed { i, (weight, text) ->
                        SegmentedButton(
                            selected = appearance.clockWeight == weight,
                            onClick = { onChange(appearance.copy(clockWeight = weight)) },
                            shape = SegmentedButtonDefaults.itemShape(i, weights.size),
                        ) { Text(stringResource(text)) }
                    }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                ToggleItem(R.string.ring_animated, appearance.animated) { onChange(appearance.copy(animated = it)) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                ToggleItem(R.string.show_date, appearance.showDate) { onChange(appearance.copy(showDate = it)) }
            }
        }
    }
}

/** The real ringing screen laid out at phone size and scaled down. */
@Composable
private fun Preview(appearance: RingingAppearance) {
    val width = 400.dp
    val height = 844.dp
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .size(width * PREVIEW_SCALE, height * PREVIEW_SCALE)
            .clip(shape)
            .border(4.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .requiredSize(width, height)
                .graphicsLayer {
                    scaleX = PREVIEW_SCALE
                    scaleY = PREVIEW_SCALE
                },
        ) {
            RingingScreen(
                label = stringResource(R.string.preview_label),
                dismissMethod = DismissMethod.SLIDE_HORIZONTAL,
                appearance = appearance,
                onDismiss = {},
                systemBarsPadding = false,
            )
        }
    }
}

@Composable
private fun StyleSwatch(style: RingingStyle, selected: Boolean, onClick: () -> Unit) {
    val colors = ringingColors(style)
    Column(
        Modifier.width(72.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(colors.top, colors.middle, colors.accent)))
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White)
        }
        Text(
            stringResource(styleName(style)),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun ToggleItem(title: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun Label(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

private fun styleName(style: RingingStyle) = when (style) {
    RingingStyle.NIGHT -> R.string.style_night
    RingingStyle.OCEAN -> R.string.style_ocean
    RingingStyle.FOREST -> R.string.style_forest
    RingingStyle.SUNSET -> R.string.style_sunset
    RingingStyle.AMOLED -> R.string.style_amoled
    RingingStyle.SYSTEM -> R.string.style_system
}
