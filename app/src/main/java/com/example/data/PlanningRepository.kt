package com.example.data

import android.content.Context
import com.example.R
import com.example.model.PaymentMethod
import com.example.model.RecurringTemplate
import com.example.model.SavingGoal
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
import java.util.Calendar

class PlanningRepository(
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

    fun observeSavingGoals(userId: String): Flow<List<SavingGoal>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/savingGoals"
        emitAll(
            userDoc(userId).collection("savingGoals")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavingGoal::class.java,
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

    suspend fun addSavingGoal(
        userId: String,
        name: String,
        targetAmount: Long,
        currentAmount: Long,
        targetDateMillis: Long,
        iconKey: String,
        colorHex: String,
        note: String?
    ): Result<Unit> {
        var docPath = "users/$userId/savingGoals"
        return try {
            val uid = requireMatchingUserId(userId)
            val cleanName = name.trim()
            val cleanIcon = iconKey.trim()
            val cleanColor = colorHex.trim()
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }

            require(cleanName.length in 1..60) { "Nama target harus 1–60 karakter." }
            require(targetAmount in 1L..MAX_AMOUNT) { "Target nominal harus lebih dari Rp 0." }
            require(currentAmount in 0L..MAX_AMOUNT) { "Nominal terkumpul tidak valid." }
            require(targetDateMillis > 0L) { "Tanggal target tidak valid." }
            require(cleanIcon.length in 1..40 && ICON_REGEX.matches(cleanIcon)) { "Ikon tidak valid." }
            require(HEX_REGEX.matches(cleanColor)) { "Warna tidak valid." }
            require(cleanNote == null || cleanNote.length <= 200) { "Catatan maksimal 200 karakter." }

            val docRef = userDoc(uid).collection("savingGoals").document()
            docPath = docRef.path
            val payload = linkedMapOf<String, Any?>(
                "userId" to uid,
                "name" to cleanName,
                "targetAmount" to targetAmount,
                "currentAmount" to currentAmount,
                "targetDateMillis" to targetDateMillis,
                "iconKey" to cleanIcon,
                "colorHex" to cleanColor,
                "note" to cleanNote,
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

    suspend fun updateSavingGoal(
        userId: String,
        goalId: String,
        name: String,
        targetAmount: Long,
        currentAmount: Long,
        targetDateMillis: Long,
        iconKey: String,
        colorHex: String,
        note: String?
    ): Result<Unit> {
        val docPath = "users/$userId/savingGoals/$goalId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(goalId.isNotBlank() && ID_REGEX.matches(goalId)) { "ID target tidak valid." }
            val cleanName = name.trim()
            val cleanIcon = iconKey.trim()
            val cleanColor = colorHex.trim()
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }

            require(cleanName.length in 1..60) { "Nama target harus 1–60 karakter." }
            require(targetAmount in 1L..MAX_AMOUNT) { "Target nominal harus lebih dari Rp 0." }
            require(currentAmount in 0L..MAX_AMOUNT) { "Nominal terkumpul tidak valid." }
            require(targetDateMillis > 0L) { "Tanggal target tidak valid." }
            require(cleanIcon.length in 1..40 && ICON_REGEX.matches(cleanIcon)) { "Ikon tidak valid." }
            require(HEX_REGEX.matches(cleanColor)) { "Warna tidak valid." }
            require(cleanNote == null || cleanNote.length <= 200) { "Catatan maksimal 200 karakter." }

            val updates = linkedMapOf<String, Any?>(
                "name" to cleanName,
                "targetAmount" to targetAmount,
                "currentAmount" to currentAmount,
                "targetDateMillis" to targetDateMillis,
                "iconKey" to cleanIcon,
                "colorHex" to cleanColor,
                "note" to (cleanNote ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDoc(uid).collection("savingGoals").document(goalId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun topUpSavingGoal(
        userId: String,
        goal: SavingGoal,
        depositAmount: Long,
        recordAsExpense: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/savingGoals/${goal.id}"
        return try {
            val uid = requireMatchingUserId(userId)
            require(goal.id.isNotBlank() && ID_REGEX.matches(goal.id)) { "ID target tidak valid." }
            require(depositAmount in 1L..MAX_AMOUNT) { "Nominal setoran harus lebih dari Rp 0." }
            val newAmount = (goal.currentAmount + depositAmount).coerceAtMost(MAX_AMOUNT)

            val batch = db.batch()
            val goalRef = userDoc(uid).collection("savingGoals").document(goal.id)
            batch.update(
                goalRef,
                mapOf(
                    "currentAmount" to newAmount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (recordAsExpense) {
                val nowMillis = System.currentTimeMillis()
                val (ym, day) = FinanceFormatters.extractYearMonthAndDay(nowMillis)
                val txRef = userDoc(uid).collection("transactions").document()
                val txTitle = "Setor Tabungan: ${goal.name}".take(100)
                batch.set(
                    txRef,
                    mapOf(
                        "userId" to uid,
                        "title" to txTitle,
                        "amount" to depositAmount,
                        "type" to TransactionType.EXPENSE.name,
                        "categoryId" to "cat_lainnya",
                        "categoryName" to "Tabungan",
                        "paymentMethod" to PaymentMethod.TRANSFER.name,
                        "note" to "Alokasi ke target ${goal.name}".take(300),
                        "dateMillis" to nowMillis,
                        "yearMonth" to ym,
                        "dayOfMonth" to day,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSavingGoal(userId: String, goalId: String): Result<Unit> {
        val docPath = "users/$userId/savingGoals/$goalId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(goalId.isNotBlank() && ID_REGEX.matches(goalId)) { "ID target tidak valid." }
            userDoc(uid).collection("savingGoals").document(goalId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeRecurringTemplates(userId: String): Flow<List<RecurringTemplate>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/recurringTemplates"
        emitAll(
            userDoc(userId).collection("recurringTemplates")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RecurringTemplate::class.java,
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

    suspend fun addRecurringTemplate(
        userId: String,
        title: String,
        amount: Long,
        type: TransactionType,
        categoryId: String,
        categoryName: String,
        paymentMethod: PaymentMethod,
        dayOfMonth: Int,
        note: String?
    ): Result<Unit> {
        var docPath = "users/$userId/recurringTemplates"
        return try {
            val uid = requireMatchingUserId(userId)
            val cleanTitle = title.trim()
            val cleanCatId = categoryId.trim()
            val cleanCatName = categoryName.trim()
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }

            require(cleanTitle.length in 1..100) { "Keterangan harus 1–100 karakter." }
            require(amount in 1L..MAX_AMOUNT) { "Jumlah harus lebih dari Rp 0." }
            require(cleanCatId.length in 1..64 && ID_REGEX.matches(cleanCatId)) { "Kategori tidak valid." }
            require(cleanCatName.length in 1..50) { "Nama kategori tidak valid." }
            require(dayOfMonth in 1..31) { "Tanggal rutin harus antara 1–31." }
            require(cleanNote == null || cleanNote.length <= 300) { "Catatan maksimal 300 karakter." }

            val docRef = userDoc(uid).collection("recurringTemplates").document()
            docPath = docRef.path
            val payload = linkedMapOf<String, Any?>(
                "userId" to uid,
                "title" to cleanTitle,
                "amount" to amount,
                "type" to type.name,
                "categoryId" to cleanCatId,
                "categoryName" to cleanCatName,
                "paymentMethod" to paymentMethod.name,
                "dayOfMonth" to dayOfMonth,
                "active" to true,
                "note" to cleanNote,
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

    suspend fun updateRecurringTemplate(
        userId: String,
        templateId: String,
        title: String,
        amount: Long,
        type: TransactionType,
        categoryId: String,
        categoryName: String,
        paymentMethod: PaymentMethod,
        dayOfMonth: Int,
        active: Boolean,
        note: String?
    ): Result<Unit> {
        val docPath = "users/$userId/recurringTemplates/$templateId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(templateId.isNotBlank() && ID_REGEX.matches(templateId)) { "ID jadwal rutin tidak valid." }
            val cleanTitle = title.trim()
            val cleanCatId = categoryId.trim()
            val cleanCatName = categoryName.trim()
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }

            require(cleanTitle.length in 1..100) { "Keterangan harus 1–100 karakter." }
            require(amount in 1L..MAX_AMOUNT) { "Jumlah harus lebih dari Rp 0." }
            require(cleanCatId.length in 1..64 && ID_REGEX.matches(cleanCatId)) { "Kategori tidak valid." }
            require(cleanCatName.length in 1..50) { "Nama kategori tidak valid." }
            require(dayOfMonth in 1..31) { "Tanggal rutin harus antara 1–31." }
            require(cleanNote == null || cleanNote.length <= 300) { "Catatan maksimal 300 karakter." }

            val updates = linkedMapOf<String, Any?>(
                "title" to cleanTitle,
                "amount" to amount,
                "type" to type.name,
                "categoryId" to cleanCatId,
                "categoryName" to cleanCatName,
                "paymentMethod" to paymentMethod.name,
                "dayOfMonth" to dayOfMonth,
                "active" to active,
                "note" to (cleanNote ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDoc(uid).collection("recurringTemplates").document(templateId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setRecurringActive(
        userId: String,
        templateId: String,
        active: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/recurringTemplates/$templateId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(templateId.isNotBlank() && ID_REGEX.matches(templateId)) { "ID jadwal rutin tidak valid." }
            userDoc(uid).collection("recurringTemplates").document(templateId).update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteRecurringTemplate(userId: String, templateId: String): Result<Unit> {
        val docPath = "users/$userId/recurringTemplates/$templateId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(templateId.isNotBlank() && ID_REGEX.matches(templateId)) { "ID jadwal rutin tidak valid." }
            userDoc(uid).collection("recurringTemplates").document(templateId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun applyRecurringTemplatesForMonth(
        userId: String,
        yearMonth: String,
        templates: List<RecurringTemplate>
    ): Result<Int> {
        val path = "users/$userId/recurringTemplates"
        return try {
            val uid = requireMatchingUserId(userId)
            require(YEAR_MONTH_REGEX.matches(yearMonth)) { "Format bulan tidak valid." }
            val eligible = templates.filter { it.id.isNotBlank() && it.lastAppliedYearMonth != yearMonth }
            if (eligible.isEmpty()) return Result.success(0)

            val (year, month) = FinanceFormatters.parseYearMonth(yearMonth)
            val maxDay = FinanceFormatters.daysInMonth(yearMonth)
            val batch = db.batch()

            eligible.forEach { tpl ->
                val clampedDay = tpl.dayOfMonth.coerceIn(1, maxDay)
                val dateMillis = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.DAY_OF_MONTH, clampedDay)
                    set(Calendar.HOUR_OF_DAY, 9)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val txRef = userDoc(uid).collection("transactions").document()
                val txPayload = linkedMapOf<String, Any?>(
                    "userId" to uid,
                    "title" to tpl.title.trim().take(100),
                    "amount" to tpl.amount,
                    "type" to tpl.parsedType.name,
                    "categoryId" to tpl.categoryId,
                    "categoryName" to tpl.categoryName,
                    "paymentMethod" to tpl.parsedPaymentMethod.name,
                    "note" to (tpl.note?.trim()?.takeIf { it.isNotEmpty() } ?: "Transaksi rutin bulanan"),
                    "dateMillis" to dateMillis,
                    "yearMonth" to yearMonth,
                    "dayOfMonth" to clampedDay,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                batch.set(txRef, txPayload)

                val tplRef = userDoc(uid).collection("recurringTemplates").document(tpl.id)
                batch.update(
                    tplRef,
                    mapOf(
                        "lastAppliedYearMonth" to yearMonth,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(eligible.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private companion object {
        const val MAX_AMOUNT = 1_000_000_000_000L
        val ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
        val ICON_REGEX = Regex("^[a-z0-9_]+$")
        val HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
        val YEAR_MONTH_REGEX = Regex("^[0-9]{4}-[0-9]{2}$")
    }
}
