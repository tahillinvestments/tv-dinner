package com.tvdinner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvdinner.data.repository.AuthRepository
import com.tvdinner.ui.components.TvFocusableCard
import com.tvdinner.ui.components.TvKeypad
import com.tvdinner.ui.theme.*

@Composable
fun ActivationScreen(
    authRepo: AuthRepository,
    onActivated: () -> Unit
) {
    var rawDigits by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val formattedDisplay = remember(rawDigits) {
        val clean = rawDigits.take(10)
        val pad = clean.padEnd(10, '_')
        "(${pad.substring(0, 3)}) ${pad.substring(3, 6)}-${pad.substring(6, 10)}"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(CinemaBackground, Color(0xFF0F172A), CinemaBackground)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Icon & Header
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CinemaPrimary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "TV Dinner",
                        tint = CinemaPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Text(
                text = "TV DINNER",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = TextPrimary
            )

            Text(
                text = "Enter your 10-digit phone number to activate Live TV, VOD & Podcasts",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            // Phone Display Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CinemaSurface,
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (rawDigits.length == 10) CinemaAccent else CinemaSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formattedDisplay,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        color = if (rawDigits.length == 10) CinemaAccent else TextPrimary
                    )
                }
            }

            // Error message if any
            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = CinemaRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            // Numeric Keypad
            TvKeypad(
                onDigitClick = { digit ->
                    if (rawDigits.length < 10) {
                        rawDigits += digit
                        errorMessage = null
                    }
                },
                onBackspaceClick = {
                    if (rawDigits.isNotEmpty()) {
                        rawDigits = rawDigits.dropLast(1)
                        errorMessage = null
                    }
                },
                onClearClick = {
                    rawDigits = ""
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Activate Action Button
            TvFocusableCard(
                onClick = {
                    if (rawDigits.length < 10) {
                        errorMessage = "Please enter a complete 10-digit phone number."
                    } else {
                        val success = authRepo.activatePhone(rawDigits)
                        if (success) {
                            onActivated()
                        } else {
                            errorMessage = "Phone number not found in authorized credentials table."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                backgroundColor = CinemaPrimary,
                focusedBorderColor = CinemaFocus,
                focusedScale = 1.04f
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Activate",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ACTIVATE NOW",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
