package com.watercantracker.app.data.export

import android.content.Context
import android.os.Environment
import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.dao.PaymentDao
import com.opencsv.CSVWriter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManager @Inject constructor(
    private val paymentDao: PaymentDao,
    private val memberDao: MemberDao
) {
    private val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timestamp get() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    private fun exportsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports")
        dir.mkdirs()
        return dir
    }

    suspend fun exportCsv(context: Context): String {
        val payments = paymentDao.observeAllPayments().let {
            // Collect once
            var result = listOf<com.watercantracker.app.data.local.entity.PaymentEntity>()
            kotlinx.coroutines.flow.first(it) { list -> result = list; true }
            result
        }

        val file = File(exportsDir(context), "water_can_payments_$timestamp.csv")
        CSVWriter(FileWriter(file)).use { writer ->
            writer.writeNext(arrayOf("Date", "Paid By", "Cans", "Amount", "Vendor", "Notes"))
            payments.forEach { p ->
                writer.writeNext(
                    arrayOf(
                        df.format(Date(p.purchaseDate)),
                        p.paidByNameSnapshot,
                        p.quantity.toString(),
                        String.format("%.2f", p.amount),
                        p.vendorName ?: "",
                        p.notes ?: ""
                    )
                )
            }
        }
        return file.absolutePath
    }

    suspend fun exportExcel(context: Context): String {
        val payments = paymentDao.observeAllPayments().let {
            var result = listOf<com.watercantracker.app.data.local.entity.PaymentEntity>()
            kotlinx.coroutines.flow.first(it) { list -> result = list; true }
            result
        }

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Payments")

        // Header
        val headerRow = sheet.createRow(0)
        listOf("Date", "Paid By", "Cans", "Amount", "Vendor", "Notes")
            .forEachIndexed { idx, title -> headerRow.createCell(idx).setCellValue(title) }

        // Data
        payments.forEachIndexed { rowIdx, p ->
            val row = sheet.createRow(rowIdx + 1)
            row.createCell(0).setCellValue(df.format(Date(p.purchaseDate)))
            row.createCell(1).setCellValue(p.paidByNameSnapshot)
            row.createCell(2).setCellValue(p.quantity.toDouble())
            row.createCell(3).setCellValue(p.amount)
            row.createCell(4).setCellValue(p.vendorName ?: "")
            row.createCell(5).setCellValue(p.notes ?: "")
        }

        (0..5).forEach { sheet.autoSizeColumn(it) }

        val file = File(exportsDir(context), "water_can_payments_$timestamp.xlsx")
        file.outputStream().use { workbook.write(it) }
        workbook.close()
        return file.absolutePath
    }

    suspend fun exportPdf(context: Context): String {
        val payments = paymentDao.observeAllPayments().let {
            var result = listOf<com.watercantracker.app.data.local.entity.PaymentEntity>()
            kotlinx.coroutines.flow.first(it) { list -> result = list; true }
            result
        }

        // Build a simple PDF using Android's built-in PdfDocument (no iText dependency required
        // for basic output; iText is available if richer formatting is needed later).
        val pdfDoc = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint().apply { textSize = 12f }

        var y = 40f
        paint.textSize = 18f; paint.isFakeBoldText = true
        canvas.drawText("Water Can Tracker – Payment Report", 30f, y, paint)
        y += 10f
        paint.textSize = 10f; paint.isFakeBoldText = false
        canvas.drawText("Generated: ${SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date())}", 30f, y + 16, paint)
        y += 36f

        paint.textSize = 11f; paint.isFakeBoldText = true
        canvas.drawText("Date", 30f, y, paint)
        canvas.drawText("Paid By", 110f, y, paint)
        canvas.drawText("Cans", 260f, y, paint)
        canvas.drawText("Amount", 320f, y, paint)
        canvas.drawText("Vendor", 410f, y, paint)
        y += 18f
        paint.isFakeBoldText = false; paint.textSize = 10f

        payments.take(60).forEach { p ->
            if (y > 800f) return@forEach // simple overflow guard
            canvas.drawText(df.format(Date(p.purchaseDate)), 30f, y, paint)
            canvas.drawText(p.paidByNameSnapshot.take(18), 110f, y, paint)
            canvas.drawText("${p.quantity}", 260f, y, paint)
            canvas.drawText(String.format("$%.2f", p.amount), 320f, y, paint)
            canvas.drawText((p.vendorName ?: "").take(14), 410f, y, paint)
            y += 16f
        }

        pdfDoc.finishPage(page)
        val file = File(exportsDir(context), "water_can_payments_$timestamp.pdf")
        file.outputStream().use { pdfDoc.writeTo(it) }
        pdfDoc.close()
        return file.absolutePath
    }
}

// Tiny helper to collect first emission of a Flow without coroutines scope
private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.first(predicate: (T) -> Boolean): T =
    kotlinx.coroutines.flow.first(this) { predicate(it) }
