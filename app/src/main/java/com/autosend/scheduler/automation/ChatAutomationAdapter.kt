package com.autosend.scheduler.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.autosend.scheduler.data.ChatPlatform
import com.autosend.scheduler.data.ScheduledTaskEntity

enum class AutomationStage {
    LAUNCHING,
    SEARCHING_GROUP,
    SELECTING_GROUP,
    VERIFYING_CHAT_HEADER,
    ENTERING_MESSAGE,
    READY_TO_SEND,
    DONE
}

data class AutomationSession(
    val task: ScheduledTaskEntity,
    val hasImages: Boolean,
    var stage: AutomationStage = AutomationStage.LAUNCHING,
    var startedAtMillis: Long = System.currentTimeMillis(),
    var lastActionAtMillis: Long = 0L,
    var searchEntered: Boolean = false,
    var messageEntered: Boolean = false,
    var retryCount: Int = 0
)

sealed class AutomationStepResult {
    data object Waiting : AutomationStepResult()
    data class Progress(val nextStage: AutomationStage, val note: String) : AutomationStepResult()
    data class Completed(val note: String) : AutomationStepResult()
    data class DryRunReady(val note: String) : AutomationStepResult()
    data class AbortSafety(val reason: String) : AutomationStepResult()
    data class Failed(val reason: String) : AutomationStepResult()
}

/**
 * Giao diện chuẩn (Adapter Pattern) cho mọi ứng dụng chat.
 * Khi cần mở rộng sang Telegram, Messenger, Viber, WhatsApp...
 * chỉ cần tạo lớp mới kế thừa ChatAutomationAdapter và đăng ký trong AdapterRegistry.
 */
interface ChatAutomationAdapter {
    val platform: ChatPlatform

    fun isAppInstalled(context: Context): Boolean {
        return context.packageManager.getLaunchIntentForPackage(platform.packageName) != null
    }

    /** Tạo Intent khởi chạy app chat (dùng Share Intent nếu có hình ảnh, hoặc Launch Intent nếu chỉ có chữ) */
    fun buildStartIntent(context: Context, task: ScheduledTaskEntity): Intent?

    /** Xử lý từng bước trên cây giao diện Accessibility của ứng dụng chat đích */
    fun processScreen(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        session: AutomationSession
    ): AutomationStepResult
}

object AdapterRegistry {
    private val adapters: Map<ChatPlatform, ChatAutomationAdapter> = mapOf(
        ChatPlatform.ZALO to ZaloAutomationAdapter()
        // Mở rộng trong tương lai:
        // ChatPlatform.TELEGRAM to TelegramAutomationAdapter(),
        // ChatPlatform.MESSENGER to MessengerAutomationAdapter(),
    )

    fun getAdapter(platform: ChatPlatform): ChatAutomationAdapter? = adapters[platform]
}
