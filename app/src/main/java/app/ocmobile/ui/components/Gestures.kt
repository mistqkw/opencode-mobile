package app.ocmobile.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.ocmobile.Graph

/**
 * Нажатие как в Claude: без ряби, с лёгким «проседанием» и затемнением; долгое нажатие даёт вибро.
 * [scale] = 1f отключает сжатие (для широких строк оставляем только затемнение).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tap(
    enabled: Boolean = true,
    scale: Float = 0.96f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) scale else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    val a by animateFloatAsState(if (pressed && enabled) 0.72f else 1f, label = "pressAlpha")
    val haptic = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = s; scaleY = s; alpha = a }
        .combinedClickable(
            interactionSource = source, indication = null, enabled = enabled,
            onLongClick = onLongClick?.let { l -> { if (Graph.prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress); l() } },
            onClick = onClick,
        )
}

/** Долгое нажатие без визуального отклика (для сообщений в списке: иначе при прокрутке мигает затемнение). */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.longPress(onLong: () -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    combinedClickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
        onLongClick = { if (Graph.prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLong() },
        onClick = {},
    )
}
