package com.watercantracker.app.ui.screens.payments

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watercantracker.app.data.local.entity.MemberEntity
import com.watercantracker.app.ui.components.formatAmount
import com.watercantracker.app.ui.screens.settings.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPaymentScreen(
    paymentId: Long?,
    onBack: () -> Unit,
    viewModel: PaymentsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val pricePerCan = settingsState.settings.defaultPricePerCan
    val currency = settingsState.settings.currencySymbol

    val context = LocalContext.current
    val df = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val isEditing = paymentId != null

    // Form state
    var quantity by remember { mutableStateOf("1") }
    var amount by remember { mutableStateOf("") }
    var selectedMember by remember { mutableStateOf<MemberEntity?>(null) }
    var purchaseDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var memberDropdownExpanded by remember { mutableStateOf(false) }

    // Derived: expected full amount based on price-per-can setting
    val quantityInt = quantity.toIntOrNull() ?: 0
    val expectedAmount = if (pricePerCan > 0 && quantityInt > 0) pricePerCan * quantityInt else 0.0
    val enteredAmount = amount.toDoubleOrNull() ?: 0.0
    val isPartialPayment = expectedAmount > 0 && enteredAmount > 0 && enteredAmount < expectedAmount
    val shortfall = if (isPartialPayment) expectedAmount - enteredAmount else 0.0

    // Auto-fill amount when quantity changes and price is set
    LaunchedEffect(quantity, pricePerCan) {
        if (!isEditing && pricePerCan > 0) {
            val qty = quantity.toIntOrNull() ?: 0
            if (qty > 0) amount = String.format("%.2f", pricePerCan * qty)
        }
    }

    // Pre-fill next active member for new payments
    LaunchedEffect(state.members) {
        if (!isEditing && selectedMember == null) {
            selectedMember = state.members.firstOrNull { it.isActive }
        }
    }

    // Pre-fill when editing
    LaunchedEffect(paymentId, state.payments) {
        if (isEditing && paymentId != null) {
            state.payments.firstOrNull { it.id == paymentId }?.let { p ->
                quantity = p.quantity.toString()
                amount = p.amount.toString()
                purchaseDate = p.purchaseDate
                notes = p.notes ?: ""
                vendor = p.vendorName ?: ""
                selectedMember = state.members.firstOrNull { it.id == p.paidByMemberId }
            }
        }
    }

    var quantityError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var memberError by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        quantityError = quantity.toIntOrNull()?.let { it <= 0 } ?: true
        amountError = amount.toDoubleOrNull()?.let { it <= 0 } ?: true
        memberError = selectedMember == null
        return !quantityError && !amountError && !memberError
    }

    fun onSave() {
        if (!validate()) return
        val member = selectedMember ?: return
        if (isEditing && paymentId != null) {
            val existing = state.payments.firstOrNull { it.id == paymentId } ?: return
            viewModel.updatePayment(existing.copy(
                quantity = quantityInt,
                amount = enteredAmount,
                paidByMemberId = member.id,
                paidByNameSnapshot = member.name,
                purchaseDate = purchaseDate,
                notes = notes.trim().takeIf { it.isNotEmpty() },
                vendorName = vendor.trim().takeIf { it.isNotEmpty() }
            ))
        } else {
            viewModel.addPayment(
                quantityInt, enteredAmount, member, purchaseDate,
                notes.trim().takeIf { it.isNotEmpty() },
                vendor.trim().takeIf { it.isNotEmpty() }, null
            )
        }
        onBack()
    }

    val cal = Calendar.getInstance().apply { timeInMillis = purchaseDate }
    val datePicker = DatePickerDialog(
        context,
        { _, y, m, d -> cal.set(y, m, d); purchaseDate = cal.timeInMillis },
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Payment" else "Record Payment", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                }
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

            // ── Price-per-can info banner ──────────────────────────────────────
            if (pricePerCan > 0) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Info, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Price per can: ${formatAmount(pricePerCan, currency)}  •  " +
                                    "Full amount for $quantityInt can${if (quantityInt != 1) "s" else ""}: " +
                                    if (expectedAmount > 0) formatAmount(expectedAmount, currency) else "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // ── Member selector ───────────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded = memberDropdownExpanded,
                onExpandedChange = { memberDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedMember?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Paid by *") },
                    trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) },
                    isError = memberError,
                    supportingText = if (memberError) {{ Text("Select who paid") }} else null,
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = memberDropdownExpanded, onDismissRequest = { memberDropdownExpanded = false }) {
                    state.members.filter { it.isActive }.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.name) },
                            onClick = { selectedMember = member; memberDropdownExpanded = false; memberError = false }
                        )
                    }
                }
            }

            // ── Quantity ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it; quantityError = false },
                label = { Text("Number of cans *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = quantityError,
                supportingText = if (quantityError) {{ Text("Enter a valid quantity") }} else null,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Amount ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; amountError = false },
                label = {
                    Text(if (pricePerCan > 0) "Amount paid (partial allowed) *" else "Total amount paid *")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                leadingIcon = { Text(currency, modifier = Modifier.padding(start = 12.dp)) },
                isError = amountError,
                supportingText = when {
                    amountError -> {{ Text("Enter a valid amount") }}
                    isPartialPayment -> {{
                        Text(
                            "⚠ Partial payment — ${formatAmount(shortfall, currency)} still owed",
                            color = MaterialTheme.colorScheme.error
                        )
                    }}
                    expectedAmount > 0 && enteredAmount >= expectedAmount -> {{
                        Text("✓ Full payment", color = MaterialTheme.colorScheme.primary)
                    }}
                    else -> null
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Partial payment info card ─────────────────────────────────────
            if (isPartialPayment) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Partial Payment",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${selectedMember?.name ?: "This person"} will owe ${formatAmount(shortfall, currency)} " +
                                    "after this payment. This will show in the Balances section.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── Date ──────────────────────────────────────────────────────────
            OutlinedTextField(
                value = df.format(Date(purchaseDate)),
                onValueChange = {},
                readOnly = true,
                label = { Text("Purchase date") },
                trailingIcon = {
                    IconButton(onClick = { datePicker.show() }) {
                        Icon(Icons.Rounded.CalendarMonth, "Pick date")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Vendor ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = vendor,
                onValueChange = { vendor = it },
                label = { Text("Vendor name (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Notes ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = ::onSave,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    if (isEditing) "Update Payment" else "Save Payment",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
