package com.autosend.scheduler.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autosend.scheduler.data.ExecutionStatus
import com.autosend.scheduler.data.ScheduleType
import com.autosend.scheduler.data.ScheduledTaskEntity
import com.autosend.scheduler.scheduler.ScheduleManager
import com.autosend.scheduler.ui.theme.DangerRed
import com.autosend.scheduler.ui.theme.DangerRedBg
import com.autosend.scheduler.ui.theme.SuccessGreen
import com.autosend.scheduler.ui.theme.SuccessGreenBg
import com.autosend.scheduler.ui.theme.WarningAmber
import com.autosend.scheduler.ui.theme.WarningAmberBg
import com.autosend.scheduler.ui.theme.ZaloBlue
import com.autosend.scheduler.ui.theme.ZaloBlueLight
import com.autosend.scheduler.util.DeviceSetupHelper

private enum class ScheduleFilter(val label: String) {
    ALL("Tất cả"),
    DAILY("Lặp hằng ngày"),
    ONE_TIME("Từng ngày")
}

@Composable
fun SchedulesScreen(
    tasks: List<ScheduledTaskEntity>,
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    onToggleEnabled: (ScheduledTaskEntity, Boolean) -> Unit,
    onDeleteTask: (ScheduledTaskEntity) -> Unit,
    onDryRunNow: (ScheduledTaskEntity) -> Unit,
    onGoToSetup: () -> Unit
) {
    val context = LocalContext.current
    val permissionStatus = remember(tasks) { DeviceSetupHelper.checkStatus(context) }
    var selectedFilter by remember { mutableStateOf(ScheduleFilter.ALL) }
    var taskPendingDelete by remember { mutableStateOf<ScheduledTaskEntity?>(null) }

    val filteredTasks = remember(tasks, selectedFilter) {
        when (selectedFilter) {
            ScheduleFilter.ALL -> tasks
            ScheduleFilter.DAILY -> tasks.filter { it.scheduleMode() == ScheduleType.DAILY_RECURRING }
            ScheduleFilter.ONE_TIME -> tasks.filter { it.scheduleMode() == ScheduleType.ONE_TIME }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = ZaloBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tạo lịch mới")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Lịch gửi tin tự động",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Hỗ trợ gửi lặp hằng ngày hoặc soạn riêng từng ngày kèm ảnh",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!permissionStatus.accessibilityEnabled || !permissionStatus.exactAlarmAllowed) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    onClick = onGoToSetup,
                    colors = CardDefaults.cardColors(containerColor = WarningAmberBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningAmber
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cần hoàn tất cấp quyền để tự động gửi",
                                fontWeight = FontWeight.SemiBold,
                                color = WarningAmber,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Bấm vào đây để mở mục Thiết lập (Samsung / Xiaomi)",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningAmber
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Chưa có lịch hẹn nào",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bấm nút + để soạn tin nhắn & hình ảnh gửi vào nhóm Zalo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onToggle = { enabled -> onToggleEnabled(task, enabled) },
                            onEdit = { onEditClick(task.id) },
                            onDelete = { taskPendingDelete = task },
                            onDryRun = { onDryRunNow(task) }
                        )
                    }
                }
            }
        }
    }

    taskPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { taskPendingDelete = null },
            title = { Text("Xóa lịch gửi này?") },
            text = { Text("Lịch gửi tới nhóm '${target.targetGroupName}' và ảnh đính kèm sẽ bị xóa.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTask(target)
                        taskPendingDelete = null
                    }
                ) {
                    Text("Xóa", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskPendingDelete = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun TaskCard(
    task: ScheduledTaskEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDryRun: () -> Unit
) {
    val imagesCount = task.imagePaths().size
    val timeText = String.format("%02d:%02d", task.hour, task.minute)
    val modeBadge = if (task.scheduleMode() == ScheduleType.DAILY_RECURRING) {
        "Lặp: ${formatDaysShort(task.repeatDaysSet())}"
    } else {
        "Gửi 1 lần"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ZaloBlueLight)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = task.chatPlatform().displayName,
                        color = ZaloBlue,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (task.scheduleMode() == ScheduleType.DAILY_RECURRING) {
                        Icons.Default.Repeat
                    } else {
                        Icons.Default.Schedule
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = modeBadge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Switch(checked = task.isEnabled, onCheckedChange = onToggle)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Nhóm: ${task.targetGroupName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (task.messageText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.messageText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (imagesCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$imagesCount ảnh",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (task.dryRun) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(WarningAmberBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Chạy thử",
                            color = WarningAmber,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                StatusChip(status = task.lastStatus())
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Lần gửi kế tiếp: ${ScheduleManager.formatMillis(task.nextTriggerAtMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDryRun,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chạy thử ngay", style = MaterialTheme.typography.labelMedium)
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Chỉnh sửa")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = DangerRed)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: ExecutionStatus) {
    val (bg, fg) = when (status) {
        ExecutionStatus.SUCCESS -> SuccessGreenBg to SuccessGreen
        ExecutionStatus.DRY_RUN_READY, ExecutionStatus.WAITING_UNLOCK -> WarningAmberBg to WarningAmber
        ExecutionStatus.ABORTED_SAFETY, ExecutionStatus.FAILED -> DangerRedBg to DangerRed
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = status.label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDaysShort(days: Set<Int>): String {
    if (days.size == 7) return "Mỗi ngày"
    if (days == setOf(1, 2, 3, 4, 5)) return "T2 - T6"
    val labels = mapOf(1 to "T2", 2 to "T3", 3 to "T4", 4 to "T5", 5 to "T6", 6 to "T7", 7 to "CN")
    return days.sorted().mapNotNull { labels[it] }.joinToString(", ")
}
