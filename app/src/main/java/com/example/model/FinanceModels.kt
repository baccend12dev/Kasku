package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TransactionType(val labelId: String) {
    EXPENSE("Pengeluaran"),
    INCOME("Pemasukan");

    companion object {
        fun fromRaw(raw: String?): TransactionType =
            entries.firstOrNull { it.name == raw } ?: EXPENSE
    }
}

enum class PaymentMethod(val label: String) {
    TUNAI("Tunai"),
    TRANSFER("Transfer Bank"),
    EWALLET("E-Wallet"),
    KARTU("Kartu");

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name == raw } ?: TUNAI
    }
}

@Keep
@IgnoreExtraProperties
data class Transaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val amount: Long = 0L,
    val type: String = TransactionType.EXPENSE.name,
    val categoryId: String = "",
    val categoryName: String = "",
    val paymentMethod: String = PaymentMethod.TUNAI.name,
    val note: String? = null,
    val dateMillis: Long = 0L,
    val yearMonth: String = "",
    val dayOfMonth: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: TransactionType get() = TransactionType.fromRaw(type)
    val parsedPaymentMethod: PaymentMethod get() = PaymentMethod.fromRaw(paymentMethod)
}

@Keep
@IgnoreExtraProperties
data class Category(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val type: String = TransactionType.EXPENSE.name,
    val iconKey: String = "category",
    val colorHex: String = "#0E6B4E",
    val budgetLimit: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: TransactionType get() = TransactionType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class MonthConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val yearMonth: String = "",
    val openingBalance: Long = 0L,
    val monthlyBudget: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DailyBalancePoint(
    val dayOfMonth: Int,
    val dateLabel: String,
    val income: Long,
    val expense: Long,
    val closingBalance: Long,
    val transactionCount: Int
)

data class CategorySummary(
    val categoryId: String,
    val categoryName: String,
    val iconKey: String,
    val colorHex: String,
    val totalAmount: Long,
    val transactionCount: Int,
    val percentage: Float,
    val budgetLimit: Long
)

data class MonthlyReportData(
    val yearMonth: String,
    val monthLabel: String,
    val openingBalance: Long,
    val monthlyBudget: Long,
    val totalIncome: Long,
    val totalExpense: Long,
    val closingBalance: Long,
    val dailyPoints: List<DailyBalancePoint>,
    val expenseByCategory: List<CategorySummary>,
    val incomeByCategory: List<CategorySummary>,
    val transactions: List<Transaction>,
    val averageDailyExpense: Long,
    val highestExpenseDay: DailyBalancePoint?
)

data class DefaultCategorySeed(
    val id: String,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val colorHex: String
)

@Keep
@IgnoreExtraProperties
data class SavingGoal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val targetAmount: Long = 0L,
    val currentAmount: Long = 0L,
    val targetDateMillis: Long = 0L,
    val iconKey: String = "savings",
    val colorHex: String = "#0E6B4E",
    val note: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val progressFraction: Float
        get() = if (targetAmount > 0L) (currentAmount.toFloat() / targetAmount.toFloat()).coerceIn(0f, 1f) else 0f

    val remainingAmount: Long
        get() = (targetAmount - currentAmount).coerceAtLeast(0L)

    val reachedTarget: Boolean
        get() = targetAmount > 0L && currentAmount >= targetAmount
}

@Keep
@IgnoreExtraProperties
data class RecurringTemplate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val amount: Long = 0L,
    val type: String = TransactionType.EXPENSE.name,
    val categoryId: String = "",
    val categoryName: String = "",
    val paymentMethod: String = PaymentMethod.TRANSFER.name,
    val dayOfMonth: Int = 1,
    val active: Boolean = true,
    val lastAppliedYearMonth: String? = null,
    val note: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: TransactionType get() = TransactionType.fromRaw(type)
    val parsedPaymentMethod: PaymentMethod get() = PaymentMethod.fromRaw(paymentMethod)
}

enum class BudgetAlertSeverity {
    WARNING_80,
    EXCEEDED_100
}

data class BudgetAlert(
    val key: String,
    val title: String,
    val spent: Long,
    val limit: Long,
    val percentage: Int,
    val severity: BudgetAlertSeverity
)

data class ReminderPreferences(
    val dailyReminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val budgetAlertsEnabled: Boolean = true
)

object DefaultCategories {
    val seeds: List<DefaultCategorySeed> = listOf(
        DefaultCategorySeed("cat_makan", "Makan & Minum", TransactionType.EXPENSE, "restaurant", "#EA580C"),
        DefaultCategorySeed("cat_transport", "Transportasi", TransactionType.EXPENSE, "directions_car", "#0284C7"),
        DefaultCategorySeed("cat_belanja", "Belanja", TransactionType.EXPENSE, "shopping_bag", "#E11D48"),
        DefaultCategorySeed("cat_tagihan", "Tagihan & Utilitas", TransactionType.EXPENSE, "receipt_long", "#D97706"),
        DefaultCategorySeed("cat_kesehatan", "Kesehatan", TransactionType.EXPENSE, "medical_services", "#0D9488"),
        DefaultCategorySeed("cat_hiburan", "Hiburan", TransactionType.EXPENSE, "movie", "#7C3AED"),
        DefaultCategorySeed("cat_pendidikan", "Pendidikan", TransactionType.EXPENSE, "school", "#4F46E5"),
        DefaultCategorySeed("cat_lainnya", "Lainnya", TransactionType.EXPENSE, "more_horiz", "#475569"),
        DefaultCategorySeed("cat_gaji", "Gaji", TransactionType.INCOME, "payments", "#0E6B4E"),
        DefaultCategorySeed("cat_bonus", "Bonus & Tambahan", TransactionType.INCOME, "savings", "#10B981")
    )
}
