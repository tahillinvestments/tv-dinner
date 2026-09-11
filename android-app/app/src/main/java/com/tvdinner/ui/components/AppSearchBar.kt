package com.tvdinner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val textFieldFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    var isEditing by remember { mutableStateOf(false) }
    var isCardFocused by remember { mutableStateOf(false) }

    val isPreSelectFocused = isCardFocused && !isEditing

    // --- Animated focus indicators: smooth color, scale, shadow, and border width transitions ---
    // The animated scale, glowing shadow, and vibrant background make focus unmistakably visible on TV before clicking in.
    val scale by animateFloatAsState(
        targetValue = if (isPreSelectFocused) 1.04f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "searchScale"
    )

    val animatedBackground by animateColorAsState(
        targetValue = when {
            isEditing          -> Color(0xFF162D4A)
            isPreSelectFocused -> Color(0xFF1E3C72)
            else               -> CinemaSurface
        },
        animationSpec = tween(durationMillis = 150),
        label = "searchBg"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isEditing          -> CinemaAccent
            isPreSelectFocused -> CinemaFocus
            else               -> CinemaSurfaceLight
        },
        animationSpec = tween(durationMillis = 150),
        label = "searchBorder"
    )

    // Focused border is 3dp — thick and sharp so D-pad focus is unmissable pre-click
    val animatedBorderWidth by animateDpAsState(
        targetValue = when {
            isEditing          -> 2.5.dp
            isPreSelectFocused -> 3.dp
            else               -> 1.dp
        },
        animationSpec = tween(durationMillis = 120),
        label = "searchBorderWidth"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .onFocusChanged { state ->
                isCardFocused = state.isFocused || state.hasFocus
            }
            .then(modifier)
            .scale(scale)
            .shadow(
                elevation = if (isPreSelectFocused) 14.dp else 0.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = CinemaFocus,
                spotColor = CinemaFocus
            )
            .background(animatedBackground, RoundedCornerShape(10.dp))
            .border(animatedBorderWidth, animatedBorderColor, RoundedCornerShape(10.dp))
            .focusable(!isEditing)
            .padding(horizontal = 12.dp, vertical = 6.dp)
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
                val code = keyEvent.nativeKeyEvent.keyCode
                val isSelectKey = code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                  code == android.view.KeyEvent.KEYCODE_ENTER ||
                                  code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                if (!isEditing && isSelectKey) {
                    if (keyEvent.type == KeyEventType.KeyUp) {
                        isEditing = true
                        coroutineScope.launch {
                            delay(50)
                            try {
                                textFieldFocusRequester.requestFocus()
                                keyboardController?.show()
                            } catch (_: Exception) {}
                        }
                    }
                    return@onKeyEvent true
                }
                false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isEditing = true
                coroutineScope.launch {
                    delay(50)
                    try {
                        textFieldFocusRequester.requestFocus()
                        keyboardController?.show()
                    } catch (_: Exception) {}
                }
            }
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = if (isPreSelectFocused || isEditing) CinemaFocus else TextSecondary,
            modifier = Modifier.size(if (isPreSelectFocused) 20.dp else 18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // High-contrast TV Focus Badge: unmistakable cue when D-pad focuses the bar before clicking
        if (isPreSelectFocused) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = CinemaFocus,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "PRESS OK TO SEARCH",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && !isEditing) {
                Text(
                    text = placeholder,
                    color = if (isPreSelectFocused) Color.White else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (isPreSelectFocused) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                readOnly = !isEditing,
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
                    .focusRequester(textFieldFocusRequester)
                    .focusProperties {
                        canFocus = isEditing
                    }
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
