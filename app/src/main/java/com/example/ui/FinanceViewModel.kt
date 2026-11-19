package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FinanceRepository
import com.example.model.Category
import com.example.model.MonthlyReportData
import com.example.model.PaymentMethod
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

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(
    private val repository: FinanceRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedYearMonth = MutableStateFlow(FinanceFormatters.currentYearMonth())
    val selectedYearMonth: StateFlow<String> = _selectedYearMonth.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

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

    fun emitMessage(message: String) {
        _snackbarEvents.tryEmit(message)
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
