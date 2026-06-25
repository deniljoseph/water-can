package com.watercantracker.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watercantracker.app.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    bottomPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showMonthlyResetDialog by remember { mutableStateOf(false) }

    // Local editable price state — committed on focus-lost / done
    var priceText by remember(state.settings.defaultPricePerCan) {
        mutableStateOf(
            if (state.settings.defaultPricePerCan > 0)
                String.format("%.2f", state.settings.defaultPricePerCan)
            else ""
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            // ── Water Can Price ───────────────────────────────────────────────
            SettingsSection(title = "Water Can Price") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Set the price of a single can. The app will auto-fill the total amount " +
                                "when you record a payment, and use this to calculate partial payment shortfalls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price per can (${state.settings.currencySymbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        leadingIcon = {
                            Text(state.settings.currencySymbol, modifier = Modifier.padding(start = 12.dp))
                        },
                        trailingIcon = {
                            if (priceText.isNotEmpty()) {
                                IconButton(onClick = {
                                    val price = priceText.toDoubleOrNull() ?: 0.0
                                    viewModel.setDefaultPrice(price)
                                }) {
                                    Icon(Icons.Rounded.Check, "Save price", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        supportingText = {
                            Text(
                                if (state.settings.defaultPricePerCan > 0)
                                    "Current: ${state.settings.currencySymbol}${String.format("%.2f", state.settings.defaultPricePerCan)} per can  •  Tap ✓ to save changes"
                                else
                                    "Not set — enter a price and tap ✓ to save"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.settings.defaultPricePerCan > 0) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { priceText = ""; viewModel.setDefaultPrice(0.0) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear price")
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Appearance ────────────────────────────────────────────────────
            SettingsSection(title = "Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AppThemeMode.LIGHT to "Light", AppThemeMode.DARK to "Dark", AppThemeMode.SYSTEM to "System").forEach { (mode, label) ->
                        FilterChip(selected = state.themeMode == mode, onClick = { viewModel.updateTheme(mode) }, label = { Text(label) })
                    }
                }
            }

            HorizontalDivider()

            // ── Notifications ─────────────────────────────────────────────────
            SettingsSection(title = "Notifications") {
                SwitchRow(
                    title = "Payment reminders",
                    subtitle = "Remind the next payer when it's their turn",
                    checked = state.settings.remindersEnabled,
                    onCheckedChange = { viewModel.setRemindersEnabled(it) }
                )
                SwitchRow(
                    title = "Daily overdue reminders",
                    subtitle = "Alert daily if payment is overdue by ${state.settings.overdueThresholdDays}+ days",
                    checked = state.settings.overdueRemindersEnabled,
                    onCheckedChange = { viewModel.setOverdueRemindersEnabled(it) }
                )
            }

            HorizontalDivider()

            // ── Data ──────────────────────────────────────────────────────────
            SettingsSection(title = "Data") {
                SettingsClickRow(
                    title = "Monthly reset",
                    subtitle = "Archive this month and start a fresh cycle",
                    icon = Icons.Rounded.Refresh,
                    onClick = { showMonthlyResetDialog = true }
                )
            }

            HorizontalDivider()

            SettingsSection(title = "About") {
                SettingsClickRow(
                    title = "Water Can Tracker",
                    subtitle = "Version 1.0.0 · Built with Jetpack Compose",
                    icon = Icons.Rounded.WaterDrop,
                    onClick = {}
                )
            }

            Spacer(Modifier.height(bottomPadding.calculateBottomPadding() + 32.dp))
        }
    }

    if (showMonthlyResetDialog) {
        AlertDialog(
            onDismissRequest = { showMonthlyResetDialog = false },
            title = { Text("Monthly reset") },
            text = { Text("This marks the current month as reset. Historical data is preserved but the monthly summary will start fresh.") },
            confirmButton = {
                Button(onClick = { viewModel.recordMonthlyReset(); showMonthlyResetDialog = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showMonthlyResetDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        content()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsClickRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
