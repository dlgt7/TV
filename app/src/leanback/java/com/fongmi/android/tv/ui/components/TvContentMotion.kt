package com.fongmi.android.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.fongmi.android.tv.ui.theme.JetStreamAnimations

/** Outgoing crossfade content is visible only: it must never retain remote focus. */
internal val LocalTvContentActive = compositionLocalOf { true }

/** A single content tree for drawers: changing settings never waits for an animation. */
@Composable
internal fun Modifier.tvContentFade(key: Any?): Modifier {
    val alpha = remember { Animatable(1f) }
    val previousKey = remember { arrayOf(key) }
    LaunchedEffect(key) {
        if (previousKey[0] != key) {
            previousKey[0] = key
            alpha.snapTo(0.65f)
            alpha.animateTo(1f, tween(JetStreamAnimations.DurationExit))
        }
    }
    return graphicsLayer { this.alpha = alpha.value }
}
