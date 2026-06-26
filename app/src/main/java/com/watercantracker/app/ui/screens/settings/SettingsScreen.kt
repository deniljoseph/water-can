package com.watercantracker.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watercantracker.app.ui.theme.AccentColor
import com.watercantracker.app.ui.theme.AppThemeMode
import com.watercantracker.app.ui.theme.DarkModeVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    bottomPadding: PaddingValues,
    onSync: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showMonthlyResetDialog by remember { mutableStateOf(false) }
    var priceText by remember(state.settings.defaultPricePerCan) {
        mutableStateOf(
            if (state.settings.defaultPricePerCan > 0)
                String.format("%.2f", state.settings.defaultPricePerCan) else ""
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            // ── Water Can Price ───────────────────────────────────────────────
            SettingsSection("Water Can Price") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Set the price per can. The app auto-fills payment amounts and calculates partial shortfalls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price per can (${state.settings.currencySymbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        leadingIcon = { Text(state.settings.currencySymbol, Modifier.padding(start = 12.dp)) },
                        trailingIcon = {
                            if (priceText.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.setDefaultPrice(priceText.toDoubleOrNull() ?: 0.0)
                                }) { Icon(Icons.Rounded.Check, "Save", tint = MaterialTheme.colorScheme.primary) }
                            }
                        },
                        supportingText = { Text(if (state.settings.defaultPricePerCan > 0)
                            "Current: ${state.settings.currencySymbol} ${String.format("%.2f", state.settings.defaultPricePerCan)} · Tap ✓ to save"
                            else "Not set · enter price and tap ✓") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.settings.defaultPricePerCan > 0) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { priceText = ""; viewModel.setDefaultPrice(0.0) },
                            modifier = Modifier.fillMaxWidth()) { Text("Clear price") }
                    }
                }
            }

            HorizontalDivider()

            // ── Appearance ────────────────────────────────────────────────────
            SettingsSection("Appearance") {
                // Theme mode
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Theme", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(AppThemeMode.LIGHT to "Light", AppThemeMode.DARK to "Dark", AppThemeMode.SYSTEM to "System")
                            .forEach { (mode, label) ->
                                FilterChip(selected = state.themeMode == mode,
                                    onClick = { viewModel.updateTheme(mode) },
                                    label = { Text(label) })
                            }
                    }

                    // Dark mode variant (only when dark)
                    if (state.themeMode != AppThemeMode.LIGHT) {
                        Spacer(Modifier.height(12.dp))
                        Text("Dark mode style", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                DarkModeVariant.DARK     to "Dark",
                                DarkModeVariant.AMOLED   to "AMOLED",
                                DarkModeVariant.DARK_GRAY to "Gray"
                            ).forEach { (variant, label) ->
                                FilterChip(
                                    selected = state.darkModeVariant == variant,
                                    onClick  = { viewModel.updateDarkVariant(variant) },
                                    label    = { Text(label) }
                                )
                            }
                        }
                    }

                    // Accent color
                    Spacer(Modifier.height(12.dp))
                    Text("Accent color", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccentColor.values().forEach { accent ->
                            val selected = state.accentColor == accent
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(accent.primary)
                                    .then(
                                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { viewModel.updateAccentColor(accent) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) Icon(Icons.Rounded.Check, null,
                                    tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Notifications ─────────────────────────────────────────────────
            SettingsSection("Notifications") {
                SwitchRow("Payment reminders", "Remind the next payer when it's their turn",
                    state.settings.remindersEnabled, viewModel::setRemindersEnabled)
                SwitchRow("Daily overdue reminders",
                    "Alert daily if payment overdue by ${state.settings.overdueThresholdDays}+ days",
                    state.settings.overdueRemindersEnabled, viewModel::setOverdueRemindersEnabled)
            }

            HorizontalDivider()

            // ── Sync ──────────────────────────────────────────────────────────
            SettingsSection("Live Sync") {
                ClickRow("Real-time sync & QR invite",
                    if (state.settings.firebaseRoomId != null) "Connected to room" else "Not configured",
                    Icons.Rounded.Sync, onSync)
            }

            HorizontalDivider()

            // ── Data ──────────────────────────────────────────────────────────
            SettingsSection("Data") {
                ClickRow("Monthly reset", "Archive this month and start fresh",
                    Icons.Rounded.Refresh) { showMonthlyResetDialog = true }
            }

            HorizontalDivider()

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection("About") {
                AboutCard()
            }

            Spacer(Modifier.height(bottomPadding.calculateBottomPadding() + 32.dp))
        }
    }

    if (showMonthlyResetDialog) {
        AlertDialog(
            onDismissRequest = { showMonthlyResetDialog = false },
            title   = { Text("Monthly reset") },
            text    = { Text("Historical data is preserved. The monthly summary will start fresh from today.") },
            confirmButton = {
                Button(onClick = { viewModel.recordMonthlyReset(); showMonthlyResetDialog = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showMonthlyResetDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AboutCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WaterDrop, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Water Can Tracker", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    Text("Version 1.3.0", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Made by Denil Joseph", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text("Built with Kotlin · Jetpack Compose · Material 3 · Room · Firebase",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tracks shared water can expenses, manages payment rotation, calculates monthly settlements, and syncs across devices in real time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        content()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ClickRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
