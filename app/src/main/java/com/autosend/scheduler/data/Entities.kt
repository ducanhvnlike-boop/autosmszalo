package com.autosend.scheduler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val platform: String = ChatPlatform.ZALO.name,
    val targetGroupName: String,
    val messageText: String,
    /** Đường dẫn các file ảnh nội bộ, phân tách bởi dấu '|' */
    val imagePathsSerialized: String = "",
    /** ONE_TIME hoặc DAILY_RECURRING */
    val scheduleType: String = ScheduleType.ONE_TIME.name,
    /** Ngày gửi cho chế độ ONE_TIME (epoch millis lúc 00:00 ngày đó) */
    val targetDateEpochDay: Long = 0L,
    val hour: Int = 8,
    val minute: Int = 0,
    /** Các thứ trong tuần cho DAILY_RECURRING: "1,2,3,4,5,6,7" (1=T2 ... 7=CN) */
    val repeatDaysCsv: String = "1,2,3,4,5,6,7",
    /** Bắt buộc tên nhóm khớp 100% (không chọn kết quả gần đúng) */
    val strictExactMatch: Boolean = true,
    /** Chế độ chạy thử: điền sẵn nội dung & chọn đúng nhóm nhưng không tự bấm nút Gửi */
    val dryRun: Boolean = false,
    val isEnabled: Boolean = true,
    val nextTriggerAtMillis: Long = 0L,
    val lastRunStatus: String = ExecutionStatus.PENDING.name,
    val lastRunAtMillis: Long = 0L,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun imagePaths(): List<String> =
        imagePathsSerialized.split("|").map { it.trim() }.filter { it.isNotEmpty() }

    fun repeatDaysSet(): Set<Int> =
        repeatDaysCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    fun chatPlatform(): ChatPlatform = ChatPlatform.fromName(platform)

    fun scheduleMode(): ScheduleType = ScheduleType.fromName(scheduleType)

    fun lastStatus(): ExecutionStatus = ExecutionStatus.fromName(lastRunStatus)
}

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val taskId: Long,
    val platform: String,
    val targetGroupName: String,
    val status: String,
    val detail: String,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    fun executionStatus(): ExecutionStatus = ExecutionStatus.fromName(status)
    fun chatPlatform(): ChatPlatform = ChatPlatform.fromName(platform)
}
