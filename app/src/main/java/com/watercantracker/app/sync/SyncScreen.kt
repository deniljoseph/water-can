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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusBanner(state.syncState)

            when {
                state.syncState.status == SyncStatus.ERROR ->
                    ErrorCard(
                        errorCode = state.syncState.errorCode,
                        errorMsg  = state.syncState.error ?: "",
                        isMaster  = state.isMaster,
                        onRetry   = { if (state.isMaster) viewModel.createRoom() else viewModel.joinRoom() },
                        onReset   = viewModel::disconnect
                    )

                state.roomId == null ->
                    SetupCard(
                        isCreating     = state.isCreatingRoom,
                        isJoining      = state.isJoiningRoom,
                        joinRoomId     = state.joinRoomId,
                        onJoinIdChange = viewModel::setJoinRoomId,
                        onCreateRoom   = viewModel::createRoom,
                        onJoinRoom     = viewModel::joinRoom
                    )

                state.isMaster ->
                    MasterCard(
                        roomId       = state.roomId!!,
                        qrBitmap     = state.qrBitmap,
                        syncState    = state.syncState,
                        onCopyId     = { clipboard.setText(AnnotatedString(state.roomId!!)) },
                        onDisconnect = { showDisconnectDialog = true }
                    )

                else ->
                    SecondaryCard(
                        roomId       = state.roomId!!,
                        syncState    = state.syncState,
                        onDisconnect = { showDisconnectDialog = true }
                    )
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
                Button(onClick = { viewModel.disconnect(); showDisconnectDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Disconnect")
                }
            },
            dismissButton = { TextButton(onClick = { showDisconnectDialog = false }) { Text("Cancel") } }
        )
    }
}

