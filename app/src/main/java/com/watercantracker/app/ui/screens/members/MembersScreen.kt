package com.watercantracker.app.ui.screens.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.domain.model.MemberBalance
import com.watercantracker.app.ui.components.ConfirmDialog
import com.watercantracker.app.ui.components.EmptyState
import com.watercantracker.app.ui.components.MemberAvatar
import com.watercantracker.app.ui.components.formatAmount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    onAddMember: () -> Unit,
    onEditMember: (Long) -> Unit,
    bottomPadding: PaddingValues,
    viewModel: MembersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<MemberEntity?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Rotation", "Balances")
    val currency = state.settings.currencySymbol

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Members", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onAddMember) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = "Add member")
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.members.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onAddMember,
                    modifier = Modifier.padding(bottom = bottomPadding.calculateBottomPadding())
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add member")
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.members.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.GroupOff,
                title = "No members yet",
                subtitle = "Add the people who share water can costs to begin tracking.",
                action = { Button(onClick = onAddMember) { Text("Add First Member") } },
                modifier = Modifier.padding(padding).fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Tab row
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(title) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> RotationTab(
                        state = state,
                        bottomPadding = bottomPadding,
                        onEdit = onEditMember,
                        onDelete = { pendingDelete = it },
                        onSetNext = { viewModel.setManualNextPayer(it) },
                        onSkip = { viewModel.skipMember(it) },
                        onToggleActive = { id, active -> viewModel.setActiveStatus(id, active) },
                        onMoveUp = { viewModel.moveUp(it) },
                        onMoveDown = { viewModel.moveDown(it) }
                    )
                    1 -> BalancesTab(
                        balances = state.memberBalances,
                        totalGroupSpend = state.totalGroupSpend,
                        currency = currency,
                        bottomPadding = bottomPadding
                    )
                }
            }
        }
    }

    pendingDelete?.let { member ->
        ConfirmDialog(
            title = "Remove ${member.name}?",
            message = "Their payment history will be kept, but they'll be removed from the rotation queue.",
            onConfirm = { viewModel.deleteMember(member); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}

// ── Rotation Tab ──────────────────────────────────────────────────────────────
@Composable
private fun RotationTab(
    state: MembersUiState,
    bottomPadding: PaddingValues,
    onEdit: (Long) -> Unit,
    onDelete: (MemberEntity) -> Unit,
    onSetNext: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onToggleActive: (Long, Boolean) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = 8.dp, bottom = bottomPadding.calculateBottomPadding() + 80.dp,
            start = 16.dp, end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Rotation queue — top member pays next",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        itemsIndexed(state.members, key = { _, m -> m.id }) { index, member ->
            val stats = state.memberStats.firstOrNull { it.memberId == member.id }
            MemberCard(
                member = member, index = index, totalMembers = state.members.size,
                totalContributed = stats?.totalAmountContributed ?: 0.0,
                timePaid = stats?.totalPayments ?: 0,
                currency = state.settings.currencySymbol,
                onEdit = { onEdit(member.id) },
                onDelete = { onDelete(member) },
                onSetNext = { onSetNext(member.id) },
                onSkip = { onSkip(member.id) },
                onToggleActive = { onToggleActive(member.id, !member.isActive) },
                onMoveUp = { onMoveUp(member.id) },
                onMoveDown = { onMoveDown(member.id) }
            )
        }
    }
}

// ── Balances Tab ──────────────────────────────────────────────────────────────
@Composable
private fun BalancesTab(
    balances: List<MemberBalance>,
    totalGroupSpend: Double,
    currency: String,
    bottomPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = 12.dp, bottom = bottomPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp, end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary header
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Group Summary", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Total spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatAmount(totalGroupSpend, currency), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Fair share each", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatAmount(if (balances.isNotEmpty()) totalGroupSpend / balances.size else 0.0, currency),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (balances.isEmpty()) {
            item {
                Text("No active members.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }

        // Owes section
        val owesList = balances.filter { it.status == MemberBalance.Status.OWES }
        if (owesList.isNotEmpty()) {
            item {
                Text(
                    "OWES MONEY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }
            items(owesList.size) { i -> BalanceCard(owesList[i], currency) }
        }

        // Settled section
        val settledList = balances.filter { it.status == MemberBalance.Status.SETTLED }
        if (settledList.isNotEmpty()) {
            item {
                Text(
                    "SETTLED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }
            items(settledList.size) { i -> BalanceCard(settledList[i], currency) }
        }

        // Credit section
        val creditList = balances.filter { it.status == MemberBalance.Status.CREDIT }
        if (creditList.isNotEmpty()) {
            item {
                Text(
                    "IN CREDIT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }
            items(creditList.size) { i -> BalanceCard(creditList[i], currency) }
        }
    }
}

@Composable
private fun BalanceCard(balance: MemberBalance, currency: String) {
    val (bgColor, labelText, amountColor) = when (balance.status) {
        MemberBalance.Status.OWES -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            "Owes ${formatAmount(balance.owes, currency)}",
            MaterialTheme.colorScheme.error
        )
        MemberBalance.Status.CREDIT -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            "Credit ${formatAmount(balance.credit, currency)}",
            MaterialTheme.colorScheme.primary
        )
        MemberBalance.Status.SETTLED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            "Settled",
            MaterialTheme.colorScheme.secondary
        )
    }

    Surface(shape = MaterialTheme.shapes.medium, color = bgColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemberAvatar(name = balance.memberName, avatarUri = balance.avatarUri, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(balance.memberName, fontWeight = FontWeight.SemiBold)
                Text(
                    "Paid: ${formatAmount(balance.totalPaid, currency)}  •  Fair share: ${formatAmount(balance.fairShare, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(shape = MaterialTheme.shapes.small, color = amountColor.copy(alpha = 0.15f)) {
                    Text(
                        labelText,
                        style = MaterialTheme.typography.labelMedium,
                        color = amountColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ── Member rotation card (unchanged logic, slight cleanup) ────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberCard(
    member: MemberEntity,
    index: Int,
    totalMembers: Int,
    totalContributed: Double,
    timePaid: Int,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetNext: () -> Unit,
    onSkip: () -> Unit,
    onToggleActive: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (index == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            MemberAvatar(name = member.name, avatarUri = member.avatarUri, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name, fontWeight = FontWeight.SemiBold)
                    if (!member.isActive) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.errorContainer) {
                            Text("Inactive", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    if (member.isManualNextPayer) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text("Next up", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(
                    "${formatAmount(totalContributed, currency)} contributed · $timePaid payments",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, "Options") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Set as next payer") }, leadingIcon = { Icon(Icons.Rounded.Star, null) }, onClick = { menuExpanded = false; onSetNext() })
                    DropdownMenuItem(text = { Text("Skip this turn") }, leadingIcon = { Icon(Icons.Rounded.SkipNext, null) }, onClick = { menuExpanded = false; onSkip() })
                    if (index > 0) DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) }, onClick = { menuExpanded = false; onMoveUp() })
                    if (index < totalMembers - 1) DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) }, onClick = { menuExpanded = false; onMoveDown() })
                    DropdownMenuItem(
                        text = { Text(if (member.isActive) "Mark inactive" else "Mark active") },
                        leadingIcon = { Icon(if (member.isActive) Icons.Rounded.PersonOff else Icons.Rounded.PersonAdd, null) },
                        onClick = { menuExpanded = false; onToggleActive() }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}
