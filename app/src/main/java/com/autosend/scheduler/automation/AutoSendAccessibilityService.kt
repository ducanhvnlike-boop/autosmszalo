package com.autosend.scheduler.automation

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.autosend.scheduler.data.ExecutionStatus

class AutoSendAccessibilityService : AccessibilityService() {

    private var unlockReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerUnlockReceiver()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val session = AutomationOrchestrator.activeSession.value ?: return
        val pkg = event?.packageName?.toString() ?: return
        val platform = session.task.chatPlatform()
        if (pkg != platform.packageName) return

        // Giãn cách tối thiểu 450ms giữa các bước để giao diện Zalo kịp render ổn định
        val now = System.currentTimeMillis()
        if (now - session.lastActionAtMillis < 450L) return

        val root = rootInActiveWindow ?: return
        val adapter = AdapterRegistry.getAdapter(platform) ?: return

        when (val result = adapter.processScreen(this, root, session)) {
            is AutomationStepResult.Waiting -> Unit
            is AutomationStepResult.Progress -> {
                session.stage = result.nextStage
                session.lastActionAtMillis = now
                session.retryCount = 0
            }
            is AutomationStepResult.Completed -> {
                session.stage = AutomationStage.DONE
                AutomationOrchestrator.finishSession(this, ExecutionStatus.SUCCESS, result.note)
            }
            is AutomationStepResult.DryRunReady -> {
                session.stage = AutomationStage.DONE
                AutomationOrchestrator.finishSession(this, ExecutionStatus.DRY_RUN_READY, result.note)
            }
            is AutomationStepResult.AbortSafety -> {
                session.stage = AutomationStage.DONE
                AutomationOrchestrator.finishSession(this, ExecutionStatus.ABORTED_SAFETY, result.reason)
            }
            is AutomationStepResult.Failed -> {
                session.stage = AutomationStage.DONE
                AutomationOrchestrator.finishSession(this, ExecutionStatus.FAILED, result.reason)
            }
        }
    }

    override fun onInterrupt() {
        // Không cần xử lý đặc biệt khi hệ thống tạm ngắt sự kiện
    }

    override fun onDestroy() {
        unregisterUnlockReceiver()
        super.onDestroy()
    }

    private fun registerUnlockReceiver() {
        if (unlockReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_PRESENT) {
                    AutomationOrchestrator.onDeviceUnlocked(context)
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_EXPORTED
        )
        unlockReceiver = receiver
    }

    private fun unregisterUnlockReceiver() {
        unlockReceiver?.let {
            runCatching { unregisterReceiver(it) }
        }
        unlockReceiver = null
    }
}
