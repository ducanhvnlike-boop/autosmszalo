package com.autosend.scheduler.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.autosend.scheduler.MainActivity
import com.autosend.scheduler.data.ScheduleType
import com.autosend.scheduler.data.ScheduledTaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object ScheduleManager {
    const val EXTRA_TASK_ID = "extra_task_id"

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy")

    fun formatMillis(millis: Long): String {
        if (millis <= 0L) return "Chưa hẹn"
        val zdt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return zdt.format(dateTimeFormatter)
    }

    /**
     * Tính thời điểm chạy kế tiếp cho cả 2 chế độ:
     * - ONE_TIME: Đúng ngày & giờ người dùng đã chọn.
     * - DAILY_RECURRING: Ngày gần nhất thuộc các thứ trong tuần đã chọn mà giờ hẹn chưa qua.
     */
    fun computeNextTriggerMillis(
        task: ScheduledTaskEntity,
        fromMillis: Long = System.currentTimeMillis()
    ): Long {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(fromMillis).atZone(zone)
        val targetTime = LocalTime.of(task.hour.coerceIn(0, 23), task.minute.coerceIn(0, 59))

        return when (task.scheduleMode()) {
            ScheduleType.ONE_TIME -> {
                val date = if (task.targetDateEpochDay > 0L) {
                    LocalDate.ofEpochDay(task.targetDateEpochDay)
                } else {
                    now.toLocalDate()
                }
                ZonedDateTime.of(date, targetTime, zone).toInstant().toEpochMilli()
            }

            ScheduleType.DAILY_RECURRING -> {
                val allowedDays = task.repeatDaysSet().ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) }
                for (offsetDays in 0..7) {
                    val candidateDate = now.toLocalDate().plusDays(offsetDays.toLong())
                    val dayOfWeekValue = candidateDate.dayOfWeek.value // 1=T2 ... 7=CN
                    if (dayOfWeekValue in allowedDays) {
                        val candidateZdt = ZonedDateTime.of(candidateDate, targetTime, zone)
                        if (candidateZdt.toInstant().toEpochMilli() > fromMillis) {
                            return candidateZdt.toInstant().toEpochMilli()
                        }
                    }
                }
                now.plusDays(1).with(targetTime).toInstant().toEpochMilli()
            }
        }
    }

    fun scheduleAlarm(context: Context, task: ScheduledTaskEntity) {
        if (!task.isEnabled) {
            cancelAlarm(context, task.id)
            return
        }
        val triggerAt = if (task.nextTriggerAtMillis > System.currentTimeMillis()) {
            task.nextTriggerAtMillis
        } else {
            computeNextTriggerMillis(task)
        }
        if (triggerAt <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, task.id)

        // setAlarmClock có độ ưu tiên cao nhất, ít bị Samsung One UI & Xiaomi MIUI trì hoãn nhất
        val showIntent = PendingIntent.getActivity(
            context,
            task.id.toInt() + 10000,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canUseExact) {
            val clockInfo = AlarmManager.AlarmClockInfo(triggerAt, showIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancelAlarm(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context, taskId))
    }

    private fun buildPendingIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
