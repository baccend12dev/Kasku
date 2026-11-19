package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FinanceRepository
import com.example.data.PlanningRepository
import com.example.model.BudgetAlert
import com.example.model.BudgetAlertSeverity
import com.example.model.Category
import com.example.model.MonthlyReportData
import com.example.model.PaymentMethod
import com.example.model.RecurringTemplate
import com.example.model.SavingGoal
import com.example.model.TransactionType
import com.example.model.UiState
import com.example.util.FinanceFormatters
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(
    private val repository: FinanceRepository,
    private val planningRepository: PlanningRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedYearMonth = MutableStateFlow(FinanceFormatters.currentYearMonth())
    val selectedYearMonth: StateFlow<String> = _selectedYearMonth.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _budgetAlertEvents = MutableSharedFlow<BudgetAlert>(extraBufferCapacity = 4)
    val budgetAlertEvents: SharedFlow<BudgetAlert> = _budgetAlertEvents.asSharedFlow()

    val categoriesState: StateFlow<UiState<List<Category>>> = repository.observeCategories(currentUserId)
        .map<List<Category>, UiState<List<Category>>> { list ->
            UiState.Success(list.sortedBy { it.name.lowercase() })
        }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing categories", root)
            emit(UiState.Error(root.localizedMessage ?: "Gagal memuat kategori."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val reportState: StateFlow<UiState<MonthlyReportData>> = _selectedYearMonth
        .flatMapLatest { yearMonth ->
            combine(
                repository.observeTransactionsForMonth(currentUserId, yearMonth),
                repository.observeCategories(currentUserId),
                repository.observeMonthConfig(currentUserId, yearMonth)
            ) { transactions, categories, monthConfig ->
                FinanceFormatters.buildMonthlyReport(
                    yearMonth = yearMonth,
                    transactions = transactions,
                    categories = categories,
                    monthConfig = monthConfig
                )
            }
        }
        .map<MonthlyReportData, UiState<MonthlyReportData>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing monthly report", root)
            emit(UiState.Error(root.localizedMessage ?: "Gagal memuat data keuangan."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val savingGoalsState: StateFlow<UiState<List<SavingGoal>>> =
        planningRepository.observeSavingGoals(currentUserId)
            .map<List<SavingGoal>, UiState<List<SavingGoal>>> { list ->
                UiState.Success(
                    list.sortedWith(
                        compareBy<SavingGoal> { it.reachedTarget }
                            .thenBy { it.targetDateMillis }
                    )
                )
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing saving goals", root)
                emit(UiState.Error(root.localizedMessage ?: "Gagal memuat target tabungan."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val recurringTemplatesState: StateFlow<UiState<List<RecurringTemplate>>> =
        planningRepository.observeRecurringTemplates(currentUserId)
            .map<List<RecurringTemplate>, UiState<List<RecurringTemplate>>> { list ->
                UiState.Success(
                    list.sortedWith(
                        compareByDescending<RecurringTemplate> { it.active }
                            .thenBy { it.dayOfMonth }
                    )
                )
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing recurring templates", root)
                emit(UiState.Error(root.localizedMessage ?: "Gagal memuat transaksi rutin."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val budgetAlerts: StateFlow<List<BudgetAlert>> = reportState
        .map { state ->
            val report = (state as? UiState.Success)?.data ?: return@map emptyList()
            computeBudgetAlerts(report)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val pendingRecurringForMonth: StateFlow<List<RecurringTemplate>> =
        combine(recurringTemplatesState, _selectedYearMonth) { recState, ym ->
            val list = (recState as? UiState.Success)?.data.orEmpty()
            list.filter { it.active && it.lastAppliedYearMonth != ym }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.seedDefaultCategoriesIfEmpty(currentUserId)
                .onFailure { Log.w(TAG, "Default category seed skipped/failed", it) }
        }
    }

    fun selectPreviousMonth() {
        _selectedYearMonth.value = FinanceFormatters.shiftYearMonth(_selectedYearMonth.value, -1)
    }

    fun selectNextMonth() {
        _selectedYearMonth.value = FinanceFormatters.shiftYearMonth(_selectedYearMonth.value, 1)
    }

    fun selectCurrentMonth() {
        _selectedYearMonth.value = FinanceFormatters.currentYearMonth()
    }

    fun setCategoryFilter(categoryId: String?) {
        _selectedCategoryFilter.value = categoryId
    }

    fun saveTransaction(
        editingId: String?,
        title: String,
        amount: Long,
        type: TransactionType,
        categoryId: String,
        categoryName: String,
        paymentMethod: PaymentMethod,
        note: String?,
        dateMillis: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val previousReport = (reportState.value as? UiState.Success)?.data
            val result = if (editingId == null) {
                repository.addTransaction(
                    userId = currentUserId,
                    title = title,
                    amount = amount,
                    type = type,
                    categoryId = categoryId,
                    categoryName = categoryName,
                    paymentMethod = paymentMethod,
                    note = note,
                    dateMillis = dateMillis
                )
            } else {
                repository.updateTransaction(
                    userId = currentUserId,
                    transactionId = editingId,
                    title = title,
                    amount = amount,
                    type = type,
                    categoryId = categoryId,
                    categoryName = categoryName,
                    paymentMethod = paymentMethod,
                    note = note,
                    dateMillis = dateMillis
                )
            }
            result.onSuccess {
                val (txYearMonth, _) = FinanceFormatters.extractYearMonthAndDay(dateMillis)
                if (txYearMonth != _selectedYearMonth.value) {
                    _selectedYearMonth.value = txYearMonth
                } else if (type == TransactionType.EXPENSE && editingId == null && previousReport != null) {
                    evaluatePostExpenseAlert(previousReport, categoryId, categoryName, amount)
                }
                _snackbarEvents.tryEmit(
                    if (editingId == null) "Transaksi berhasil dicatat" else "Transaksi diperbarui"
                )
                onComplete()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Gagal menyimpan transaksi")
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(currentUserId, transactionId)
                .onSuccess { _snackbarEvents.tryEmit("Transaksi dihapus") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Gagal menghapus transaksi") }
        }
    }

    fun saveCategory(
        editingId: String?,
        name: String,
        type: TransactionType,
        iconKey: String,
        colorHex: String,
        budgetLimit: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = if (editingId == null) {
                repository.addCategory(currentUserId, name, type, iconKey, colorHex, budgetLimit)
            } else {
                repository.updateCategory(currentUserId, editingId, name, type, iconKey, colorHex, budgetLimit)
            }
            result.onSuccess {
                _snackbarEvents.tryEmit(
                    if (editingId == null) "Kategori ditambahkan" else "Kategori diperbarui"
                )
                onComplete()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Gagal menyimpan kategori")
            }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            repository.deleteCategory(currentUserId, categoryId)
                .onSuccess {
                    if (_selectedCategoryFilter.value == categoryId) {
                        _selectedCategoryFilter.value = null
                    }
                    _snackbarEvents.tryEmit("Kategori dihapus")
                }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Gagal menghapus kategori") }
        }
    }

    fun saveMonthConfig(openingBalance: Long, monthlyBudget: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.upsertMonthConfig(
                userId = currentUserId,
                yearMonth = _selectedYearMonth.value,
                openingBalance = openingBalance,
                monthlyBudget = monthlyBudget
            ).onSuccess {
                _snackbarEvents.tryEmit("Saldo awal & anggaran disimpan")
                onComplete()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Gagal menyimpan pengaturan bulan")
            }
        }
    }

    // --- SAVING GOALS ACTIONS ---
    fun saveSavingGoal(
        editingId: String?,
        name: String,
        targetAmount: Long,
        currentAmount: Long,
        targetDateMillis: Long,
        iconKey: String,
        colorHex: String,
        note: String?,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = if (editingId == null) {
                planningRepository.addSavingGoal(
                    userId = currentUserId,
                    name = name,
                    targetAmount = targetAmount,
                    currentAmount = currentAmount,
                    targetDateMillis = targetDateMillis,
                    iconKey = iconKey,
                    colorHex = colorHex,
                    note = note
                )
            } else {
                planningRepository.updateSavingGoal(
                    userId = currentUserId,
                    goalId = editingId,
                    name = name,
                    targetAmount = targetAmount,
                    currentAmount = currentAmount,
                    targetDateMillis = targetDateMillis,
                    iconKey = iconKey,
                    colorHex = colorHex,
                    note = note
                )
            }
            result.onSuccess {
                _snackbarEvents.tryEmit(
                    if (editingId == null) "Target tabungan dibuat" else "Target tabungan diperbarui"
                )
                onComplete()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Gagal menyimpan target tabungan")
            }
        }
    }

    fun topUpSavingGoal(
        goal: SavingGoal,
        depositAmount: Long,
        recordAsExpense: Boolean,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            planningRepository.topUpSavingGoal(
                userId = currentUserId,
                goal = goal,
                depositAmount = depositAmount,
                recordAsExpense = recordAsExpense
            ).onSuccess {
                _snackbarEvents.tryEmit(
                    "Setoran ${FinanceFormatters.formatRupiah(depositAmount)} ke ${goal.name} berhasil!"
                )
                onComplete()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Gagal menambah setoran tabungan")
            }
        }
    }

    fun deleteSavingGoal(goalId: String) {
        viewModelScope.launch {
            planningRepository.deleteSavingGoal(currentUserId, goalId)
                .onSuccess { _snackbarEvents.tryEmit("Target tabungan dihapus") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Gagal menghapus target tabungan") }
        }
    }

    // --- RECURRING TEMPLATES ACTIONS ---
    fun saveRecurringTemplate(
        editingId: String?,
        title: String,
        amount: Long,
        type: TransactionType,
        categoryId: String,
        categoryName: String,
        paymentMethod: PaymentMethod,
        dayOfMonth: Int,
        active: Boolean,
        note: String?,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = if (editingId == null) {
                planningRepository.addRecurringTemplate(
                    userId = currentUserId,
                    title = title,
                    amount = amount,
                    type = type,
                    categoryId = categoryId,
                    categoryName = categoryName,
                    paymentMethod = paymentMethod,
                    dayOfMonth = dayOfMonth,
                    note = note
                )
            } else {
                planningRepository.updateRecurringTemplate(
                    userId = currentUserId,
                    templateId = editingId,
                    title = title,
                    amount = amount,
                    type = type,
                    categoryId = categoryId,
                    categoryName = categoryName,
                    paymentMethod = paymentMethod,
                    dayOfMonth = dayOfMonth,
                    active = active,
                    note = note
                )
            }
            result.onSuccess {
                _snackbarEvents.tryEmit(
                    if (editingId == null) "Transaksi rutin ditambahkan" else "Transaksi rutin diperbarui"
                )
                onComplete()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Gagal menyimpan transaksi rutin")
            }
        }
    }

    fun toggleRecurringActive(templateId: String, active: Boolean) {
        viewModelScope.launch {
            planningRepository.setRecurringActive(currentUserId, templateId, active)
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Gagal mengubah status rutin") }
        }
    }

    fun deleteRecurringTemplate(templateId: String) {
        viewModelScope.launch {
            planningRepository.deleteRecurringTemplate(currentUserId, templateId)
                .onSuccess { _snackbarEvents.tryEmit("Transaksi rutin dihapus") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Gagal menghapus transaksi rutin") }
        }
    }

    fun applyRecurringTemplates(templates: List<RecurringTemplate>) {
        viewModelScope.launch {
            val ym = _selectedYearMonth.value
            planningRepository.applyRecurringTemplatesForMonth(currentUserId, ym, templates)
                .onSuccess { count ->
                    if (count > 0) {
                        _snackbarEvents.tryEmit("$count transaksi rutin dicatat ke ${FinanceFormatters.formatMonthLabel(ym)}")
                    } else {
                        _snackbarEvents.tryEmit("Semua jadwal rutin bulan ini sudah tercatat")
                    }
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Gagal menerapkan transaksi rutin")
                }
        }
    }

    fun emitMessage(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    private fun computeBudgetAlerts(report: MonthlyReportData): List<BudgetAlert> {
        val alerts = mutableListOf<BudgetAlert>()
        if (report.monthlyBudget > 0L) {
            val pct = ((report.totalExpense.toDouble() / report.monthlyBudget.toDouble()) * 100.0).roundToInt()
            if (pct >= 80) {
                alerts.add(
                    BudgetAlert(
                        key = "monthly_${report.yearMonth}",
                        title = "Anggaran Bulanan",
                        spent = report.totalExpense,
                        limit = report.monthlyBudget,
                        percentage = pct,
                        severity = if (pct >= 100) BudgetAlertSeverity.EXCEEDED_100 else BudgetAlertSeverity.WARNING_80
                    )
                )
            }
        }
        report.expenseByCategory.forEach { cat ->
            if (cat.budgetLimit > 0L) {
                val pct = ((cat.totalAmount.toDouble() / cat.budgetLimit.toDouble()) * 100.0).roundToInt()
                if (pct >= 80) {
                    alerts.add(
                        BudgetAlert(
                            key = "cat_${cat.categoryId}_${report.yearMonth}",
                            title = cat.categoryName,
                            spent = cat.totalAmount,
                            limit = cat.budgetLimit,
                            percentage = pct,
                            severity = if (pct >= 100) BudgetAlertSeverity.EXCEEDED_100 else BudgetAlertSeverity.WARNING_80
                        )
                    )
                }
            }
        }
        return alerts.sortedByDescending { it.percentage }
    }

    private fun evaluatePostExpenseAlert(
        previousReport: MonthlyReportData,
        categoryId: String,
        categoryName: String,
        addedAmount: Long
    ) {
        if (previousReport.monthlyBudget > 0L) {
            val newMonthlySpent = previousReport.totalExpense + addedAmount
            val newPct = ((newMonthlySpent.toDouble() / previousReport.monthlyBudget.toDouble()) * 100.0).roundToInt()
            val oldPct = ((previousReport.totalExpense.toDouble() / previousReport.monthlyBudget.toDouble()) * 100.0).roundToInt()
            if ((oldPct < 80 && newPct >= 80) || (oldPct < 100 && newPct >= 100) || newPct >= 100) {
                _budgetAlertEvents.tryEmit(
                    BudgetAlert(
                        key = "monthly_${previousReport.yearMonth}",
                        title = "Anggaran Bulanan",
                        spent = newMonthlySpent,
                        limit = previousReport.monthlyBudget,
                        percentage = newPct,
                        severity = if (newPct >= 100) BudgetAlertSeverity.EXCEEDED_100 else BudgetAlertSeverity.WARNING_80
                    )
                )
            }
        }

        val catFromList = (categoriesState.value as? UiState.Success)?.data?.firstOrNull { it.id == categoryId }
        val catLimit = catFromList?.budgetLimit ?: 0L
        if (catLimit > 0L) {
            val prevCatSpent = previousReport.expenseByCategory.firstOrNull { it.categoryId == categoryId }?.totalAmount ?: 0L
            val newCatSpent = prevCatSpent + addedAmount
            val newPct = ((newCatSpent.toDouble() / catLimit.toDouble()) * 100.0).roundToInt()
            if (newPct >= 80) {
                _budgetAlertEvents.tryEmit(
                    BudgetAlert(
                        key = "cat_${categoryId}_${previousReport.yearMonth}",
                        title = categoryName,
                        spent = newCatSpent,
                        limit = catLimit,
                        percentage = newPct,
                        severity = if (newPct >= 100) BudgetAlertSeverity.EXCEEDED_100 else BudgetAlertSeverity.WARNING_80
                    )
                )
            }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "FinanceVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
