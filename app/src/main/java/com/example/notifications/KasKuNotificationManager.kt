package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.BudgetAlert
import com.example.model.BudgetAlertSeverity
import com.example.model.ReminderPreferences
import com.example.util.FinanceFormatters
import java.util.Calendar
import java.util.Locale

object KasKuNotificationManager {
    private const val PREFS_NAME = "kasku_reminder_prefs"
    private const val KEY_DAILY_ENABLED = "daily_enabled"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_MINUTE = "reminder_minute"
    private const val KEY_BUDGET_ENABLED = "budget_alerts_enabled"

    const val CHANNEL_DAILY = "kasku_daily_reminder"
    const val CHANNEL_BUDGET = "kasku_budget_alert"

    private const val DAILY_REQUEST_CODE = 4101
    private const val DAILY_NOTIFICATION_ID = 4102
    private const val BUDGET_NOTIFICATION_BASE_ID = 4200

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val dailyChannel = NotificationChannel(
            CHANNEL_DAILY,
            "Pengingat Catat Harian",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Pengingat harian untuk mencatat pengeluaran di KasKu."
        }

        val budgetChannel = NotificationChannel(
            CHANNEL_BUDGET,
            "Peringatan Batas Anggaran",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifikasi ketika pengeluaran mendekati atau melebihi batas anggaran."
        }

        manager.createNotificationChannel(dailyChannel)
        manager.createNotificationChannel(budgetChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun loadPreferences(context: Context): ReminderPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ReminderPreferences(
            dailyReminderEnabled = prefs.getBoolean(KEY_DAILY_ENABLED, false),
            reminderHour = prefs.getInt(KEY_HOUR, 20).coerceIn(0, 23),
            reminderMinute = prefs.getInt(KEY_MINUTE, 0).coerceIn(0, 59),
            budgetAlertsEnabled = prefs.getBoolean(KEY_BUDGET_ENABLED, true)
        )
    }

    fun savePreferences(context: Context, preferences: ReminderPreferences) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DAILY_ENABLED, preferences.dailyReminderEnabled)
            .putInt(KEY_HOUR, preferences.reminderHour)
            .putInt(KEY_MINUTE, preferences.reminderMinute)
            .putBoolean(KEY_BUDGET_ENABLED, preferences.budgetAlertsEnabled)
            .apply()

        if (preferences.dailyReminderEnabled) {
            scheduleDailyReminder(context, preferences.reminderHour, preferences.reminderMinute)
        } else {
            cancelDailyReminder(context)
        }
    }

    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
        ensureChannels(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = dailyReminderPendingIntent(context)

        val triggerCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Inexact repeating alarm complies with Google Play Policy without sensitive exact-alarm permissions.
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerCalendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancelDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(dailyReminderPendingIntent(context))
    }

    fun showDailyReminderNotification(context: Context) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            DAILY_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Waktunya Catat Pengeluaran Hari Ini")
            .setContentText("Yuk luangkan 1 menit di KasKu agar grafik saldo harian tetap akurat.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(DAILY_NOTIFICATION_ID, notification)
        }
    }

    fun showBudgetAlertNotification(context: Context, alert: BudgetAlert) {
        val prefs = loadPreferences(context)
        if (!prefs.budgetAlertsEnabled || !hasNotificationPermission(context)) return
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            BUDGET_NOTIFICATION_BASE_ID + alert.key.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isExceeded = alert.severity == BudgetAlertSeverity.EXCEEDED_100
        val title = if (isExceeded) {
            "Anggaran ${alert.title} Terlampaui (${alert.percentage}%)"
        } else {
            "Anggaran ${alert.title} Hampir Penuh (${alert.percentage}%)"
        }
        val body = "Terpakai ${FinanceFormatters.formatRupiah(alert.spent)} dari batas ${FinanceFormatters.formatRupiah(alert.limit)}."

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(BUDGET_NOTIFICATION_BASE_ID + (alert.key.hashCode() and 0xFF), notification)
        }
    }

    fun formatTime(hour: Int, minute: Int): String =
        String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    private fun dailyReminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            DAILY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
