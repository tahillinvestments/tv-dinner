package com.tvdinner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvdinner.ui.theme.*

@Composable
fun AppSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var isEditing by remember { mutableStateOf(false) }
    var isCardFocused by remember { mutableStateOf(false) }

    // --- Animated focus indicators: smooth color and border width transitions ---
    // The animated background makes focus unmistakably visible on TV even at a distance.
    val animatedBackground by animateColorAsState(
        targetValue = when {
            isEditing     -> Color(0xFF162D4A)
            isCardFocused -> Color(0xFF1A3560)
            else          -> CinemaSurface
        },
        animationSpec = tween(durationMillis = 150),
        label = "searchBg"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isEditing     -> CinemaAccent
            isCardFocused -> CinemaFocus
            else          -> CinemaSurfaceLight
        },
        animationSpec = tween(durationMillis = 150),
        label = "searchBorder"
    )

    // Focused border is 3dp — slightly thicker than editing so D-pad focus is unmissable pre-click
    val animatedBorderWidth by animateDpAsState(
        targetValue = when {
            isEditing     -> 2.5.dp
            isCardFocused -> 3.dp
            else          -> 1.dp
        },
        animationSpec = tween(durationMillis = 120),
        label = "searchBorderWidth"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(animatedBackground, RoundedCornerShape(10.dp))
            .border(animatedBorderWidth, animatedBorderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .focusable(!isEditing)
            .onFocusChanged { state ->
                isCardFocused = state.isFocused
            }
            .onPreviewKeyEvent { keyEvent ->
                if (!isEditing && keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionDown -> {
                            if (onMoveDown != null) {
                                onMoveDown()
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.DirectionLeft -> {
                            if (onMoveLeft != null) {
                                onMoveLeft()
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.DirectionRight -> {
                            if (onMoveRight != null) {
                                onMoveRight()
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                }
                false
            }
            .onKeyEvent { keyEvent ->
                if (!isEditing && keyEvent.type == KeyEventType.KeyUp) {
                    val code = keyEvent.nativeKeyEvent.keyCode
                    if (code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == android.view.KeyEvent.KEYCODE_ENTER ||
                        code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        isEditing = true
                        focusRequester.requestFocus()
                        keyboardController?.show()
                        return@onKeyEvent true
                    }
                }
                false
            }
            .clickable {
                isEditing = true
                focusRequester.requestFocus()
                keyboardController?.show()
            }
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = if (isCardFocused || isEditing) CinemaFocus else TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // Focused "SEARCH" hint label — visible only when D-pad focuses the bar before clicking in
        if (isCardFocused && !isEditing) {
            Text(
                text = "SEARCH",
                color = CinemaFocus,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && !isEditing) {
                Text(
                    text = placeholder,
                    color = if (isCardFocused) TextPrimary else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (isCardFocused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(CinemaAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        isEditing = false
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onSearch?.invoke()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (!state.isFocused && isEditing) {
                            isEditing = false
                        }
                    }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            val code = keyEvent.nativeKeyEvent.keyCode
                            if (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN ||
                                code == android.view.KeyEvent.KEYCODE_ESCAPE ||
                                code == android.view.KeyEvent.KEYCODE_BACK) {
                                isEditing = false
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                return@onKeyEvent true
                            }
                        }
                        false
                    }
            )
        }
        if (value.isNotEmpty()) {
            IconButton(
                onClick = {
                    onValueChange("")
                    isEditing = false
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
