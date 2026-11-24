package com.autosend.scheduler.data

enum class ChatPlatform(
    val displayName: String,
    val packageName: String,
    val isSupported: Boolean
) {
    ZALO("Zalo", "com.zing.zalo", true),
    TELEGRAM("Telegram", "org.telegram.messenger", false),
    MESSENGER("Messenger", "com.facebook.orca", false),
    VIBER("Viber", "com.viber.voip", false),
    WHATSAPP("WhatsApp", "com.whatsapp", false);

    companion object {
        fun fromName(name: String): ChatPlatform =
            entries.firstOrNull { it.name == name } ?: ZALO
    }
}

enum class ScheduleType(val label: String) {
    ONE_TIME("Gửi 1 lần (Theo ngày)"),
    DAILY_RECURRING("Lặp lại hằng ngày / theo thứ");

    companion object {
        fun fromName(name: String): ScheduleType =
            entries.firstOrNull { it.name == name } ?: ONE_TIME
    }
}

enum class ExecutionStatus(val label: String) {
    PENDING("Chờ đến giờ"),
    RUNNING("Đang thao tác"),
    WAITING_UNLOCK("Chờ mở khóa màn hình"),
    SUCCESS("Đã gửi thành công"),
    DRY_RUN_READY("Chạy thử: Đã điền sẵn"),
    ABORTED_SAFETY("Đã dừng để tránh gửi nhầm"),
    FAILED("Thất bại");

    companion object {
        fun fromName(name: String): ExecutionStatus =
            entries.firstOrNull { it.name == name } ?: PENDING
    }
}
