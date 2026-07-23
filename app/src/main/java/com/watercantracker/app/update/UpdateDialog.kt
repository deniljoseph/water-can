package com.watercantracker.app.update

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Drop this composable near the top of MainActivity's content (or Dashboard) —
 * it silently checks for updates once, and shows a dialog only if one is found.
 */
@Composable
fun UpdateCheckerHost(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!state.checkedOnce) viewModel.checkForUpdate()
    }

    state.updateInfo?.let { info ->
        UpdateAvailableDialog(
            info = info,
            downloadState = state.downloadState,
            onUpdate = { viewModel.downloadUpdate(context) },
            onDismiss = { viewModel.dismissUpdate() }
        )
    }
}

@Composable
private fun UpdateAvailableDialog(
    info: UpdateInfo,
    downloadState: DownloadState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (downloadState == DownloadState.Idle) onDismiss() },
        icon = { Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Update available", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Version ${info.versionName} is ready to install.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("What's new:", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(info.changelog, style = MaterialTheme.typography.bodySmall)
                }

                when (val ds = downloadState) {
                    is DownloadState.Downloading -> {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text("Downloading…", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is DownloadState.Done -> {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download complete — opening installer…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is DownloadState.Failed -> {
                        Spacer(Modifier.height(14.dp))
                        Text("Download failed: ${ds.reason}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    DownloadState.Idle -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = downloadState == DownloadState.Idle || downloadState is DownloadState.Failed
            ) {
                Text(if (downloadState is DownloadState.Failed) "Retry" else "Update Now")
            }
        },
        dismissButton = {
            if (downloadState == DownloadState.Idle) {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        }
    )
}
