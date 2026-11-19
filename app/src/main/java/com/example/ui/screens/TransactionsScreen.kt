package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Category
import com.example.model.MonthlyReportData
import com.example.model.Transaction
import com.example.model.TransactionType
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.FinanceFormatters

@Composable
fun TransactionsScreen(
    report: MonthlyReportData,
    categories: List<Category>,
    selectedCategoryId: String?,
    onSelectCategoryFilter: (String?) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<TransactionType?>(null) }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    val filteredTransactions = remember(report.transactions, searchQuery, typeFilter, selectedCategoryId) {
        val q = searchQuery.trim().lowercase()
        report.transactions.filter { tx ->
            val matchType = typeFilter == null || tx.parsedType == typeFilter
            val matchCat = selectedCategoryId == null || tx.categoryId == selectedCategoryId
            val matchQuery = q.isEmpty() ||
                tx.title.lowercase().contains(q) ||
                tx.categoryName.lowercase().contains(q) ||
                (tx.note?.lowercase()?.contains(q) == true)
            matchType && matchCat && matchQuery
        }
    }

    val groupedByDay = remember(filteredTransactions) {
        filteredTransactions.groupBy { it.dayOfMonth }
            .toList()
            .sortedByDescending { it.first }
    }

    val filteredExpenseTotal = remember(filteredTransactions) {
        filteredTransactions.filter { it.parsedType == TransactionType.EXPENSE }.sumOf { it.amount }
    }
    val filteredIncomeTotal = remember(filteredTransactions) {
        filteredTransactions.filter { it.parsedType == TransactionType.INCOME }.sumOf { it.amount }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "month_bar") {
            MonthSelectorBar(
                monthLabel = report.monthLabel,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth
            )
        }

        item(key = "search_bar") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.tx_search_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_clear_search)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_search_input")
            )
        }

        // Type & Category Filter Row
        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = typeFilter == null,
                        onClick = { typeFilter = null },
                        label = { Text(stringResource(R.string.tx_filter_all)) },
                        modifier = Modifier.testTag("filter_type_all")
                    )
                    FilterChip(
                        selected = typeFilter == TransactionType.EXPENSE,
                        onClick = { typeFilter = TransactionType.EXPENSE },
                        label = { Text(stringResource(R.string.tx_type_expense)) },
                        modifier = Modifier.testTag("filter_type_expense")
                    )
                    FilterChip(
                        selected = typeFilter == TransactionType.INCOME,
                        onClick = { typeFilter = TransactionType.INCOME },
                        label = { Text(stringResource(R.string.tx_type_income)) },
                        modifier = Modifier.testTag("filter_type_income")
                    )
                }

                if (categories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { onSelectCategoryFilter(null) },
                            label = { Text("Semua Kategori") }
                        )
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = {
                                    onSelectCategoryFilter(
                                        if (selectedCategoryId == cat.id) null else cat.id
                                    )
                                },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                }
            }
        }

        // Filtered summary banner
        item(key = "filtered_summary") {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${filteredTransactions.size} transaksi tampil",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Keluar: ${FinanceFormatters.formatRupiah(filteredExpenseTotal)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (filteredIncomeTotal > 0L) {
                        Text(
                            text = "+${FinanceFormatters.formatCompactRupiah(filteredIncomeTotal)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        if (groupedByDay.isEmpty()) {
            item(key = "no_matches") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Tidak ada transaksi ditemukan",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Coba ubah filter atau ketuk tombol + untuk mencatat pengeluaran baru.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            groupedByDay.forEach { (day, dayTxs) ->
                val dayOutflow = dayTxs.filter { it.parsedType == TransactionType.EXPENSE }.sumOf { it.amount }
                val firstDate = dayTxs.firstOrNull()?.dateMillis ?: 0L
                item(key = "day_header_$day") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FinanceFormatters.formatDateWithDay(firstDate),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (dayOutflow > 0L) {
                            Text(
                                text = "-${FinanceFormatters.formatRupiah(dayOutflow)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                items(
                    items = dayTxs,
                    key = { it.id.ifBlank { "${it.dateMillis}_${it.title}_${it.amount}" } }
                ) { tx ->
                    TransactionRowCard(
                        transaction = tx,
                        category = categoryMap[tx.categoryId],
                        onClick = { onTransactionClick(tx) }
                    )
                }
            }
        }
    }
}
