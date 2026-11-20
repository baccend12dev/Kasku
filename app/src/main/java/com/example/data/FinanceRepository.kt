package com.example.data

import android.content.Context
import com.example.R
import com.example.model.Category
import com.example.model.DefaultCategories
import com.example.model.MonthConfig
import com.example.model.PaymentMethod
import com.example.model.Transaction
import com.example.model.TransactionType
import com.example.util.FinanceFormatters
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FinanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireMatchingUserId(expectedUid: String): String {
        val currentUid = auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
        check(currentUid == expectedUid) { "Sesi pengguna berubah; operasi dibatalkan." }
        return currentUid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeTransactionsForMonth(userId: String, yearMonth: String): Flow<List<Transaction>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/transactions"
        emitAll(
            userDoc(userId).collection("transactions")
                .whereEqualTo("yearMonth", yearMonth)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Transaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCategories(userId: String): Flow<List<Category>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/categories"
        emitAll(
            userDoc(userId).collection("categories")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Category::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMonthConfig(userId: String, yearMonth: String): Flow<MonthConfig?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/monthConfigs/$yearMonth"
        emitAll(
            userDoc(userId).collection("monthConfigs").document(yearMonth)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            MonthConfig::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun seedDefaultCategoriesIfEmpty(userId: String): Result<Unit> {
        val colPath = "users/$userId/categories"
        return try {
            val uid = requireMatchingUserId(userId)
            val existing = userDoc(uid).collection("categories").limit(1).get().await()
            if (!existing.isEmpty) return Result.success(Unit)

            val batch = db.batch()
            DefaultCategories.seeds.forEach { seed ->
                val ref = userDoc(uid).collection("categories").document(seed.id)
                val payload = mapOf(
                    "userId" to uid,
                    "name" to seed.name,
                    "type" to seed.type.name,
                    "iconKey" to seed.iconKey,
                    "colorHex" to seed.colorHex,
                    "budgetLimit" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(ref, payload)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colPath)
            Result.failure(e)
        }
    }

    suspend fun addTransaction(
        userId: String,
        title: String,
        amount: Long,
        type: TransactionType,
        categoryId: String,
        categoryName: String,
        paymentMethod: PaymentMethod,
        note: String?,
        dateMillis: Long
    ): Result<Unit> {
        var docPath = "users/$userId/transactions"
        return try {
            val uid = requireMatchingUserId(userId)
            val cleanTitle = title.trim()
            val cleanCatId = categoryId.trim()
            val cleanCatName = categoryName.trim()
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }

            require(cleanTitle.length in 1..100) { "Keterangan harus 1–100 karakter." }
            require(amount in 1L..1_000_000_000_000L) { "Jumlah harus lebih dari Rp 0." }
            require(cleanCatId.length in 1..64 && ID_REGEX.matches(cleanCatId)) { "Kategori tidak valid." }
            require(cleanCatName.length in 1..50) { "Nama kategori tidak valid." }
            require(cleanNote == null || cleanNote.length <= 300) { "Catatan maksimal 300 karakter." }
            require(dateMillis > 0L) { "Tanggal tidak valid." }

            val (yearMonth, dayOfMonth) = FinanceFormatters.extractYearMonthAndDay(dateMillis)
            val docRef = userDoc(uid).collection("transactions").document()
            docPath = docRef.path

            val payload = linkedMapOf<String, Any?>(
                "userId" to uid,
                "title" to cleanTitle,
                "amount" to amount,
                "type" to type.name,
                "categoryId" to cleanCatId,
                "categoryName" to cleanCatName,
                "paymentMethod" to paymentMethod.name,
                "note" to cleanNote,
                "dateMillis" to dateMillis,
                "yearMonth" to yearMonth,
                "dayOfMonth" to dayOfMonth,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(
        userId: String,
        transactionId: String,
        title: String,
        amount: Long,
        type: TransactionType,
        categoryId: String,
        categoryName: String,
        paymentMethod: PaymentMethod,
        note: String?,
        dateMillis: Long
    ): Result<Unit> {
        val docPath = "users/$userId/transactions/$transactionId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(transactionId.isNotBlank() && ID_REGEX.matches(transactionId)) { "ID transaksi tidak valid." }

            val cleanTitle = title.trim()
            val cleanCatId = categoryId.trim()
            val cleanCatName = categoryName.trim()
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }

            require(cleanTitle.length in 1..100) { "Keterangan harus 1–100 karakter." }
            require(amount in 1L..1_000_000_000_000L) { "Jumlah harus lebih dari Rp 0." }
            require(cleanCatId.length in 1..64 && ID_REGEX.matches(cleanCatId)) { "Kategori tidak valid." }
            require(cleanCatName.length in 1..50) { "Nama kategori tidak valid." }
            require(cleanNote == null || cleanNote.length <= 300) { "Catatan maksimal 300 karakter." }
            require(dateMillis > 0L) { "Tanggal tidak valid." }

            val (yearMonth, dayOfMonth) = FinanceFormatters.extractYearMonthAndDay(dateMillis)
            val updates = linkedMapOf<String, Any?>(
                "title" to cleanTitle,
                "amount" to amount,
                "type" to type.name,
                "categoryId" to cleanCatId,
                "categoryName" to cleanCatName,
                "paymentMethod" to paymentMethod.name,
                "note" to (cleanNote ?: FieldValue.delete()),
                "dateMillis" to dateMillis,
                "yearMonth" to yearMonth,
                "dayOfMonth" to dayOfMonth,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            userDoc(uid).collection("transactions").document(transactionId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(userId: String, transactionId: String): Result<Unit> {
        val docPath = "users/$userId/transactions/$transactionId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(transactionId.isNotBlank() && ID_REGEX.matches(transactionId)) { "ID transaksi tidak valid." }
            userDoc(uid).collection("transactions").document(transactionId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addCategory(
        userId: String,
        name: String,
        type: TransactionType,
        iconKey: String,
        colorHex: String,
        budgetLimit: Long
    ): Result<Unit> {
        var docPath = "users/$userId/categories"
        return try {
            val uid = requireMatchingUserId(userId)
            val cleanName = name.trim()
            val cleanIcon = iconKey.trim()
            val cleanColor = colorHex.trim()

            require(cleanName.length in 1..50) { "Nama kategori harus 1–50 karakter." }
            require(cleanIcon.length in 1..40 && ICON_REGEX.matches(cleanIcon)) { "Ikon tidak valid." }
            require(HEX_REGEX.matches(cleanColor)) { "Warna tidak valid." }
            require(budgetLimit in 0L..1_000_000_000_000L) { "Batas anggaran tidak valid." }

            val docRef = userDoc(uid).collection("categories").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "type" to type.name,
                "iconKey" to cleanIcon,
                "colorHex" to cleanColor,
                "budgetLimit" to budgetLimit,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCategory(
        userId: String,
        categoryId: String,
        name: String,
        type: TransactionType,
        iconKey: String,
        colorHex: String,
        budgetLimit: Long
    ): Result<Unit> {
        val docPath = "users/$userId/categories/$categoryId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(categoryId.isNotBlank() && ID_REGEX.matches(categoryId)) { "ID kategori tidak valid." }
            val cleanName = name.trim()
            val cleanIcon = iconKey.trim()
            val cleanColor = colorHex.trim()

            require(cleanName.length in 1..50) { "Nama kategori harus 1–50 karakter." }
            require(cleanIcon.length in 1..40 && ICON_REGEX.matches(cleanIcon)) { "Ikon tidak valid." }
            require(HEX_REGEX.matches(cleanColor)) { "Warna tidak valid." }
            require(budgetLimit in 0L..1_000_000_000_000L) { "Batas anggaran tidak valid." }

            val updates = mapOf(
                "name" to cleanName,
                "type" to type.name,
                "iconKey" to cleanIcon,
                "colorHex" to cleanColor,
                "budgetLimit" to budgetLimit,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDoc(uid).collection("categories").document(categoryId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(userId: String, categoryId: String): Result<Unit> {
        val docPath = "users/$userId/categories/$categoryId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(categoryId.isNotBlank() && ID_REGEX.matches(categoryId)) { "ID kategori tidak valid." }
            userDoc(uid).collection("categories").document(categoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertMonthConfig(
        userId: String,
        yearMonth: String,
        openingBalance: Long,
        monthlyBudget: Long,
        weeklyBudget: Long = 0L
    ): Result<Unit> {
        val docPath = "users/$userId/monthConfigs/$yearMonth"
        return try {
            val uid = requireMatchingUserId(userId)
            require(YEAR_MONTH_REGEX.matches(yearMonth)) { "Format bulan tidak valid." }
            require(openingBalance in -1_000_000_000_000L..1_000_000_000_000L) { "Saldo awal di luar batas." }
            require(monthlyBudget in 0L..1_000_000_000_000L) { "Anggaran bulanan tidak valid." }
            require(weeklyBudget in 0L..1_000_000_000_000L) { "Anggaran mingguan tidak valid." }

            val docRef = userDoc(uid).collection("monthConfigs").document(yearMonth)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "openingBalance" to openingBalance,
                        "monthlyBudget" to monthlyBudget,
                        "weeklyBudget" to weeklyBudget,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "yearMonth" to yearMonth,
                        "openingBalance" to openingBalance,
                        "monthlyBudget" to monthlyBudget,
                        "weeklyBudget" to weeklyBudget,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private companion object {
        val ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
        val ICON_REGEX = Regex("^[a-z0-9_]+$")
        val HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
        val YEAR_MONTH_REGEX = Regex("^[0-9]{4}-[0-9]{2}$")
    }
}
