package com.watercantracker.app.settlement

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watercantracker.app.data.local.entity.SettlementEntity
import com.watercantracker.app.data.repository.SettlementRepository
import com.watercantracker.app.data.repository.SettingsRepository
import com.watercantracker.app.settlement.pdf.SettlementPdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class SettlementUiState(
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val selectedYear: Int  = Calendar.getInstance().get(Calendar.YEAR),
    val settlement: MonthlySettlement?       = null,
    val allSettlements: List<SettlementEntity> = emptyList(),
    val currencySymbol: String = "AED",
    val isGenerating: Boolean  = false,
    val exportPath: String?    = null,
    val error: String?         = null
)

@HiltViewModel
class SettlementViewModel @Inject constructor(
    private val settlementRepository: SettlementRepository,
    private val settingsRepository: SettingsRepository,
    private val pdfExporter: SettlementPdfExporter
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    private val _selectedYear  = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val _settlement    = MutableStateFlow<MonthlySettlement?>(null)
    private val _isGenerating  = MutableStateFlow(false)
    private val _exportPath    = MutableStateFlow<String?>(null)
    private val _error         = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettlementUiState> = combine(
        _selectedMonth,
        _selectedYear,
        _settlement,
        settlementRepository.observeAllSettlements(),
        settingsRepository.observeSettings()
    ) { month, year, settlement, allSettlements, settings ->
        SettlementUiState(
            selectedMonth  = month,
            selectedYear   = year,
            settlement     = settlement,
            allSettlements = allSettlements,
            currencySymbol = settings.currencySymbol,
            isGenerating   = _isGenerating.value,
            exportPath     = _exportPath.value,
            error          = _error.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettlementUiState())

    init { loadForCurrentSelection() }

    fun selectMonth(month: Int, year: Int) {
        _selectedMonth.update { month }
        _selectedYear.update { year }
        loadForCurrentSelection()
    }

    private fun loadForCurrentSelection() = viewModelScope.launch {
        val entity = settlementRepository.observeSettlement(
            _selectedMonth.value, _selectedYear.value
        ).first()
        _settlement.update { entity?.let { settlementRepository.deserialize(it) } }
    }

    fun generateSettlement() = viewModelScope.launch {
        _isGenerating.update { true }
        _error.update { null }
        try {
            val result = settlementRepository.regenerate(_selectedMonth.value, _selectedYear.value)
            _settlement.update { result }
        } catch (e: Exception) {
            _error.update { "Failed to generate: ${e.message}" }
        } finally {
            _isGenerating.update { false }
        }
    }

    fun exportPdf(context: Context) = viewModelScope.launch {
        val s        = _settlement.value ?: return@launch
        val currency = uiState.value.currencySymbol
        try {
            val path = pdfExporter.export(context, s, currency)
            _exportPath.update { path }
        } catch (e: Exception) {
            _error.update { "PDF export failed: ${e.message}" }
        }
    }

    fun clearExportPath() = _exportPath.update { null }
    fun clearError()      = _error.update { null }
}
