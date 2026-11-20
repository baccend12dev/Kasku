package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Category
import com.example.model.PaymentMethod
import com.example.model.Transaction
import com.example.model.TransactionType
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.CategoryVisuals
import com.example.util.FinanceFormatters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorSheet(
    editingTransaction: Transaction?,
    defaultYearMonth: String,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (
        editingId: String?,
        title: String,
        amount: Long,
        type: TransactionType,
        categoryId: String,
        categoryName: String,
        paymentMethod: PaymentMethod,
        note: String?,
        dateMillis: Long
    ) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.parsedType ?: TransactionType.EXPENSE)
    }
    var amountText by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.amount?.toString() ?: "")
    }
    var title by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.title ?: "")
    }
    var paymentMethod by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.parsedPaymentMethod ?: PaymentMethod.EWALLET)
    }
    var note by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.note ?: "")
    }
    var dateMillis by remember(editingTransaction, defaultYearMonth) {
        mutableLongStateOf(
            editingTransaction?.dateMillis ?: FinanceFormatters.defaultDateMillisForMonth(defaultYearMonth)
        )
    }

    val filteredCategories = remember(categories, type) {
        categories.filter { it.parsedType == type }
    }
    var selectedCategoryId by remember(editingTransaction, type, filteredCategories) {
        val initial = editingTransaction?.categoryId?.takeIf { id ->
            filteredCategories.any { it.id == id }
        } ?: filteredCategories.firstOrNull()?.id ?: if (type == TransactionType.EXPENSE) "cat_lainnya" else "cat_gaji"
        mutableStateOf(initial)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingTransaction == null) {
                        stringResource(R.string.tx_add_new)
                    } else {
                        stringResource(R.string.tx_edit)
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                if (editingTransaction != null && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_transaction_button")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.tx_delete))
                    }
                }
            }

            // Type Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TransactionType.entries.forEach { option ->
                    val selected = type == option
                    val container = when {
                        selected && option == TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                        selected && option == TransactionType.INCOME -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        color = container,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                type = option
                                val nextCats = categories.filter { it.parsedType == option }
                                selectedCategoryId = nextCats.firstOrNull()?.id
                                    ?: if (option == TransactionType.EXPENSE) "cat_lainnya" else "cat_gaji"
                            }
                            .testTag("tx_type_${option.name.lowercase()}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = option.labelId,
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor
                            )
                        }
                    }
                }
            }

            // Amount Field + Quick Nominal Chips
            OutlinedTextField(
                value = amountText,
                onValueChange = { raw ->
                    amountText = raw.filter { it.isDigit() }.take(12)
                    validationError = null
                },
                label = { Text(stringResource(R.string.tx_field_amount)) },
                prefix = { Text("Rp ", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold) },
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_amount_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(25_000L to "25rb", 50_000L to "50rb", 100_000L to "100rb", 250_000L to "250rb")
                    .forEach { (nominal, label) ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val current = amountText.toLongOrNull() ?: 0L
                                    amountText = (current + nominal).toString()
                                    validationError = null
                                }
                                .testTag("quick_amount_$label")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "+$label",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
            }

            // Title / Where the money went
            OutlinedTextField(
                value = title,
                onValueChange = {
                    if (it.length <= 100) title = it
                    validationError = null
                },
                label = { Text(stringResource(R.string.tx_field_title)) },
                placeholder = { Text(stringResource(R.string.tx_field_title_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_title_input")
            )

            // Category Selector
            Text(
                text = stringResource(R.string.tx_field_category),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredCategories.forEach { cat ->
                    val selected = cat.id == selectedCategoryId
                    val accent = CategoryVisuals.parseColor(cat.colorHex)
                    Surface(
                        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedCategoryId = cat.id }
                            .testTag("tx_category_chip_${cat.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Icon(
                                imageVector = CategoryVisuals.iconForKey(cat.iconKey),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Payment Method Selector
            Text(
                text = stringResource(R.string.tx_field_payment),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PaymentMethod.entries.forEach { pm ->
                    FilterChip(
                        selected = paymentMethod == pm,
                        onClick = { paymentMethod = pm },
                        label = { Text(pm.label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("tx_payment_${pm.name.lowercase()}")
                    )
                }
            }

            // Date Picker Row
            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("tx_date_button")
            ) {
                Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${stringResource(R.string.tx_field_date)}: ${FinanceFormatters.formatDateWithDay(dateMillis)}",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Optional Note
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 300) note = it },
                label = { Text(stringResource(R.string.tx_field_note)) },
                maxLines = 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_note_input")
            )

            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.tx_cancel))
                }
                Button(
                    onClick = {
                        val parsedAmount = amountText.toLongOrNull() ?: 0L
                        val cleanTitle = title.trim()
                        if (parsedAmount <= 0L) {
                            validationError = "Masukkan jumlah lebih dari Rp 0."
                            return@Button
                        }
                        if (cleanTitle.isEmpty()) {
                            validationError = "Isi keterangan transaksi terlebih dahulu."
                            return@Button
                        }
                        val chosenCat = filteredCategories.firstOrNull { it.id == selectedCategoryId }
                        val catId = chosenCat?.id ?: selectedCategoryId
                        val catName = chosenCat?.name ?: if (type == TransactionType.EXPENSE) "Lainnya" else "Gaji"
                        onSave(
                            editingTransaction?.id,
                            cleanTitle,
                            parsedAmount,
                            type,
                            catId,
                            catName,
                            paymentMethod,
                            note.trim().takeIf { it.isNotEmpty() },
                            dateMillis
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("tx_save_button")
                ) {
                    Text(
                        text = if (editingTransaction == null) {
                            stringResource(R.string.tx_save)
                        } else {
                            stringResource(R.string.tx_update)
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { dateMillis = it }
                        showDatePicker = false
                    }
                ) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.tx_cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDeleteConfirm && editingTransaction != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.tx_confirm_delete_title)) },
            text = { Text(stringResource(R.string.tx_confirm_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(editingTransaction.id)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.tx_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.tx_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthConfigDialog(
    monthLabel: String,
    initialOpeningBalance: Long,
    initialMonthlyBudget: Long,
    initialWeeklyBudget: Long,
    onDismiss: () -> Unit,
    onSave: (openingBalance: Long, monthlyBudget: Long, weeklyBudget: Long) -> Unit
) {
    var openingText by remember(initialOpeningBalance) {
        mutableStateOf(if (initialOpeningBalance != 0L) initialOpeningBalance.toString() else "")
    }
    var monthlyEnabled by remember(initialMonthlyBudget) {
        mutableStateOf(initialMonthlyBudget > 0L)
    }
    var budgetText by remember(initialMonthlyBudget) {
        mutableStateOf(if (initialMonthlyBudget > 0L) initialMonthlyBudget.toString() else "")
    }
    var weeklyEnabled by remember(initialWeeklyBudget) {
        mutableStateOf(initialWeeklyBudget > 0L)
    }
    var weeklyText by remember(initialWeeklyBudget) {
        mutableStateOf(if (initialWeeklyBudget > 0L) initialWeeklyBudget.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.month_config_title), style = MaterialTheme.typography.titleLarge)
                Text(monthLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.month_config_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = openingText,
                    onValueChange = { raw -> openingText = raw.filter { it.isDigit() }.take(12) },
                    label = { Text(stringResource(R.string.month_config_opening)) },
                    prefix = { Text("Rp ", fontFamily = JetBrainsMonoFamily) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opening_balance_input")
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Optional Monthly Budget Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Target Bulanan (Opsional)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Notifikasi saat pengeluaran bulan ini mencapai 80% & 100%.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = monthlyEnabled,
                        onCheckedChange = { monthlyEnabled = it },
                        modifier = Modifier.testTag("monthly_budget_switch")
                    )
                }

                if (monthlyEnabled) {
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { raw -> budgetText = raw.filter { it.isDigit() }.take(12) },
                        label = { Text(stringResource(R.string.month_config_budget)) },
                        prefix = { Text("Rp ", fontFamily = JetBrainsMonoFamily) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("monthly_budget_input")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Optional Weekly Budget Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Target Mingguan (Opsional)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Pantau jatah pengeluaran Senin–Minggu agar belanja lebih terkontrol.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = weeklyEnabled,
                        onCheckedChange = { weeklyEnabled = it },
                        modifier = Modifier.testTag("weekly_budget_switch")
                    )
                }

                if (weeklyEnabled) {
                    OutlinedTextField(
                        value = weeklyText,
                        onValueChange = { raw -> weeklyText = raw.filter { it.isDigit() }.take(12) },
                        label = { Text(stringResource(R.string.month_config_weekly_budget)) },
                        prefix = { Text("Rp ", fontFamily = JetBrainsMonoFamily) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("weekly_budget_input")
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(200_000L to "200rb", 300_000L to "300rb", 500_000L to "500rb", 1_000_000L to "1jt")
                            .forEach { (preset, label) ->
                                FilterChip(
                                    selected = weeklyText.toLongOrNull() == preset,
                                    onClick = { weeklyText = preset.toString() },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                        )
                                    }
                                )
                            }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val opening = openingText.toLongOrNull() ?: 0L
                    val budget = if (monthlyEnabled) (budgetText.toLongOrNull() ?: 0L) else 0L
                    val weekly = if (weeklyEnabled) (weeklyText.toLongOrNull() ?: 0L) else 0L
                    onSave(opening, budget, weekly)
                },
                modifier = Modifier.testTag("save_month_config_button")
            ) {
                Text(stringResource(R.string.tx_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tx_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorSheet(
    editingCategory: Category?,
    defaultType: TransactionType,
    onDismiss: () -> Unit,
    onSave: (
        editingId: String?,
        name: String,
        type: TransactionType,
        iconKey: String,
        colorHex: String,
        budgetLimit: Long
    ) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(editingCategory) { mutableStateOf(editingCategory?.name ?: "") }
    var type by remember(editingCategory, defaultType) {
        mutableStateOf(editingCategory?.parsedType ?: defaultType)
    }
    var iconKey by remember(editingCategory) { mutableStateOf(editingCategory?.iconKey ?: "restaurant") }
    var colorHex by remember(editingCategory) { mutableStateOf(editingCategory?.colorHex ?: "#0E6B4E") }
    var budgetEnabled by remember(editingCategory) {
        mutableStateOf((editingCategory?.budgetLimit ?: 0L) > 0L)
    }
    var budgetText by remember(editingCategory) {
        mutableStateOf(if ((editingCategory?.budgetLimit ?: 0L) > 0L) editingCategory?.budgetLimit.toString() else "")
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingCategory == null) stringResource(R.string.cat_add_new) else stringResource(R.string.cat_edit),
                    style = MaterialTheme.typography.titleLarge
                )
                if (editingCategory != null && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_category_button")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.tx_delete))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { type = option },
                        label = { Text(option.labelId) }
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= 50) name = it
                    validationError = null
                },
                label = { Text(stringResource(R.string.cat_field_name)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cat_name_input")
            )

            if (type == TransactionType.EXPENSE) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = stringResource(R.string.cat_budget_toggle_label),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = stringResource(R.string.cat_budget_toggle_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = budgetEnabled,
                                onCheckedChange = { budgetEnabled = it },
                                modifier = Modifier.testTag("cat_budget_switch")
                            )
                        }

                        if (budgetEnabled) {
                            OutlinedTextField(
                                value = budgetText,
                                onValueChange = { raw -> budgetText = raw.filter { it.isDigit() }.take(12) },
                                label = { Text(stringResource(R.string.cat_field_budget)) },
                                prefix = { Text("Rp ", fontFamily = JetBrainsMonoFamily) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("cat_budget_input")
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(200_000L to "200rb", 500_000L to "500rb", 1_000_000L to "1jt", 2_000_000L to "2jt")
                                    .forEach { (preset, label) ->
                                        FilterChip(
                                            selected = budgetText.toLongOrNull() == preset,
                                            onClick = { budgetText = preset.toString() },
                                            label = {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                                )
                                            }
                                        )
                                    }
                            }
                        }
                    }
                }
            }

            Text(stringResource(R.string.cat_field_color), style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CategoryVisuals.colorHexOptions.forEach { hex ->
                    val swatchColor = CategoryVisuals.parseColor(hex)
                    val selected = hex.equals(colorHex, ignoreCase = true)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { colorHex = hex }
                    ) {
                        if (selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Text(stringResource(R.string.cat_field_icon), style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val activeColor = CategoryVisuals.parseColor(colorHex)
                CategoryVisuals.iconOptions.forEach { opt ->
                    val selected = opt.key == iconKey
                    Surface(
                        color = if (selected) activeColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) activeColor else MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { iconKey = opt.key }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = opt.icon,
                                contentDescription = opt.label,
                                tint = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.tx_cancel))
                }
                Button(
                    onClick = {
                        val cleanName = name.trim()
                        if (cleanName.isEmpty()) {
                            validationError = "Nama kategori tidak boleh kosong."
                            return@Button
                        }
                        val budget = if (type == TransactionType.EXPENSE && budgetEnabled) {
                            budgetText.toLongOrNull() ?: 0L
                        } else {
                            0L
                        }
                        onSave(editingCategory?.id, cleanName, type, iconKey, colorHex, budget)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("cat_save_button")
                ) {
                    Text(stringResource(R.string.tx_save))
                }
            }
        }
    }

    if (showDeleteConfirm && editingCategory != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.cat_confirm_delete_title)) },
            text = { Text(stringResource(R.string.cat_confirm_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(editingCategory.id)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.tx_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.tx_cancel))
                }
            }
        )
    }
}
