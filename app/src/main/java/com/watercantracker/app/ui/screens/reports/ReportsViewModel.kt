package com.watercantracker.app.ui.screens.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watercantracker.app.data.repository.MemberRepository
import com.watercantracker.app.data.repository.PaymentRepository
import com.watercantracker.app.domain.model.MemberStats
import com.watercantracker.app.domain.model.MonthlySpendingSummary
import com.watercantracker.app.domain.model.PriceTrendPoint
import com.watercantracker.app.data.export.ExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportsUiState(
    val monthlySummaries: List<MonthlySpendingSummary> = emptyList(),
    val memberStats: List<MemberStats> = emptyList(),
    val priceTrend: List<PriceTrendPoint> = emptyList(),
    val groupAverage: Double = 0.0,
    val isLoading: Boolean = true,
    val exportInProgress: Boolean = false,
    val exportResult: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val memberRepository: MemberRepository,
    private val exportManager: ExportManager
) : ViewModel() {

    val uiState: StateFlow<ReportsUiState> = combine(
        paymentRepository.observeAllMonthlySummaries(),
        paymentRepository.observeMemberStats(),
        paymentRepository.observePriceTrend()
    ) { summaries, stats, trend ->
        val avg = if (stats.isNotEmpty()) stats.sumOf { it.totalAmountContributed } / stats.size else 0.0
        ReportsUiState(
            monthlySummaries = summaries,
            memberStats = stats,
            priceTrend = trend,
            groupAverage = avg,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsUiState())

    private val _exportInProgress = MutableStateFlow(false)
    private val _exportResult = MutableStateFlow<String?>(null)

    fun exportCsv(context: Context) = viewModelScope.launch {
        _exportInProgress.update { true }
        try {
            val path = exportManager.exportCsv(context)
            _exportResult.update { "CSV exported to: $path" }
        } catch (e: Exception) {
            _exportResult.update { "Export failed: ${e.message}" }
        } finally {
            _exportInProgress.update { false }
        }
    }

    fun exportPdf(context: Context) = viewModelScope.launch {
        _exportInProgress.update { true }
        try {
            val path = exportManager.exportPdf(context)
            _exportResult.update { "PDF exported to: $path" }
        } catch (e: Exception) {
            _exportResult.update { "Export failed: ${e.message}" }
        } finally {
            _exportInProgress.update { false }
        }
    }

    fun exportExcel(context: Context) = viewModelScope.launch {
        _exportInProgress.update { true }
        try {
            val path = exportManager.exportExcel(context)
            _exportResult.update { "Excel exported to: $path" }
        } catch (e: Exception) {
            _exportResult.update { "Export failed: ${e.message}" }
        } finally {
            _exportInProgress.update { false }
        }
    }

    fun clearExportResult() { _exportResult.update { null } }
}
