package com.example.util

import com.example.model.Category
import com.example.model.CategorySummary
import com.example.model.DailyBalancePoint
import com.example.model.MonthConfig
import com.example.model.MonthlyReportData
import com.example.model.Transaction
import com.example.model.TransactionType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object FinanceFormatters {
    private val localeId = Locale("in", "ID")

    private val indonesianMonths = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    private val shortMonths = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )

    fun formatRupiah(amount: Long, showPlus: Boolean = false): String {
        val formatter = NumberFormat.getNumberInstance(localeId).apply {
            maximumFractionDigits = 0
        }
        val formattedAbs = formatter.format(abs(amount))
        return when {
            amount < 0 -> "-Rp $formattedAbs"
            showPlus && amount > 0 -> "+Rp $formattedAbs"
            else -> "Rp $formattedAbs"
        }
    }

    fun formatCompactRupiah(amount: Long): String {
        val sign = if (amount < 0) "-" else ""
        val absVal = abs(amount).toDouble()
        return when {
            absVal >= 1_000_000_000.0 -> "${sign}${String.format(localeId, "%.1f", absVal / 1_000_000_000.0)} M"
            absVal >= 1_000_000.0 -> "${sign}${String.format(localeId, "%.1f", absVal / 1_000_000.0)} jt"
            absVal >= 1_000.0 -> "${sign}${String.format(localeId, "%.0f", absVal / 1_000.0)} rb"
            else -> "${sign}${abs(amount)}"
        }
    }

    fun currentYearMonth(): String {
        val cal = Calendar.getInstance()
        return formatYearMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun formatYearMonth(year: Int, month1Based: Int): String =
        String.format(Locale.US, "%04d-%02d", year, month1Based.coerceIn(1, 12))

    fun parseYearMonth(yearMonth: String): Pair<Int, Int> {
        val parts = yearMonth.split("-")
        val now = Calendar.getInstance()
        val year = parts.getOrNull(0)?.toIntOrNull() ?: now.get(Calendar.YEAR)
        val month = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: (now.get(Calendar.MONTH) + 1)
        return year to month
    }

    fun shiftYearMonth(yearMonth: String, deltaMonths: Int): String {
        val (year, month) = parseYearMonth(yearMonth)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, deltaMonths)
        }
        return formatYearMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun formatMonthLabel(yearMonth: String): String {
        val (year, month) = parseYearMonth(yearMonth)
        return "${indonesianMonths[(month - 1).coerceIn(0, 11)]} $year"
    }

    fun formatShortMonth(yearMonth: String): String {
        val (_, month) = parseYearMonth(yearMonth)
        return shortMonths[(month - 1).coerceIn(0, 11)]
    }

    fun daysInMonth(yearMonth: String): Int {
        val (year, month) = parseYearMonth(yearMonth)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun extractYearMonthAndDay(dateMillis: Long): Pair<String, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val ym = formatYearMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return ym to day
    }

    fun defaultDateMillisForMonth(yearMonth: String): Long {
        val (year, month) = parseYearMonth(yearMonth)
        val now = Calendar.getInstance()
        val maxDay = daysInMonth(yearMonth)
        val targetDay = if (now.get(Calendar.YEAR) == year && (now.get(Calendar.MONTH) + 1) == month) {
            now.get(Calendar.DAY_OF_MONTH)
        } else {
            1.coerceAtMost(maxDay)
        }
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, targetDay)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun formatDateFull(dateMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", localeId)
        return sdf.format(Date(dateMillis))
    }

    fun formatDateWithDay(dateMillis: Long): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy", localeId)
        return sdf.format(Date(dateMillis))
    }

    fun formatGeneratedTimestamp(): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", localeId)
        return sdf.format(Date())
    }

    fun buildMonthlyReport(
        yearMonth: String,
        transactions: List<Transaction>,
        categories: List<Category>,
        monthConfig: MonthConfig?
    ): MonthlyReportData {
        val openingBalance = monthConfig?.openingBalance ?: 0L
        val monthlyBudget = monthConfig?.monthlyBudget ?: 0L
        val totalDays = daysInMonth(yearMonth)
        val shortMonth = formatShortMonth(yearMonth)

        val sortedTransactions = transactions.sortedWith(
            compareByDescending<Transaction> { it.dateMillis }
                .thenByDescending { it.createdAt?.seconds ?: 0L }
        )

        val byDay = sortedTransactions.groupBy { it.dayOfMonth.coerceIn(1, totalDays) }
        val dailyPoints = ArrayList<DailyBalancePoint>(totalDays)
        var runningBalance = openingBalance
        var totalIncome = 0L
        var totalExpense = 0L

        for (day in 1..totalDays) {
            val dayTxs = byDay[day].orEmpty()
            val dayIncome = dayTxs.filter { it.parsedType == TransactionType.INCOME }.sumOf { it.amount }
            val dayExpense = dayTxs.filter { it.parsedType == TransactionType.EXPENSE }.sumOf { it.amount }
            totalIncome += dayIncome
            totalExpense += dayExpense
            runningBalance += dayIncome - dayExpense
            dailyPoints.add(
                DailyBalancePoint(
                    dayOfMonth = day,
                    dateLabel = "$day $shortMonth",
                    income = dayIncome,
                    expense = dayExpense,
                    closingBalance = runningBalance,
                    transactionCount = dayTxs.size
                )
            )
        }

        val categoryMap = categories.associateBy { it.id }

        fun summarize(type: TransactionType, grandTotal: Long): List<CategorySummary> {
            return sortedTransactions
                .filter { it.parsedType == type }
                .groupBy { it.categoryId }
                .map { (catId, txs) ->
                    val cat = categoryMap[catId]
                    val sum = txs.sumOf { it.amount }
                    CategorySummary(
                        categoryId = catId,
                        categoryName = cat?.name ?: txs.firstOrNull()?.categoryName.orEmpty().ifBlank { "Lainnya" },
                        iconKey = cat?.iconKey ?: "category",
                        colorHex = cat?.colorHex ?: if (type == TransactionType.EXPENSE) "#475569" else "#0E6B4E",
                        totalAmount = sum,
                        transactionCount = txs.size,
                        percentage = if (grandTotal > 0L) (sum.toFloat() / grandTotal.toFloat()) else 0f,
                        budgetLimit = cat?.budgetLimit ?: 0L
                    )
                }
                .sortedByDescending { it.totalAmount }
        }

        val nowYm = currentYearMonth()
        val elapsedDays = if (yearMonth == nowYm) {
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        } else {
            totalDays
        }

        val highestExpenseDay = dailyPoints
            .filter { it.expense > 0L }
            .maxByOrNull { it.expense }

        return MonthlyReportData(
            yearMonth = yearMonth,
            monthLabel = formatMonthLabel(yearMonth),
            openingBalance = openingBalance,
            monthlyBudget = monthlyBudget,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            closingBalance = runningBalance,
            dailyPoints = dailyPoints,
            expenseByCategory = summarize(TransactionType.EXPENSE, totalExpense),
            incomeByCategory = summarize(TransactionType.INCOME, totalIncome),
            transactions = sortedTransactions,
            averageDailyExpense = if (elapsedDays > 0) totalExpense / elapsedDays else 0L,
            highestExpenseDay = highestExpenseDay
        )
    }
}
