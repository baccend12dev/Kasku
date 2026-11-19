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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.model.RecurringTemplate
import com.example.model.SavingGoal
import com.example.model.TransactionType
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.CategoryVisuals
import com.example.util.FinanceFormatters
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SavingGoalEditorSheet(
    editingGoal: SavingGoal?,
    onDismiss: () -> Unit,
    onSave: (
        editingId: String?,
        name: String,
        targetAmount: Long,
        currentAmount: Long,
        targetDateMillis: Long,
        iconKey: String,
        colorHex: String,
        note: String?
    ) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultDeadline = remember {
        Calendar.getInstance().apply { add(Calendar.MONTH, 6) }.timeInMillis
    }

    var name by remember(editingGoal) { mutableStateOf(editingGoal?.name ?: "") }
    var targetText by remember(editingGoal) {
        mutableStateOf(editingGoal?.targetAmount?.takeIf { it > 0L }?.toString() ?: "")
    }
    var currentText by remember(editingGoal) {
        mutableStateOf(editingGoal?.currentAmount?.takeIf { it > 0L }?.toString() ?: "")
    }
    var targetDateMillis by remember(editingGoal) {
        mutableLongStateOf(editingGoal?.targetDateMillis?.takeIf { it > 0L } ?: defaultDeadline)
    }
    var iconKey by remember(editingGoal) { mutableStateOf(editingGoal?.iconKey ?: "savings") }
    var colorHex by remember(editingGoal) { mutableStateOf(editingGoal?.colorHex ?: "#0E6B4E") }
    var note by remember(editingGoal) { mutableStateOf(editingGoal?.note ?: "") }
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
                    text = if (editingGoal == null) stringResource(R.string.goal_add_new) else stringResource(R.string.goal_edit),
                    style = MaterialTheme.typography.titleLarge
                )
                if (editingGoal != null && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_goal_button")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.tx_delete))
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it; validationError = null },
                label = { Text(stringResource(R.string.goal_field_name)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("goal_name_input")
            )

            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it.filter(Char::isDigit).take(12); validationError = null },
                label = { Text(stringResource(R.string.goal_field_target)) },
                prefix = { Text("Rp ", fontFamily = JetBrainsMonoFamily) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("goal_target_input")
            )

            OutlinedTextField(
                value = currentText,
                onValueChange = { currentText = it.filter(Char::isDigit).take(12) },
                label = { Text(stringResource(R.string.goal_field_current)) },
                prefix = { Text("Rp ", fontFamily = JetBrainsMonoFamily) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("goal_current_input")
            )

            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("goal_deadline_button")
            ) {
                Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${stringResource(R.string.goal_field_deadline)}: ${FinanceFormatters.formatDateFull(targetDateMillis)}",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(stringResource(R.string.cat_field_color), style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CategoryVisuals.colorHexOptions.forEach { hex ->
                    val swatch = CategoryVisuals.parseColor(hex)
                    val selected = hex.equals(colorHex, ignoreCase = true)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(swatch)
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
                val goalIcons = CategoryVisuals.iconOptions.filter {
                    it.key in listOf("savings", "account_balance", "flight", "home", "directions_car", "school", "card_giftcard", "phone_android", "medical_services", "work")
                }
                goalIcons.forEach { opt ->
                    val selected = opt.key == iconKey
                    Surface(
                        color = if (selected) activeColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) activeColor else MaterialTheme.colorScheme.outlineVariant),
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

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 200) note = it },
                label = { Text(stringResource(R.string.tx_field_note)) },
                maxLines = 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (validationError != null) {
                Text(validationError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Text(stringResource(R.string.tx_cancel)) }
                Button(
                    onClick = {
                        val cleanName = name.trim()
                        val target = targetText.toLongOrNull() ?: 0L
                        val current = currentText.toLongOrNull() ?: 0L
                        if (cleanName.isEmpty()) {
                            validationError = "Masukkan nama target tabungan."
                            return@Button
                        }
                        if (target <= 0L) {
                            validationError = "Target nominal harus lebih dari Rp 0."
                            return@Button
                        }
                        onSave(
                            editingGoal?.id,
                            cleanName,
                            target,
                            current,
                            targetDateMillis,
                            iconKey,
                            colorHex,
                            note.trim().takeIf { it.isNotEmpty() }
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.4f).height(50.dp).testTag("goal_save_button")
                ) { Text(stringResource(R.string.tx_save)) }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = targetDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { targetDateMillis = it }
                    showDatePicker = false
                }) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.tx_cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showDeleteConfirm && editingGoal != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus Target Tabungan?") },
            text = { Text("Target \"${editingGoal.name}\" akan dihapus permanen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(editingGoal.id)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.tx_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.tx_cancel)) }
            }
        )
    }
}

