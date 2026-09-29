package com.example.feynman.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/*
 * Material 3 Expressive pieces used across the app (material3 1.4.0-alpha18): the expressive
 * slider (tall rounded track, narrow bar thumb), the floating toolbar and the loading indicator.
 */

/**
 * The M3 Expressive slider: a 24 dp track with 12 dp corners and a 4 × 44 dp bar thumb.
 * With [trackBrush] the track shows that gradient instead (the color picker's tracks).
 * [steps] makes it snap, as for the number of digits.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    trackBrush: Brush? = null,
) {
    val state = remember(valueRange, steps) { SliderState(value = value.coerceIn(valueRange), steps = steps, valueRange = valueRange) }
    // The value lives outside (the view model); the state follows it and reports drags back.
    SideEffect {
        state.value = value.coerceIn(valueRange)
        state.onValueChange = { v -> state.value = v; onValueChange(v) }
    }
    val interaction = remember { MutableInteractionSource() }
    val colors = if (trackBrush == null) SliderDefaults.colors() else SliderDefaults.colors(
        activeTrackColor = Color.Transparent,
        inactiveTrackColor = Color.Transparent,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
        thumbColor = MaterialTheme.colorScheme.onSurface,
    )
    Slider(
        state = state,
        modifier = modifier,
        interactionSource = interaction,
        colors = colors,
        thumb = { SliderDefaults.Thumb(interactionSource = interaction, colors = colors, thumbSize = DpSize(4.dp, 44.dp)) },
        track = {
            SliderDefaults.Track(
                sliderState = it,
                colors = colors,
                modifier = Modifier
                    .height(24.dp)
                    .drawBehind { if (trackBrush != null) drawRoundRect(trackBrush, cornerRadius = CornerRadius(12.dp.toPx())) },
                // A 24 dp track is fully rounded by default: 12 dp corners.
                drawStopIndicator = null,
            )
        },
    )
}

/**
 * A compact floating toolbar in the M3 Expressive style: a raised pill of icon buttons.
 * (material3 1.4.0-alpha18's HorizontalFloatingToolbar drew a large, oversized shape, so this
 * is built directly.)
 */
@Composable
fun ExpressiveToolbar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        modifier
            .androidx_shadow()
            .background(colors.surfaceContainerHigh, CircleShape)
            .padding(4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        content = content,
    )
}

private fun Modifier.androidx_shadow() = this.shadow(6.dp, CircleShape)

/** The M3 Expressive loading indicator (morphing shapes), for work that takes a moment. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Busy(modifier: Modifier = Modifier) {
    LoadingIndicator(modifier.size(36.dp))
}
