package com.troyh.tvdinner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.troyh.tvdinner.data.repository.AuthRepository
import com.troyh.tvdinner.ui.components.TvFocusableCard
import com.troyh.tvdinner.ui.theme.*

@Composable
fun SettingsScreen(
    authRepo: AuthRepository,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePhone = remember { authRepo.getActivatedPhone() ?: "Not Activated" }
    val activeCred = remember { authRepo.getActiveLiveCredentials() }
    var credentialsList by remember { mutableStateOf(authRepo.getAllCredentials()) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Admin Password Protection
    var isAdminUnlocked by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var newPhone by remember { mutableStateOf("") }
    var newUser by remember { mutableStateOf("") }
    var newPswd by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize().background(CinemaBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Text(
                text = "SETTINGS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )

            // Current Session Card (Always visible)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CinemaSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Active Account",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = activePhone,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = CinemaAccent
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CinemaGreen.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "ACTIVE & UNLOCKED",
                                color = CinemaGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (activeCred != null) {
                        Text(
                            text = "Linked Account: ${activeCred.user}",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    // Sign Out CTA Button (Always accessible)
                    TvFocusableCard(
                        onClick = {
                            authRepo.signOut()
                            onSignOut()
                        },
                        backgroundColor = CinemaRed.copy(alpha = 0.2f),
                        focusedBorderColor = CinemaRed,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = CinemaRed)
                            Text("Sign Out / Switch Phone Account", color = CinemaRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Admin Portal (Locked behind 'admintvd')
            if (!isAdminUnlocked) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CinemaSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Protected",
                                tint = CinemaAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Admin Configuration & Credentials Table",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Enter admin password to view endpoints and credential management",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = {
                                    passwordInput = it
                                    passwordError = null
                                },
                                placeholder = { Text("Admin Password", color = TextMuted, fontSize = 13.sp) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = CinemaSurfaceVariant,
                                    unfocusedContainerColor = CinemaSurfaceVariant,
                                    focusedBorderColor = CinemaAccent,
                                    unfocusedBorderColor = CinemaSurfaceLight,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f).height(50.dp)
                            )

                            TvFocusableCard(
                                onClick = {
                                    if (passwordInput == "admintvd") {
                                        isAdminUnlocked = true
                                        passwordInput = ""
                                        passwordError = null
                                    } else {
                                        passwordError = "Incorrect admin password."
                                    }
                                },
                                backgroundColor = CinemaPrimary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(50.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxHeight().padding(horizontal = 18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Unlock",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        if (passwordError != null) {
                            Text(
                                text = passwordError ?: "",
                                color = CinemaRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                // Admin Unlocked Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ADMIN PORTAL (UNLOCKED)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = CinemaAccent
                    )

                    TvFocusableCard(
                        onClick = { isAdminUnlocked = false },
                        backgroundColor = CinemaSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = TextSecondary, modifier = Modifier.size(14.dp))
                            Text("Lock Admin", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                // Streaming Configuration Summary
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CinemaSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Streaming Endpoints",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Live TV Portal: ${authRepo.getLivePortalUrl()}",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "VOD Portal: ${authRepo.getVodPortalUrl()}",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "VOD Account: ${authRepo.getVodUsername()}",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Credential Table Management Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Credential Lookup Table (${credentialsList.size} registered)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    TvFocusableCard(
                        onClick = { showAddDialog = true },
                        backgroundColor = CinemaPrimary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Add Account", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Credential Table List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (cred in credentialsList) {
                        val isActive = authRepo.normalizePhone(cred.phone) == authRepo.normalizePhone(activePhone)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isActive) CinemaSurfaceLight else CinemaSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isActive) CinemaAccent else CinemaSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = cred.phone,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        if (isActive) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = CinemaPrimary
                                            ) {
                                                Text(
                                                    text = "CURRENT",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "User: ${cred.user} • Pass: ${cred.pswd.map { '*' }.joinToString("")}",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        authRepo.deleteCredential(cred.phone)
                                        credentialsList = authRepo.getAllCredentials()
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Credential Dialog
        if (showAddDialog) {
            Dialog(onDismissRequest = { showAddDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CinemaSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                    modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Add New Credential Entry",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        OutlinedTextField(
                            value = newPhone,
                            onValueChange = { newPhone = it },
                            label = { Text("10-Digit Phone Number") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CinemaSurfaceVariant,
                                unfocusedContainerColor = CinemaSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newUser,
                            onValueChange = { newUser = it },
                            label = { Text("Xtream Username") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CinemaSurfaceVariant,
                                unfocusedContainerColor = CinemaSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newPswd,
                            onValueChange = { newPswd = it },
                            label = { Text("Xtream Password") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CinemaSurfaceVariant,
                                unfocusedContainerColor = CinemaSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showAddDialog = false }) {
                                Text("Cancel", color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newPhone.isNotBlank() && newUser.isNotBlank()) {
                                        authRepo.addOrUpdateCredential(newPhone, newUser, newPswd)
                                        credentialsList = authRepo.getAllCredentials()
                                        newPhone = ""
                                        newUser = ""
                                        newPswd = ""
                                        showAddDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary)
                            ) {
                                Text("Save Credential")
                            }
                        }
                    }
                }
            }
        }
    }
}
