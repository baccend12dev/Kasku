package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = KasKuNotificationManager.loadPreferences(context)
        if (prefs.dailyReminderEnabled) {
            KasKuNotificationManager.showDailyReminderNotification(context)
        }
    }
}
