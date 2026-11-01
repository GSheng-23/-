package com.rollcallhelper.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

object SignInScheduler {
    private const val TAG = "RollCall"
    const val ACTION_TRIGGER = "com.rollcallhelper.app.action.TRIGGER"
    const val EXTRA_FROM_ALARM = "from_alarm"
    const val EXTRA_SCHEDULE_ONLY = "schedule_only"
    const val EXTRA_TEST_AFTER_SECONDS = "test_after_seconds"
    private const val REQ = 2103
    private const val PREFS = "schedule"
    private const val KEY_NEXT = "next_trigger"

    fun scheduleNext(context: Context) {
        val now = LocalDateTime.now()
        var next = now.withHour(21).withMinute(3).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        scheduleAt(context, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    fun scheduleAt(context: Context, triggerAtMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_TRIGGER)
        val pi = PendingIntent.getBroadcast(
            context,
            REQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                Log.w(TAG, "exact alarm not allowed; scheduled inexact")
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_NEXT, triggerAtMillis)
                .apply()
            Log.i(TAG, "next alarm at ${format(triggerAtMillis)}")
        } catch (e: SecurityException) {
            Log.e(TAG, "schedule failed", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_NEXT, triggerAtMillis)
                .apply()
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun nextTrigger(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_NEXT, 0L)

    fun nextTriggerText(context: Context): String {
        val at = nextTrigger(context)
        if (at <= 0L) return "未设置"
        val text = format(at)
        return if (canScheduleExact(context)) text else "$text（未授权精确闹钟，可能延迟）"
    }

    private fun format(at: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(at))
}
