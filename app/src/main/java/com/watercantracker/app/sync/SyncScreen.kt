package com.watercantracker.app.sync

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    bottomPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    var showDisconnectDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Sync", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Status banner ─────────────────────────────────────────────────
            StatusBanner(state.syncState)

            if (state.roomId == null) {
                // ── No room yet: create or join ───────────────────────────────
                SetupCard(
                    isCreating    = state.isCreatingRoom,
                    isJoining     = state.isJoiningRoom,
                    joinRoomId    = state.joinRoomId,
                    onJoinIdChange = viewModel::setJoinRoomId,
                    onCreateRoom  = viewModel::createRoom,
                    onJoinRoom    = viewModel::joinRoom
                )
            } else if (state.isMaster) {
                // ── Master device: show QR ────────────────────────────────────
                MasterCard(
                    roomId    = state.roomId!!,
                    qrBitmap  = state.qrBitmap,
                    syncState = state.syncState,
                    onCopyId  = { clipboard.setText(AnnotatedString(state.roomId!!)) },
                    onDisconnect = { showDisconnectDialog = true }
                )
            } else {
                // ── Secondary device: connected view ──────────────────────────
                SecondaryCard(
                    roomId       = state.roomId!!,
                    syncState    = state.syncState,
                    onDisconnect = { showDisconnectDialog = true }
                )
            }

            // ── How it works ──────────────────────────────────────────────────
            HowItWorksCard()

            Spacer(Modifier.height(bottomPadding.calculateBottomPadding()))
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect from sync?") },
            text  = { Text("Your local data will be kept. You can reconnect anytime using the room ID.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.disconnect(); showDisconnectDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { showDisconnectDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StatusBanner(syncState: SyncState) {
    val (color, icon, text) = when (syncState.status) {
        SyncStatus.IDLE     -> Triple(MaterialTheme.colorScheme.surfaceVariant, Icons.Rounded.CloudOff, "Not connected")
        SyncStatus.SYNCING  -> Triple(MaterialTheme.colorScheme.primaryContainer, Icons.Rounded.Sync, "Syncing…")
        SyncStatus.SUCCESS  -> Triple(MaterialTheme.colorScheme.primaryContainer, Icons.Rounded.CloudDone, "Live sync active")
        SyncStatus.ERROR    -> Triple(MaterialTheme.colorScheme.errorContainer, Icons.Rounded.CloudOff, "Sync error")
        SyncStatus.DISABLED -> Triple(MaterialTheme.colorScheme.surfaceVariant, Icons.Rounded.CloudOff, "Sync disabled")
    }
    Surface(shape = MaterialTheme.shapes.medium, color = color, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                syncState.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SetupCard(
    isCreating: Boolean,
    isJoining: Boolean,
    joinRoomId: String,
    onJoinIdChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Set Up Sync", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // Create room (master)
            Text("This is your device (master)", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Button(
                onClick = onCreateRoom,
                enabled = !isCreating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Create Sync Room")
            }

            HorizontalDivider()

            // Join room (secondary)
            Text("Join another device's room", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = joinRoomId,
                onValueChange = onJoinIdChange,
                label = { Text("Room ID") },
                placeholder = { Text("Paste room ID here") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onJoinRoom,
                enabled = joinRoomId.isNotBlank() && !isJoining,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isJoining) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Join Room")
            }
        }
    }
}

@Composable
private fun MasterCard(
    roomId: String,
    qrBitmap: android.graphics.Bitmap?,
    syncState: SyncState,
    onCopyId: () -> Unit,
    onDisconnect: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Your Sync Room", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Other devices scan this QR or enter the Room ID below",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)

            // QR Code
            qrBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Sync QR Code",
                    modifier = Modifier
                        .size(220.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                        .padding(8.dp)
                )
            } ?: CircularProgressIndicator(modifier = Modifier.size(48.dp))

            // Room ID with copy
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(roomId, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onCopyId) {
                        Icon(Icons.Rounded.ContentCopy, "Copy room ID")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Devices, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp))
                Text("${syncState.connectedDevices} device(s) connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.LinkOff, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Disconnect")
            }
        }
    }
}

@Composable
private fun SecondaryCard(roomId: String, syncState: SyncState, onDisconnect: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connected to Sync Room", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Text("Room ID: ", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(roomId, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
            syncState.lastSyncAt?.let { ts ->
                val fmt = java.text.SimpleDateFormat("d MMM, HH:mm:ss", java.util.Locale.getDefault())
                Text("Last synced: ${fmt.format(java.util.Date(ts))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("This device receives updates from the master device automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.LinkOff, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Disconnect")
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How it works", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            listOf(
                "🔵  Your device is the master — all data changes come from here",
                "📱  Other devices scan the QR code or paste the Room ID to join",
                "☁️   Data syncs in real-time via Firebase (free, no account needed on other devices)",
                "📶  Works offline — changes sync automatically when back online",
                "🔒  Room ID is private — only share it with your group members"
            ).forEach { step ->
                Text(step, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