@Composable
fun GoalTopUpDialog(
    goal: SavingGoal,
    onDismiss: () -> Unit,
    onConfirm: (depositAmount: Long, recordAsExpense: Boolean) -> Unit
) {
    var depositText by remember { mutableStateOf("") }
    var recordAsExpense by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.goal_topup), style = MaterialTheme.typography.titleLarge)
                Text(goal.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Sisa target: ${FinanceFormatters.formatRupiah(goal.remainingAmount)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = depositText,
                    onValueChange = { depositText = it.filter(Char::isDigit).take(12); errorText = null },
                    label = { Text("Nominal Setoran (Rp)") },
                    prefix = { Text("Rp ", fontFamily = JetBrainsMonoFamily) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("topup_amount_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(50_000L to "50rb", 100_000L to "100rb", 500_000L to "500rb", 1_000_000L to "1jt")
                        .forEach { (nominal, label) ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val cur = depositText.toLongOrNull() ?: 0L
                                        depositText = (cur + nominal).toString()
                                        errorText = null
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "+$label",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                    )
                                }
                            }
                        }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { recordAsExpense = !recordAsExpense }
                ) {
                    Checkbox(
                        checked = recordAsExpense,
                        onCheckedChange = { recordAsExpense = it }
                    )
                    Text(
                        text = "Catat juga sebagai transaksi bulan ini agar saldo harian terpotong otomatis",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (errorText != null) {
                    Text(errorText ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = depositText.toLongOrNull() ?: 0L
                    if (amount <= 0L) {
                        errorText = "Masukkan nominal setoran lebih dari Rp 0."
                        return@Button
                    }
                    onConfirm(amount, recordAsExpense)
                },
                modifier = Modifier.testTag("confirm_topup_button")
            ) { Text("Setor") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tx_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringTemplateEditorSheet(
    editingTemplate: RecurringTemplate?,
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
        dayOfMonth: Int,
        active: Boolean,
        note: String?
    ) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember(editingTemplate) {
        mutableStateOf(editingTemplate?.parsedType ?: TransactionType.EXPENSE)
    }
    var title by remember(editingTemplate) { mutableStateOf(editingTemplate?.title ?: "") }
    var amountText by remember(editingTemplate) {
        mutableStateOf(editingTemplate?.amount?.takeIf { it > 0L }?.toString() ?: "")
    }
    var dayText by remember(editingTemplate) {
        mutableStateOf((editingTemplate?.dayOfMonth ?: 1).toString())
    }
    var paymentMethod by remember(editingTemplate) {
        mutableStateOf(editingTemplate?.parsedPaymentMethod ?: PaymentMethod.TRANSFER)
    }
    var note by remember(editingTemplate) { mutableStateOf(editingTemplate?.note ?: "") }
    val filteredCategories = remember(categories, type) {
        categories.filter { it.parsedType == type }
    }
    var selectedCategoryId by remember(editingTemplate, type, filteredCategories) {
        val initial = editingTemplate?.categoryId?.takeIf { id ->
            filteredCategories.any { it.id == id }
        } ?: filteredCategories.firstOrNull()?.id ?: if (type == TransactionType.EXPENSE) "cat_tagihan" else "cat_gaji"
        mutableStateOf(initial)
    }
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
                    text = if (editingTemplate == null) stringResource(R.string.rec_add_new) else stringResource(R.string.rec_edit),
                    style = MaterialTheme.typography.titleLarge
                )
                if (editingTemplate != null && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_recurring_button")
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
                                    ?: if (option == TransactionType.EXPENSE) "cat_tagihan" else "cat_gaji"
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (option == TransactionType.EXPENSE) "Tagihan Rutin" else "Pemasukan / Gaji",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 100) title = it; validationError = null },
                label = { Text("Nama Tagihan / Gaji") },
                placeholder = { Text("Contoh: Gaji Bulanan, Bayar Kos, WiFi") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("rec_title_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit).take(12); validationError = null },
                    label = { Text(stringResource(R.string.tx_field_amount)) },
                    prefix = { Text("Rp ", fontFamily = JetBrainsMonoFamily) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.5f).testTag("rec_amount_input")
                )
                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = it.filter(Char::isDigit).take(2); validationError = null },
                    label = { Text("Tgl (1–31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(0.9f).testTag("rec_day_input")
                )
            }

            Text(stringResource(R.string.tx_field_category), style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredCategories.forEach { cat ->
                    val selected = cat.id == selectedCategoryId
                    val accent = CategoryVisuals.parseColor(cat.colorHex)
                    Surface(
                        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedCategoryId = cat.id }
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

            Text(stringResource(R.string.tx_field_payment), style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PaymentMethod.entries.forEach { pm ->
                    FilterChip(
                        selected = paymentMethod == pm,
                        onClick = { paymentMethod = pm },
                        label = { Text(pm.label) }
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 300) note = it },
                label = { Text(stringResource(R.string.tx_field_note)) },
                maxLines = 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (validationError != null) {
                Text(validationError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Text(stringResource(R.string.tx_cancel)) }
                Button(
                    onClick = {
                        val cleanTitle = title.trim()
                        val amount = amountText.toLongOrNull() ?: 0L
                        val day = dayText.toIntOrNull() ?: 0
                        if (cleanTitle.isEmpty()) {
                            validationError = "Masukkan nama tagihan atau pemasukan."
                            return@Button
                        }
                        if (amount <= 0L) {
                            validationError = "Masukkan jumlah lebih dari Rp 0."
                            return@Button
                        }
                        if (day !in 1..31) {
                            validationError = "Tanggal rutin harus antara 1 sampai 31."
                            return@Button
                        }
                        val cat = filteredCategories.firstOrNull { it.id == selectedCategoryId }
                        onSave(
                            editingTemplate?.id,
                            cleanTitle,
                            amount,
                            type,
                            cat?.id ?: selectedCategoryId,
                            cat?.name ?: if (type == TransactionType.EXPENSE) "Tagihan & Utilitas" else "Gaji",
                            paymentMethod,
                            day,
                            editingTemplate?.active ?: true,
                            note.trim().takeIf { it.isNotEmpty() }
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.4f).height(50.dp).testTag("rec_save_button")
                ) { Text(stringResource(R.string.tx_save)) }
            }
        }
    }

    if (showDeleteConfirm && editingTemplate != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus Transaksi Rutin?") },
            text = { Text("Jadwal \"${editingTemplate.title}\" akan dihapus. Transaksi yang sudah tercatat sebelumnya tetap aman.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(editingTemplate.id)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.tx_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.tx_cancel)) }
            }
        )
    }
}
