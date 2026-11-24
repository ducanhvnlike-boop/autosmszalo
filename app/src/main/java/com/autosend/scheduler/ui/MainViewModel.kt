package com.autosend.scheduler.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosend.scheduler.automation.AutomationOrchestrator
import com.autosend.scheduler.data.AppDatabase
import com.autosend.scheduler.data.ExecutionLogEntity
import com.autosend.scheduler.data.ScheduledTaskEntity
import com.autosend.scheduler.scheduler.ScheduleManager
import com.autosend.scheduler.util.MediaStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).taskDao()

    val tasks: StateFlow<List<ScheduledTaskEntity>> = dao.observeAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs: StateFlow<List<ExecutionLogEntity>> = dao.observeRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedGroupNames: StateFlow<List<String>> = dao.observeSavedGroupNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun getTaskById(id: Long): ScheduledTaskEntity? = withContext(Dispatchers.IO) {
        dao.getTaskById(id)
    }

    suspend fun importPickedImages(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        uris.mapNotNull { uri -> MediaStorageHelper.copyUriToInternalStorage(ctx, uri) }
    }

    fun saveTask(task: ScheduledTaskEntity, onSaved: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val nextTrigger = ScheduleManager.computeNextTriggerMillis(task)
            val prepared = task.copy(nextTriggerAtMillis = nextTrigger)
            val savedId = if (prepared.id == 0L) {
                dao.insertTask(prepared)
            } else {
                dao.updateTask(prepared)
                prepared.id
            }
            val finalTask = prepared.copy(id = savedId)
            if (finalTask.isEnabled) {
                ScheduleManager.scheduleAlarm(ctx, finalTask)
            } else {
                ScheduleManager.cancelAlarm(ctx, finalTask.id)
            }
            withContext(Dispatchers.Main) { onSaved() }
        }
    }

    fun toggleTaskEnabled(task: ScheduledTaskEntity, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val nextTrigger = if (enabled) ScheduleManager.computeNextTriggerMillis(task) else task.nextTriggerAtMillis
            val updated = task.copy(isEnabled = enabled, nextTriggerAtMillis = nextTrigger)
            dao.updateTask(updated)
            if (enabled) {
                ScheduleManager.scheduleAlarm(ctx, updated)
            } else {
                ScheduleManager.cancelAlarm(ctx, updated.id)
            }
        }
    }

    fun deleteTask(task: ScheduledTaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            ScheduleManager.cancelAlarm(ctx, task.id)
            MediaStorageHelper.deleteFiles(task.imagePaths())
            dao.deleteTask(task)
        }
    }

    fun runDryTestNow(task: ScheduledTaskEntity) {
        AutomationOrchestrator.triggerTaskById(
            context = getApplication(),
            taskId = task.id,
            forceDryRun = true
        )
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearLogs()
        }
    }
}
