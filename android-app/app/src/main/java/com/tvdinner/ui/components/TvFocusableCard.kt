package com.tvdinner.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tvdinner.ui.theme.CinemaFocus
import com.tvdinner.ui.theme.CinemaSurfaceVariant

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TvFocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = CinemaSurfaceVariant,
    focusedBorderColor: Color = CinemaFocus,
    focusedScale: Float = 1.05f,
    elevation: Dp = 4.dp,
    content: @Composable BoxScope.(Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var isLongPressHandled by remember { mutableStateOf(false) }
    var isKeyDownOnThisCard by remember { mutableStateOf(false) }
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
                        ambientColor = focusedBorderColor,
                        spotColor = focusedBorderColor
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
                    isKeyDownOnThisCard = false
                    isLongPressHandled = false
                }
            }
            .onKeyEvent { keyEvent ->
                val code = keyEvent.nativeKeyEvent.keyCode
                val isSelectKey = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == KeyEvent.KEYCODE_ENTER ||
                        code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                        code == KeyEvent.KEYCODE_BUTTON_A ||
                        code == KeyEvent.KEYCODE_BUTTON_SELECT

                if (keyEvent.type == KeyEventType.KeyDown && isSelectKey) {
                    if (onLongClick != null && (keyEvent.nativeKeyEvent.isLongPress || keyEvent.nativeKeyEvent.repeatCount >= 1)) {
                        isLongPressHandled = true
                        onLongClick()
                        return@onKeyEvent true
                    }
                    isKeyDownOnThisCard = true
                    return@onKeyEvent true
                } else if (keyEvent.type == KeyEventType.KeyUp && isSelectKey) {
                    if (isLongPressHandled) {
                        isLongPressHandled = false
                        isKeyDownOnThisCard = false
                        return@onKeyEvent true
                    }
                    if (!isKeyDownOnThisCard) {
                        // Stray KeyUp leaked from a prior screen or focus transition: consume and ignore
                        return@onKeyEvent true
                    }
                    isKeyDownOnThisCard = false
                    onClick()
                    return@onKeyEvent true
                }
                false
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) focusedBorderColor else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Box {
            content(isFocused)
        }
    }
}
