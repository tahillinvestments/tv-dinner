package com.tvdinner.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tvdinner.ui.theme.CinemaFocus
import com.tvdinner.ui.theme.CinemaSurfaceVariant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object TvCardGlobalState {
    var isLongPressActive: Boolean = false
    var lastLongPressTimestamp: Long = 0L
    var suppressUntilTimestamp: Long = 0L
}

@Composable
fun TvFocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = Color.Unspecified,
    focusedBorderColor: Color = Color.Unspecified,
    focusedScale: Float = 1.05f,
    elevation: Dp = 4.dp,
    content: @Composable BoxScope.(Boolean) -> Unit
) {
    val actualBg = if (backgroundColor != Color.Unspecified) backgroundColor else CinemaSurfaceVariant
    val actualBorder = if (focusedBorderColor != Color.Unspecified) focusedBorderColor else CinemaFocus

    var isFocused by remember { mutableStateOf(false) }
    var isLongPressHandled by remember { mutableStateOf(false) }
    var isKeyDownOnThisCard by remember { mutableStateOf(false) }
    var lastLongClickTimestamp by remember { mutableLongStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    val triggerLongClick: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastLongClickTimestamp > 500L && now - TvCardGlobalState.lastLongPressTimestamp > 500L) {
            lastLongClickTimestamp = now
            TvCardGlobalState.lastLongPressTimestamp = now
            TvCardGlobalState.isLongPressActive = true
            TvCardGlobalState.suppressUntilTimestamp = now + 400L
            onLongClick?.invoke()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tv_card_scale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .then(
                if (isFocused) {
                    Modifier.shadow(
                        elevation = 12.dp,
                        shape = shape,
                        ambientColor = actualBorder,
                        spotColor = actualBorder
                    )
                } else {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = shape
                    )
                }
            )
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (!state.isFocused) {
                    longPressJob?.cancel()
                    longPressJob = null
                    isKeyDownOnThisCard = false
                    isLongPressHandled = false
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                val code = keyEvent.nativeKeyEvent.keyCode
                val isSelectKey = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == KeyEvent.KEYCODE_ENTER ||
                        code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                        code == KeyEvent.KEYCODE_BUTTON_A ||
                        code == KeyEvent.KEYCODE_BUTTON_SELECT

                if (!isSelectKey) return@onPreviewKeyEvent false

                val now = System.currentTimeMillis()
                val isGlobalSuppressed = TvCardGlobalState.isLongPressActive || (now < TvCardGlobalState.suppressUntilTimestamp)

                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (isGlobalSuppressed) {
                        return@onPreviewKeyEvent true
                    }
                    if (onLongClick != null && !isLongPressHandled && (keyEvent.nativeKeyEvent.isLongPress || keyEvent.nativeKeyEvent.repeatCount >= 1)) {
                        longPressJob?.cancel()
                        longPressJob = null
                        isLongPressHandled = true
                        triggerLongClick()
                        return@onPreviewKeyEvent true
                    }
                    if (isLongPressHandled) {
                        return@onPreviewKeyEvent true
                    }
                    if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                        isKeyDownOnThisCard = true
                        if (onLongClick != null) {
                            longPressJob?.cancel()
                            longPressJob = coroutineScope.launch {
                                delay(500L)
                                if (isKeyDownOnThisCard) {
                                    isLongPressHandled = true
                                    triggerLongClick()
                                }
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                } else if (keyEvent.type == KeyEventType.KeyUp) {
                    longPressJob?.cancel()
                    longPressJob = null
                    if (TvCardGlobalState.isLongPressActive) {
                        TvCardGlobalState.isLongPressActive = false
                        TvCardGlobalState.suppressUntilTimestamp = System.currentTimeMillis() + 300L
                    }
                    if (isLongPressHandled) {
                        isLongPressHandled = false
                        isKeyDownOnThisCard = false
                        return@onPreviewKeyEvent true
                    }
                    if (isGlobalSuppressed || now < TvCardGlobalState.suppressUntilTimestamp) {
                        isKeyDownOnThisCard = false
                        return@onPreviewKeyEvent true
                    }
                    if (!isKeyDownOnThisCard) {
                        return@onPreviewKeyEvent true
                    }
                    isKeyDownOnThisCard = false
                    onClick()
                    return@onPreviewKeyEvent true
                }
                false
            }
            .focusable(
                interactionSource = remember { MutableInteractionSource() }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (!TvCardGlobalState.isLongPressActive && now >= TvCardGlobalState.suppressUntilTimestamp) {
                            onClick()
                        }
                    },
                    onLongPress = if (onLongClick != null) { _ ->
                        triggerLongClick()
                    } else null
                )
            },
        shape = shape,
        color = actualBg,
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) actualBorder else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Box {
            content(isFocused)
        }
    }
}