// ── Status banner ─────────────────────────────────────────────────────────────
@Composable
private fun StatusBanner(syncState: SyncState) {
    val (color, icon, label) = when (syncState.status) {
        SyncStatus.IDLE     -> Triple(MaterialTheme.colorScheme.surfaceVariant,     Icons.Rounded.CloudOff,  "Not connected to sync")
        SyncStatus.SYNCING  -> Triple(MaterialTheme.colorScheme.secondaryContainer, Icons.Rounded.Sync,      "Connecting…")
        SyncStatus.SUCCESS  -> Triple(MaterialTheme.colorScheme.primaryContainer,   Icons.Rounded.CloudDone, "Live sync active ✓")
        SyncStatus.ERROR    -> Triple(MaterialTheme.colorScheme.errorContainer,     Icons.Rounded.CloudOff,  "Sync error")
        SyncStatus.DISABLED -> Triple(MaterialTheme.colorScheme.surfaceVariant,     Icons.Rounded.CloudOff,  "Sync disabled")
    }
    Surface(shape = MaterialTheme.shapes.medium, color = color, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (syncState.status == SyncStatus.SYNCING)
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else
                Icon(icon, null, modifier = Modifier.size(20.dp))
            Column {
                Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                syncState.lastSyncAt?.let { ts ->
                    Text("Last sync: ${SimpleDateFormat("d MMM HH:mm:ss", Locale.getDefault()).format(Date(ts))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Error card with specific guidance per error code ──────────────────────────
@Composable
private fun ErrorCard(
    errorCode: SyncErrorCode,
    errorMsg: String,
    isMaster: Boolean,
    onRetry: () -> Unit,
    onReset: () -> Unit
) {
    val (title, steps) = when (errorCode) {

        SyncErrorCode.PLACEHOLDER_JSON -> Pair(
            "⚙️  Firebase setup required",
            listOf(
                Step("1", "Open", "console.firebase.google.com", "in a browser"),
                Step("2", "Create a project → Add Android app", "", ""),
                Step("3", "Package name:", "com.watercantracker.app", ""),
                Step("4", "Download", "google-services.json", "from the Firebase console"),
                Step("5", "Replace", "app/google-services.json", "in your project with the downloaded file"),
                Step("6", "In Firebase Console → Build → Realtime Database →", "Create Database", "(test mode)"),
                Step("7", "Build → Authentication → Sign-in method → enable", "Anonymous", ""),
                Step("8", "Push to GitHub → Actions will build a new APK → install it"),
                Step("9", "Come back to this screen and tap", "Create Sync Room", "again")
            )
        )

        SyncErrorCode.AUTH_TIMEOUT -> Pair(
            "⏱  Authentication timed out",
            listOf(
                Step("1", "Check your internet connection — mobile data or Wi-Fi"),
                Step("2", "Open Firebase Console → Authentication → Sign-in method"),
                Step("3", "Make sure", "Anonymous", "sign-in is enabled (toggle ON)"),
                Step("4", "If it was off, enable it, then tap", "Retry", "below"),
                Step("5", "If still failing, check that your", "google-services.json", "matches your Firebase project")
            )
        )

        SyncErrorCode.DB_TIMEOUT -> Pair(
            "⏱  Database timed out",
            listOf(
                Step("1", "Check your internet connection"),
                Step("2", "Open Firebase Console → Build →", "Realtime Database", ""),
                Step("3", "Make sure the database was", "Created", "(not just clicked)"),
                Step("4", "Check the Rules tab — set to test mode or add read/write rules"),
                Step("5", "Tap", "Retry", "below")
            )
        )

        SyncErrorCode.ROOM_NOT_FOUND -> Pair(
            "🔍  Room not found",
            listOf(
                Step("1", "Ask the master device owner to share the Room ID again"),
                Step("2", "Make sure you copied the", "full Room ID", "without extra spaces"),
                Step("3", "The Room ID is case-sensitive"),
                Step("4", "If the master device reset their sync, a new room was created — get the new ID")
            )
        )

        else -> Pair(
            "❌  Sync error",
            listOf(
                Step("1", "Check your internet connection"),
                Step("2", "Make sure", "google-services.json", "is your real Firebase file (not the placeholder)"),
                Step("3", "Verify Anonymous Auth and Realtime Database are both enabled in Firebase Console"),
                Step("4", "Try tapping", "Retry", "below")
            )
        )
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Raw error message in a code-style box
            if (errorMsg.isNotBlank()) {
                Surface(shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(
                        errorMsg,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Text("What to do:", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)

            steps.forEach { step -> StepRow(step) }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }
                Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isMaster) "Retry Create" else "Retry Join")
                }
            }
        }
    }
}

private data class Step(
    val num: String,
    val before: String,
    val highlight: String = "",
    val after: String = ""
) {
    constructor(num: String, text: String) : this(num, text, "", "")
}

@Composable
private fun StepRow(step: Step) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(step.num, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                append(step.before)
                if (step.highlight.isNotBlank()) {
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)) {
                        append(step.highlight)
                    }
                }
                if (step.after.isNotBlank()) append(" ${step.after}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Setup card ────────────────────────────────────────────────────────────────
@Composable
private fun SetupCard(
    isCreating: Boolean, isJoining: Boolean,
    joinRoomId: String, onJoinIdChange: (String) -> Unit,
    onCreateRoom: () -> Unit, onJoinRoom: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Set Up Sync", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Text("This is your master device", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text("All data changes happen here. Other devices receive updates automatically.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Create Sync Room")
                }
            }

            HorizontalDivider()

            Text("Join another device's room", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text("Scan the QR from the master device — or paste the Room ID below.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = joinRoomId, onValueChange = onJoinIdChange,
                label = { Text("Room ID") }, placeholder = { Text("Paste Room ID here") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onJoinRoom, enabled = joinRoomId.isNotBlank() && !isJoining && !isCreating,
                modifier = Modifier.fillMaxWidth()) {
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
    roomId: String, qrBitmap: android.graphics.Bitmap?,
    syncState: SyncState, onCopyId: () -> Unit, onDisconnect: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your Sync Room", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Other devices scan this QR or paste the Room ID below",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

            if (qrBitmap != null) {
                Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "Sync QR Code",
                    modifier = Modifier.size(220.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                        .padding(8.dp))
            } else {
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Room ID", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(roomId, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    IconButton(onClick = onCopyId) { Icon(Icons.Rounded.ContentCopy, "Copy") }
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
                Text("Connected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()) {
                Text("Room: $roomId", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(10.dp))
            }
            syncState.lastSyncAt?.let { ts ->
                Text("Last sync: ${SimpleDateFormat("d MMM HH:mm:ss", Locale.getDefault()).format(Date(ts))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Data syncs automatically when the master device makes changes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                "📱  Other devices scan the QR code or paste the Room ID to join",
                "☁️   Powered by Firebase Realtime Database (free, always-on)",
                "📶  Offline-ready — changes sync when back online",
                "🔒  Room ID is private — only share it with your group",
                "⚙️   Requires a real google-services.json from Firebase Console"
            ).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
