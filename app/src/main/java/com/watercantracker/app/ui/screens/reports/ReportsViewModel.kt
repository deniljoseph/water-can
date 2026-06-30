package com.watercantracker.app.ui.screens.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watercantracker.app.data.repository.MemberRepository
import com.watercantracker.app.data.repository.PaymentRepository
import com.watercantracker.app.data.repository.SettingsRepository
import com.watercantracker.app.domain.model.MemberStats
import com.watercantracker.app.domain.model.MonthlySpendingSummary
import com.watercantracker.app.domain.model.PriceTrendPoint
import com.watercantracker.app.data.export.ExportManager
import com.watercantracker.app.data.local.entity.PaymentEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class MonthlyReportData(
    val summary: MonthlySpendingSummary,
    val payments: List<PaymentEntity>,
    val memberBreakdown: List<MemberPaymentShare>
)

data class MemberPaymentShare(
    val memberName: String,
    val avatarUri: String?,
    val totalPaid: Double,
    val paymentCount: Int,
    val canCount: Int,
    val fairShare: Double,
    val balance: Double   // positive = credit, negative = owes
)

data class ReportsUiState(
    val monthlySummaries: List<MonthlySpendingSummary> = emptyList(),
    val memberStats: List<MemberStats> = emptyList(),
    val priceTrend: List<PriceTrendPoint> = emptyList(),
    val groupAverage: Double = 0.0,
    val currencySymbol: String = "AED",
    // Monthly drill-down
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val selectedYear: Int  = Calendar.getInstance().get(Calendar.YEAR),
    val monthlyReportData: MonthlyReportData? = null,
    val allPayments: List<PaymentEntity> = emptyList(),
    val isLoading: Boolean = true,
    val exportInProgress: Boolean = false,
    val exportResult: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val memberRepository: MemberRepository,
    private val settingsRepository: SettingsRepository,
    private val exportManager: ExportManager
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    private val _selectedYear  = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val _exportResult  = MutableStateFlow<String?>(null)
    private val _exportInProgress = MutableStateFlow(false)

    val uiState: StateFlow<ReportsUiState> = combine(
        paymentRepository.observeAllMonthlySummaries(),
        paymentRepository.observeMemberStats(),
        paymentRepository.observeAllPayments(),
        settingsRepository.observeSettings()
    ) { summaries, stats, allPayments, settings ->
        val avg = if (stats.isNotEmpty()) stats.sumOf { it.totalAmountContributed } / stats.size else 0.0
        val month = _selectedMonth.value
        val year  = _selectedYear.value

        // Build monthly drill-down for selected month
        val monthPayments = allPayments.filter { p ->
            val cal = Calendar.getInstance().apply { timeInMillis = p.purchaseDate }
            cal.get(Calendar.MONTH) + 1 == month && cal.get(Calendar.YEAR) == year
        }
        val monthTotal = monthPayments.sumOf { it.amount }
        val activeMembers = memberRepository.observeActiveMembers().let {
            try { kotlinx.coroutines.flow.first(it) } catch (_: Exception) { emptyList() }
        }
        val fairShare = if (activeMembers.isNotEmpty()) monthTotal / activeMembers.size else 0.0

        val memberBreakdown = activeMembers.map { member ->
            val memberPayments = monthPayments.filter { it.paidByMemberId == member.id }
            val paid = memberPayments.sumOf { it.amount }
            MemberPaymentShare(
                memberName   = member.name,
                avatarUri    = member.avatarUri,
                totalPaid    = paid,
                paymentCount = memberPayments.size,
                canCount     = memberPayments.sumOf { it.quantity },
                fairShare    = fairShare,
                balance      = paid - fairShare
            )
        }.sortedByDescending { it.totalPaid }

        val monthSummary = summaries.firstOrNull { it.yearMonth == "$year-${String.format("%02d", month)}" }
        val reportData = if (monthPayments.isNotEmpty() || monthSummary != null) {
            MonthlyReportData(
                summary         = monthSummary ?: MonthlySpendingSummary(
                    yearMonth    = "$year-${String.format("%02d", month)}",
                    totalCans    = monthPayments.sumOf { it.quantity },
                    totalAmount  = monthTotal,
                    paymentCount = monthPayments.size
                ),
                payments        = monthPayments.sortedByDescending { it.purchaseDate },
                memberBreakdown = memberBreakdown
            )
        } else null

        ReportsUiState(
            monthlySummaries  = summaries,
            memberStats       = stats,
            groupAverage      = avg,
            currencySymbol    = settings.currencySymbol,
            selectedMonth     = month,
            selectedYear      = year,
            monthlyReportData = reportData,
            allPayments       = allPayments,
            isLoading         = false,
            exportInProgress  = _exportInProgress.value,
            exportResult      = _exportResult.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsUiState())

    fun selectMonth(month: Int, year: Int) {
        _selectedMonth.update { month }
        _selectedYear.update { year }
    }

    fun prevMonth() {
        val m = _selectedMonth.value
        val y = _selectedYear.value
        if (m == 1) { _selectedMonth.update { 12 }; _selectedYear.update { y - 1 } }
        else _selectedMonth.update { m - 1 }
    }

    fun nextMonth() {
        val m = _selectedMonth.value
        val y = _selectedYear.value
        if (m == 12) { _selectedMonth.update { 1 }; _selectedYear.update { y + 1 } }
        else _selectedMonth.update { m + 1 }
    }

    fun exportCsv(context: Context) = viewModelScope.launch {
        _exportInProgress.update { true }
        try { _exportResult.update { exportManager.exportCsv(context) } }
        catch (e: Exception) { _exportResult.update { "Export failed: ${e.message}" } }
        finally { _exportInProgress.update { false } }
    }

    fun exportExcel(context: Context) = viewModelScope.launch {
        _exportInProgress.update { true }
        try { _exportResult.update { exportManager.exportExcel(context) } }
        catch (e: Exception) { _exportResult.update { "Export failed: ${e.message}" } }
        finally { _exportInProgress.update { false } }
    }

    fun exportPdf(context: Context) = viewModelScope.launch {
        _exportInProgress.update { true }
        try { _exportResult.update { exportManager.exportPdf(context) } }
        catch (e: Exception) { _exportResult.update { "Export failed: ${e.message}" } }
        finally { _exportInProgress.update { false } }
    }

    fun clearExportResult() { _exportResult.update { null } }
}
