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
import androidx.compose.ui.focus.onFocusChanged
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
    var quantityText by remember { mutableStateOf("1") }
    var amountText by remember { mutableStateOf("") }
    var selectedMember by remember { mutableStateOf<MemberEntity?>(null) }
    var purchaseDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var memberDropdownExpanded by remember { mutableStateOf(false) }
    // Track whether user manually edited amount so auto-fill doesn't overwrite their input
    var amountManuallyEdited by remember { mutableStateOf(false) }

    // ── Derived values ────────────────────────────────────────────────────────
    val quantityInt = quantityText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val enteredAmount = amountText.toDoubleOrNull() ?: 0.0
    val expectedAmount = if (pricePerCan > 0 && quantityInt > 0) pricePerCan * quantityInt else 0.0
    val isPartialPayment = expectedAmount > 0.01 && enteredAmount > 0.01 && enteredAmount < expectedAmount - 0.01
    val shortfall = if (isPartialPayment) expectedAmount - enteredAmount else 0.0

    // ── Auto-fill amount from price-per-can when quantity changes ─────────────
    // Only auto-fill if: price is set, not editing existing, user hasn't manually changed amount
    LaunchedEffect(quantityText, pricePerCan) {
        if (!isEditing && !amountManuallyEdited && pricePerCan > 0) {
            val qty = quantityText.toIntOrNull() ?: 0
            if (qty > 0) amountText = String.format("%.2f", pricePerCan * qty)
        }
    }

    // ── Default payer to first active member ──────────────────────────────────
    LaunchedEffect(state.members) {
        if (!isEditing && selectedMember == null) {
            selectedMember = state.members.firstOrNull { it.isActive }
        }
    }

    // ── Pre-fill when editing ─────────────────────────────────────────────────
    LaunchedEffect(paymentId, state.payments) {
        if (isEditing && paymentId != null) {
            state.payments.firstOrNull { it.id == paymentId }?.let { p ->
                quantityText = p.quantity.toString()
                // Format existing amount properly — this was the main bug (stored as 0.0)
                amountText = if (p.amount > 0) String.format("%.2f", p.amount) else ""
                purchaseDate = p.purchaseDate
                notes = p.notes ?: ""
                vendor = p.vendorName ?: ""
                selectedMember = state.members.firstOrNull { it.id == p.paidByMemberId }
                amountManuallyEdited = true // don't overwrite loaded value
            }
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────
    var quantityError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var memberError by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        quantityError = when {
            quantityText.isBlank() -> "Enter number of cans"
            quantityText.toIntOrNull() == null -> "Must be a whole number"
            quantityInt <= 0 -> "Must be at least 1"
            else -> null
        }
        amountError = when {
            amountText.isBlank() -> "Enter the amount paid"
            amountText.toDoubleOrNull() == null -> "Must be a valid number"
            enteredAmount <= 0 -> "Amount must be greater than 0"
            else -> null
        }
        memberError = selectedMember == null
        return quantityError == null && amountError == null && !memberError
    }

    fun onSave() {
        focusManager.clearFocus()
        if (!validate()) return
        val member = selectedMember ?: return
        val finalAmount = amountText.toDoubleOrNull() ?: return

        if (isEditing && paymentId != null) {
            val existing = state.payments.firstOrNull { it.id == paymentId } ?: return
            viewModel.updatePayment(existing.copy(
                quantity           = quantityInt,
                amount             = finalAmount,
                paidByMemberId     = member.id,
                paidByNameSnapshot = member.name,
                purchaseDate       = purchaseDate,
                notes              = notes.trim().takeIf { it.isNotEmpty() },
                vendorName         = vendor.trim().takeIf { it.isNotEmpty() }
            ))
        } else {
            viewModel.addPayment(
                quantityInt, finalAmount, member, purchaseDate,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Price banner ──────────────────────────────────────────────────
            if (pricePerCan > 0) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
            ExposedDropdownMenuBox(expanded = memberDropdownExpanded, onExpandedChange = { memberDropdownExpanded = it }) {
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
                value = quantityText,
                onValueChange = {
                    quantityText = it.filter { c -> c.isDigit() }
                    quantityError = null
                    amountManuallyEdited = false  // allow re-auto-fill when qty changes
                },
                label = { Text("Number of cans *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                isError = quantityError != null,
                supportingText = quantityError?.let { err -> { Text(err) } },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Amount ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    // Only allow digits and a single decimal point
                    val filtered = it.filter { c -> c.isDigit() || c == '.' }
                        .let { s ->
                            val dotIdx = s.indexOf('.')
                            if (dotIdx >= 0) s.substring(0, dotIdx + 1) + s.substring(dotIdx + 1).filter { c -> c.isDigit() }
                            else s
                        }
                    amountText = filtered
                    amountError = null
                    amountManuallyEdited = true
                },
                label = { Text("Amount paid ($currency) *") },
                placeholder = { Text("e.g. 25.50") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                isError = amountError != null,
                supportingText = when {
                    amountError != null -> { { Text(amountError!!) } }
                    isPartialPayment -> { {
                        Text("⚠ Partial — ${formatAmount(shortfall, currency)} short of full payment", color = MaterialTheme.colorScheme.error)
                    } }
                    expectedAmount > 0.01 && enteredAmount >= expectedAmount - 0.01 && enteredAmount > 0 -> { {
                        Text("✓ Full payment covered", color = MaterialTheme.colorScheme.primary)
                    } }
                    else -> null
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Partial payment warning card ──────────────────────────────────
            if (isPartialPayment) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Partial Payment Recorded", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${selectedMember?.name ?: "This member"} will show as owing " +
                                    "${formatAmount(shortfall, currency)} in the Balances tab.",
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
                label = { Text("Vendor (optional)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
