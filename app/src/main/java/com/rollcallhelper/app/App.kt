package com.rollcallhelper.app

import android.app.Application
import com.rollcallhelper.app.notify.AlarmNotifier
import com.rollcallhelper.app.notify.RunNotifier
import com.rollcallhelper.app.schedule.SignInScheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RunNotifier.ensureChannel(this)
        AlarmNotifier.ensureChannel(this)
        SignInScheduler.scheduleNext(this)
    }
}
