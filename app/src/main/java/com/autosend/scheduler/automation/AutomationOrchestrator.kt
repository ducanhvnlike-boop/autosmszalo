package com.autosend.scheduler.automation

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.autosend.scheduler.data.AppDatabase
import com.autosend.scheduler.data.ExecutionLogEntity
import com.autosend.scheduler.data.ExecutionStatus
import com.autosend.scheduler.data.ScheduledTaskEntity
import com.autosend.scheduler.data.ScheduleType
import com.autosend.scheduler.scheduler.ScheduleManager
import com.autosend.scheduler.util.DeviceSetupHelper
import com.autosend.scheduler.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Điều phối phiên tự động hóa: quản lý hàng đợi chờ mở khóa, timeout an toàn 45 giây,
 * ghi lịch sử vào Room DB và tự động lên lịch lại cho các tác vụ lặp hằng ngày.
 */
object AutomationOrchestrator {
    private const val SESSION_TIMEOUT_MS = 45_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _activeSession = MutableStateFlow<AutomationSession?>(null)
    val activeSession: StateFlow<AutomationSession?> = _activeSession

    @Volatile
    private var pendingUnlockTaskId: Long? = null

    private var timeoutRunnable: Runnable? = null

    fun triggerTaskById(context: Context, taskId: Long, forceDryRun: Boolean = false) {
        val appContext = context.applicationContext
        scope.launch {
            val dao = AppDatabase.getInstance(appContext).taskDao()
            val task = dao.getTaskById(taskId) ?: return@launch
            val effectiveTask = if (forceDryRun) task.copy(dryRun = true) else task
            startTaskInternal(appContext, effectiveTask)
        }
    }

    fun onDeviceUnlocked(context: Context) {
        val taskId = pendingUnlockTaskId ?: return
        pendingUnlockTaskId = null
        triggerTaskById(context.applicationContext, taskId)
    }

    private suspend fun startTaskInternal(context: Context, task: ScheduledTaskEntity) {
        val platform = task.chatPlatform()
        val adapter = AdapterRegistry.getAdapter(platform)
        if (adapter == null) {
            recordOutcome(
                context,
                task,
                ExecutionStatus.FAILED,
                "Nền tảng ${platform.displayName} chưa được hỗ trợ ở phiên bản này."
            )
            return
        }

        if (!adapter.isAppInstalled(context)) {
            recordOutcome(
                context,
                task,
                ExecutionStatus.FAILED,
                "Không tìm thấy ứng dụng ${platform.displayName} trên điện thoại."
            )
            return
        }

        if (!DeviceSetupHelper.isAccessibilityServiceEnabled(context)) {
            recordOutcome(
                context,
                task,
                ExecutionStatus.FAILED,
                "Dịch vụ Hỗ trợ thao tác (Accessibility Service) chưa được bật."
            )
            NotificationHelper.notify(
                context,
                task.id.toInt() + 1000,
                "Chưa bật quyền tự động gửi Zalo",
                "Hãy vào tab Thiết lập trong AutoSend Zalo để bật Accessibility Service.",
                isHighPriority = true
            )
            return
        }

        wakeScreenBriefly(context)

        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isKeyguardLocked) {
            pendingUnlockTaskId = task.id
            recordOutcome(
                context,
                task,
                ExecutionStatus.WAITING_UNLOCK,
                "Đã đến giờ gửi tới nhóm '${task.targetGroupName}' nhưng điện thoại đang khóa màn hình. Mở khóa để tự động chạy ngay.",
                rescheduleNow = false
            )
            NotificationHelper.notify(
                context,
                task.id.toInt() + 2000,
                "Đến giờ gửi Zalo — Mở khóa để gửi",
                "Nhóm: ${task.targetGroupName}. Ngay khi bạn mở khóa, ứng dụng sẽ tự động gửi.",
                isHighPriority = true
            )
            return
        }

        val startIntent = adapter.buildStartIntent(context, task)
        if (startIntent == null) {
            recordOutcome(
                context,
                task,
                ExecutionStatus.FAILED,
                "Không thể tạo lệnh mở ${platform.displayName} hoặc tệp ảnh không còn tồn tại."
            )
            return
        }

