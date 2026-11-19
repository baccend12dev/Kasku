package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Category
import com.example.model.MonthlyReportData
import com.example.model.TransactionType
import com.example.ui.components.CategoryDonutChart
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.CategoryVisuals
import com.example.util.FinanceFormatters
import kotlin.math.roundToInt

@Composable
fun CategoriesScreen(
    report: MonthlyReportData,
    categories: List<Category>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddCategory: (TransactionType) -> Unit,
    onEditCategory: (Category) -> Unit,
    onInspectCategoryTransactions: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeType by remember { mutableStateOf(TransactionType.EXPENSE) }

    val summaryMap = remember(report, activeType) {
        val list = if (activeType == TransactionType.EXPENSE) report.expenseByCategory else report.incomeByCategory
        list.associateBy { it.categoryId }
    }

    val filteredCategories = remember(categories, activeType, summaryMap) {
        categories.filter { it.parsedType == activeType }
            .sortedByDescending { summaryMap[it.id]?.totalAmount ?: 0L }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "month_bar") {
            MonthSelectorBar(
                monthLabel = report.monthLabel,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth
            )
        }

        // Donut Breakdown Card
        item(key = "donut_card") {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Distribusi Pengeluaran",
                            style = MaterialTheme.typography.titleMedium
                        )
                        FilledTonalButton(
                            onClick = { onAddCategory(activeType) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("add_category_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Kategori Baru", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    CategoryDonutChart(
                        categories = report.expenseByCategory,
                        totalExpense = report.totalExpense
                    )
                }
            }
        }

        // Toggle Pengeluaran / Pemasukan Categories
        item(key = "type_tabs") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    TransactionType.entries.forEach { option ->
                        val active = activeType == option
                        Surface(
                            color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            shadowElevation = if (active) 1.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { activeType = option }
                                .testTag("cat_tab_${option.name.lowercase()}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = option.labelId,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        items(
            items = filteredCategories,
            key = { it.id }
        ) { cat ->
            val summary = summaryMap[cat.id]
            val spent = summary?.totalAmount ?: 0L
            val txCount = summary?.transactionCount ?: 0
            val sharePct = summary?.percentage ?: 0f
            val accent = CategoryVisuals.parseColor(cat.colorHex)

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onInspectCategoryTransactions(cat.id) }
                    .testTag("category_card_${cat.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CategoryVisuals.iconForKey(cat.iconKey),
                                contentDescription = cat.name,
                                tint = accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "$txCount transaksi • ${(sharePct * 100f).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = FinanceFormatters.formatRupiah(spent),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (spent > 0L) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onEditCategory(cat) },
                            modifier = Modifier.testTag("edit_category_${cat.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.cat_edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (cat.budgetLimit > 0L && activeType == TransactionType.EXPENSE) {
                        val budgetRatio = (spent.toFloat() / cat.budgetLimit.toFloat()).coerceIn(0f, 1f)
                        val exceeded = spent > cat.budgetLimit
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { budgetRatio },
                            color = if (exceeded) MaterialTheme.colorScheme.error else accent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (exceeded) "Melebihi batas anggaran" else "Batas bulanan",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = FinanceFormatters.formatRupiah(cat.budgetLimit),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
