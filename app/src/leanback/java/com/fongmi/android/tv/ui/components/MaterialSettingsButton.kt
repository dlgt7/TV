package com.fongmi.android.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults

enum class MaterialSettingsButtonStyle { Filled, Tonal, Outlined, Text }

/** TV Button owns remote input and long press; Material color roles define action hierarchy. */
@Composable
fun MaterialSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    style: MaterialSettingsButtonStyle = MaterialSettingsButtonStyle.Tonal,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        selected -> colors.primaryContainer
        style == MaterialSettingsButtonStyle.Filled -> colors.primary
        style == MaterialSettingsButtonStyle.Tonal -> colors.secondaryContainer
        else -> Color.Transparent
    }
    val foreground = when {
        selected -> colors.onPrimaryContainer
        style == MaterialSettingsButtonStyle.Filled -> colors.onPrimary
        style == MaterialSettingsButtonStyle.Tonal -> colors.onSecondaryContainer
        else -> colors.primary
    }
    Button(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = selected },
        shape = ButtonDefaults.shape(shape = shape),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(
            containerColor = container,
            contentColor = foreground,
            focusedContainerColor = colors.primary,
            focusedContentColor = colors.onPrimary,
            pressedContainerColor = colors.primaryContainer,
            pressedContentColor = colors.onPrimaryContainer
        ),
        border = ButtonDefaults.border(
            border = if (style == MaterialSettingsButtonStyle.Outlined && !selected)
                Border(BorderStroke(1.dp, colors.outline), shape = shape) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, colors.onSurface), shape = shape)
        ),
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        CompositionLocalProvider(LocalContentColor provides androidx.tv.material3.LocalContentColor.current) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )) { content() }
        }
    }
}
