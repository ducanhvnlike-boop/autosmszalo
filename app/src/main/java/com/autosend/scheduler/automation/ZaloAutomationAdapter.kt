package com.autosend.scheduler.automation

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.autosend.scheduler.data.ChatPlatform
import com.autosend.scheduler.data.ScheduledTaskEntity
import com.autosend.scheduler.util.MediaStorageHelper

/**
 * Adapter điều khiển tự động ứng dụng Zalo (com.zing.zalo) với cơ chế chống gửi nhầm 3 lớp:
 * 1. Khớp chính xác tên nhóm (chuẩn hóa khoảng trắng), không chọn khớp một phần.
 * 2. Phát hiện trùng tên: nếu có > 1 kết quả trùng tên nhóm -> lập tức hủy để đảm bảo an toàn.
 * 3. Xác minh tiêu đề nhóm / vùng chọn trước khi bấm nút Gửi + hỗ trợ chế độ chạy thử (Dry-Run).
 */
class ZaloAutomationAdapter : ChatAutomationAdapter {

    override val platform: ChatPlatform = ChatPlatform.ZALO

    override fun buildStartIntent(context: Context, task: ScheduledTaskEntity): Intent? {
        val imagePaths = task.imagePaths()
        if (imagePaths.isNotEmpty()) {
            val uris = MediaStorageHelper.getShareableUris(context, imagePaths)
            if (uris.isEmpty()) return null

            // Cấp quyền đọc FileProvider cho Zalo
            uris.forEach { uri ->
                context.grantUriPermission(
                    platform.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    clipData = ClipData.newRawUri("image", uris.first())
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    val clip = ClipData.newRawUri("image_0", uris.first())
                    for (i in 1 until uris.size) {
                        clip.addItem(ClipData.Item(uris[i]))
                    }
                    clipData = clip
                }
            }

            if (task.messageText.isNotBlank()) {
                intent.putExtra(Intent.EXTRA_TEXT, task.messageText)
            }

            return intent.apply {
                setPackage(platform.packageName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

        // Trường hợp chỉ gửi tin nhắn văn bản: mở thẳng ứng dụng Zalo
        return context.packageManager.getLaunchIntentForPackage(platform.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    override fun processScreen(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        session: AutomationSession
    ): AutomationStepResult {
        return if (session.hasImages) {
            processShareImagesFlow(root, session)
        } else {
            processTextOnlyFlow(root, session)
        }
    }

    /**
     * Luồng gửi Hình ảnh + Tin nhắn (mở qua Share Intent vào màn hình Chia sẻ của Zalo):
     * Bước 1: Tìm ô Tìm kiếm trên màn hình Chia sẻ và nhập đúng tên nhóm.
     * Bước 2: Kiểm tra toàn bộ danh sách kết quả -> bắt buộc chỉ có ĐÚNG 1 kết quả khớp tên nhóm -> chọn.
     * Bước 3: Điền nội dung tin nhắn vào ô mô tả (nếu có).
     * Bước 4: Nếu Dry-Run thì dừng báo người dùng; ngược lại bấm nút Gửi/Chia sẻ.
     */
    private fun processShareImagesFlow(
        root: AccessibilityNodeInfo,
        session: AutomationSession
    ): AutomationStepResult {
        val target = normalize(session.task.targetGroupName)

        return when (session.stage) {
            AutomationStage.LAUNCHING,
            AutomationStage.SEARCHING_GROUP -> {
                val searchInput = findSearchEditText(root)
                if (searchInput != null) {
                    val currentText = normalize(searchInput.text?.toString().orEmpty())
                    if (currentText != target) {
                        clickOrFocus(searchInput)
                        setNodeText(searchInput, session.task.targetGroupName.trim())
                        session.searchEntered = true
                        AutomationStepResult.Progress(
                            AutomationStage.SELECTING_GROUP,
                            "Đã nhập tên nhóm '${session.task.targetGroupName}' vào ô tìm kiếm chia sẻ."
                        )
                    } else {
                        AutomationStepResult.Progress(
                            AutomationStage.SELECTING_GROUP,
                            "Đang chờ kết quả tìm nhóm trên Zalo."
                        )
                    }
                } else {
                    // Một số phiên bản Zalo có nút kính lúp/Tìm kiếm cần bấm trước khi hiện ô nhập
                    val searchTrigger = findNodeByKeywords(
                        root,
                        listOf("tìm kiếm", "search", "tìm bạn", "tìm nhóm")
                    )
                    if (searchTrigger != null && performClickableAction(searchTrigger)) {
                        AutomationStepResult.Progress(
                            AutomationStage.SEARCHING_GROUP,
                            "Đã mở thanh tìm kiếm trên màn hình chia sẻ Zalo."
                        )
                    } else {
                        AutomationStepResult.Waiting
                    }
                }
            }

            AutomationStage.SELECTING_GROUP -> {
                val matches = findExactRecipientMatches(
                    root = root,
                    targetName = session.task.targetGroupName,
                    strictExact = session.task.strictExactMatch
                )
                when {
                    matches.size > 1 -> {
                        AutomationStepResult.AbortSafety(
                            "Phát hiện ${matches.size} nhóm/người trùng tên '${session.task.targetGroupName}'. " +
                                "Đã tự động dừng để đảm bảo không gửi nhầm."
                        )
                    }
                    matches.size == 1 -> {
                        if (performClickableAction(matches.first())) {
                            val nextStage = if (session.task.messageText.isNotBlank()) {
                                AutomationStage.ENTERING_MESSAGE
                            } else {
                                AutomationStage.READY_TO_SEND
                            }
                            AutomationStepResult.Progress(
                                nextStage,
                                "Đã chọn chính xác nhóm '${session.task.targetGroupName}'."
                            )
                        } else {
                            AutomationStepResult.Waiting
                        }
                    }
                    else -> {
                        // Đợi danh sách lọc kịp cập nhật trước khi kết luận không tìm thấy
                        session.retryCount++
                        if (session.retryCount > 10) {
                            AutomationStepResult.AbortSafety(
                                "Không tìm thấy nhóm nào khớp chính xác tên '${session.task.targetGroupName}' trên Zalo."
                            )
                        } else {
                            AutomationStepResult.Waiting
                        }
                    }
                }
            }

            AutomationStage.ENTERING_MESSAGE -> {
                val captionBox = findMessageInputEditText(root, excludeSearchText = session.task.targetGroupName)
                if (captionBox != null) {
                    val expected = session.task.messageText.trim()
                    if (captionBox.text?.toString()?.trim() != expected) {
                        setNodeText(captionBox, expected)
                        session.messageEntered = true
                    }
                    AutomationStepResult.Progress(
                        AutomationStage.READY_TO_SEND,
                        "Đã điền nội dung tin nhắn đính kèm ảnh."
                    )
                } else {
                    // Nếu Zalo không hiện ô nhập riêng ở bước này (hoặc đã tự nhận EXTRA_TEXT), chuyển sang bước kiểm tra gửi
                    session.retryCount++
                    if (session.retryCount > 4) {
                        AutomationStepResult.Progress(
                            AutomationStage.READY_TO_SEND,
                            "Chuyển sang bước xác nhận gửi."
                        )
                    } else {
                        AutomationStepResult.Waiting
                    }
                }
            }

            AutomationStage.READY_TO_SEND,
            AutomationStage.VERIFYING_CHAT_HEADER -> {
                if (session.task.dryRun) {
                    return AutomationStepResult.DryRunReady(
                        "Chế độ chạy thử: Đã chọn đúng nhóm '${session.task.targetGroupName}' và chuẩn bị sẵn ảnh/tin nhắn. Hãy kiểm tra và tự bấm Gửi."
                    )
                }
                val sendBtn = findSendButton(root)
                if (sendBtn != null && performClickableAction(sendBtn)) {
                    AutomationStepResult.Completed(
                        "Đã tự động gửi tin nhắn kèm ảnh tới nhóm '${session.task.targetGroupName}'."
                    )
                } else {
                    AutomationStepResult.Waiting
                }
            }

            AutomationStage.DONE -> AutomationStepResult.Completed("Hoàn tất.")
        }
    }

    /**
     * Luồng chỉ gửi Tin nhắn văn bản:
     * Mở Zalo -> Tìm tên nhóm -> Khớp duy nhất 1 kết quả -> Xác minh tiêu đề khung chat -> Nhập tin -> Gửi.
     */
    private fun processTextOnlyFlow(
        root: AccessibilityNodeInfo,
        session: AutomationSession
    ): AutomationStepResult {
        val target = normalize(session.task.targetGroupName)

        return when (session.stage) {
            AutomationStage.LAUNCHING,
            AutomationStage.SEARCHING_GROUP -> {
                // Nếu Zalo đang mở sẵn đúng nhóm chat rồi -> bỏ qua bước tìm kiếm
                if (isChatHeaderExactMatch(root, session.task.targetGroupName)) {
                    return AutomationStepResult.Progress(
                        AutomationStage.ENTERING_MESSAGE,
                        "Zalo đang mở sẵn đúng nhóm '${session.task.targetGroupName}'."
                    )
                }

                val searchInput = findSearchEditText(root)
                if (searchInput != null) {
                    if (normalize(searchInput.text?.toString().orEmpty()) != target) {
                        clickOrFocus(searchInput)
                        setNodeText(searchInput, session.task.targetGroupName.trim())
                        session.searchEntered = true
                    }
                    AutomationStepResult.Progress(
                        AutomationStage.SELECTING_GROUP,
                        "Đã nhập tên nhóm '${session.task.targetGroupName}' vào thanh tìm kiếm Zalo."
                    )
                } else {
                    val searchTrigger = findNodeByKeywords(root, listOf("tìm kiếm", "search"))
                    if (searchTrigger != null && performClickableAction(searchTrigger)) {
                        AutomationStepResult.Progress(
                            AutomationStage.SEARCHING_GROUP,
                            "Đã bấm thanh tìm kiếm Zalo."
                        )
                    } else {
                        AutomationStepResult.Waiting
                    }
                }
            }

            AutomationStage.SELECTING_GROUP -> {
                val matches = findExactRecipientMatches(
                    root = root,
                    targetName = session.task.targetGroupName,
                    strictExact = session.task.strictExactMatch
                )
                when {
                    matches.size > 1 -> {
                        AutomationStepResult.AbortSafety(
                            "Phát hiện ${matches.size} nhóm/người trùng tên '${session.task.targetGroupName}'. Đã dừng để tránh gửi nhầm."
                        )
                    }
                    matches.size == 1 -> {
                        if (performClickableAction(matches.first())) {
                            AutomationStepResult.Progress(
                                AutomationStage.VERIFYING_CHAT_HEADER,
                                "Đã mở cuộc trò chuyện '${session.task.targetGroupName}', đang xác minh tiêu đề."
                            )
                        } else {
                            AutomationStepResult.Waiting
                        }
                    }
                    else -> {
                        session.retryCount++
                        if (session.retryCount > 10) {
                            AutomationStepResult.AbortSafety(
                                "Không tìm thấy nhóm khớp chính xác '${session.task.targetGroupName}' trong Zalo."
                            )
                        } else {
                            AutomationStepResult.Waiting
                        }
                    }
                }
            }

            AutomationStage.VERIFYING_CHAT_HEADER -> {
                if (isChatHeaderExactMatch(root, session.task.targetGroupName)) {
                    AutomationStepResult.Progress(
                        AutomationStage.ENTERING_MESSAGE,
                        "Đã xác minh 100% tiêu đề nhóm '${session.task.targetGroupName}'."
                    )
                } else {
                    session.retryCount++
                    if (session.retryCount > 8) {
                        AutomationStepResult.AbortSafety(
                            "Tiêu đề khung chat đang mở không khớp chính xác '${session.task.targetGroupName}'. Đã hủy gửi để đảm bảo an toàn."
                        )
                    } else {
                        AutomationStepResult.Waiting
                    }
                }
            }

            AutomationStage.ENTERING_MESSAGE -> {
                // Xác minh lại tiêu đề lần nữa trước khi nhập tin
                if (!isChatHeaderExactMatch(root, session.task.targetGroupName)) {
                    return AutomationStepResult.AbortSafety(
                        "Màn hình chat đã thay đổi, không còn khớp nhóm '${session.task.targetGroupName}'."
                    )
                }
                val msgBox = findMessageInputEditText(root, excludeSearchText = session.task.targetGroupName)
                if (msgBox != null) {
                    setNodeText(msgBox, session.task.messageText.trim())
                    session.messageEntered = true
                    AutomationStepResult.Progress(
                        AutomationStage.READY_TO_SEND,
                        "Đã điền nội dung tin nhắn."
                    )
                } else {
                    AutomationStepResult.Waiting
                }
            }

            AutomationStage.READY_TO_SEND -> {
                if (!isChatHeaderExactMatch(root, session.task.targetGroupName)) {
                    return AutomationStepResult.AbortSafety(
                        "Kiểm tra cuối trước khi gửi thất bại: tiêu đề nhóm không khớp '${session.task.targetGroupName}'."
                    )
                }
                if (session.task.dryRun) {
                    return AutomationStepResult.DryRunReady(
                        "Chế độ chạy thử: Đã vào đúng nhóm '${session.task.targetGroupName}' và điền sẵn tin nhắn. Hãy kiểm tra và tự bấm Gửi."
                    )
                }
                val sendBtn = findSendButton(root)
                if (sendBtn != null && performClickableAction(sendBtn)) {
                    AutomationStepResult.Completed(
                        "Đã gửi tin nhắn tới nhóm '${session.task.targetGroupName}'."
                    )
                } else {
                    AutomationStepResult.Waiting
                }
            }

            AutomationStage.DONE -> AutomationStepResult.Completed("Hoàn tất.")
        }
    }

    // ==================== CÁC HÀM TÌM KIẾM & XÁC MINH NODE AN TOÀN ====================

    private fun collectAllNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            result.add(current)
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { stack.add(it) }
            }
        }
        return result
    }

    private fun findSearchEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = collectAllNodes(root)
        return nodes.firstOrNull { node ->
            val cls = node.className?.toString().orEmpty()
            if (!cls.contains("EditText", ignoreCase = true)) return@firstOrNull false
            val hint = node.hintText?.toString().orEmpty().lowercase()
            val desc = node.contentDescription?.toString().orEmpty().lowercase()
            val id = node.viewIdResourceName.orEmpty().lowercase()
            hint.contains("tìm") || hint.contains("search") ||
                desc.contains("tìm") || desc.contains("search") ||
                id.contains("search")
        }
    }

    private fun findMessageInputEditText(
        root: AccessibilityNodeInfo,
        excludeSearchText: String
    ): AccessibilityNodeInfo? {
        val normalizedExclude = normalize(excludeSearchText)
        val nodes = collectAllNodes(root)
        // Ưu tiên EditText có gợi ý "tin nhắn" / "message" / "mô tả" / "caption"
        val exactHintMatch = nodes.firstOrNull { node ->
            val cls = node.className?.toString().orEmpty()
            if (!cls.contains("EditText", ignoreCase = true)) return@firstOrNull false
            val hint = node.hintText?.toString().orEmpty().lowercase()
            val desc = node.contentDescription?.toString().orEmpty().lowercase()
            val id = node.viewIdResourceName.orEmpty().lowercase()
            hint.contains("tin nhắn") || hint.contains("message") ||
                hint.contains("nhập") || hint.contains("mô tả") ||
                desc.contains("tin nhắn") || id.contains("chat_input") || id.contains("message")
        }
        if (exactHintMatch != null) return exactHintMatch

        // Dự phòng: EditText không phải ô search
        return nodes.firstOrNull { node ->
            val cls = node.className?.toString().orEmpty()
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val text = normalize(node.text?.toString().orEmpty())
            cls.contains("EditText", ignoreCase = true) &&
                !id.contains("search") &&
                text != normalizedExclude
        }
    }

    /**
     * Tìm các dòng kết quả khớp tên nhóm (bỏ qua chính ô EditText đang gõ tìm kiếm).
     */
    private fun findExactRecipientMatches(
        root: AccessibilityNodeInfo,
        targetName: String,
        strictExact: Boolean
    ): List<AccessibilityNodeInfo> {
        val target = normalize(targetName)
        if (target.isEmpty()) return emptyList()

        val nodes = collectAllNodes(root)
        val matchedContainers = LinkedHashSet<AccessibilityNodeInfo>()

        for (node in nodes) {
            val cls = node.className?.toString().orEmpty()
            if (cls.contains("EditText", ignoreCase = true)) continue

            val text = normalize(node.text?.toString().orEmpty())
            if (text.isEmpty()) continue

            val isMatch = if (strictExact) {
                text.equals(target, ignoreCase = true)
            } else {
                text.equals(target, ignoreCase = true) || text.contains(target, ignoreCase = true)
            }

            if (isMatch) {
                val clickableParent = findNearestClickableAncestor(node)
                if (clickableParent != null) {
                    matchedContainers.add(clickableParent)
                }
            }
        }
        return matchedContainers.toList()
    }

    /**
     * Xác minh tiêu đề nhóm ở phần trên màn hình (top 22% màn hình) khớp chính xác tên nhóm.
     */
    private fun isChatHeaderExactMatch(root: AccessibilityNodeInfo, targetName: String): Boolean {
        val target = normalize(targetName)
        val rootRect = android.graphics.Rect()
        root.getBoundsInScreen(rootRect)
        val headerMaxY = if (rootRect.height() > 0) rootRect.top + (rootRect.height() * 0.22f) else 450f

        val nodes = collectAllNodes(root)
        return nodes.any { node ->
            val cls = node.className?.toString().orEmpty()
            if (cls.contains("EditText", ignoreCase = true)) return@any false
            val text = normalize(node.text?.toString().orEmpty())
            if (!text.equals(target, ignoreCase = true)) return@any false
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            rect.top in 0..headerMaxY.toInt()
        }
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val keywords = listOf("gửi", "send", "chia sẻ", "share")
        val nodes = collectAllNodes(root)
        return nodes.lastOrNull { node ->
            val cls = node.className?.toString().orEmpty()
            if (cls.contains("EditText", ignoreCase = true)) return@lastOrNull false
            val text = node.text?.toString().orEmpty().trim().lowercase()
            val desc = node.contentDescription?.toString().orEmpty().trim().lowercase()
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val matchesLabel = keywords.any { kw ->
                text == kw || desc == kw || desc.startsWith("$kw ") || id.contains("send")
            }
            matchesLabel && (node.isClickable || findNearestClickableAncestor(node) != null)
        }
    }

    private fun findNodeByKeywords(
        root: AccessibilityNodeInfo,
        keywords: List<String>
    ): AccessibilityNodeInfo? {
        val nodes = collectAllNodes(root)
        return nodes.firstOrNull { node ->
            val text = node.text?.toString().orEmpty().lowercase()
            val desc = node.contentDescription?.toString().orEmpty().lowercase()
            keywords.any { kw -> text.contains(kw) || desc.contains(kw) }
        }
    }

    private fun findNearestClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var curr: AccessibilityNodeInfo? = node
        var depth = 0
        while (curr != null && depth < 6) {
            if (curr.isClickable && curr.isEnabled) return curr
            curr = curr.parent
            depth++
        }
        return null
    }

    private fun performClickableAction(node: AccessibilityNodeInfo): Boolean {
        val clickable = findNearestClickableAncestor(node) ?: node
        return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun clickOrFocus(node: AccessibilityNodeInfo) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun setNodeText(node: AccessibilityNodeInfo, value: String) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun normalize(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")
}
