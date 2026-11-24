package com.autosend.scheduler

import android.app.Application
import com.autosend.scheduler.util.NotificationHelper

class AutoSendApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }
}
