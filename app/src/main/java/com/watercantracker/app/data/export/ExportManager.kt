package com.watercantracker.app.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.watercantracker.app.data.local.entity.PaymentEntity
import com.watercantracker.app.data.local.dao.MemberDao
import com.watercantracker.app.data.local.dao.PaymentDao
import kotlinx.coroutines.flow.first
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
    private val ts get() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    private fun exportsDir(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports")
            .also { it.mkdirs() }

    private suspend fun allPayments(): List<PaymentEntity> =
        paymentDao.observeAllPayments().first()

    // ── CSV (pure stdlib, no OpenCSV needed) ─────────────────────────────────
    suspend fun exportCsv(context: Context): String {
        val payments = allPayments()
        val file = File(exportsDir(context), "water_can_payments_$ts.csv")
        FileWriter(file).use { fw ->
            fw.write("Date,Paid By,Cans,Amount,Vendor,Notes\n")
            payments.forEach { p ->
                fw.write(
                    listOf(
                        df.format(Date(p.purchaseDate)),
                        p.paidByNameSnapshot.csvEscape(),
                        p.quantity.toString(),
                        String.format("%.2f", p.amount),
                        (p.vendorName ?: "").csvEscape(),
                        (p.notes ?: "").csvEscape()
                    ).joinToString(",") + "\n"
                )
            }
        }
        return file.absolutePath
    }

    // ── Excel (Apache POI OOXML) ──────────────────────────────────────────────
    suspend fun exportExcel(context: Context): String {
        val payments = allPayments()
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Payments")
        val headers = listOf("Date", "Paid By", "Cans", "Amount", "Vendor", "Notes")

        sheet.createRow(0).also { row ->
            headers.forEachIndexed { i, h -> row.createCell(i).setCellValue(h) }
        }
        payments.forEachIndexed { rowIdx, p ->
            sheet.createRow(rowIdx + 1).also { row ->
                row.createCell(0).setCellValue(df.format(Date(p.purchaseDate)))
                row.createCell(1).setCellValue(p.paidByNameSnapshot)
                row.createCell(2).setCellValue(p.quantity.toDouble())
                row.createCell(3).setCellValue(p.amount)
                row.createCell(4).setCellValue(p.vendorName ?: "")
                row.createCell(5).setCellValue(p.notes ?: "")
            }
        }
        (0..5).forEach { sheet.autoSizeColumn(it) }

        val file = File(exportsDir(context), "water_can_payments_$ts.xlsx")
        file.outputStream().use { workbook.write(it) }
        workbook.close()
        return file.absolutePath
    }

    // ── PDF (Android built-in PdfDocument, zero extra deps) ──────────────────
    suspend fun exportPdf(context: Context): String {
        val payments = allPayments()
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        var y = 50f
        paint.textSize = 18f; paint.isFakeBoldText = true
        canvas.drawText("Water Can Tracker – Payment Report", 30f, y, paint)
        y += 20f
        paint.textSize = 10f; paint.isFakeBoldText = false
        canvas.drawText(
            "Generated: ${SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date())}",
            30f, y, paint
        )
        y += 30f

        // Column headers
        paint.textSize = 11f; paint.isFakeBoldText = true
        canvas.drawText("Date", 30f, y, paint)
        canvas.drawText("Paid By", 105f, y, paint)
        canvas.drawText("Cans", 245f, y, paint)
        canvas.drawText("Amount", 295f, y, paint)
        canvas.drawText("Vendor", 385f, y, paint)
        y += 6f
        // Divider line
        paint.strokeWidth = 1f
        canvas.drawLine(30f, y, 565f, y, paint.also { it.isFakeBoldText = false })
        y += 14f

        paint.textSize = 10f
        payments.take(65).forEach { p ->
            if (y > 810f) return@forEach
            canvas.drawText(df.format(Date(p.purchaseDate)), 30f, y, paint)
            canvas.drawText(p.paidByNameSnapshot.take(18), 105f, y, paint)
            canvas.drawText("${p.quantity}", 245f, y, paint)
            canvas.drawText(String.format("$%.2f", p.amount), 295f, y, paint)
            canvas.drawText((p.vendorName ?: "").take(14), 385f, y, paint)
            y += 15f
        }

        doc.finishPage(page)
        val file = File(exportsDir(context), "water_can_payments_$ts.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file.absolutePath
    }

    private fun String.csvEscape(): String =
        if (contains(",") || contains("\"") || contains("\n"))
            "\"${replace("\"", "\"\"")}\""
        else this
}
