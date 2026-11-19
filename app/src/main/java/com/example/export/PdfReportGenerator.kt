package com.example.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.model.MonthlyReportData
import com.example.model.TransactionType
import com.example.util.CategoryVisuals
import com.example.util.FinanceFormatters
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595 // A4 width in pt
    private const val PAGE_HEIGHT = 842 // A4 height in pt
    private const val MARGIN_X = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2)

    private val COLOR_EMERALD = Color.parseColor("#0E6B4E")
    private val COLOR_EMERALD_SOFT = Color.parseColor("#E6F5EE")
    private val COLOR_INK = Color.parseColor("#121C17")
    private val COLOR_MUTED = Color.parseColor("#53635B")
    private val COLOR_LINE = Color.parseColor("#DCE3DD")
    private val COLOR_ROW_ALT = Color.parseColor("#F6F7F4")
    private val COLOR_EXPENSE = Color.parseColor("#D94A38")
    private val COLOR_INCOME = Color.parseColor("#0D9468")

    fun suggestedFileName(yearMonth: String): String = "Laporan_KasKu_$yearMonth.pdf"

    fun writeReportToStream(
        report: MonthlyReportData,
        userLabel: String,
        outputStream: OutputStream
    ) {
        val document = PdfDocument()
        try {
            renderReportPages(document, report, userLabel)
            document.writeTo(outputStream)
            outputStream.flush()
        } finally {
            document.close()
        }
    }

    fun writeReportToCacheFile(
        context: Context,
        report: MonthlyReportData,
        userLabel: String
    ): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, suggestedFileName(report.yearMonth))
        FileOutputStream(file).use { fos ->
            writeReportToStream(report, userLabel, fos)
        }
        return file
    }

    private fun renderReportPages(
        document: PdfDocument,
        report: MonthlyReportData,
        userLabel: String
    ) {
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D6F2E6")
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_INK
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_INK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_INK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = 8.5f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_LINE
            strokeWidth = 0.8f
        }

        // 1. Header Banner
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_EMERALD }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 92f, headerPaint)
        canvas.drawText("LAPORAN KEUANGAN BULANAN", MARGIN_X, 38f, titlePaint)
        canvas.drawText(
            "KasKu  •  Periode: ${report.monthLabel}",
            MARGIN_X,
            58f,
            subtitlePaint
        )
        canvas.drawText(
            "Akun: $userLabel  •  Dicetak: ${FinanceFormatters.formatGeneratedTimestamp()}",
            MARGIN_X,
            74f,
            subtitlePaint
        )

        var y = 112f

        // 2. Summary Cards (4 boxes across)
        val boxGap = 8f
        val boxW = (CONTENT_WIDTH - boxGap * 3) / 4f
        val boxH = 54f
        val summaryItems = listOf(
            Triple("Saldo Awal", FinanceFormatters.formatRupiah(report.openingBalance), COLOR_INK),
            Triple("Pemasukan", FinanceFormatters.formatRupiah(report.totalIncome), COLOR_INCOME),
            Triple("Pengeluaran", FinanceFormatters.formatRupiah(report.totalExpense), COLOR_EXPENSE),
            Triple(
                "Saldo Akhir",
                FinanceFormatters.formatRupiah(report.closingBalance),
                if (report.closingBalance >= 0L) COLOR_EMERALD else COLOR_EXPENSE
            )
        )
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ROW_ALT }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_LINE
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
        }
        summaryItems.forEachIndexed { idx, (label, value, valColor) ->
            val left = MARGIN_X + idx * (boxW + boxGap)
            val rect = RectF(left, y, left + boxW, y + boxH)
            canvas.drawRoundRect(rect, 8f, 8f, cardBgPaint)
            canvas.drawRoundRect(rect, 8f, 8f, cardBorderPaint)
            canvas.drawText(label, left + 10f, y + 20f, mutedPaint)
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = valColor
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText(truncateText(value, valuePaint, boxW - 18f), left + 10f, y + 39f, valuePaint)
        }
        y += boxH + 22f

        // 3. Embedded Daily Balance Chart
        canvas.drawText("Grafik Pergerakan Saldo Harian (${report.monthLabel})", MARGIN_X, y, sectionPaint)
        y += 10f
        val chartHeight = 128f
        drawDailyBalanceChartOnPdf(canvas, report, MARGIN_X, y, CONTENT_WIDTH, chartHeight)
        y += chartHeight + 22f

        // 4. Category Breakdown Table
        canvas.drawText("Ringkasan Pengeluaran per Kategori", MARGIN_X, y, sectionPaint)
        y += 12f
        if (report.expenseByCategory.isEmpty()) {
            canvas.drawText("Belum ada pengeluaran tercatat pada periode ini.", MARGIN_X, y + 10f, mutedPaint)
            y += 26f
        } else {
            // Table header
            val thBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_EMERALD_SOFT }
            canvas.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 20f, thBg)
            canvas.drawText("Kategori", MARGIN_X + 8f, y + 13.5f, bodyBoldPaint)
            canvas.drawText("Transaksi", MARGIN_X + 230f, y + 13.5f, bodyBoldPaint)
            canvas.drawText("Porsi", MARGIN_X + 315f, y + 13.5f, bodyBoldPaint)
            canvas.drawText("Total (Rp)", MARGIN_X + 390f, y + 13.5f, bodyBoldPaint)
            y += 20f

            report.expenseByCategory.take(10).forEachIndexed { idx, cat ->
                if (idx % 2 == 1) {
                    canvas.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 19f, cardBgPaint)
                }
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = CategoryVisuals.parseColorInt(cat.colorHex)
                }
                canvas.drawCircle(MARGIN_X + 12f, y + 9.5f, 3.5f, dotPaint)
                canvas.drawText(
                    truncateText(cat.categoryName, bodyPaint, 195f),
                    MARGIN_X + 22f,
                    y + 13f,
                    bodyPaint
                )
                canvas.drawText("${cat.transactionCount}x", MARGIN_X + 230f, y + 13f, bodyPaint)
                val pctText = String.format("%.1f%%", cat.percentage * 100f)
                canvas.drawText(pctText, MARGIN_X + 315f, y + 13f, bodyPaint)
                canvas.drawText(
                    FinanceFormatters.formatRupiah(cat.totalAmount),
                    MARGIN_X + 390f,
                    y + 13f,
                    bodyBoldPaint
                )
                y += 19f
                canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            }
            y += 18f
        }

        // Helper to start new page when running out of vertical space
        fun ensureSpace(neededHeight: Float, drawTxHeaderAfterBreak: Boolean = false) {
            if (y + neededHeight > PAGE_HEIGHT - 44f) {
                drawPageFooter(canvas, pageNumber, mutedPaint, linePaint)
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 42f
                canvas.drawText(
                    "KasKu — Laporan ${report.monthLabel} (Lanjutan)",
                    MARGIN_X,
                    y,
                    mutedPaint
                )
                y += 16f
                if (drawTxHeaderAfterBreak) {
                    y = drawTransactionTableHeader(canvas, y, bodyBoldPaint)
                }
            }
        }

        // 5. Full Transaction Ledger
        ensureSpace(64f)
        canvas.drawText(
            "Rincian Transaksi (${report.transactions.size} catatan)",
            MARGIN_X,
            y,
            sectionPaint
        )
        y += 12f

        if (report.transactions.isEmpty()) {
            canvas.drawText("Tidak ada transaksi pada periode ini.", MARGIN_X, y + 12f, mutedPaint)
        } else {
            y = drawTransactionTableHeader(canvas, y, bodyBoldPaint)
            report.transactions.forEachIndexed { idx, tx ->
                val rowHeight = if (!tx.note.isNullOrBlank()) 28f else 20f
                ensureSpace(rowHeight + 6f, drawTxHeaderAfterBreak = true)

                if (idx % 2 == 1) {
                    canvas.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + rowHeight, cardBgPaint)
                }
                val dateStr = FinanceFormatters.formatDateFull(tx.dateMillis)
                canvas.drawText(dateStr, MARGIN_X + 6f, y + 13f, mutedPaint)
                canvas.drawText(
                    truncateText(tx.title, bodyBoldPaint, 165f),
                    MARGIN_X + 76f,
                    y + 13f,
                    bodyBoldPaint
                )
                if (!tx.note.isNullOrBlank()) {
                    canvas.drawText(
                        truncateText(tx.note, mutedPaint, 165f),
                        MARGIN_X + 76f,
                        y + 23.5f,
                        mutedPaint
                    )
                }
                canvas.drawText(
                    truncateText(tx.categoryName, bodyPaint, 95f),
                    MARGIN_X + 248f,
                    y + 13f,
                    bodyPaint
                )
                canvas.drawText(
                    tx.parsedPaymentMethod.label,
                    MARGIN_X + 348f,
                    y + 13f,
                    mutedPaint
                )

                val isExpense = tx.parsedType == TransactionType.EXPENSE
                val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isExpense) COLOR_EXPENSE else COLOR_INCOME
                    textSize = 9.2f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
                val signedAmount = if (isExpense) -tx.amount else tx.amount
                canvas.drawText(
                    FinanceFormatters.formatRupiah(signedAmount, showPlus = !isExpense),
                    PAGE_WIDTH - MARGIN_X - 6f,
                    y + 13f,
                    amountPaint
                )
                y += rowHeight
                canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            }
        }

        drawPageFooter(canvas, pageNumber, mutedPaint, linePaint)
        document.finishPage(page)
    }

    private fun drawTransactionTableHeader(
        canvas: Canvas,
        startY: Float,
        bodyBoldPaint: Paint
    ): Float {
        val thBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_EMERALD_SOFT }
        canvas.drawRect(MARGIN_X, startY, PAGE_WIDTH - MARGIN_X, startY + 20f, thBg)
        canvas.drawText("Tanggal", MARGIN_X + 6f, startY + 13.5f, bodyBoldPaint)
        canvas.drawText("Keterangan", MARGIN_X + 76f, startY + 13.5f, bodyBoldPaint)
        canvas.drawText("Kategori", MARGIN_X + 248f, startY + 13.5f, bodyBoldPaint)
        canvas.drawText("Metode", MARGIN_X + 348f, startY + 13.5f, bodyBoldPaint)
        val rightHeader = Paint(bodyBoldPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Jumlah", PAGE_WIDTH - MARGIN_X - 6f, startY + 13.5f, rightHeader)
        return startY + 20f
    }

    private fun drawDailyBalanceChartOnPdf(
        canvas: Canvas,
        report: MonthlyReportData,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val boxRect = RectF(left, top, left + width, top + height)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FAFBF9") }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_LINE
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
        }
        canvas.drawRoundRect(boxRect, 8f, 8f, bgPaint)
        canvas.drawRoundRect(boxRect, 8f, 8f, borderPaint)

        val points = report.dailyPoints
        if (points.isEmpty()) return

        val padLeft = 58f
        val padRight = 14f
        val padTop = 14f
        val padBottom = 22f
        val plotLeft = left + padLeft
        val plotRight = left + width - padRight
        val plotTop = top + padTop
        val plotBottom = top + height - padBottom
        val plotW = plotRight - plotLeft
        val plotH = plotBottom - plotTop

        val rawMin = points.minOf { it.closingBalance }
        val rawMax = points.maxOf { it.closingBalance }
        val minVal = min(rawMin, 0L).toFloat()
        val maxVal = if (rawMax == minVal.toLong()) (minVal + 100_000f) else max(rawMax.toFloat(), minVal + 10_000f)
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_LINE
            strokeWidth = 0.6f
        }
        val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = 7.5f
            textAlign = Paint.Align.RIGHT
        }

        // 3 horizontal gridlines
        for (i in 0..2) {
            val frac = i / 2f
            val yPos = plotBottom - frac * plotH
            val valAtLine = (minVal + frac * range).toLong()
            canvas.drawLine(plotLeft, yPos, plotRight, yPos, gridPaint)
            canvas.drawText(
                FinanceFormatters.formatCompactRupiah(valAtLine),
                plotLeft - 6f,
                yPos + 2.5f,
                axisLabelPaint
            )
        }

        val linePath = Path()
        val fillPath = Path()
        val stepX = if (points.size > 1) plotW / (points.size - 1) else 0f

        points.forEachIndexed { index, pt ->
            val x = plotLeft + index * stepX
            val normalized = (pt.closingBalance.toFloat() - minVal) / range
            val y = plotBottom - normalized * plotH
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, plotBottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (index == points.lastIndex) {
                fillPath.lineTo(x, plotBottom)
                fillPath.close()
            }
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(38, 14, 107, 78)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_EMERALD
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, strokePaint)

        // X-axis day ticks
        val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = 7.5f
            textAlign = Paint.Align.CENTER
        }
        val tickDays = listOf(1, 5, 10, 15, 20, 25, points.size).distinct().filter { it <= points.size }
        tickDays.forEach { day ->
            val x = plotLeft + (day - 1) * stepX
            canvas.drawText("Tgl $day", x, plotBottom + 13f, xLabelPaint)
        }
    }

    private fun drawPageFooter(
        canvas: Canvas,
        pageNumber: Int,
        mutedPaint: Paint,
        linePaint: Paint
    ) {
        val footerY = PAGE_HEIGHT - 26f
        canvas.drawLine(MARGIN_X, footerY - 10f, PAGE_WIDTH - MARGIN_X, footerY - 10f, linePaint)
        canvas.drawText("Dibuat otomatis oleh KasKu — Pencatat Keuangan Pribadi", MARGIN_X, footerY, mutedPaint)
        val rightPaint = Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Halaman $pageNumber", PAGE_WIDTH - MARGIN_X, footerY, rightPaint)
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }
}
