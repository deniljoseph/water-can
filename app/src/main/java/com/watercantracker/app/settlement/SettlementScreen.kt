package com.watercantracker.app.settlement

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import com.watercantracker.app.ui.components.MemberAvatar
import com.watercantracker.app.ui.components.formatAmount
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    bottomPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: SettlementViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currency = state.currencySymbol

    // Month picker state
    val currentYear  = Calendar.getInstance().get(Calendar.YEAR)
    val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    var showMonthPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Settlement", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.generateSettlement() }) {
                        Icon(Icons.Rounded.Refresh, "Regenerate")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp,
                bottom = bottomPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Month selector ────────────────────────────────────────────────
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = {
                            val m = if (state.selectedMonth == 1) 12 else state.selectedMonth - 1
                            val y = if (state.selectedMonth == 1) state.selectedYear - 1 else state.selectedYear
                            viewModel.selectMonth(m, y)
                        }) { Icon(Icons.Rounded.ChevronLeft, "Previous") }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${months[state.selectedMonth - 1]} ${state.selectedYear}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Settlement period", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        IconButton(onClick = {
                            val m = if (state.selectedMonth == 12) 1 else state.selectedMonth + 1
                            val y = if (state.selectedMonth == 12) state.selectedYear + 1 else state.selectedYear
                            viewModel.selectMonth(m, y)
                        }) { Icon(Icons.Rounded.ChevronRight, "Next") }
                    }
                }
            }

            // ── Generate / loading ────────────────────────────────────────────
            if (state.isGenerating) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Calculating settlement…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                return@LazyColumn
            }

            val settlement = state.settlement

            if (settlement == null) {
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.Calculate, null, modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("No settlement generated yet", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text("Tap Generate to calculate who owes whom for this month.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.generateSettlement() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Calculate, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Generate Settlement")
                            }
                        }
                    }
                }
                return@LazyColumn
            }

            // ── Section 1: Total monthly spending ────────────────────────────
            item {
                SectionCard(
                    title = "Total Monthly Spending",
                    icon = Icons.Rounded.Payments
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem("Total Spent", formatAmount(settlement.totalSpent, currency))
                        StatItem("Members", "${settlement.memberCount}")
                        StatItem("Fair Share Each", formatAmount(settlement.fairShare, currency))
                    }
                }
            }

            // ── Section 2: Member balance table ──────────────────────────────
            item {
                SectionCard(title = "Member Balances", icon = Icons.Rounded.Balance) {
                    // Table header
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Text("Member", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.5f))
                        Text("Paid", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text("Share", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text("Balance", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    settlement.memberBalances.forEach { mb ->
                        BalanceTableRow(mb, currency)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // ── Section 3: Settlement instructions ───────────────────────────
            item {
                SectionCard(
                    title = "Settlement Instructions",
                    icon = Icons.Rounded.SwapHoriz,
                    headerBadge = if (settlement.transactions.isNotEmpty())
                        "${settlement.transactions.size} transfer${if (settlement.transactions.size > 1) "s" else ""}"
                    else "Settled ✓"
                ) {
                    if (settlement.transactions.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("All members are settled — no transfers needed!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        settlement.transactions.forEachIndexed { idx, tx ->
                            TransactionRow(tx, idx + 1, currency)
                            if (idx < settlement.transactions.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
                            }
                        }
                    }
                }
            }

            // ── Section 4: Export ─────────────────────────────────────────────
            item {
                SectionCard(title = "Export & Share", icon = Icons.Rounded.Share) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.exportPdf(context) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Export PDF Report")
                        }
                        OutlinedButton(
                            onClick = { shareSettlementText(context, settlement, currency) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share Settlement Summary")
                        }
                        OutlinedButton(
                            onClick = { copyToClipboard(context, settlement, currency) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy to Clipboard")
                        }
                    }
                }
            }

            // ── Past settlements ──────────────────────────────────────────────
            if (state.allSettlements.size > 1) {
                item {
                    Text("Past Settlements", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                }
                items(state.allSettlements.drop(1).take(6)) { entity ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val m = months.getOrElse(entity.month - 1) { entity.month.toString() }
                            Text("$m ${entity.year}", fontWeight = FontWeight.Medium)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatAmount(entity.totalSpent, currency),
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("${entity.transactionCount} transfer${if (entity.transactionCount != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // Export result snackbar
    state.exportPath?.let { path ->
        LaunchedEffect(path) {
            // Share the file
            viewModel.clearExportPath()
        }
        AlertDialog(
            onDismissRequest = { viewModel.clearExportPath() },
            title = { Text("PDF Exported") },
            text = { Text("Saved to:\n$path") },
            confirmButton = { TextButton(onClick = { viewModel.clearExportPath() }) { Text("OK") } }
        )
    }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
        )
    }
}

// ── Composable helpers ────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    headerBadge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                headerBadge?.let {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BalanceTableRow(mb: MemberBalance, currency: String) {
    val (balColor, balSign) = when {
        mb.balance > 0.005  -> MaterialTheme.colorScheme.primary to "+"
        mb.balance < -0.005 -> MaterialTheme.colorScheme.error   to ""
        else                -> MaterialTheme.colorScheme.secondary to ""
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(name = mb.memberName, avatarUri = null, size = 26.dp)
            Spacer(Modifier.width(6.dp))
            Text(mb.memberName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
        Text(formatAmount(mb.paidAmount, currency), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(formatAmount(mb.fairShare, currency), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$balSign${formatAmount(mb.balance, currency)}", style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold, color = balColor,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
private fun TransactionRow(tx: SettlementTransaction, index: Int, currency: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("$index", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tx.fromMemberName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.ArrowForward, null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(tx.toMemberName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            }
            Text("${tx.fromMemberName} pays ${tx.toMemberName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatAmount(tx.amount, currency), fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium)
    }
}

// ── Share / copy helpers ──────────────────────────────────────────────────────

private fun buildSettlementText(settlement: MonthlySettlement, currency: String): String =
    buildString {
        appendLine("💧 Water Can Settlement — ${settlement.monthLabel}")
        appendLine("━".repeat(36))
        appendLine("Total spent:   $currency ${String.format("%.2f", settlement.totalSpent)}")
        appendLine("Fair share:    $currency ${String.format("%.2f", settlement.fairShare)} per member")
        appendLine()
        appendLine("Member Balances:")
        settlement.memberBalances.forEach { mb ->
            val sign = if (mb.balance >= 0) "+" else ""
            appendLine("  ${mb.memberName.padEnd(14)} Paid: $currency ${String.format("%.2f", mb.paidAmount)}  Balance: $sign$currency ${String.format("%.2f", mb.balance)}")
        }
        appendLine()
        if (settlement.transactions.isEmpty()) {
            appendLine("✅ All settled — no transfers needed.")
        } else {
            appendLine("Transfers required:")
            settlement.transactions.forEachIndexed { i, tx ->
                appendLine("  ${i + 1}. ${tx.fromMemberName} → ${tx.toMemberName}: $currency ${String.format("%.2f", tx.amount)}")
            }
        }
        appendLine("━".repeat(36))
        appendLine("Generated by Water Can Tracker")
    }

private fun shareSettlementText(context: Context, settlement: MonthlySettlement, currency: String) {
    val text = buildSettlementText(settlement, currency)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Water Can Settlement — ${settlement.monthLabel}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Settlement"))
}

private fun copyToClipboard(context: Context, settlement: MonthlySettlement, currency: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Settlement", buildSettlementText(settlement, currency)))
}
