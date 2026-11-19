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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.RecurringTemplate
import com.example.model.SavingGoal
import com.example.model.TransactionType
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.CategoryVisuals
import com.example.util.FinanceFormatters
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PlanningScreen(
    selectedYearMonth: String,
    savingGoals: List<SavingGoal>,
    recurringTemplates: List<RecurringTemplate>,
    pendingRecurring: List<RecurringTemplate>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddGoal: () -> Unit,
    onEditGoal: (SavingGoal) -> Unit,
    onTopUpGoal: (SavingGoal) -> Unit,
    onAddRecurring: () -> Unit,
    onEditRecurring: (RecurringTemplate) -> Unit,
    onToggleRecurringActive: (String, Boolean) -> Unit,
    onApplyRecurring: (List<RecurringTemplate>) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = Target Tabungan, 1 = Rutin Bulanan
    val monthLabel = remember(selectedYearMonth) { FinanceFormatters.formatMonthLabel(selectedYearMonth) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sub-tab segmented switcher
        item(key = "plan_switcher") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    listOf(
                        0 to stringResource(R.string.plan_tab_goals),
                        1 to "${stringResource(R.string.plan_tab_recurring)} (${recurringTemplates.size})"
                    ).forEach { (idx, label) ->
                        val active = activeSubTab == idx
                        Surface(
                            color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            shadowElevation = if (active) 1.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { activeSubTab = idx }
                                .testTag("plan_subtab_$idx")
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
        }

        if (activeSubTab == 0) {
            // --- SAVING GOALS SECTION ---
            item(key = "goals_summary") {
                val totalSaved = savingGoals.sumOf { it.currentAmount }
                val totalTarget = savingGoals.sumOf { it.targetAmount }
                val overallPct = if (totalTarget > 0L) ((totalSaved.toDouble() / totalTarget.toDouble()) * 100.0).roundToInt() else 0

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total Tabungan Terkumpul",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = FinanceFormatters.formatRupiah(totalSaved),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            FilledTonalButton(
                                onClick = onAddGoal,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("add_goal_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.goal_add_new), style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (totalTarget > 0L) {
                            LinearProgressIndicator(
                                progress = { (totalSaved.toFloat() / totalTarget.toFloat()).coerceIn(0f, 1f) },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$overallPct% dari total target",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = FinanceFormatters.formatRupiah(totalTarget),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (savingGoals.isEmpty()) {
                item(key = "goals_empty") {
                    EmptyPlanningCard(
                        icon = Icons.Rounded.Savings,
                        title = stringResource(R.string.goal_empty_title),
                        subtitle = stringResource(R.string.goal_empty_subtitle),
                        actionLabel = stringResource(R.string.goal_add_new),
                        onAction = onAddGoal
                    )
                }
            } else {
                items(savingGoals, key = { it.id }) { goal ->
                    SavingGoalCard(
                        goal = goal,
                        onTopUp = { onTopUpGoal(goal) },
                        onEdit = { onEditGoal(goal) }
                    )
                }
            }
        } else {
            // --- RECURRING TRANSACTIONS SECTION ---
            item(key = "rec_month_bar") {
                MonthSelectorBar(
                    monthLabel = monthLabel,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth
                )
            }

            item(key = "rec_header_card") {
                val monthlyFixedExpense = recurringTemplates
                    .filter { it.active && it.parsedType == TransactionType.EXPENSE }
                    .sumOf { it.amount }
                val monthlyFixedIncome = recurringTemplates
                    .filter { it.active && it.parsedType == TransactionType.INCOME }
                    .sumOf { it.amount }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Ringkasan Rutin Aktif",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Masuk ${FinanceFormatters.formatCompactRupiah(monthlyFixedIncome)} • Keluar ${FinanceFormatters.formatCompactRupiah(monthlyFixedExpense)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = onAddRecurring,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("add_recurring_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Jadwal Baru", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (pendingRecurring.isNotEmpty()) {
                            Button(
                                onClick = { onApplyRecurring(pendingRecurring) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("apply_all_recurring_button")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "${stringResource(R.string.rec_apply_all)} (${pendingRecurring.size}) ke $monthLabel",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            if (recurringTemplates.isEmpty()) {
                item(key = "rec_empty") {
                    EmptyPlanningCard(
                        icon = Icons.Rounded.EventRepeat,
                        title = stringResource(R.string.rec_empty_title),
                        subtitle = stringResource(R.string.rec_empty_subtitle),
                        actionLabel = stringResource(R.string.rec_add_new),
                        onAction = onAddRecurring
                    )
                }
            } else {
                items(recurringTemplates, key = { it.id }) { tpl ->
                    RecurringTemplateCard(
                        template = tpl,
                        selectedYearMonth = selectedYearMonth,
                        onToggleActive = { active -> onToggleRecurringActive(tpl.id, active) },
                        onApplySingle = { onApplyRecurring(listOf(tpl)) },
                        onEdit = { onEditRecurring(tpl) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavingGoalCard(
    goal: SavingGoal,
    onTopUp: () -> Unit,
    onEdit: () -> Unit
) {
    val accent = CategoryVisuals.parseColor(goal.colorHex)
    val pct = (goal.progressFraction * 100f).roundToInt()
    val monthsRemaining = remember(goal.targetDateMillis) {
        val diffMillis = (goal.targetDateMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        max(1, ceil(diffMillis / (1000.0 * 60.0 * 60.0 * 24.0 * 30.0)).toInt())
    }
    val monthlyRecommended = if (goal.remainingAmount > 0L) goal.remainingAmount / monthsRemaining else 0L

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saving_goal_card_${goal.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CategoryVisuals.iconForKey(goal.iconKey),
                        contentDescription = goal.name,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (goal.reachedTarget) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Tercapai",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "Target: ${FinanceFormatters.formatDateFull(goal.targetDateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_goal_${goal.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.goal_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { goal.progressFraction },
                color = if (goal.reachedTarget) MaterialTheme.colorScheme.tertiary else accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${FinanceFormatters.formatRupiah(goal.currentAmount)} / ${FinanceFormatters.formatRupiah(goal.targetAmount)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (goal.reachedTarget) {
                            "Selamat! Target tabungan tercapai"
                        } else {
                            "Kurang ${FinanceFormatters.formatCompactRupiah(goal.remainingAmount)} • ±${FinanceFormatters.formatCompactRupiah(monthlyRecommended)}/bln"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (goal.reachedTarget) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!goal.reachedTarget) {
                    FilledTonalButton(
                        onClick = onTopUp,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("topup_goal_${goal.id}")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Setor ($pct%)", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Text(
                        text = "100%",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun RecurringTemplateCard(
    template: RecurringTemplate,
    selectedYearMonth: String,
    onToggleActive: (Boolean) -> Unit,
    onApplySingle: () -> Unit,
    onEdit: () -> Unit
) {
    val isExpense = template.parsedType == TransactionType.EXPENSE
    val appliedThisMonth = template.lastAppliedYearMonth == selectedYearMonth
    val amountColor = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recurring_card_${template.id}")
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(amountColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${template.dayOfMonth}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = amountColor
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tiap tgl ${template.dayOfMonth} • ${template.categoryName} • ${template.parsedPaymentMethod.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = template.active,
                    onCheckedChange = onToggleActive,
                    modifier = Modifier.testTag("toggle_recurring_${template.id}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val signed = if (isExpense) -template.amount else template.amount
                Text(
                    text = FinanceFormatters.formatRupiah(signed, showPlus = !isExpense),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = amountColor
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (appliedThisMonth) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Sudah Dicatat",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    } else if (template.active) {
                        FilledTonalButton(
                            onClick = onApplySingle,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("apply_single_${template.id}")
                        ) {
                            Text(stringResource(R.string.rec_apply_one), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.rec_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlanningCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit
) {
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(12.dp)) {
                Text(actionLabel)
            }
        }
    }
}
