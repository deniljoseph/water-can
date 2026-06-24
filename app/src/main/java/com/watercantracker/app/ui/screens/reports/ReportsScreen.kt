package com.watercantracker.app.ui.screens.reports

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watercantracker.app.domain.model.MemberStats
import com.watercantracker.app.ui.components.MemberAvatar
import com.watercantracker.app.ui.components.formatAmount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(bottomPadding: PaddingValues, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Balance tracking ──────────────────────────────────────────────
            SectionCard(title = "Balance Tracking") {
                if (state.memberStats.isEmpty()) {
                    Text("No data yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.memberStats.forEach { stats ->
                        BalanceRow(stats = stats, groupAverage = state.groupAverage)
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // ── Monthly summary table ─────────────────────────────────────────
            SectionCard(title = "Monthly Spending") {
                if (state.monthlySummaries.isEmpty()) {
                    Text("No payment data yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Month", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Cans", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Amount", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    state.monthlySummaries.take(12).forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(s.yearMonth ?: "", style = MaterialTheme.typography.bodySmall)
                            Text("${s.totalCans}", style = MaterialTheme.typography.bodySmall)
                            Text(formatAmount(s.totalAmount), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── Member contribution summary ───────────────────────────────────
            SectionCard(title = "Contributions by Member") {
                state.memberStats.forEach { stats ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(name = stats.memberName, avatarUri = stats.avatarUri, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stats.memberName, fontWeight = FontWeight.Medium)
                            Text(
                                "${stats.totalPayments} payments · avg ${formatAmount(stats.averageContribution)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            formatAmount(stats.totalAmountContributed),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ── Export ────────────────────────────────────────────────────────
            SectionCard(title = "Export") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportButton(
                        label = "Export as CSV",
                        icon = Icons.Rounded.TableChart,
                        loading = state.exportInProgress,
                        onClick = { viewModel.exportCsv(context) }
                    )
                    ExportButton(
                        label = "Export as Excel",
                        icon = Icons.Rounded.GridOn,
                        loading = state.exportInProgress,
                        onClick = { viewModel.exportExcel(context) }
                    )
                    ExportButton(
                        label = "Export as PDF",
                        icon = Icons.Rounded.PictureAsPdf,
                        loading = state.exportInProgress,
                        onClick = { viewModel.exportPdf(context) }
                    )
                }
            }

            Spacer(Modifier.height(bottomPadding.calculateBottomPadding() + 24.dp))
        }
    }

    state.exportResult?.let { message ->
        LaunchedEffect(message) {
            // In production, show a Snackbar - using AlertDialog here for simplicity
        }
        AlertDialog(
            onDismissRequest = { viewModel.clearExportResult() },
            title = { Text("Export") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearExportResult() }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun BalanceRow(stats: MemberStats, groupAverage: Double) {
    val status = stats.balanceStatus(groupAverage)
    val (statusColor, statusLabel) = when (status) {
        MemberStats.BalanceStatus.ABOVE_AVERAGE ->
            MaterialTheme.colorScheme.primary to "Above avg"
        MemberStats.BalanceStatus.BELOW_AVERAGE ->
            MaterialTheme.colorScheme.error to "Below avg"
        MemberStats.BalanceStatus.ON_AVERAGE ->
            MaterialTheme.colorScheme.secondary to "On avg"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MemberAvatar(name = stats.memberName, avatarUri = stats.avatarUri, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stats.memberName, fontWeight = FontWeight.Medium)
            Text(
                formatAmount(stats.totalAmountContributed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = statusColor.copy(alpha = 0.15f)
        ) {
            Text(
                statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ExportButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    loading: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
