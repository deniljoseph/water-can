package com.watercantracker.app.settlement.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.watercantracker.app.settlement.MonthlySettlement
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettlementPdfExporter @Inject constructor() {

    fun export(context: Context, settlement: MonthlySettlement, currency: String): String {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val boldPaint  = Paint().apply { typeface = Typeface.DEFAULT_BOLD }
        val plainPaint = Paint().apply { typeface = Typeface.DEFAULT }
        val redPaint   = Paint().apply { typeface = Typeface.DEFAULT; color = android.graphics.Color.rgb(180, 20, 20) }
        val greenPaint = Paint().apply { typeface = Typeface.DEFAULT; color = android.graphics.Color.rgb(10, 100, 50) }
        val grayPaint  = Paint().apply { color = android.graphics.Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 1f }

        var y = 48f
        val lm = 36f  // left margin
        val pw = 523f // page width inside margins

        // ── Header ────────────────────────────────────────────────────────────
        boldPaint.textSize = 22f
        canvas.drawText("💧 Water Can Tracker", lm, y, boldPaint); y += 28f
        boldPaint.textSize = 16f
        canvas.drawText("Monthly Settlement Report — ${settlement.monthLabel}", lm, y, boldPaint); y += 14f
        plainPaint.textSize = 9f
        canvas.drawText("Generated: ${SimpleDateFormat("d MMM yyyy, HH:mm").format(Date())}", lm, y, plainPaint); y += 18f
        canvas.drawLine(lm, y, lm + pw, y, grayPaint); y += 14f

        // ── Summary ───────────────────────────────────────────────────────────
        boldPaint.textSize = 13f
        canvas.drawText("Summary", lm, y, boldPaint); y += 16f
        plainPaint.textSize = 11f
        canvas.drawText("Total spent this month:   $currency ${String.format("%.2f", settlement.totalSpent)}", lm, y, plainPaint); y += 14f
        canvas.drawText("Number of members:        ${settlement.memberCount}", lm, y, plainPaint); y += 14f
        canvas.drawText("Fair share per member:    $currency ${String.format("%.2f", settlement.fairShare)}", lm, y, plainPaint); y += 20f
        canvas.drawLine(lm, y, lm + pw, y, grayPaint); y += 14f

        // ── Member balances ───────────────────────────────────────────────────
        boldPaint.textSize = 13f
        canvas.drawText("Member Balances", lm, y, boldPaint); y += 16f

        // Table header
        boldPaint.textSize = 10f
        canvas.drawText("Member", lm, y, boldPaint)
        canvas.drawText("Paid", lm + 180f, y, boldPaint)
        canvas.drawText("Fair Share", lm + 280f, y, boldPaint)
        canvas.drawText("Balance", lm + 390f, y, boldPaint)
        y += 4f
        canvas.drawLine(lm, y, lm + pw, y, grayPaint); y += 12f

        plainPaint.textSize = 10f
        for (mb in settlement.memberBalances) {
            val balPaint = when {
                mb.balance > 0.005  -> greenPaint
                mb.balance < -0.005 -> redPaint
                else                -> plainPaint
            }
            balPaint.textSize = 10f
            canvas.drawText(mb.memberName.take(24), lm, y, balPaint)
            canvas.drawText("$currency ${String.format("%.2f", mb.paidAmount)}", lm + 180f, y, plainPaint.also { it.textSize = 10f })
            canvas.drawText("$currency ${String.format("%.2f", mb.fairShare)}", lm + 280f, y, plainPaint)
            val balSign = if (mb.balance >= 0) "+" else ""
            canvas.drawText("$balSign$currency ${String.format("%.2f", mb.balance)}", lm + 390f, y, balPaint)
            y += 14f
            if (y > 780f) break  // overflow guard
        }

        y += 6f
        canvas.drawLine(lm, y, lm + pw, y, grayPaint); y += 14f

        // ── Settlement transactions ───────────────────────────────────────────
        boldPaint.textSize = 13f
        canvas.drawText("Settlement Instructions", lm, y, boldPaint); y += 16f

        if (settlement.transactions.isEmpty()) {
            plainPaint.textSize = 11f
            canvas.drawText("✓ All members are settled — no transfers needed.", lm, y, greenPaint.also { it.textSize = 11f }); y += 14f
        } else {
            plainPaint.textSize = 11f
            settlement.transactions.forEachIndexed { idx, tx ->
                if (y > 800f) return@forEachIndexed
                canvas.drawText(
                    "${idx + 1}.  ${tx.fromMemberName}  →  ${tx.toMemberName}   $currency ${String.format("%.2f", tx.amount)}",
                    lm, y, plainPaint
                )
                y += 14f
            }
        }

        y += 10f
        canvas.drawLine(lm, y, lm + pw, y, grayPaint); y += 12f
        plainPaint.textSize = 8f
        canvas.drawText("Water Can Tracker · Auto Settlement System", lm, y, plainPaint)

        doc.finishPage(page)

        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "settlements").also { it.mkdirs() }
        val ts  = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val file = File(dir, "settlement_${settlement.year}_${String.format("%02d", settlement.month)}_$ts.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file.absolutePath
    }
}
