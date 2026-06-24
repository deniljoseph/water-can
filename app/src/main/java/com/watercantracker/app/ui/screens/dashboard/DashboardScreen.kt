package com.watercantracker.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watercantracker.app.domain.model.NextPayerReason
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
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val df = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.WaterDrop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Water Can Tracker", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        // FAB always visible — sits above bottom nav via bottomPadding
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPayment,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Record Payment") },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = bottomPadding.calculateBottomPadding())
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
                    Text(
                        text = "NEXT TO PAY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    if (nextMember != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(name = nextMember.name, avatarUri = nextMember.avatarUri, size = 56.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = nextMember.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = state.nextPayerResult?.reason?.label ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onAddPayment,
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(listOf(AmberAccent, AmberAccent))
                            )
                        ) {
                            Text("Mark as paid →", color = AmberAccent, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(
                            "No members yet — add members to start tracking.",
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // ── Last Payment ──────────────────────────────────────────────────
            val lp = state.lastPayment
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Last Payment", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    if (lp != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MemberAvatar(name = lp.paidByNameSnapshot, avatarUri = state.lastPaymentMember?.avatarUri, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(lp.paidByNameSnapshot, fontWeight = FontWeight.SemiBold)
                                    Text(df.format(Date(lp.purchaseDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatAmount(lp.amount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text("${lp.quantity} can${if (lp.quantity != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        lp.vendorName?.let {
                            Spacer(Modifier.height(6.dp))
                            Text("Vendor: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("No payments recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Monthly Spending ──────────────────────────────────────────────
            val ms = state.monthSummary
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("This Month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatChip("Total Spent", formatAmount(ms?.totalAmount ?: 0.0))
                        StatChip("Cans", "${ms?.totalCans ?: 0}")
                        StatChip("Payments", "${ms?.paymentCount ?: 0}")
                    }
                }
            }

            // ── Active Members ────────────────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("${state.activeMemberCount}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Active members in rotation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Extra space so FAB never covers last card
            Spacer(Modifier.height(100.dp))
        }
    }
}

private val NextPayerReason.label: String
    get() = when (this) {
        NextPayerReason.MANUAL_OVERRIDE -> "Manually set"
        NextPayerReason.ROTATION_ORDER -> "Next in rotation"
        NextPayerReason.NO_ACTIVE_MEMBERS -> ""
        NextPayerReason.ALL_SKIPPED -> "All others skipped"
    }
