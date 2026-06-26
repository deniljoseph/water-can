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
import java.text.SimpleDateFormat
import java.util.*

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
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Status banner ─────────────────────────────────────────────────
            StatusBanner(state.syncState)

            when {
                // ── Error state with retry ────────────────────────────────────
                state.syncState.status == SyncStatus.ERROR -> {
                    ErrorCard(
                        error     = state.syncState.error ?: "Unknown error",
                        onRetry   = { if (state.isMaster) viewModel.createRoom() else viewModel.joinRoom() },
                        onReset   = { viewModel.disconnect() }
                    )
                }

                // ── Not connected yet ─────────────────────────────────────────
                state.roomId == null -> {
                    SetupCard(
                        isCreating     = state.isCreatingRoom,
                        isJoining      = state.isJoiningRoom,
                        joinRoomId     = state.joinRoomId,
                        onJoinIdChange = viewModel::setJoinRoomId,
                        onCreateRoom   = viewModel::createRoom,
                        onJoinRoom     = viewModel::joinRoom
                    )
                }

                // ── Master device ─────────────────────────────────────────────
                state.isMaster -> {
                    MasterCard(
                        roomId       = state.roomId!!,
                        qrBitmap     = state.qrBitmap,
                        syncState    = state.syncState,
                        onCopyId     = {
                            clipboard.setText(AnnotatedString(state.roomId!!))
                        },
                        onDisconnect = { showDisconnectDialog = true }
                    )
                }

                // ── Secondary device ──────────────────────────────────────────
                else -> {
                    SecondaryCard(
                        roomId       = state.roomId!!,
                        syncState    = state.syncState,
                        onDisconnect = { showDisconnectDialog = true }
                    )
                }
            }

            HowItWorksCard()
            Spacer(Modifier.height(bottomPadding.calculateBottomPadding()))
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect from sync?") },
            text  = { Text("Your local data is kept. You can reconnect anytime using the Room ID.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.disconnect(); showDisconnectDialog = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Status banner ─────────────────────────────────────────────────────────────
@Composable
private fun StatusBanner(syncState: SyncState) {
    val (color, icon, label) = when (syncState.status) {
        SyncStatus.IDLE     -> Triple(MaterialTheme.colorScheme.surfaceVariant,      Icons.Rounded.CloudOff,  "Not connected")
        SyncStatus.SYNCING  -> Triple(MaterialTheme.colorScheme.secondaryContainer,  Icons.Rounded.Sync,      "Connecting…")
        SyncStatus.SUCCESS  -> Triple(MaterialTheme.colorScheme.primaryContainer,    Icons.Rounded.CloudDone, "Live sync active")
        SyncStatus.ERROR    -> Triple(MaterialTheme.colorScheme.errorContainer,      Icons.Rounded.CloudOff,  "Sync error")
        SyncStatus.DISABLED -> Triple(MaterialTheme.colorScheme.surfaceVariant,      Icons.Rounded.CloudOff,  "Sync disabled")
    }
    Surface(shape = MaterialTheme.shapes.medium, color = color, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (syncState.status == SyncStatus.SYNCING) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, null, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                syncState.lastSyncAt?.let { ts ->
                    Text(
                        "Last sync: ${SimpleDateFormat("d MMM HH:mm:ss", Locale.getDefault()).format(Date(ts))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Error card ────────────────────────────────────────────────────────────────
@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit, onReset: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sync Error", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()) {
                Text(error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp))
            }
            Text("Common fixes:", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(
                "Check internet connection",
                "Make sure you followed FIREBASE_SETUP.md and replaced google-services.json",
                "Verify Anonymous Auth is enabled in Firebase Console",
                "Check Firebase Realtime Database rules allow read/write"
            ).forEach { tip ->
                Text("• $tip", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
                Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Retry") }
            }
        }
    }
}

// ── Setup card ────────────────────────────────────────────────────────────────
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

            // Create
            Text("This is your master device", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text("Create a sync room — other devices can then join using the QR code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCreateRoom, enabled = !isCreating && !isJoining,
                modifier = Modifier.fillMaxWidth()) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Creating room…")
                } else {
                    Icon(Icons.Rounded.AddCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create Sync Room (Master)")
                }
            }

            HorizontalDivider()

            // Join
            Text("Join another device's room", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text("Scan a QR code — or paste the Room ID below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = joinRoomId,
                onValueChange = onJoinIdChange,
                label = { Text("Room ID") },
                placeholder = { Text("Paste Room ID here") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onJoinRoom,
                enabled = joinRoomId.isNotBlank() && !isJoining && !isCreating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isJoining) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Joining…")
                } else {
                    Icon(Icons.Rounded.Link, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Join Room")
                }
            }
        }
    }
}

// ── Master card ───────────────────────────────────────────────────────────────
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
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Your Sync Room", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Other devices scan this QR code or paste the Room ID to join",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)

            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Sync QR Code",
                    modifier = Modifier
                        .size(220.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                        .padding(8.dp)
                )
            } else {
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            Surface(shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Room ID", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(roomId, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onCopyId) {
                        Icon(Icons.Rounded.ContentCopy, "Copy Room ID")
                    }
                }
            }

            if (syncState.connectedDevices > 0) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Devices, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp))
                    Text("${syncState.connectedDevices} device(s) synced",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.LinkOff, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Disconnect")
            }
        }
    }
}

// ── Secondary card ────────────────────────────────────────────────────────────
@Composable
private fun SecondaryCard(roomId: String, syncState: SyncState, onDisconnect: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudDone, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connected to Sync Room", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
            Surface(shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp)) {
                    Text("Room ID: ", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(roomId, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
            Text("Data syncs automatically whenever the master device makes changes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.LinkOff, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Leave Room")
            }
        }
    }
}

// ── How it works ──────────────────────────────────────────────────────────────
@Composable
private fun HowItWorksCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How Sync Works", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            listOf(
                "🔵  Your device is the master — all data changes originate here",
                "📱  Other devices scan the QR or paste the Room ID to join as viewers",
                "☁️   Firebase Realtime Database powers the sync (free, always-on)",
                "📶  Offline-ready — changes sync automatically when back online",
                "🔒  Room ID is private — only share it with your group"
            ).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
