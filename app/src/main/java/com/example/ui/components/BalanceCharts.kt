package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CategorySummary
import com.example.model.DailyBalancePoint
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.CategoryVisuals
import com.example.util.FinanceFormatters
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun DailyBalanceLineChart(
    points: List<DailyBalancePoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    var selectedIndex by remember(points.size) {
        val lastActive = points.indexOfLast { it.transactionCount > 0 }
        mutableIntStateOf(if (lastActive >= 0) lastActive else points.lastIndex)
    }
    val safeIndex = selectedIndex.coerceIn(0, points.lastIndex)
    val selectedPoint = points[safeIndex]

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val lineColor = if (selectedPoint.closingBalance >= 0L) primaryColor else errorColor

    val minBalance = remember(points) { min(points.minOf { it.closingBalance }, 0L) }
    val maxBalance = remember(points) {
        val rawMax = points.maxOf { it.closingBalance }
        if (rawMax == minBalance) minBalance + 100_000L else max(rawMax, minBalance + 10_000L)
    }
    val midBalance = (minBalance + maxBalance) / 2L

    Column(modifier = modifier.testTag("daily_balance_line_chart")) {
        // Interactive Day Inspector Strip
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Saldo ${selectedPoint.dateLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FinanceFormatters.formatRupiah(selectedPoint.closingBalance),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = lineColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (selectedPoint.expense > 0L) {
                            "Keluar ${FinanceFormatters.formatCompactRupiah(selectedPoint.expense)}"
                        } else {
                            "Tidak ada keluar"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = if (selectedPoint.expense > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedPoint.income > 0L) {
                        Text(
                            text = "Masuk +${FinanceFormatters.formatCompactRupiah(selectedPoint.income)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    } else {
                        Text(
                            text = "Geser / ketuk grafik",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .height(156.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = FinanceFormatters.formatCompactRupiah(maxBalance),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = FinanceFormatters.formatCompactRupiah(midBalance),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = FinanceFormatters.formatCompactRupiah(minBalance),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(8.dp))

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(166.dp)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            if (size.width > 0 && points.size > 1) {
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                selectedIndex = (fraction * (points.lastIndex)).roundToInt()
                            }
                        }
                    }
                    .pointerInput(points) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            if (size.width > 0 && points.size > 1) {
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                selectedIndex = (fraction * (points.lastIndex)).roundToInt()
                            }
                        }
                    }
            ) {
                val topPad = 10.dp.toPx()
                val bottomPad = 10.dp.toPx()
                val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                val plotW = size.width.coerceAtLeast(1f)
                val range = (maxBalance - minBalance).toFloat().coerceAtLeast(1f)

                // Horizontal dashed gridlines
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                listOf(0f, 0.5f, 1f).forEach { frac ->
                    val y = topPad + plotH * frac
                    drawLine(
                        color = outlineColor,
                        start = Offset(0f, y),
                        end = Offset(plotW, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }

                // Build coordinates
                val stepX = if (points.size > 1) plotW / (points.size - 1) else 0f
                val coords = points.mapIndexed { idx, pt ->
                    val x = idx * stepX
                    val norm = (pt.closingBalance - minBalance).toFloat() / range
                    val y = topPad + plotH * (1f - norm)
                    Offset(x, y)
                }

                val linePath = Path()
                val fillPath = Path()
                coords.forEachIndexed { idx, pt ->
                    if (idx == 0) {
                        linePath.moveTo(pt.x, pt.y)
                        fillPath.moveTo(pt.x, topPad + plotH)
                        fillPath.lineTo(pt.x, pt.y)
                    } else {
                        val prev = coords[idx - 1]
                        val ctrlX = (prev.x + pt.x) / 2f
                        linePath.cubicTo(ctrlX, prev.y, ctrlX, pt.y, pt.x, pt.y)
                        fillPath.cubicTo(ctrlX, prev.y, ctrlX, pt.y, pt.x, pt.y)
                    }
                    if (idx == coords.lastIndex) {
                        fillPath.lineTo(pt.x, topPad + plotH)
                        fillPath.close()
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.24f),
                            lineColor.copy(alpha = 0.01f)
                        ),
                        startY = topPad,
                        endY = topPad + plotH
                    )
                )

                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(
                        width = 2.6.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Active point crosshair & dot
                val selCoord = coords[safeIndex]
                drawLine(
                    color = lineColor.copy(alpha = 0.4f),
                    start = Offset(selCoord.x, topPad),
                    end = Offset(selCoord.x, topPad + plotH),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawCircle(
                    color = lineColor.copy(alpha = 0.22f),
                    radius = 10.dp.toPx(),
                    center = selCoord
                )
                drawCircle(
                    color = surfaceColor,
                    radius = 5.5.dp.toPx(),
                    center = selCoord
                )
                drawCircle(
                    color = lineColor,
                    radius = 3.8.dp.toPx(),
                    center = selCoord
                )
            }
        }

        // X-axis day markers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 62.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val markers = listOf(1, 8, 15, 22, points.size).distinct()
            markers.forEach { day ->
                Text(
                    text = "$day",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = if (selectedPoint.dayOfMonth == day) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DailyExpenseBarChart(
    points: List<DailyBalancePoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    var selectedIndex by remember(points.size) {
        val peakIdx = points.indices.maxByOrNull { points[it].expense } ?: 0
        mutableIntStateOf(peakIdx)
    }
    val safeIndex = selectedIndex.coerceIn(0, points.lastIndex)
    val selectedPoint = points[safeIndex]

    val maxExpense = remember(points) { max(points.maxOf { it.expense }, 10_000L) }
    val expenseColor = MaterialTheme.colorScheme.error
    val mutedBar = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.testTag("daily_expense_bar_chart")) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pengeluaran ${selectedPoint.dateLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FinanceFormatters.formatRupiah(selectedPoint.expense),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = "${selectedPoint.transactionCount} transaksi",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .height(156.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = FinanceFormatters.formatCompactRupiah(maxExpense),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = FinanceFormatters.formatCompactRupiah(maxExpense / 2L),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "0",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(166.dp)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            if (size.width > 0 && points.isNotEmpty()) {
                                val idx = ((offset.x / size.width) * points.size).toInt()
                                selectedIndex = idx.coerceIn(0, points.lastIndex)
                            }
                        }
                    }
                    .pointerInput(points) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            if (size.width > 0 && points.isNotEmpty()) {
                                val idx = ((change.position.x / size.width) * points.size).toInt()
                                selectedIndex = idx.coerceIn(0, points.lastIndex)
                            }
                        }
                    }
            ) {
                val topPad = 8.dp.toPx()
                val bottomPad = 8.dp.toPx()
                val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                val slotW = size.width / points.size.coerceAtLeast(1)
                val barW = (slotW * 0.62f).coerceAtLeast(2.dp.toPx())

                points.forEachIndexed { idx, pt ->
                    val frac = (pt.expense.toFloat() / maxExpense.toFloat()).coerceIn(0f, 1f)
                    val barH = if (pt.expense > 0L) max(frac * plotH, 4.dp.toPx()) else 2.dp.toPx()
                    val left = idx * slotW + (slotW - barW) / 2f
                    val top = topPad + plotH - barH
                    val color = when {
                        idx == safeIndex -> expenseColor
                        pt.expense > 0L -> expenseColor.copy(alpha = 0.42f)
                        else -> mutedBar.copy(alpha = 0.55f)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 62.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val markers = listOf(1, 8, 15, 22, points.size).distinct()
            markers.forEach { day ->
                Text(
                    text = "$day",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CategoryDonutChart(
    categories: List<CategorySummary>,
    totalExpense: Long,
    modifier: Modifier = Modifier
) {
    val emptyRingColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_donut_chart"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(136.dp)
        ) {
            Canvas(modifier = Modifier.size(136.dp)) {
                val strokeW = 20.dp.toPx()
                val inset = strokeW / 2f
                val arcSize = Size(size.width - strokeW, size.height - strokeW)
                if (categories.isEmpty() || totalExpense <= 0L) {
                    drawArc(
                        color = emptyRingColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                } else {
                    var startAngle = -90f
                    categories.forEach { item ->
                        val sweep = (item.percentage * 360f).coerceAtLeast(3f)
                        drawArc(
                            color = CategoryVisuals.parseColor(item.colorHex),
                            startAngle = startAngle,
                            sweepAngle = (sweep - 2f).coerceAtLeast(1.5f),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                        startAngle += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total Keluar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = FinanceFormatters.formatCompactRupiah(totalExpense),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (categories.isEmpty()) {
                Text(
                    text = "Catat pengeluaran untuk melihat komposisi kategori.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                categories.take(4).forEach { cat ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(CategoryVisuals.parseColor(cat.colorHex))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = cat.categoryName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "${(cat.percentage * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
