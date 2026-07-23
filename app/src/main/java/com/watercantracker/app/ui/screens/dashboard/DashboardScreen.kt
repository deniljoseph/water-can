package com.watercantracker.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watercantracker.app.domain.model.NextPayerReason
import com.watercantracker.app.sync.SyncStatus
import com.watercantracker.app.ui.components.MemberAvatar
import com.watercantracker.app.ui.components.StatChip
import com.watercantracker.app.ui.components.formatAmount
import com.watercantracker.app.ui.theme.AmberAccent
import com.watercantracker.app.ui.theme.TealDeep
import com.watercantracker.app.ui.theme.TealMid
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    bottomPadding: PaddingValues,
    onAddPayment: () -> Unit,
    onSettlement: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val df = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    var isRefreshing by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WaterDrop, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Water Can Tracker", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (state.syncState.status == SyncStatus.SUCCESS) {
                        Icon(Icons.Rounded.CloudDone, "Synced",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp).size(20.dp))
                    }
                    IconButton(onClick = {
                        if (!isRefreshing) {
                            isRefreshing = true
                            viewModel.refresh { isRefreshing = false }
                        }
                    }) {
                        if (isRefreshing)
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Rounded.Refresh, "Refresh",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onSettlement) {
                        Icon(Icons.Rounded.Calculate, "Settlement",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPayment,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Record Payment") },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = bottomPadding.calculateBottomPadding() + 48.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Hero: Next Person To Pay ──────────────────────────────────────
            val nextMember = state.nextPayerResult?.member
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(TealDeep, TealMid)))
                    .padding(24.dp)
            ) {
                Column {
                    Text("NEXT TO PAY",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        letterSpacing = 2.sp)
                    Spacer(Modifier.height(12.dp))

                    if (nextMember != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(name = nextMember.name,
                                avatarUri = nextMember.avatarUri, size = 56.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(nextMember.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White, fontWeight = FontWeight.Bold)
                                Text(state.nextPayerResult?.reason?.label ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f))
                            }
                        }

                        // ── Can quota progress ────────────────────────────────
                        if (state.cansPerTurn > 1) {
                            Spacer(Modifier.height(12.dp))
                            val paid  = state.cansPaidThisTurn
                            val total = state.cansPerTurn
                            val remaining = total - paid

                            Column {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Can quota: $paid / $total bought",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f))
                                    Text("$remaining more to go",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberAccent)
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { if (total > 0) paid.toFloat() / total else 0f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = AmberAccent,
                                    trackColor = Color.White.copy(alpha = 0.25f)
                                )
                            }
                        } else if (state.cansPerTurn == 1) {
                            // Single can per turn — show simple "needs to pay 1 can"
                            Text("Needs to buy 1 can",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f))
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onAddPayment,
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.linearGradient(listOf(AmberAccent, AmberAccent))
                                )
                            ) {
                                Text("Mark as paid →", color = AmberAccent,
                                    fontWeight = FontWeight.SemiBold)
                            }

                            // Nudge button — sends an on-demand reminder to the next payer
                            OutlinedButton(
                                onClick = {
                                    if (!state.nudgeSent) viewModel.sendNudge(context, nextMember.name)
                                },
                                enabled = !state.nudgeSent,
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.linearGradient(
                                        listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.6f))
                                    )
                                )
                            ) {
                                if (state.nudgeSent) {
                                    Icon(Icons.Rounded.Check, null,
                                        tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Nudged!", color = Color.White, fontWeight = FontWeight.SemiBold)
                                } else {
                                    Icon(Icons.Rounded.NotificationsActive, null,
                                        tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Nudge", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        Text("No members yet — add members to start tracking.",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // ── Settlement shortcut ───────────────────────────────────────────
            ElevatedCard(onClick = onSettlement, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Calculate, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Monthly Settlement", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall)
                        Text("Calculate who owes whom this month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
            }

            // ── Last Payment ──────────────────────────────────────────────────
            val lp = state.lastPayment
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Last Payment", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    if (lp != null) {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MemberAvatar(name = lp.paidByNameSnapshot,
                                    avatarUri = state.lastPaymentMember?.avatarUri, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(lp.paidByNameSnapshot, fontWeight = FontWeight.SemiBold)
                                    Text(df.format(Date(lp.purchaseDate)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatAmount(lp.amount, state.currencySymbol),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary)
                                Text("${lp.quantity} can${if (lp.quantity != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        lp.vendorName?.let {
                            Spacer(Modifier.height(4.dp))
                            Text("Vendor: $it", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("No payments recorded yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Monthly Spending ──────────────────────────────────────────────
            val ms = state.monthSummary
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("This Month", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatChip("Total Spent", formatAmount(ms?.totalAmount ?: 0.0, state.currencySymbol))
                        StatChip("Cans", "${ms?.totalCans ?: 0}")
                        StatChip("Payments", "${ms?.paymentCount ?: 0}")
                    }
                }
            }

            // ── Active Members ────────────────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Groups, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("${state.activeMemberCount}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Active members in rotation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(72.dp))
            Text("Made by Denil Joseph",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(bottomPadding.calculateBottomPadding() + 16.dp))
        }
    }
}

private val NextPayerReason.label: String
    get() = when (this) {
        NextPayerReason.MANUAL_OVERRIDE   -> "Manually set"
        NextPayerReason.ROTATION_ORDER    -> "Next in rotation"
        NextPayerReason.NO_ACTIVE_MEMBERS -> ""
        NextPayerReason.ALL_SKIPPED       -> "All others skipped"
    }
