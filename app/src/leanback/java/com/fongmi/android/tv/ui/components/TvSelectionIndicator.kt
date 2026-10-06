package com.fongmi.android.tv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fongmi.android.tv.ui.theme.JetStreamAnimations

/** Decoration only; the existing Material buttons continue to own focus and selection. */
@Composable
internal fun TvSelectionIndicator(left: Dp, top: Dp, modifier: Modifier = Modifier) {
    val x by animateDpAsState(left, tween(JetStreamAnimations.DurationShort), label = "tabIndicatorX")
    val y by animateDpAsState(top, tween(JetStreamAnimations.DurationShort), label = "tabIndicatorY")
    Box(modifier.offset(x = x, y = y).size(width = 16.dp, height = 2.dp)
        .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(1.dp)))
}
