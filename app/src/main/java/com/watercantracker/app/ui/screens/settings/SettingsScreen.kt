package com.watercantracker.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watercantracker.app.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showMonthlyResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Appearance ────────────────────────────────────────────────────
            SettingsSection(title = "Appearance") {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        AppThemeMode.LIGHT to "Light",
                        AppThemeMode.DARK to "Dark",
                        AppThemeMode.SYSTEM to "System"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.updateTheme(mode) },
                            label = { Text(label) }
                        )
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
                    subtitle = "Alert daily if payment is more than ${state.settings.overdueThresholdDays} days overdue",
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

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection(title = "About") {
                SettingsClickRow(
                    title = "Water Can Tracker",
                    subtitle = "Version 1.0.0 · Built with Jetpack Compose",
                    icon = Icons.Rounded.WaterDrop,
                    onClick = {}
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showMonthlyResetDialog) {
        AlertDialog(
            onDismissRequest = { showMonthlyResetDialog = false },
            title = { Text("Monthly reset") },
            text = {
                Text("This marks the current month as reset. Historical data is preserved but the monthly summary will start fresh. Are you sure?")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.recordMonthlyReset()
                    showMonthlyResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showMonthlyResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsClickRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
