package com.troyh.tvdinner.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.troyh.tvdinner.BuildConfig
import com.troyh.tvdinner.data.network.XtreamApiClient
import com.troyh.tvdinner.data.repository.AuthRepository
import com.troyh.tvdinner.data.repository.CatalogManager
import com.troyh.tvdinner.ui.components.TvFocusableCard
import com.troyh.tvdinner.ui.theme.*
import com.troyh.tvdinner.update.UpdateManifest
import com.troyh.tvdinner.update.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    authRepo: AuthRepository,
    catalogManager: CatalogManager? = null,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apiClient = remember { XtreamApiClient() }
    val updateManager = remember { UpdateManager(context) }
    val coroutineScope = rememberCoroutineScope()

    // Account & Credentials State
    var isTestingCreds by remember { mutableStateOf(false) }
    val hasValidCreds = remember(authRepo.getActiveUsername(), authRepo.getActivePassword()) {
        authRepo.hasValidCredentials()
    }
    var accountStatus by remember { mutableStateOf(if (hasValidCreds) "ACTIVE & SAVED" else "CREDENTIALS REQUIRED") }
    var isAccountActive by remember { mutableStateOf(hasValidCreds) }
    var accountStatusDetail by remember { 
        mutableStateOf<String?>(if (hasValidCreds) "Credentials loaded from local storage." else "Enter your username and password below to activate live TV & movies.") 
    }

    var customUser by remember { mutableStateOf(authRepo.getActiveUsername()) }
    var customPswd by remember { mutableStateOf(authRepo.getActivePassword()) }
    var editingField by remember { mutableStateOf<String?>(null) }
    var credsSavedMessage by remember { mutableStateOf<String?>(null) }

    // Subtitle Preferences State
    var vodSubtitlesEnabled by remember { mutableStateOf(authRepo.isVodSubtitlesEnabled()) }

    // Content Filtering State
    var adultContentEnabled by remember { mutableStateOf(authRepo.isAdultContentEnabled()) }

    // Update Management State
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateManifest?>(null) }
    var updateCheckMessage by remember { mutableStateOf<String?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }

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

            // 1. Subscription & Credentials Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CinemaSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subscription",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isAccountActive) CinemaGreen.copy(alpha = 0.15f) else CinemaYellow.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isAccountActive) CinemaGreen.copy(alpha = 0.4f) else CinemaYellow.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = accountStatus,
                                color = if (isAccountActive) CinemaGreen else CinemaYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (accountStatusDetail != null) {
                        Text(
                            text = accountStatusDetail!!,
                            fontSize = 12.sp,
                            color = if (isAccountActive) CinemaGreen else TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider(color = CinemaSurfaceLight, thickness = 1.dp)

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Server Credentials",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )

                        // Username Field (Click to Edit - no keyboard on D-pad navigation)
                        TvFocusableCard(
                            onClick = { editingField = "user" },
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = CinemaSurfaceVariant,
                            focusedBorderColor = CinemaFocus,
                            focusedScale = 1.02f,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Username", color = TextMuted, fontSize = 11.sp)
                                    Text(
                                        text = customUser.ifBlank { "Click to enter username" },
                                        color = if (customUser.isNotBlank()) TextPrimary else TextMuted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Username",
                                    tint = CinemaAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Password Field (Click to Edit - no keyboard on D-pad navigation)
                        TvFocusableCard(
                            onClick = { editingField = "pswd" },
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = CinemaSurfaceVariant,
                            focusedBorderColor = CinemaFocus,
                            focusedScale = 1.02f,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Password", color = TextMuted, fontSize = 11.sp)
                                    Text(
                                        text = if (customPswd.isNotBlank()) "••••••••••••" else "Click to enter password",
                                        color = if (customPswd.isNotBlank()) TextPrimary else TextMuted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Password",
                                    tint = CinemaAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (credsSavedMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAccountActive) CinemaGreen.copy(alpha = 0.15f) else CinemaRed.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isAccountActive) CinemaGreen.copy(alpha = 0.3f) else CinemaRed.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = credsSavedMessage!!,
                                color = if (isAccountActive) CinemaGreen else CinemaRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                customUser = ""
                                customPswd = ""
                                authRepo.clearCredentials()
                                catalogManager?.clearAllCaches()
                                isAccountActive = false
                                accountStatus = "CREDENTIALS REQUIRED"
                                accountStatusDetail = "Credentials cleared. Enter new credentials above to connect."
                                credsSavedMessage = "Credentials cleared."
                            },
                            enabled = !isTestingCreds,
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Clear", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                val u = customUser.trim()
                                val p = customPswd.trim()
                                if (u.isBlank() || p.isBlank()) {
                                    credsSavedMessage = "Please enter both username and password."
                                    return@Button
                                }
                                authRepo.setDirectCredentials(u, p)
                                catalogManager?.clearAllCaches()
                                isTestingCreds = true
                                accountStatus = "TESTING..."
                                credsSavedMessage = "Testing credentials with server..."
                                coroutineScope.launch {
                                    val testPortal = authRepo.getLivePortalUrl()
                                    val result = apiClient.testCredentials(testPortal, u, p)
                                    isTestingCreds = false
                                    if (result.isValid) {
                                        isAccountActive = true
                                        accountStatus = "ACTIVE & VERIFIED"
                                        accountStatusDetail = result.message
                                        credsSavedMessage = "Credentials verified & activated! Feeds updated."
                                    } else {
                                        isAccountActive = true
                                        accountStatus = "ACTIVE & SAVED"
                                        accountStatusDetail = result.message
                                        credsSavedMessage = "Credentials saved locally & active."
                                    }
                                }
                            },
                            enabled = !isTestingCreds,
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            if (isTestingCreds) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save & Apply", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. Playback & Subtitles Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CinemaSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Subtitles",
                                tint = CinemaAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Playback & Subtitles",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    // Movie & Series Subtitles / Closed Captions Toggle
                    TvFocusableCard(
                        onClick = {
                            val next = !vodSubtitlesEnabled
                            vodSubtitlesEnabled = next
                            authRepo.setVodSubtitlesEnabled(next)
                        },
                        backgroundColor = CinemaSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Movie & Series Subtitles (Closed Captions)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (vodSubtitlesEnabled) "Subtitles are enabled by default for Movies and Series" else "Subtitles are disabled by default (Default: OFF)",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Switch(
                                checked = vodSubtitlesEnabled,
                                onCheckedChange = {
                                    vodSubtitlesEnabled = it
                                    authRepo.setVodSubtitlesEnabled(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CinemaAccent,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = CinemaSurfaceLight
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }

                    // Clear Channel History
                    TvFocusableCard(
                        onClick = {
                            authRepo.clearChannelHistory()
                            Toast.makeText(context, "Channel watch history cleared", Toast.LENGTH_SHORT).show()
                        },
                        backgroundColor = CinemaSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "History",
                                    tint = CinemaAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Clear Channel History",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Reset the last 5 watched channels in Live TV",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CinemaRed.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaRed.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "CLEAR",
                                    color = CinemaRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. App Updates & Version Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CinemaSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Updates",
                                tint = CinemaAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "App Updates",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Current Version: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (availableUpdate != null) CinemaYellow.copy(alpha = 0.2f) else CinemaGreen.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (availableUpdate != null) CinemaYellow.copy(alpha = 0.5f) else CinemaGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (availableUpdate != null) "UPDATE AVAILABLE" else "UP TO DATE",
                                color = if (availableUpdate != null) CinemaYellow else CinemaGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (updateCheckMessage != null) {
                        Text(
                            text = updateCheckMessage!!,
                            fontSize = 12.sp,
                            color = if (availableUpdate != null) CinemaYellow else CinemaAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Available Update Information Box
                    if (availableUpdate != null) {
                        val update = availableUpdate!!
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CinemaSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "New Version: v${update.versionName} (Build ${update.versionCode})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                if (!update.title.isNullOrBlank()) {
                                    Text(
                                        text = update.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CinemaAccent
                                    )
                                }
                                Text(
                                    text = update.releaseNotes,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )

                                if (isDownloadingUpdate) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                                        LinearProgressIndicator(
                                            progress = { downloadProgress },
                                            modifier = Modifier.fillMaxWidth().height(6.dp),
                                            color = CinemaAccent,
                                            trackColor = CinemaSurfaceLight
                                        )
                                        Text(
                                            text = "Downloading APK: ${(downloadProgress * 100).toInt()}%",
                                            fontSize = 11.sp,
                                            color = CinemaAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                isCheckingUpdate = true
                                updateCheckMessage = "Checking update manifest..."
                                coroutineScope.launch {
                                    val update = updateManager.checkForUpdates()
                                    isCheckingUpdate = false
                                    availableUpdate = update
                                    if (update != null) {
                                        updateCheckMessage = "New update v${update.versionName} found!"
                                    } else if (updateManager.lastCheckError != null) {
                                        updateCheckMessage = updateManager.lastCheckError!!
                                    } else {
                                        updateCheckMessage = "You have the latest version installed."
                                    }
                                }
                            },
                            enabled = !isCheckingUpdate && !isDownloadingUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(color = CinemaAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Check for Updates", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (availableUpdate != null) {
                            Button(
                                onClick = {
                                    val update = availableUpdate ?: return@Button
                                    if (downloadedApkFile != null && downloadedApkFile!!.exists()) {
                                        updateManager.installApk(downloadedApkFile!!)
                                    } else {
                                        isDownloadingUpdate = true
                                        downloadProgress = 0f
                                        coroutineScope.launch {
                                            val file = updateManager.downloadApk(update.apkUrl) { progress ->
                                                downloadProgress = progress
                                            }
                                            isDownloadingUpdate = false
                                            if (file != null) {
                                                downloadedApkFile = file
                                                Toast.makeText(context, "Download complete. Launching installer...", Toast.LENGTH_SHORT).show()
                                                updateManager.installApk(file)
                                            } else {
                                                Toast.makeText(context, "Download failed. Please try again.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !isDownloadingUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                if (isDownloadingUpdate) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        text = if (downloadedApkFile != null) "Install Update" else "Download & Install",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Content Filtering (Adult 18+ Filter) Card — at the VERY BOTTOM
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CinemaSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Content Filter",
                                tint = CinemaAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Content Filtering",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    // Adult Content (18+) Toggle
                    TvFocusableCard(
                        onClick = {
                            val next = !adultContentEnabled
                            adultContentEnabled = next
                            authRepo.setAdultContentEnabled(next)
                            catalogManager?.clearAllCaches()
                        },
                        backgroundColor = CinemaSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Adult Content (18+)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (adultContentEnabled) "Adult categories and channels are visible" else "18+ adult categories and channels are hidden (Default: OFF)",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Switch(
                                checked = adultContentEnabled,
                                onCheckedChange = {
                                    adultContentEnabled = it
                                    authRepo.setAdultContentEnabled(it)
                                    catalogManager?.clearAllCaches()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CinemaRed,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = CinemaSurfaceLight
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }
                }
            }
        }

        // Credential Edit Dialog (Keyboard activates ONLY when user clicks into field)
        if (editingField != null) {
            val isUserField = editingField == "user"
            var tempValue by remember(editingField) {
                mutableStateOf(if (isUserField) customUser else customPswd)
            }
            val editFocusRequester = remember { FocusRequester() }

            Dialog(onDismissRequest = { editingField = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CinemaSurface,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaAccent),
                    modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (isUserField) "Edit Username" else "Edit Password",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        OutlinedTextField(
                            value = tempValue,
                            onValueChange = { tempValue = it },
                            singleLine = true,
                            visualTransformation = if (!isUserField) PasswordVisualTransformation() else VisualTransformation.None,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaSurfaceLight,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = CinemaAccent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(editFocusRequester)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                        ) {
                            Button(
                                onClick = { editingField = null },
                                colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel", color = TextSecondary)
                            }

                            Button(
                                onClick = {
                                    if (isUserField) {
                                        customUser = tempValue
                                    } else {
                                        customPswd = tempValue
                                    }
                                    credsSavedMessage = null
                                    editingField = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        LaunchedEffect(Unit) {
                            editFocusRequester.requestFocus()
                        }
                    }
                }
            }
        }
    }
}
