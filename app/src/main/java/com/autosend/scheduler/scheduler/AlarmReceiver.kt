package com.autosend.scheduler.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autosend.scheduler.automation.AutomationOrchestrator

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ScheduleManager.EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) return
        AutomationOrchestrator.triggerTaskById(context.applicationContext, taskId)
    }
}
