package com.watercantracker.app.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watercantracker.app.domain.model.MemberStats
import com.watercantracker.app.ui.components.MemberAvatar
import com.watercantracker.app.ui.components.formatAmount
import java.text.SimpleDateFormat
import java.util.*

private val MONTH_NAMES = listOf(
    "", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    bottomPadding: PaddingValues,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currency = state.currencySymbol
    val df = SimpleDateFormat("d MMM", Locale.getDefault())

    // Tab state: 0 = Monthly Report, 1 = All-time Summary, 2 = Export
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Monthly", "All-time", "Export")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Reports", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, title ->
                    Tab(selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title) })
                }
            }

            when (selectedTab) {
                0 -> MonthlyReportTab(state, currency, df, viewModel, bottomPadding)
                1 -> AllTimeSummaryTab(state, currency, bottomPadding)
                2 -> ExportTab(state, currency, bottomPadding,
                    onCsv   = { viewModel.exportCsv(context) },
                    onExcel = { viewModel.exportExcel(context) },
                    onPdf   = { viewModel.exportPdf(context) })
            }
        }
    }

    state.exportResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearExportResult() },
            title = { Text("Export") },
            text  = { Text(result) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearExportResult() }) { Text("OK") }
            }
        )
    }
}

// ── Monthly Report Tab ────────────────────────────────────────────────────────
@Composable
private fun MonthlyReportTab(
    state: ReportsUiState,
    currency: String,
    df: SimpleDateFormat,
    viewModel: ReportsViewModel,
    bottomPadding: PaddingValues
) {
    val monthLabel = "${MONTH_NAMES[state.selectedMonth]} ${state.selectedYear}"
    val report = state.monthlyReportData

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 12.dp,
            bottom = bottomPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Month navigator ───────────────────────────────────────────────────
        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = viewModel::prevMonth) {
                        Icon(Icons.Rounded.ChevronLeft, "Previous month")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(monthLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text("Tap ‹ › to navigate months",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.Rounded.ChevronRight, "Next month")
                    }
                }
            }
        }

        if (report == null) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.BarChart, null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("No payments in $monthLabel",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }
                }
            }
            return@LazyColumn
        }

        // ── Summary stats ─────────────────────────────────────────────────────
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Summary", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SummaryItem("Total Spent", formatAmount(report.summary.totalAmount, currency))
                        SummaryItem("Cans", "${report.summary.totalCans}")
                        SummaryItem("Payments", "${report.summary.paymentCount}")
                    }
                    if (report.memberBreakdown.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        val fairShare = report.memberBreakdown.firstOrNull()?.fairShare ?: 0.0
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Members sharing:", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${report.memberBreakdown.size} members",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fair share each:", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatAmount(fairShare, currency),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // ── Member breakdown ──────────────────────────────────────────────────
        if (report.memberBreakdown.isNotEmpty()) {
            item {
                Text("Member Breakdown",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            items(report.memberBreakdown) { share ->
                MemberBreakdownCard(share, currency)
            }
        }

        // ── Payment transactions ──────────────────────────────────────────────
        if (report.payments.isNotEmpty()) {
            item {
                Text("Transactions (${report.payments.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            items(report.payments, key = { it.id }) { payment ->
                TransactionRow(payment, currency, df)
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemberBreakdownCard(share: MemberPaymentShare, currency: String) {
    val (statusColor, statusLabel) = when {
        share.balance > 0.01  -> MaterialTheme.colorScheme.primary to "Credit ${formatAmount(share.balance, currency)}"
        share.balance < -0.01 -> MaterialTheme.colorScheme.error   to "Owes ${formatAmount(-share.balance, currency)}"
        else                   -> MaterialTheme.colorScheme.secondary to "Settled ✓"
    }
    val bgColor = when {
        share.balance > 0.01  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        share.balance < -0.01 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        else                   -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemberAvatar(name = share.memberName, avatarUri = share.avatarUri, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(share.memberName, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append("Paid: ${formatAmount(share.totalPaid, currency)}")
                        if (share.canCount > 0) append("  •  ${share.canCount} can${if (share.canCount != 1) "s" else ""}")
                        if (share.paymentCount > 0) append("  •  ${share.paymentCount} txn${if (share.paymentCount != 1) "s" else ""}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Fair share: ${formatAmount(share.fairShare, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Text(statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun TransactionRow(
    payment: com.watercantracker.app.data.local.entity.PaymentEntity,
    currency: String,
    df: SimpleDateFormat
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemberAvatar(name = payment.paidByNameSnapshot, avatarUri = null, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(payment.paidByNameSnapshot, fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium)
                Text(
                    buildString {
                        append(df.format(Date(payment.purchaseDate)))
                        payment.vendorName?.let { append(" · $it") }
                        payment.notes?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatAmount(payment.amount, currency),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium)
                Text("${payment.quantity} can${if (payment.quantity != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── All-time Summary Tab ──────────────────────────────────────────────────────
@Composable
private fun AllTimeSummaryTab(
    state: ReportsUiState,
    currency: String,
    bottomPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 12.dp,
            bottom = bottomPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // All-time member contributions
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("All-time Contributions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    if (state.memberStats.isEmpty()) {
                        Text("No data yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.memberStats.forEach { stats ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MemberAvatar(name = stats.memberName, avatarUri = stats.avatarUri, size = 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stats.memberName, fontWeight = FontWeight.Medium)
                                    Text("${stats.totalPayments} payments · avg ${formatAmount(stats.averageContribution, currency)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(formatAmount(stats.totalAmountContributed, currency),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // Monthly history table
        if (state.monthlySummaries.isNotEmpty()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Month-by-Month",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Month", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                            Text("Cans", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(0.6f))
                            Text("Payments", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(0.8f))
                            Text("Total", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        state.monthlySummaries.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Parse yearMonth "2026-06" → "Jun 2026"
                                val (yr, mn) = s.yearMonth?.split("-")?.let {
                                    it.getOrNull(0)?.toIntOrNull() to it.getOrNull(1)?.toIntOrNull()
                                } ?: (null to null)
                                val label = if (mn != null && yr != null)
                                    "${MONTH_NAMES.getOrElse(mn) { mn.toString() }} $yr"
                                else s.yearMonth ?: "—"

                                Text(label, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1.2f))
                                Text("${s.totalCans}", style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center, modifier = Modifier.weight(0.6f))
                                Text("${s.paymentCount}", style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
                                Text(formatAmount(s.totalAmount, currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                        // Grand total row
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text("Total", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1.2f))
                            Text("${state.monthlySummaries.sumOf { it.totalCans }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, modifier = Modifier.weight(0.6f))
                            Text("${state.monthlySummaries.sumOf { it.paymentCount }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
                            Text(formatAmount(state.monthlySummaries.sumOf { it.totalAmount }, currency),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ── Export Tab ────────────────────────────────────────────────────────────────
@Composable
private fun ExportTab(
    state: ReportsUiState,
    currency: String,
    bottomPadding: PaddingValues,
    onCsv: () -> Unit,
    onExcel: () -> Unit,
    onPdf: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 12.dp,
            bottom = bottomPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Export All Payments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text("Exports all ${state.allPayments.size} payment records to your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Button(onClick = onCsv, enabled = !state.exportInProgress,
                        modifier = Modifier.fillMaxWidth()) {
                        if (state.exportInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Rounded.TableChart, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Export as CSV")
                    }
                    OutlinedButton(onClick = onExcel, enabled = !state.exportInProgress,
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.GridOn, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export as Excel")
                    }
                    OutlinedButton(onClick = onPdf, enabled = !state.exportInProgress,
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export as PDF")
                    }
                }
            }
        }

        // Balance tracking card
        if (state.memberStats.isNotEmpty()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Balance Overview",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        state.memberStats.forEach { stats ->
                            val avg = state.groupAverage
                            val status = stats.balanceStatus(avg)
                            val (color, label) = when (status) {
                                MemberStats.BalanceStatus.ABOVE_AVERAGE ->
                                    MaterialTheme.colorScheme.primary to "Above avg"
                                MemberStats.BalanceStatus.BELOW_AVERAGE ->
                                    MaterialTheme.colorScheme.error to "Below avg"
                                MemberStats.BalanceStatus.ON_AVERAGE ->
                                    MaterialTheme.colorScheme.secondary to "On avg"
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MemberAvatar(name = stats.memberName, avatarUri = stats.avatarUri, size = 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stats.memberName, fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(formatAmount(stats.totalAmountContributed, currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(shape = MaterialTheme.shapes.small,
                                    color = color.copy(alpha = 0.15f)) {
                                    Text(label, style = MaterialTheme.typography.labelSmall,
                                        color = color, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
