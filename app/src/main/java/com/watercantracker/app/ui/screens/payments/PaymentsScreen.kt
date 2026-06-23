package com.watercantracker.app.ui.screens.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watercantracker.app.ui.components.ConfirmDialog
import com.watercantracker.app.ui.components.EmptyState
import com.watercantracker.app.ui.components.MemberAvatar
import com.watercantracker.app.ui.components.formatAmount
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(
    onAddPayment: () -> Unit,
    onEditPayment: (Long) -> Unit,
    viewModel: PaymentsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val df = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    var showDeleteDialog by remember { mutableStateOf<com.watercantracker.app.data.local.entity.PaymentEntity?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment History", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Rounded.FilterList, contentDescription = "Filter")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPayment) {
                Icon(Icons.Rounded.Add, contentDescription = "Add payment")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search bar
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = { viewModel.updateFilter(state.filter.copy(query = it)) },
                placeholder = { Text("Search by name, vendor, notes…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.payments.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Receipt,
                    title = "No payments yet",
                    subtitle = "Record the first water can purchase to get started.",
                    action = {
                        Button(onClick = onAddPayment) { Text("Add First Payment") }
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(
                        items = state.payments,
                        key = { it.id }
                    ) { payment ->
                        PaymentListItem(
                            payment = payment,
                            dateFormatted = df.format(Date(payment.purchaseDate)),
                            onEdit = { onEditPayment(payment.id) },
                            onDelete = { showDeleteDialog = payment }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { payment ->
        ConfirmDialog(
            title = "Delete this payment?",
            message = "This will remove the record for ${payment.paidByNameSnapshot} on ${df.format(Date(payment.purchaseDate))}. This can't be undone.",
            onConfirm = {
                viewModel.deletePayment(payment)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            state = state,
            onApply = { memberId, start, end ->
                viewModel.updateFilter(state.filter.copy(memberId = memberId, startDate = start, endDate = end))
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun PaymentListItem(
    payment: com.watercantracker.app.data.local.entity.PaymentEntity,
    dateFormatted: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MemberAvatar(name = payment.paidByNameSnapshot, avatarUri = null, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(payment.paidByNameSnapshot, fontWeight = FontWeight.SemiBold)
            Text(
                buildString {
                    append(dateFormatted)
                    payment.vendorName?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            payment.notes?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatAmount(payment.amount),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "${payment.quantity} can${if (payment.quantity != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(4.dp))
        Column {
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    state: PaymentsUiState,
    onApply: (Long?, Long?, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMemberId by remember { mutableStateOf(state.filter.memberId) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Filter Payments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("Member", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            FilterChip(
                selected = selectedMemberId == null,
                onClick = { selectedMemberId = null },
                label = { Text("All Members") }
            )
            state.members.forEach { m ->
                FilterChip(
                    selected = selectedMemberId == m.id,
                    onClick = { selectedMemberId = m.id },
                    label = { Text(m.name) },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    selectedMemberId = null
                    onApply(null, null, null)
                }) { Text("Clear Filters") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onApply(selectedMemberId, null, null) }) {
                    Text("Apply")
                }
            }
        }
    }
}
