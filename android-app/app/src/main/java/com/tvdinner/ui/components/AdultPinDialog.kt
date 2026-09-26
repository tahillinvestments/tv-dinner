package com.tvdinner.ui.components

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tvdinner.data.repository.AuthRepository
import com.tvdinner.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AdultPinMode {
    VERIFY,  // Enter PIN to toggle adult content or unlock
    SET,     // Set new PIN (enter -> confirm)
    CHANGE,  // Change PIN (old -> new -> confirm)
    REMOVE   // Enter current PIN to remove PIN protection
}

private enum class PinStep {
    VERIFY_CURRENT,
    ENTER_NEW,
    CONFIRM_NEW
}

@Composable
fun AdultPinDialog(
    mode: AdultPinMode,
    authRepo: AuthRepository,
    onDismissRequest: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember {
        mutableStateOf(
            when (mode) {
                AdultPinMode.VERIFY, AdultPinMode.REMOVE -> PinStep.VERIFY_CURRENT
                AdultPinMode.SET -> PinStep.ENTER_NEW
                AdultPinMode.CHANGE -> PinStep.VERIFY_CURRENT
            }
        )
    }

    var enteredPin by remember { mutableStateOf("") }
    var tempNewPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val defaultKeyFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        try {
            defaultKeyFocus.requestFocus()
        } catch (_: Exception) {}
    }

    fun handleDigit(d: Char) {
        if (isChecking || enteredPin.length >= 4) return
        errorMessage = null
        val nextPin = enteredPin + d
        enteredPin = nextPin

        if (nextPin.length == 4) {
            isChecking = true
            coroutineScope.launch {
                delay(180) // Visual feedback to see 4th dot fill
                when (mode) {
                    AdultPinMode.VERIFY -> {
                        if (authRepo.verifyAdultPin(nextPin)) {
                            onSuccess()
                        } else {
                            errorMessage = "Incorrect PIN. Try again."
                            enteredPin = ""
                            isChecking = false
                        }
                    }

                    AdultPinMode.SET -> {
                        if (step == PinStep.ENTER_NEW) {
                            tempNewPin = nextPin
                            enteredPin = ""
                            step = PinStep.CONFIRM_NEW
                            isChecking = false
                        } else if (step == PinStep.CONFIRM_NEW) {
                            if (nextPin == tempNewPin) {
                                authRepo.setAdultPin(nextPin)
                                onSuccess()
                            } else {
                                errorMessage = "PINs do not match. Try again."
                                enteredPin = ""
                                tempNewPin = ""
                                step = PinStep.ENTER_NEW
                                isChecking = false
                            }
                        }
                    }

                    AdultPinMode.CHANGE -> {
                        if (step == PinStep.VERIFY_CURRENT) {
                            if (authRepo.verifyAdultPin(nextPin)) {
                                enteredPin = ""
                                step = PinStep.ENTER_NEW
                                isChecking = false
                            } else {
                                errorMessage = "Current PIN incorrect."
                                enteredPin = ""
                                isChecking = false
                            }
                        } else if (step == PinStep.ENTER_NEW) {
                            tempNewPin = nextPin
                            enteredPin = ""
                            step = PinStep.CONFIRM_NEW
                            isChecking = false
                        } else if (step == PinStep.CONFIRM_NEW) {
                            if (nextPin == tempNewPin) {
                                authRepo.setAdultPin(nextPin)
                                onSuccess()
                            } else {
                                errorMessage = "PINs do not match. Try again."
                                enteredPin = ""
                                tempNewPin = ""
                                step = PinStep.ENTER_NEW
                                isChecking = false
                            }
                        }
                    }

                    AdultPinMode.REMOVE -> {
                        if (authRepo.verifyAdultPin(nextPin)) {
                            authRepo.setAdultPin(null)
                            onSuccess()
                        } else {
                            errorMessage = "Incorrect PIN. Cannot remove."
                            enteredPin = ""
                            isChecking = false
                        }
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (isChecking) return
        errorMessage = null
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
        }
    }

    fun handleClear() {
        if (isChecking) return
        errorMessage = null
        enteredPin = ""
    }

    val title = when (mode) {
        AdultPinMode.VERIFY -> "Adult Content PIN"
        AdultPinMode.SET -> if (step == PinStep.CONFIRM_NEW) "Confirm New PIN" else "Set Adult PIN"
        AdultPinMode.CHANGE -> when (step) {
            PinStep.VERIFY_CURRENT -> "Enter Current PIN"
            PinStep.ENTER_NEW -> "Enter New PIN"
            PinStep.CONFIRM_NEW -> "Confirm New PIN"
        }
        AdultPinMode.REMOVE -> "Remove Adult PIN"
    }

    val subtitle = when (mode) {
        AdultPinMode.VERIFY -> "Enter your 4-digit PIN to access 18+ adult content"
        AdultPinMode.SET -> if (step == PinStep.CONFIRM_NEW) "Re-enter the 4-digit PIN to confirm" else "Choose a 4-digit PIN to restrict adult content"
        AdultPinMode.CHANGE -> when (step) {
            PinStep.VERIFY_CURRENT -> "Enter your current PIN to authorize change"
            PinStep.ENTER_NEW -> "Choose a new 4-digit PIN"
            PinStep.CONFIRM_NEW -> "Re-enter the new PIN to confirm"
        }
        AdultPinMode.REMOVE -> "Enter your 4-digit PIN to turn off PIN protection"
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CinemaSurface,
            border = BorderStroke(1.dp, if (errorMessage != null) CinemaRed else CinemaSurfaceLight),
            modifier = modifier
                .width(360.dp)
                .wrapContentHeight()
                .padding(16.dp)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        val native = keyEvent.nativeKeyEvent
                        val code = native.keyCode
                        when (code) {
                            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { handleDigit('0'); true }
                            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> { handleDigit('1'); true }
                            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> { handleDigit('2'); true }
                            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> { handleDigit('3'); true }
                            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> { handleDigit('4'); true }
                            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> { handleDigit('5'); true }
                            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> { handleDigit('6'); true }
                            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> { handleDigit('7'); true }
                            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> { handleDigit('8'); true }
                            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> { handleDigit('9'); true }
                            KeyEvent.KEYCODE_DEL -> { handleBackspace(); true }
                            KeyEvent.KEYCODE_CLEAR -> { handleClear(); true }
                            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onDismissRequest(); true }
                            else -> false
                        }
                    } else false
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Icon & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (errorMessage != null) CinemaRed else CinemaAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // 4-Digit Display Boxes
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    for (i in 0..3) {
                        val isFilled = i < enteredPin.length
                        val isCurrent = i == enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    color = if (isFilled) CinemaSurfaceLight else CinemaSurfaceVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = when {
                                        errorMessage != null -> CinemaRed
                                        isCurrent -> CinemaAccent
                                        isFilled -> CinemaAccent.copy(alpha = 0.6f)
                                        else -> CinemaSurfaceLight
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFilled) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(CinemaAccent, CircleShape)
                                )
                            }
                        }
                    }
                }

                // Error Message
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = CinemaRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                // 3x4 On-Screen Numeric Keypad (D-pad & Touch accessible)
                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("⌫", "0", "✕")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    keypadRows.forEachIndexed { rIdx, row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEachIndexed { cIdx, key ->
                                val isFirstKey = (rIdx == 0 && cIdx == 0)
                                TvFocusableCard(
                                    onClick = {
                                        when (key) {
                                            "⌫" -> handleBackspace()
                                            "✕" -> onDismissRequest()
                                            else -> handleDigit(key[0])
                                        }
                                    },
                                    backgroundColor = CinemaSurfaceVariant,
                                    focusedBorderColor = CinemaAccent,
                                    focusedScale = 1.06f,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .then(if (isFirstKey) Modifier.focusRequester(defaultKeyFocus) else Modifier)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when (key) {
                                            "⌫" -> {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                    contentDescription = "Backspace",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            "✕" -> {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Cancel",
                                                    tint = CinemaRed,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            else -> {
                                                Text(
                                                    text = key,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
