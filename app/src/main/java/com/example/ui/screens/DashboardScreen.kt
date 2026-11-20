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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BudgetAlert
import com.example.model.BudgetAlertSeverity
import com.example.model.Category
import com.example.model.MonthlyReportData
import com.example.model.RecurringTemplate
import com.example.model.Transaction
import com.example.model.TransactionType
import com.example.ui.components.DailyBalanceLineChart
import com.example.ui.components.DailyExpenseBarChart
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.CategoryVisuals
import com.example.util.FinanceFormatters
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    report: MonthlyReportData,
    categories: List<Category>,
    budgetAlerts: List<BudgetAlert>,
    pendingRecurring: List<RecurringTemplate>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEditMonthConfig: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onApplyPendingRecurring: (List<RecurringTemplate>) -> Unit,
    onOpenPlanning: () -> Unit,
    modifier: Modifier = Modifier
) {
    var chartMode by remember { mutableIntStateOf(0) } // 0 = Saldo Harian, 1 = Keluar Harian
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month Switcher Header
        item(key = "month_switcher") {
            MonthSelectorBar(
                monthLabel = report.monthLabel,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth
            )
        }

        // Hero Emerald Ledger Balance Card
        item(key = "hero_balance") {
            HeroBalanceCard(
                report = report,
                onEditMonthConfig = onEditMonthConfig
            )
        }

        // In-App Budget Alerts Banner (80%+ Warning / 100%+ Exceeded)
        if (budgetAlerts.isNotEmpty()) {
            item(key = "budget_alerts_banner") {
                BudgetAlertsBanner(alerts = budgetAlerts)
            }
        }

        // Pending Recurring Bills / Salary Quick Apply Banner
        if (pendingRecurring.isNotEmpty()) {
            item(key = "pending_recurring_banner") {
                PendingRecurringBanner(
                    monthLabel = report.monthLabel,
                    pending = pendingRecurring,
                    onApplyAll = { onApplyPendingRecurring(pendingRecurring) },
                    onOpenPlanning = onOpenPlanning
                )
            }
        }

        // Monthly Budget Progress (if set)
        if (report.monthlyBudget > 0L) {
            item(key = "budget_card") {
                BudgetProgressCard(
                    title = stringResource(R.string.dashboard_budget_progress),
                    subtitle = report.monthLabel,
                    spent = report.totalExpense,
                    budget = report.monthlyBudget,
                    onEdit = onEditMonthConfig,
                    testTagName = "monthly_budget_card"
                )
            }
        }

        // Optional Weekly Budget Progress (if set)
        if (report.weeklyBudget > 0L) {
            item(key = "weekly_budget_card") {
                BudgetProgressCard(
                    title = stringResource(R.string.dashboard_weekly_budget_progress),
                    subtitle = report.currentWeekLabel,
                    spent = report.currentWeekExpense,
                    budget = report.weeklyBudget,
                    onEdit = onEditMonthConfig,
                    testTagName = "weekly_budget_card"
                )
            }
        }

        // Interactive Daily Balance / Daily Expense Visualization Card
        item(key = "daily_chart") {
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
                        Column {
                            Text(
                                text = stringResource(R.string.dashboard_daily_chart_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Rata-rata keluar ${FinanceFormatters.formatCompactRupiah(report.averageDailyExpense)}/hari",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Segmented Toggle: Saldo Harian vs Keluar Harian
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            listOf(
                                0 to stringResource(R.string.dashboard_chart_tab_balance),
                                1 to stringResource(R.string.dashboard_chart_tab_expense)
                            ).forEach { (idx, label) ->
                                val active = chartMode == idx
                                Surface(
                                    color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shape = RoundedCornerShape(9.dp),
                                    shadowElevation = if (active) 1.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .clickable { chartMode = idx }
                                        .testTag("chart_mode_tab_$idx")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (chartMode == 0) {
                        DailyBalanceLineChart(points = report.dailyPoints)
                    } else {
                        DailyExpenseBarChart(points = report.dailyPoints)
                    }
                }
            }
        }

        // Quick Insight Row (Top Category & Peak Day)
        if (report.expenseByCategory.isNotEmpty()) {
            item(key = "quick_insights") {
                val topCat = report.expenseByCategory.first()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InsightMiniCard(
                        title = "Terbesar",
                        primaryText = topCat.categoryName,
                        secondaryText = FinanceFormatters.formatRupiah(topCat.totalAmount),
                        accent = CategoryVisuals.parseColor(topCat.colorHex),
                        modifier = Modifier.weight(1f)
                    )
                    val peak = report.highestExpenseDay
                    InsightMiniCard(
                        title = "Hari Puncak",
                        primaryText = peak?.dateLabel ?: "-",
                        secondaryText = FinanceFormatters.formatRupiah(peak?.expense ?: 0L),
                        accent = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Recent Transactions Header
        item(key = "recent_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dashboard_recent_transactions),
                    style = MaterialTheme.typography.titleMedium
                )
                if (report.transactions.isNotEmpty()) {
                    TextButton(
                        onClick = onSeeAllTransactions,
                        modifier = Modifier.testTag("see_all_transactions_button")
                    ) {
                        Text(stringResource(R.string.dashboard_see_all))
                    }
                }
            }
        }

        if (report.transactions.isEmpty()) {
            item(key = "empty_state") {
                EmptyFinanceCard(onEditMonthConfig = onEditMonthConfig)
            }
        } else {
            items(
                items = report.transactions.take(6),
                key = { it.id.ifBlank { "${it.dateMillis}_${it.title}" } }
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

@Composable
fun MonthSelectorBar(
    monthLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.testTag("prev_month_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.cd_prev_month)
                )
            }
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = onNext,
                modifier = Modifier.testTag("next_month_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.cd_next_month)
                )
            }
        }
    }
}

@Composable
private fun HeroBalanceCard(
    report: MonthlyReportData,
    onEditMonthConfig: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(EmeraldPrimary, EmeraldDeep)
                )
            )
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.width * 0.36f,
                    center = Offset(size.width * 0.92f, size.height * 0.14f)
                )
                drawCircle(
                    color = GoldAccent.copy(alpha = 0.12f),
                    radius = size.width * 0.22f,
                    center = Offset(size.width * 0.10f, size.height * 0.95f)
                )
            }
            .padding(20.dp)
            .testTag("hero_balance_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dashboard_current_balance),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = FinanceFormatters.formatRupiah(report.closingBalance),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onEditMonthConfig)
                        .testTag("edit_opening_balance_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.dashboard_set_balance),
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Saldo & Batas",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            // Opening balance pill
            Text(
                text = "Saldo awal bulan: ${FinanceFormatters.formatRupiah(report.openingBalance)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = Color(0xFFD6F2E6)
            )

            // Income / Expense split row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34D399).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                tint = Color(0xFF6EE7B7),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.dashboard_total_income),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                            Text(
                                text = FinanceFormatters.formatCompactRupiah(report.totalIncome),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFCA5A5).copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = null,
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.dashboard_total_expense),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                            Text(
                                text = FinanceFormatters.formatCompactRupiah(report.totalExpense),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetProgressCard(
    title: String,
    subtitle: String,
    spent: Long,
    budget: Long,
    onEdit: () -> Unit,
    testTagName: String
) {
    val ratio = (spent.toFloat() / budget.toFloat().coerceAtLeast(1f))
    val overBudget = spent > budget
    val barColor = when {
        overBudget -> MaterialTheme.colorScheme.error
        ratio >= 0.8f -> GoldAccent
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onEdit)
            .testTag(testTagName)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "${(ratio * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                    color = barColor
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0f, 1f) },
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Terpakai ${FinanceFormatters.formatRupiah(spent)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Target ${FinanceFormatters.formatRupiah(budget)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InsightMiniCard(
    title: String,
    primaryText: String,
    secondaryText: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = primaryText,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TransactionRowCard(
    transaction: Transaction,
    category: Category?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpense = transaction.parsedType == TransactionType.EXPENSE
    val accentColor = CategoryVisuals.parseColor(
        category?.colorHex ?: if (isExpense) "#D94A38" else "#0E6B4E"
    )
    val icon = CategoryVisuals.iconForKey(category?.iconKey ?: "receipt_long")

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("transaction_card_${transaction.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = transaction.categoryName,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${transaction.categoryName} • ${transaction.parsedPaymentMethod.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!transaction.note.isNullOrBlank()) {
                    Text(
                        text = transaction.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                val signed = if (isExpense) -transaction.amount else transaction.amount
                Text(
                    text = FinanceFormatters.formatRupiah(signed, showPlus = !isExpense),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = FinanceFormatters.formatDateFull(transaction.dateMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyFinanceCard(onEditMonthConfig: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = stringResource(R.string.dashboard_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.dashboard_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onEditMonthConfig) {
                Text(stringResource(R.string.dashboard_set_balance))
            }
        }
    }
}

@Composable
private fun BudgetAlertsBanner(alerts: List<BudgetAlert>) {
    val hasExceeded = alerts.any { it.severity == BudgetAlertSeverity.EXCEEDED_100 }
    val borderColor = if (hasExceeded) MaterialTheme.colorScheme.error.copy(alpha = 0.45f) else GoldAccent.copy(alpha = 0.55f)
    val bgColor = if (hasExceeded) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
    } else {
        GoldAccent.copy(alpha = 0.12f)
    }
    val accentColor = if (hasExceeded) MaterialTheme.colorScheme.error else GoldAccent

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_budget_alerts_banner")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (hasExceeded) "Peringatan Batas Anggaran Terlampaui" else "Anggaran Mendekati Batas",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            alerts.take(3).forEach { alert ->
                val itemColor = if (alert.severity == BudgetAlertSeverity.EXCEEDED_100) {
                    MaterialTheme.colorScheme.error
                } else {
                    GoldAccent
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = alert.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${FinanceFormatters.formatRupiah(alert.spent)} / ${FinanceFormatters.formatRupiah(alert.limit)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = itemColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${alert.percentage}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = itemColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingRecurringBanner(
    monthLabel: String,
    pending: List<RecurringTemplate>,
    onApplyAll: () -> Unit,
    onOpenPlanning: () -> Unit
) {
    val totalExpense = pending.filter { it.parsedType == TransactionType.EXPENSE }.sumOf { it.amount }
    val totalIncome = pending.filter { it.parsedType == TransactionType.INCOME }.sumOf { it.amount }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_pending_recurring_banner")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EventRepeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${pending.size} transaksi rutin siap dicatat ($monthLabel)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val summaryParts = buildList {
                        if (totalExpense > 0L) add("Tagihan ${FinanceFormatters.formatCompactRupiah(totalExpense)}")
                        if (totalIncome > 0L) add("Masuk ${FinanceFormatters.formatCompactRupiah(totalIncome)}")
                    }
                    if (summaryParts.isNotEmpty()) {
                        Text(
                            text = summaryParts.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenPlanning,
                    modifier = Modifier.testTag("dashboard_manage_recurring_button")
                ) {
                    Text("Kelola Rencana")
                }
                Button(
                    onClick = onApplyAll,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("dashboard_apply_recurring_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.rec_apply_all))
                }
            }
        }
    }
}
