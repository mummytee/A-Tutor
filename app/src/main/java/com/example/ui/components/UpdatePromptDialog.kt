package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SecurityUpdateGood
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.update.AppUpdateUiState
import com.example.update.AvailableAppUpdate
import java.util.Locale

@Composable
fun UpdatePromptDialog(
    state: AppUpdateUiState,
    onStartUpdate: (AvailableAppUpdate) -> Unit,
    onRetryInstall: (AvailableAppUpdate, String) -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is AppUpdateUiState.UpdateAvailable -> {
            val update = state.update
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Update Available",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("Current: v${BuildConfig.VERSION_NAME}") }
                            )
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = "New: ${update.tagName}",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            )
                        }

                        if (update.apkSizeBytes > 0L) {
                            Text(
                                text = "Installer package: ${update.apkFileName} (${formatBytes(update.apkSizeBytes)})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "What's New",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp, max = 220.dp)
                        ) {
                            Text(
                                text = cleanReleaseNotes(update.changelogMarkdown),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onStartUpdate(update) },
                        modifier = Modifier.testTag("update_now_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Update Now")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("update_later_btn")
                    ) {
                        Text("Later")
                    }
                }
            )
        }

        is AppUpdateUiState.Downloading -> {
            val progress = state.progress
            AlertDialog(
                onDismissRequest = { /* Keep visible while streaming APK */ },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Downloading ${state.update.tagName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${(progress * 100).toInt()}% • ${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = "Downloaded ${formatBytes(state.downloadedBytes)}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }

        is AppUpdateUiState.NeedsInstallPermission -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.SecurityUpdateGood,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Allow App Installs") },
                text = {
                    Text(
                        "The update (${state.update.tagName}) is downloaded to cache. " +
                            "Enable \"Allow from this source\" in Android Settings, then tap Install APK.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onOpenInstallPermissionSettings) {
                            Text("Open Settings")
                        }
                        Button(
                            onClick = { onRetryInstall(state.update, state.cachedApkPath) },
                            modifier = Modifier.testTag("retry_install_apk_btn")
                        ) {
                            Text("Install APK")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            )
        }

        is AppUpdateUiState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.InstallMobile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Installer Ready") },
                text = {
                    Text(
                        "A-Tutor ${state.update.tagName} has been handed off to Android's package installer. " +
                            "If the prompt did not open automatically, tap Install APK below.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onRetryInstall(state.update, state.cachedApkPath) },
                        modifier = Modifier.testTag("launch_installer_btn")
                    ) {
                        Text("Install APK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            )
        }

        is AppUpdateUiState.Error -> {
            val pending = state.pendingUpdate
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Update Failed") },
                text = {
                    Column {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                    }
                },
                confirmButton = {
                    if (pending != null) {
                        Button(onClick = { onStartUpdate(pending) }) {
                            Text("Retry Download")
                        }
                    } else {
                        Button(onClick = onDismiss) {
                            Text("OK")
                        }
                    }
                },
                dismissButton = {
                    if (pending != null) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }
                }
            )
        }

        else -> Unit
    }
}

private fun cleanReleaseNotes(raw: String): String {
    return raw
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        .replace("**", "")
        .replace("`", "")
        .trim()
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = bytes / 1024.0
        String.format(Locale.US, "%.0f KB", kb)
    }
}
