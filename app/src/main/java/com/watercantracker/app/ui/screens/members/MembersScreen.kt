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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.ui.components.ConfirmDialog
import com.watercantracker.app.ui.components.EmptyState
import com.watercantracker.app.ui.components.MemberAvatar
import com.watercantracker.app.ui.components.formatAmount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    onAddMember: () -> Unit,
    onEditMember: (Long) -> Unit,
    viewModel: MembersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<MemberEntity?>(null) }

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
                FloatingActionButton(onClick = onAddMember) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add member")
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.members.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.GroupOff,
                title = "No members yet",
                subtitle = "Add the people who share water can costs to begin tracking the rotation.",
                action = { Button(onClick = onAddMember) { Text("Add First Member") } },
                modifier = Modifier.padding(padding).fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Balance summary header
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
                        member = member,
                        index = index,
                        totalMembers = state.members.size,
                        totalContributed = stats?.totalAmountContributed ?: 0.0,
                        timePaid = stats?.totalPayments ?: 0,
                        groupAverage = state.groupAverageContribution,
                        onEdit = { onEditMember(member.id) },
                        onDelete = { pendingDelete = member },
                        onSetNext = { viewModel.setManualNextPayer(member.id) },
                        onSkip = { viewModel.skipMember(member.id) },
                        onToggleActive = { viewModel.setActiveStatus(member.id, !member.isActive) },
                        onMoveUp = { viewModel.moveUp(member.id) },
                        onMoveDown = { viewModel.moveDown(member.id) }
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberCard(
    member: MemberEntity,
    index: Int,
    totalMembers: Int,
    totalContributed: Double,
    timePaid: Int,
    groupAverage: Double,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetNext: () -> Unit,
    onSkip: () -> Unit,
    onToggleActive: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rotation order badge
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (index == 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (index == 0) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
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
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                "Inactive",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (member.isManualNextPayer) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                "Next up",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    "${formatAmount(totalContributed)} contributed · $timePaid payments",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Context menu
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Set as next payer") },
                        leadingIcon = { Icon(Icons.Rounded.Star, null) },
                        onClick = { menuExpanded = false; onSetNext() }
                    )
                    DropdownMenuItem(
                        text = { Text("Skip this turn") },
                        leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                        onClick = { menuExpanded = false; onSkip() }
                    )
                    if (index > 0) {
                        DropdownMenuItem(
                            text = { Text("Move up in queue") },
                            leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) },
                            onClick = { menuExpanded = false; onMoveUp() }
                        )
                    }
                    if (index < totalMembers - 1) {
                        DropdownMenuItem(
                            text = { Text("Move down in queue") },
                            leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) },
                            onClick = { menuExpanded = false; onMoveDown() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (member.isActive) "Mark inactive" else "Mark active") },
                        leadingIcon = {
                            Icon(if (member.isActive) Icons.Rounded.PersonOff else Icons.Rounded.PersonAdd, null)
                        },
                        onClick = { menuExpanded = false; onToggleActive() }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}
