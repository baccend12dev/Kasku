package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.export.PdfReportGenerator
import com.example.model.MonthlyReportData
import com.example.notifications.KasKuNotificationManager
import com.example.ui.components.DailyBalanceLineChart
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.CategoryVisuals
import com.example.util.FinanceFormatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ReportScreen(
    report: MonthlyReportData,
    userDisplayName: String,
    userEmail: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onNotify: (String) -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    val userLabel = remember(userDisplayName, userEmail) {
        when {
            userEmail.isNotBlank() -> userEmail
            userDisplayName.isNotBlank() -> userDisplayName
            else -> "Pengguna KasKu"
        }
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isExporting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        PdfReportGenerator.writeReportToStream(report, userLabel, stream)
                    } ?: error("Tidak dapat membuka lokasi file.")
                }
            }
            isExporting = false
            result.onSuccess {
                onNotify(context.getString(R.string.report_saved_success))
            }.onFailure { err ->
                onNotify(err.localizedMessage ?: context.getString(R.string.report_saved_error))
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "month_bar") {
            MonthSelectorBar(
                monthLabel = report.monthLabel,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth
            )
        }

        // PDF Export Hero Action Card
        item(key = "pdf_export_card") {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Ekspor Laporan PDF",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Termasuk ringkasan saldo, grafik harian, kategori, dan rincian ${report.transactions.size} transaksi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            createPdfLauncher.launch(PdfReportGenerator.suggestedFileName(report.yearMonth))
                        },
                        enabled = !isExporting,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_save_pdf_button")
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.report_generating))
                        } else {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.report_export_save),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            isExporting = true
                            scope.launch {
                                val shareResult = withContext(Dispatchers.IO) {
                                    runCatching {
                                        PdfReportGenerator.writeReportToCacheFile(context, report, userLabel)
                                    }
                                }
                                isExporting = false
                                shareResult.onSuccess { pdfFile ->
                                    val authority = "${context.packageName}.fileprovider"
                                    val contentUri = FileProvider.getUriForFile(context, authority, pdfFile)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, contentUri)
                                        putExtra(
                                            Intent.EXTRA_SUBJECT,
                                            "Laporan Keuangan KasKu - ${report.monthLabel}"
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            context.getString(R.string.report_share_chooser)
                                        )
                                    )
                                }.onFailure { err ->
                                    onNotify(err.localizedMessage ?: "Gagal membuat file PDF.")
                                }
                            }
                        },
                        enabled = !isExporting,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_share_pdf_button")
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.report_export_share),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Report Executive Preview Card
        item(key = "report_summary_preview") {
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
                    Text(
                        text = "Pratinjau Laporan ${report.monthLabel}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SummaryLine("Saldo Awal Bulan", FinanceFormatters.formatRupiah(report.openingBalance), MaterialTheme.colorScheme.onSurface)
                    SummaryLine("Total Pemasukan", FinanceFormatters.formatRupiah(report.totalIncome, showPlus = true), MaterialTheme.colorScheme.tertiary)
                    SummaryLine("Total Pengeluaran", FinanceFormatters.formatRupiah(-report.totalExpense), MaterialTheme.colorScheme.error)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SummaryLine(
                        "Saldo Akhir Bulan",
                        FinanceFormatters.formatRupiah(report.closingBalance),
                        if (report.closingBalance >= 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        boldLabel = true
                    )

                    if (report.expenseByCategory.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Pengeluaran Terbesar",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        report.expenseByCategory.take(5).forEach { cat ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(CategoryVisuals.parseColor(cat.colorHex))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${cat.categoryName} (${(cat.percentage * 100f).roundToInt()}%)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    text = FinanceFormatters.formatRupiah(cat.totalAmount),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Daily Balance Chart Preview inside Report
        item(key = "report_chart_preview") {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Grafik Saldo Harian (Tercetak di PDF)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(10.dp))
                    DailyBalanceLineChart(points = report.dailyPoints)
                }
            }
        }

        // Daily Reminder & Budget Alert Settings Card
        item(key = "reminder_settings_card") {
            ReminderSettingsCard(onNotify = onNotify)
        }

        // Account & Sign Out Card
        item(key = "account_card") {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userDisplayName.ifBlank { "Akun Google" },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = userEmail.ifBlank { "Tersinkronisasi ke Cloud" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onSignOutClick,
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.auth_sign_out))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    valueColor: Color,
    boldLabel: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (boldLabel) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (boldLabel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = valueColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderSettingsCard(onNotify: (String) -> Unit) {
    val context = LocalContext.current
    var prefs by remember { mutableStateOf(KasKuNotificationManager.loadPreferences(context)) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingPermissionAction?.invoke()
        } else {
            onNotify("Izin notifikasi ditolak. Aktifkan di Pengaturan perangkat untuk menerima pengingat.")
        }
        pendingPermissionAction = null
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (KasKuNotificationManager.hasNotificationPermission(context)) {
            action()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingPermissionAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder_settings_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.reminder_section_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Daily Reminder Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.reminder_daily_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.reminder_daily_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.dailyReminderEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            runWithNotificationPermission {
                                val updated = prefs.copy(dailyReminderEnabled = true)
                                prefs = updated
                                KasKuNotificationManager.savePreferences(context, updated)
                                onNotify(
                                    "Pengingat harian aktif pukul ${
                                        KasKuNotificationManager.formatTime(updated.reminderHour, updated.reminderMinute)
                                    }"
                                )
                            }
                        } else {
                            val updated = prefs.copy(dailyReminderEnabled = false)
                            prefs = updated
                            KasKuNotificationManager.savePreferences(context, updated)
                            onNotify("Pengingat harian dinonaktifkan")
                        }
                    },
                    modifier = Modifier.testTag("daily_reminder_switch")
                )
            }

            if (prefs.dailyReminderEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.reminder_time_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.testTag("pick_custom_reminder_time_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = KasKuNotificationManager.formatTime(prefs.reminderHour, prefs.reminderMinute),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(8 to 0, 12 to 0, 19 to 0, 20 to 0, 21 to 0).forEach { (h, m) ->
                            val selected = prefs.reminderHour == h && prefs.reminderMinute == m
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = prefs.copy(reminderHour = h, reminderMinute = m)
                                    prefs = updated
                                    KasKuNotificationManager.savePreferences(context, updated)
                                    onNotify("Jam pengingat diatur ke ${KasKuNotificationManager.formatTime(h, m)}")
                                },
                                label = {
                                    Text(
                                        text = KasKuNotificationManager.formatTime(h, m),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Budget Limit Alerts Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.reminder_budget_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.reminder_budget_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.budgetAlertsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            runWithNotificationPermission {
                                val updated = prefs.copy(budgetAlertsEnabled = true)
                                prefs = updated
                                KasKuNotificationManager.savePreferences(context, updated)
                                onNotify("Peringatan batas anggaran diaktifkan")
                            }
                        } else {
                            val updated = prefs.copy(budgetAlertsEnabled = false)
                            prefs = updated
                            KasKuNotificationManager.savePreferences(context, updated)
                            onNotify("Peringatan batas anggaran dinonaktifkan")
                        }
                    },
                    modifier = Modifier.testTag("budget_alert_switch")
                )
            }

            OutlinedButton(
                onClick = {
                    runWithNotificationPermission {
                        KasKuNotificationManager.showDailyReminderNotification(context)
                        onNotify("Notifikasi uji dikirim ke bilah status perangkat")
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("test_notification_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reminder_test_button))
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = prefs.reminderHour,
            initialMinute = prefs.reminderMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Pilih Jam Pengingat") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = prefs.copy(
                            reminderHour = timePickerState.hour,
                            reminderMinute = timePickerState.minute
                        )
                        prefs = updated
                        KasKuNotificationManager.savePreferences(context, updated)
                        showTimePicker = false
                        onNotify(
                            "Jam pengingat diatur ke ${
                                KasKuNotificationManager.formatTime(updated.reminderHour, updated.reminderMinute)
                            }"
                        )
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.tx_cancel))
                }
            }
        )
    }
}
