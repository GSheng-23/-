package com.rollcallhelper.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.rollcallhelper.app.MainActivity
import com.rollcallhelper.app.notify.AlarmNotifier
import java.time.LocalTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SignInScheduler.ACTION_TRIGGER) return

        // 先排下一次，避免本次异常导致以后不再触发。
        SignInScheduler.scheduleNext(context)

        val now = LocalTime.now()
        val inWindow = !now.isBefore(LocalTime.of(21, 0)) && now.isBefore(LocalTime.of(23, 55))
        if (!inWindow) {
            Log.i("RollCall", "alarm outside window: $now")
            return
        }

        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(SignInScheduler.EXTRA_FROM_ALARM, true)
        }

        // 点亮屏幕：锁屏时仅靠通知会被勿扰模式/锁屏通知设置挡住。
        try {
            val power = context.getSystemService(PowerManager::class.java)
            @Suppress("DEPRECATION")
            val wakeLock = power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "RollCall:alarm"
            )
            wakeLock.setReferenceCounted(false)
            wakeLock.acquire(10_000)
        } catch (e: Exception) {
            Log.w("RollCall", "wake screen failed", e)
        }

        // 锁屏/后台启动 Activity 可能被系统拦截，通知是兜底。
        AlarmNotifier.show(context, launch)

        try {
            context.startActivity(launch)
            Log.i("RollCall", "alarm started MainActivity")
        } catch (e: Exception) {
            Log.e("RollCall", "alarm startActivity failed", e)
        }
    }
}