        val session = AutomationSession(
            task = task,
            hasImages = task.imagePaths().isNotEmpty(),
            stage = AutomationStage.LAUNCHING,
            startedAtMillis = System.currentTimeMillis()
        )
        _activeSession.value = session
        scheduleTimeoutWatchdog(context, session)

        mainHandler.post {
            runCatching {
                context.startActivity(startIntent)
            }.onFailure { err ->
                finishSession(
                    context,
                    ExecutionStatus.FAILED,
                    "Không mở được ${platform.displayName} từ nền (kiểm tra quyền hiển thị pop-up nền trên Xiaomi): ${err.message}"
                )
            }
        }
    }

    fun finishSession(context: Context, status: ExecutionStatus, detail: String) {
        val current = _activeSession.value ?: return
        _activeSession.value = null
        cancelTimeoutWatchdog()

        val appContext = context.applicationContext
        scope.launch {
            recordOutcome(appContext, current.task, status, detail, rescheduleNow = true)
        }
    }

    private suspend fun recordOutcome(
        context: Context,
        task: ScheduledTaskEntity,
        status: ExecutionStatus,
        detail: String,
        rescheduleNow: Boolean = true
    ) {
        val dao = AppDatabase.getInstance(context).taskDao()
        val now = System.currentTimeMillis()

        dao.insertLog(
            ExecutionLogEntity(
                taskId = task.id,
                platform = task.platform,
                targetGroupName = task.targetGroupName,
                status = status.name,
                detail = detail,
                timestampMillis = now
            )
        )

        val updatedTask = if (rescheduleNow && task.scheduleMode() == ScheduleType.DAILY_RECURRING && task.isEnabled) {
            val nextTrigger = ScheduleManager.computeNextTriggerMillis(task, now + 60_000L)
            task.copy(
                lastRunStatus = status.name,
                lastRunAtMillis = now,
                nextTriggerAtMillis = nextTrigger
            )
        } else if (rescheduleNow && task.scheduleMode() == ScheduleType.ONE_TIME) {
            val shouldDisable = status == ExecutionStatus.SUCCESS || status == ExecutionStatus.DRY_RUN_READY
            task.copy(
                isEnabled = if (shouldDisable) false else task.isEnabled,
                lastRunStatus = status.name,
                lastRunAtMillis = now
            )
        } else {
            task.copy(
                lastRunStatus = status.name,
                lastRunAtMillis = now
            )
        }

        dao.updateTask(updatedTask)

        if (rescheduleNow && updatedTask.isEnabled && updatedTask.scheduleMode() == ScheduleType.DAILY_RECURRING) {
            ScheduleManager.scheduleAlarm(context, updatedTask)
        }

        val isAlert = status == ExecutionStatus.ABORTED_SAFETY || status == ExecutionStatus.FAILED
        NotificationHelper.notify(
            context,
            task.id.toInt() + 3000,
            "${status.label} — ${task.targetGroupName}",
            detail,
            isHighPriority = isAlert
        )
    }

    private fun scheduleTimeoutWatchdog(context: Context, session: AutomationSession) {
        cancelTimeoutWatchdog()
        val runnable = Runnable {
            if (_activeSession.value?.task?.id == session.task.id) {
                finishSession(
                    context,
                    ExecutionStatus.FAILED,
                    "Quá thời gian chờ (${SESSION_TIMEOUT_MS / 1000}s) ở bước ${session.stage.name}. " +
                        "Có thể giao diện Zalo đang ở màn hình khác hoặc chưa đăng nhập."
                )
            }
        }
        timeoutRunnable = runnable
        mainHandler.postDelayed(runnable, SESSION_TIMEOUT_MS)
    }

    private fun cancelTimeoutWatchdog() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly(context: Context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) {
                val wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "AutoSendZalo:WakeLock"
                )
                wakeLock.acquire(8_000L)
            }
        }
    }
}
