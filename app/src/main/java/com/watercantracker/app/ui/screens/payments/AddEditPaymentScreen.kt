package com.watercantracker.app.ui.screens.payments

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    val focusManager = LocalFocusManager.current
    val df = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val isEditing = paymentId != null

    // ── Form state ────────────────────────────────────────────────────────────
    // FIX: Start quantity as EMPTY string so the field is blank on open.
    // Default "1" caused users to append digits (e.g. type "2" → "12") without
    // first clearing the pre-filled value.
    var quantityText   by remember { mutableStateOf("") }
    var amountText     by remember { mutableStateOf("") }
    var selectedMember by remember { mutableStateOf<MemberEntity?>(null) }
    var purchaseDate   by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notes          by remember { mutableStateOf("") }
    var vendor         by remember { mutableStateOf("") }
    var memberDropdownExpanded by remember { mutableStateOf(false) }
    var userEditedAmount by remember { mutableStateOf(false) }

    // ── Derived ───────────────────────────────────────────────────────────────
    val quantityInt   = quantityText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val enteredAmount = amountText.trim().toDoubleOrNull() ?: -1.0
    val expectedAmount = if (pricePerCan > 0.0 && quantityInt > 0) pricePerCan * quantityInt else 0.0
    val isPartialPayment = expectedAmount > 0.01
            && enteredAmount > 0.0
            && enteredAmount < (expectedAmount - 0.01)
    val shortfall = if (isPartialPayment) expectedAmount - enteredAmount else 0.0

    // ── Auto-fill amount from price × quantity (new payments only) ────────────
    LaunchedEffect(quantityText, pricePerCan) {
        if (!isEditing && !userEditedAmount && pricePerCan > 0.0) {
            val qty = quantityText.toIntOrNull() ?: 0
            amountText = if (qty > 0) String.format("%.2f", pricePerCan * qty) else ""
        }
    }

    // ── Default payer ─────────────────────────────────────────────────────────
    LaunchedEffect(state.members) {
        if (!isEditing && selectedMember == null) {
            selectedMember = state.members.firstOrNull { it.isActive }
        }
    }

    // ── Pre-fill when editing ─────────────────────────────────────────────────
    LaunchedEffect(paymentId, state.payments) {
        if (isEditing && paymentId != null) {
            state.payments.firstOrNull { it.id == paymentId }?.let { p ->
                quantityText     = p.quantity.toString()
                amountText       = String.format("%.2f", p.amount)
                purchaseDate     = p.purchaseDate
                notes            = p.notes ?: ""
                vendor           = p.vendorName ?: ""
                selectedMember   = state.members.firstOrNull { it.id == p.paidByMemberId }
                userEditedAmount = true
            }
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────
    var quantityError by remember { mutableStateOf<String?>(null) }
    var amountError   by remember { mutableStateOf<String?>(null) }
    var memberError   by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        val qty = quantityText.trim().toIntOrNull()
        quantityError = when {
            quantityText.isBlank() -> "Enter number of cans"
            qty == null            -> "Must be a whole number"
            qty <= 0               -> "Must be at least 1"
            else                   -> null
        }
        val parsedAmt = amountText.trim().trimEnd('.').toDoubleOrNull() ?: 0.0
        amountError = when {
            amountText.isBlank() -> "Enter the amount paid"
            parsedAmt <= 0.0     -> "Amount must be greater than 0"
            else                 -> null
        }
        memberError = selectedMember == null
        return quantityError == null && amountError == null && !memberError
    }

    fun onSave() {
        focusManager.clearFocus()
        if (!validate()) return
        val member      = selectedMember ?: return
        val finalQty    = quantityText.trim().toIntOrNull() ?: return
        val finalAmount = amountText.trim().trimEnd('.').toDoubleOrNull() ?: return

        if (isEditing && paymentId != null) {
            val existing = state.payments.firstOrNull { it.id == paymentId } ?: return
            viewModel.updatePayment(
                existing.copy(
                    quantity           = finalQty,
                    amount             = finalAmount,
                    paidByMemberId     = member.id,
                    paidByNameSnapshot = member.name,
                    purchaseDate       = purchaseDate,
                    notes              = notes.trim().takeIf { it.isNotEmpty() },
                    vendorName         = vendor.trim().takeIf { it.isNotEmpty() }
                )
            )
        } else {
            viewModel.addPayment(
                finalQty, finalAmount, member, purchaseDate,
                notes.trim().takeIf { it.isNotEmpty() },
                vendor.trim().takeIf { it.isNotEmpty() },
                null
            )
        }
        onBack()
    }

    // ── Date picker ───────────────────────────────────────────────────────────
    val cal = Calendar.getInstance().apply { timeInMillis = purchaseDate }
    val datePicker = DatePickerDialog(
        context,
        { _, y, m, d -> cal.set(y, m, d); purchaseDate = cal.timeInMillis },
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
    )

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) "Edit Payment" else "Record Payment",
                        fontWeight = FontWeight.Bold
                    )
                },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Price banner ──────────────────────────────────────────────────
            if (pricePerCan > 0.0) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Info, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Price per can: ${formatAmount(pricePerCan, currency)}" +
                                    if (quantityInt > 0) "  •  Full cost: ${formatAmount(expectedAmount, currency)}" else "",
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
                    supportingText = if (memberError) {{ Text("Please select who paid") }} else null,
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = memberDropdownExpanded,
                    onDismissRequest = { memberDropdownExpanded = false }
                ) {
                    state.members.filter { it.isActive }.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.name) },
                            onClick = {
                                selectedMember = member
                                memberDropdownExpanded = false
                                memberError = false
                            }
                        )
                    }
                }
            }

            // ── Quantity ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = quantityText,
                onValueChange = { v ->
                    // Allow only digits — quantity is always a whole number
                    val digits = v.filter { it.isDigit() }
                    quantityText = digits
                    quantityError = null
                    // Reset so auto-fill can recalculate for the new quantity
                    userEditedAmount = false
                },
                label = { Text("Number of cans *") },
                // Placeholder shows "1" as a hint without pre-filling the field
                placeholder = { Text("e.g. 1") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                isError = quantityError != null,
                supportingText = quantityError?.let { err -> { Text(err) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Amount ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = amountText,
                onValueChange = { v ->
                    amountText = v
                    amountError = null
                    userEditedAmount = true
                },
                label = { Text("Amount paid ($currency) *") },
                placeholder = { Text("e.g. ${if (pricePerCan > 0.0) String.format("%.2f", pricePerCan) else "10.00"}") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                isError = amountError != null,
                supportingText = when {
                    amountError != null -> { { Text(amountError!!) } }
                    isPartialPayment    -> { {
                        Text(
                            "⚠ Partial — ${formatAmount(shortfall, currency)} short of full payment",
                            color = MaterialTheme.colorScheme.error
                        )
                    } }
                    expectedAmount > 0.01 && enteredAmount >= expectedAmount - 0.01 -> { {
                        Text("✓ Full payment", color = MaterialTheme.colorScheme.primary)
                    } }
                    else -> null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Partial warning card ──────────────────────────────────────────
            if (isPartialPayment) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            "Partial Payment",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${selectedMember?.name ?: "This member"} will owe " +
                                    "${formatAmount(shortfall, currency)} shown in the Balances tab.",
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
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Vendor ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = vendor,
                onValueChange = { vendor = it },
                label = { Text("Vendor (optional)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Notes ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
