package com.rollcallhelper.app

import android.app.Application
import com.rollcallhelper.app.notify.RunNotifier

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RunNotifier.ensureChannel(this)
    }
}
