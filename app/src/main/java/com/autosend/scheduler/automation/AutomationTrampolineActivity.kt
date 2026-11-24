package com.autosend.scheduler.automation

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

/**
 * Activity trung gian nhẹ giúp:
 * 1. Bật sáng màn hình khi đang tắt (kể cả khi máy ở chế độ khóa vuốt / không đặt PIN).
 * 2. Tự động gỡ màn hình khóa vuốt (requestDismissKeyguard) hoặc bật khung nhập PIN/vân tay ngay lập tức.
 * 3. Khi đã mở khóa xong -> chạy ngay lệnh gửi Zalo từ foreground để không bị Android 12-14 chặn nền.
 */
class AutomationTrampolineActivity : Activity() {

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_FORCE_DRY_RUN = "extra_force_dry_run"

        fun createIntent(context: Context, taskId: Long, forceDryRun: Boolean = false): Intent {
            return Intent(context, AutomationTrampolineActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_FORCE_DRY_RUN, forceDryRun)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
        }
    }

    private var dispatched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWakeFlags()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatched = false
        handleIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun configureWakeFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    private fun handleIntent(intent: Intent?) {
        val taskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L
        val forceDryRun = intent?.getBooleanExtra(EXTRA_FORCE_DRY_RUN, false) ?: false
        if (taskId <= 0L) {
            finish()
            return
        }

        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isKeyguardLocked) {
            launchNow(taskId, forceDryRun)
            return
        }

        // Nếu màn hình khóa dạng vuốt (không mật khẩu) -> hệ thống sẽ tự mở khóa ngay và gọi onDismissSucceeded.
        // Nếu có mật khẩu/vân tay -> hệ thống hiện khung mở khóa, vừa mở xong là tự chạy ngay lập tức.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        launchNow(taskId, forceDryRun)
                    }

                    override fun onDismissCancelled() {
                        AutomationOrchestrator.markWaitingUnlock(
                            this@AutomationTrampolineActivity,
                            taskId
                        )
                        finish()
                    }

                    override fun onDismissError() {
                        AutomationOrchestrator.markWaitingUnlock(
                            this@AutomationTrampolineActivity,
                            taskId
                        )
                        finish()
                    }
                }
            )
        } else {
            launchNow(taskId, forceDryRun)
        }
    }

    private fun launchNow(taskId: Long, forceDryRun: Boolean) {
        if (dispatched) return
        dispatched = true
        AutomationOrchestrator.executeFromForeground(
            activity = this,
            taskId = taskId,
            forceDryRun = forceDryRun
        )
        finish()
    }
}
