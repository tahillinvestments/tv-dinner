package com.tvdinner.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.Coil
import com.tvdinner.data.network.XtreamApiClient
import com.tvdinner.data.repository.CatalogManager
import com.tvdinner.player.ExoPlayerManager
import com.tvdinner.ui.player.YouTubeRemoteBridge
import com.tvdinner.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Shared 6-step TV Dinner System Reboot sequence.
 * Flushes active media engines, blanks YouTube WebView, evicts network socket pools,
 * purges memory & disk caches, clears catalog caches, and recreates the host activity.
 */
suspend fun performSystemReboot(
    context: Context,
    playerManager: ExoPlayerManager?,
    apiClient: XtreamApiClient,
    catalogManager: CatalogManager?
) {
    // Step 1: Full media engine restart on Main thread
    try {
        playerManager?.reinitialize()
    } catch (_: Exception) {}

    // Step 2: Stop YouTube WebView playback (also on Main)
    try {
        YouTubeRemoteBridge.activeWebView?.let { wv ->
            try {
                wv.onPause()
                wv.stopLoading()
                wv.loadUrl("about:blank")
            } catch (_: Exception) {}
        }
        YouTubeRemoteBridge.activeWebView = null
        YouTubeRemoteBridge.activeVideoId = null
    } catch (_: Exception) {}

    // Step 3: Flush catalog/image OkHttp connection pool on IO
    withContext(Dispatchers.IO) {
        try {
            apiClient.okHttpClient.connectionPool.evictAll()
        } catch (_: Exception) {}
    }

    // Step 4: Clear image memory/disk caches & cacheDir
    withContext(Dispatchers.IO) {
        try {
            Coil.imageLoader(context).memoryCache?.clear()
            Coil.imageLoader(context).diskCache?.clear()
        } catch (_: Exception) {}

        try {
            context.cacheDir.deleteRecursively()
        } catch (_: Exception) {}
    }

    // Step 5: Clear catalog in-memory caches
    try {
        catalogManager?.clearAllCaches()
    } catch (_: Exception) {}

    delay(350)

    // Step 6: Recycle and reopen app cleanly at restarted state
    withContext(Dispatchers.Main) {
        Toast.makeText(
            context,
            "TV Dinner System Reboot Complete: Streaming engines & caches refreshed.",
            Toast.LENGTH_LONG
        ).show()
        (context as? android.app.Activity)?.recreate()
    }
}

/**
 * System Reboot Confirmation Dialog with warning info and progress spinner.
 */
@Composable
fun SystemRebootDialog(
    onDismissRequest: () -> Unit,
    onConfirmReboot: () -> Unit,
    isRebooting: Boolean
) {
    Dialog(onDismissRequest = { if (!isRebooting) onDismissRequest() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CinemaSurface,
            border = BorderStroke(1.5.dp, CinemaYellow),
            modifier = Modifier.fillMaxWidth(0.92f).padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = CinemaYellow)
                    Text(
                        text = "TV Dinner System Reboot",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "This will completely flush active media streams, evict open network connection pools, purge video/image caches, and reload directory channels.\n\nYour username, password, and server settings will remain 100% intact.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                if (isRebooting) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = CinemaYellow, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Rebooting media engines and clearing buffers...", color = CinemaYellow, fontSize = 13.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        Button(
                            onClick = onDismissRequest,
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }

                        Button(
                            onClick = onConfirmReboot,
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaYellow),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reboot Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
