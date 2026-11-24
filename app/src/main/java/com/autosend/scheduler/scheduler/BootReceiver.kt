package com.autosend.scheduler.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autosend.scheduler.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
        if (intent.action !in validActions) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(appContext).taskDao()
                val enabledTasks = dao.getEnabledTasks()
                enabledTasks.forEach { task ->
                    val nextTrigger = ScheduleManager.computeNextTriggerMillis(task)
                    val updated = task.copy(nextTriggerAtMillis = nextTrigger)
                    dao.updateTask(updated)
                    ScheduleManager.scheduleAlarm(appContext, updated)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
