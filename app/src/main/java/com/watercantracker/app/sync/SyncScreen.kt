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
    initialRoomId: String? = null,
    bottomPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    var showDisconnectDialog by remember { mutableStateOf(false) }

    // Auto-join when opened via QR deep link
    LaunchedEffect(initialRoomId) {
        if (!initialRoomId.isNullOrBlank() && state.roomId == null) {
            viewModel.setJoinRoomId(initialRoomId)
            viewModel.joinRoom()
        }
    }

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
            text  = { Text("Your local data is kept. Reconnect anytime with the Room ID.") },
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
        SyncStatus.ERROR    -> Triple(MaterialTheme.colorScheme.errorContainer,     Icons.Rounded.CloudOff,  "Sync error — see steps below")
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

// ── Error card ────────────────────────────────────────────────────────────────
@Composable
private fun ErrorCard(
    errorCode: SyncErrorCode,
    errorMsg: String,
    isMaster: Boolean,
    onRetry: () -> Unit,
    onReset: () -> Unit
) {
    val (title, intro, steps, rulesSnippet) = when (errorCode) {

        SyncErrorCode.PLACEHOLDER_JSON -> ErrorContent(
            title = "⚙️  Firebase not set up yet",
            intro = "The app is using a placeholder Firebase config. You need to connect it to a real Firebase project.",
            steps = listOf(
                "Go to console.firebase.google.com → Create a project",
                "Inside the project, click the Android icon → register package: com.watercantracker.app",
                "Download google-services.json and replace app/google-services.json in your project",
                "In Firebase Console → Build → Realtime Database → Create Database → choose test mode",
                "Build → Authentication → Sign-in method → enable Anonymous",
                "Commit and push to GitHub → GitHub Actions builds a new APK → install it",
                "Come back here and tap Create Sync Room"
            ),
            rulesSnippet = null
        )

        SyncErrorCode.DB_TIMEOUT -> ErrorContent(
            title = "🔒  Database rules are blocking the write",
            intro = "Auth succeeded but the database write timed out. This almost always means your " +
                    "Realtime Database security rules are set to deny all access (the default after test mode expires).",
            steps = listOf(
                "Open console.firebase.google.com",
                "Select your project → Build → Realtime Database",
                "Click the Rules tab at the top",
                "Replace the rules with the snippet below and click Publish",
                "Come back here and tap Retry"
            ),
            rulesSnippet = """
{
  "rules": {
    "rooms": {
      "${'$'}roomId": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}""".trimIndent()
        )

        SyncErrorCode.AUTH_TIMEOUT -> ErrorContent(
            title = "⏱  Authentication timed out",
            intro = "The app could not sign in anonymously to Firebase.",
            steps = listOf(
                "Check your internet connection (mobile data or Wi-Fi)",
                "Open Firebase Console → Build → Authentication → Sign-in method",
                "Make sure Anonymous is toggled ON",
                "If it was off, enable it then tap Retry below"
            ),
            rulesSnippet = null
        )

        SyncErrorCode.ROOM_NOT_FOUND -> ErrorContent(
            title = "🔍  Room not found",
            intro = "No sync room exists with that ID.",
            steps = listOf(
                "Ask the master device owner to share the Room ID again",
                "Make sure you copied the full Room ID with no extra spaces",
                "Room IDs are case-sensitive",
                "If the master device reset their sync, get the new Room ID"
            ),
            rulesSnippet = null
        )

        else -> ErrorContent(
            title = "❌  Sync error",
            intro = "Something went wrong. Follow the steps below.",
            steps = listOf(
                "Check your internet connection",
                "Make sure google-services.json is your real Firebase file (not the placeholder)",
                "Firebase Console → Build → Realtime Database → Rules tab → set to allow auth reads/writes",
                "Firebase Console → Authentication → Anonymous must be enabled",
                "Tap Retry below"
            ),
            rulesSnippet = null
        )
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(intro, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Raw error in monospace box
            if (errorMsg.isNotBlank()) {
                Surface(shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(errorMsg,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp))
                }
            }

            HorizontalDivider()
            Text("Steps to fix:", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)

            steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(22.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(step, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Rules snippet
            rulesSnippet?.let { snippet ->
                Spacer(Modifier.height(2.dp))
                Text("Paste these rules in Firebase Console → Realtime Database → Rules:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                Surface(shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(snippet,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        modifier = Modifier.padding(12.dp))
                }
            }

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
                    Text(if (isMaster) "Retry" else "Retry Join")
                }
            }
        }
    }
}

private data class ErrorContent(
    val title: String,
    val intro: String,
    val steps: List<String>,
    val rulesSnippet: String?
)

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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCreateRoom, enabled = !isCreating && !isJoining,
                modifier = Modifier.fillMaxWidth()) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp)); Text("Creating room…")
                } else {
                    Icon(Icons.Rounded.AddCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Create Sync Room")
                }
            }

            HorizontalDivider()

            Text("Join another device's room", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text("Scan the QR from the master device — or paste the Room ID below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = joinRoomId, onValueChange = onJoinIdChange,
                label = { Text("Room ID") }, placeholder = { Text("Paste Room ID here") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onJoinRoom,
                enabled = joinRoomId.isNotBlank() && !isJoining && !isCreating,
                modifier = Modifier.fillMaxWidth()) {
                if (isJoining) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp)); Text("Joining…")
                } else {
                    Icon(Icons.Rounded.Link, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Join Room")
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
            Text("Other devices scan this QR or paste the Room ID",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

            if (qrBitmap != null) {
                Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "Sync QR",
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
                Spacer(Modifier.width(6.dp)); Text("Disconnect")
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
                Text("Room: $roomId",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(10.dp))
            }
            syncState.lastSyncAt?.let { ts ->
                Text("Last sync: ${SimpleDateFormat("d MMM HH:mm:ss", Locale.getDefault()).format(Date(ts))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Data syncs automatically when the master device makes changes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.LinkOff, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Leave Room")
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
                "☁️   Powered by Firebase Realtime Database (free Spark plan)",
                "📶  Offline-ready — changes sync automatically when back online",
                "🔒  Room ID is private — only share with your group",
                "⚙️   Requires google-services.json from your Firebase project"
            ).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
