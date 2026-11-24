package com.autosend.scheduler.automation

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.autosend.scheduler.data.AppDatabase
import com.autosend.scheduler.data.ExecutionLogEntity
import com.autosend.scheduler.data.ExecutionStatus
import com.autosend.scheduler.data.ScheduleType
import com.autosend.scheduler.data.ScheduledTaskEntity
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
 * Điều phối phiên tự động hóa:
 * - Lưu hàng đợi chờ mở khóa vào SharedPreferences (không bị mất khi Android dọn RAM).
 * - Đánh thức màn hình và khởi chạy qua AutomationTrampolineActivity + Full-Screen Intent
 *   để hoạt động hoàn toàn tự động khi tắt màn hình (chế độ không khóa PIN) hoặc tự chạy ngay khi mở khóa.
 */
object AutomationOrchestrator {
    private const val SESSION_TIMEOUT_MS = 45_000L
    private const val PREFS_NAME = "autosend_runtime_prefs"
    private const val KEY_PENDING_UNLOCK_TASK_ID = "pending_unlock_task_id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _activeSession = MutableStateFlow<AutomationSession?>(null)
    val activeSession: StateFlow<AutomationSession?> = _activeSession

    private var timeoutRunnable: Runnable? = null

    private fun savePendingUnlockTaskId(context: Context, taskId: Long?) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (taskId == null) {
            prefs.edit().remove(KEY_PENDING_UNLOCK_TASK_ID).apply()
        } else {
            prefs.edit().putLong(KEY_PENDING_UNLOCK_TASK_ID, taskId).apply()
        }
    }

    private fun consumePendingUnlockTaskId(context: Context): Long? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_PENDING_UNLOCK_TASK_ID, -1L)
        if (id > 0L) {
            prefs.edit().remove(KEY_PENDING_UNLOCK_TASK_ID).apply()
            return id
        }
        return null
    }

    /**
     * Được gọi từ AlarmReceiver hoặc nút Chạy thử.
     * Mở AutomationTrampolineActivity (kèm Full-Screen Intent dự phòng) để bật màn hình & vượt chặn nền.
     */
    fun triggerTaskById(context: Context, taskId: Long, forceDryRun: Boolean = false) {
        val appContext = context.applicationContext
        wakeScreenBriefly(appContext)
        savePendingUnlockTaskId(appContext, taskId)

        val trampolineIntent = AutomationTrampolineActivity.createIntent(
            context = appContext,
            taskId = taskId,
            forceDryRun = forceDryRun
        )

        // Thử mở trực tiếp Trampoline Activity (hoạt động mượt khi có quyền Overlay/AlarmClock)
        val startedDirectly = runCatching {
            appContext.startActivity(trampolineIntent)
            true
        }.getOrDefault(false)

        // Luôn gửi thêm Full-Screen Intent notification nếu không thể mở trực tiếp từ nền
        if (!startedDirectly) {
            scope.launch {
                val task = AppDatabase.getInstance(appContext).taskDao().getTaskById(taskId)
                val groupName = task?.targetGroupName ?: "Nhóm Zalo"
                NotificationHelper.notifyFullScreenWakeup(
                    context = appContext,
                    notificationId = taskId.toInt() + 2000,
                    title = "Đến giờ gửi Zalo — $groupName",
                    body = "Đang chuẩn bị tự động gửi tin nhắn kèm hình ảnh...",
                    fullScreenIntent = trampolineIntent
                )
            }
        }
    }

    fun markWaitingUnlock(context: Context, taskId: Long) {
        val appContext = context.applicationContext
        savePendingUnlockTaskId(appContext, taskId)
        scope.launch {
            val task = AppDatabase.getInstance(appContext).taskDao().getTaskById(taskId) ?: return@launch
            recordOutcome(
                context = appContext,
                task = task,
                status = ExecutionStatus.WAITING_UNLOCK,
                detail = "Đã đến giờ gửi tới nhóm '${task.targetGroupName}' nhưng máy đang có khóa mật khẩu/vân tay. Mở khóa để tự động chạy ngay.",
                rescheduleNow = false
            )
            val trampolineIntent = AutomationTrampolineActivity.createIntent(appContext, taskId, false)
            NotificationHelper.notifyFullScreenWakeup(
                context = appContext,
                notificationId = taskId.toInt() + 2000,
                title = "Đến giờ gửi Zalo — Mở khóa để gửi",
                body = "Nhóm: ${task.targetGroupName}. Vừa mở khóa máy sẽ tự động gửi kèm ảnh.",
                fullScreenIntent = trampolineIntent
            )
        }
    }

    /**
     * Được gọi từ AutoSendAccessibilityService ngay khoảnh khắc người dùng mở khóa điện thoại (ACTION_USER_PRESENT).
     */
    fun onDeviceUnlocked(context: Context) {
        val appContext = context.applicationContext
        val pendingId = consumePendingUnlockTaskId(appContext) ?: return
        val trampolineIntent = AutomationTrampolineActivity.createIntent(appContext, pendingId, false)
        mainHandler.postDelayed({
            runCatching { appContext.startActivity(trampolineIntent) }
        }, 350L)
    }

    /**
     * Thực thi gửi Zalo từ foreground (AutomationTrampolineActivity) -> không bị Android chặn nền và giữ nguyên 100% ảnh đính kèm.
     */
    fun executeFromForeground(activity: Activity, taskId: Long, forceDryRun: Boolean = false) {
        val appContext = activity.applicationContext
        savePendingUnlockTaskId(appContext, null)
        scope.launch {
            val dao = AppDatabase.getInstance(appContext).taskDao()
            val task = dao.getTaskById(taskId) ?: return@launch
            val effectiveTask = if (forceDryRun) task.copy(dryRun = true) else task
            startTaskInternal(appContext, effectiveTask)
        }
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
                    "Không mở được ${platform.displayName}: ${err.message}"
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
        if (status != ExecutionStatus.WAITING_UNLOCK) {
            NotificationHelper.notify(
                context,
                task.id.toInt() + 3000,
                "${status.label} — ${task.targetGroupName}",
                detail,
                isHighPriority = isAlert
            )
        }
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
                wakeLock.acquire(10_000L)
            }
        }
    }
}
