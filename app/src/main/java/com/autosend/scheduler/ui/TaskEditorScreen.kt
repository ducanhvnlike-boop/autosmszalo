package com.autosend.scheduler.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.autosend.scheduler.data.ChatPlatform
import com.autosend.scheduler.data.ScheduleType
import com.autosend.scheduler.data.ScheduledTaskEntity
import com.autosend.scheduler.ui.theme.DangerRed
import com.autosend.scheduler.ui.theme.ZaloBlueLight
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    taskId: Long?,
    savedGroupNames: List<String>,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var platform by remember { mutableStateOf(ChatPlatform.ZALO) }
    var targetGroupName by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var imagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var scheduleType by remember { mutableStateOf(ScheduleType.DAILY_RECURRING) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    var repeatDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }
    var strictExactMatch by remember { mutableStateOf(true) }
    var dryRun by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(taskId) {
        if (taskId != null && taskId > 0L) {
            viewModel.getTaskById(taskId)?.let { existing ->
                platform = existing.chatPlatform()
                targetGroupName = existing.targetGroupName
                messageText = existing.messageText
                imagePaths = existing.imagePaths()
                scheduleType = existing.scheduleMode()
                selectedDate = if (existing.targetDateEpochDay > 0L) {
                    LocalDate.ofEpochDay(existing.targetDateEpochDay)
                } else {
                    LocalDate.now()
                }
                hour = existing.hour
                minute = existing.minute
                repeatDays = existing.repeatDaysSet().ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) }
                strictExactMatch = existing.strictExactMatch
                dryRun = existing.dryRun
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val imported = viewModel.importPickedImages(uris)
                imagePaths = imagePaths + imported
            }
        }
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (taskId == null) "Tạo lịch gửi mới" else "Chỉnh sửa lịch gửi")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Nền tảng chat (sẵn sàng mở rộng)
            Text("Ứng dụng chat đích", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ChatPlatform.entries) { item ->
                    FilterChip(
                        selected = platform == item,
                        onClick = { if (item.isSupported) platform = item },
                        enabled = item.isSupported,
                        label = {
                            Text(
                                if (item.isSupported) item.displayName
                                else "${item.displayName} (Sắp có)"
                            )
                        }
                    )
                }
            }

            // 2. Tên nhóm chính xác
            OutlinedTextField(
                value = targetGroupName,
                onValueChange = {
                    targetGroupName = it
                    errorMessage = null
                },
                label = { Text("Tên nhóm Zalo (chính xác 100%)") },
                placeholder = { Text("VD: Nhóm Công Tác Phòng Kỹ Thuật") },
                supportingText = {
                    Text("Nhập y hệt tên hiển thị của nhóm trên Zalo để chống gửi nhầm.")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (savedGroupNames.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedGroupNames) { group ->
                        SuggestionChip(
                            onClick = { targetGroupName = group },
                            label = { Text(group) }
                        )
                    }
                }
            }

            // 3. Chế độ hẹn giờ: Lặp hằng ngày HOẶC Gửi riêng từng ngày
            Text("Chế độ hẹn giờ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleType.entries.forEach { mode ->
                    FilterChip(
                        selected = scheduleType == mode,
                        onClick = { scheduleType = mode },
                        label = {
                            Text(
                                if (mode == ScheduleType.DAILY_RECURRING) "Lặp hằng ngày"
                                else "Theo ngày cụ thể"
                            )
                        }
                    )
                }
            }

            // Chọn Giờ & Ngày
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, selectedHour, selectedMinute ->
                                hour = selectedHour
                                minute = selectedMinute
                            },
                            hour,
                            minute,
                            true
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(String.format("%02d:%02d", hour, minute))
                }

                if (scheduleType == ScheduleType.ONE_TIME) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    selectedDate = LocalDate.of(y, m + 1, d)
                                },
                                selectedDate.year,
                                selectedDate.monthValue - 1,
                                selectedDate.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(selectedDate.format(dateFormatter))
                    }
                }
            }

            if (scheduleType == ScheduleType.DAILY_RECURRING) {
                val dayItems = listOf(1 to "T2", 2 to "T3", 3 to "T4", 4 to "T5", 5 to "T6", 6 to "T7", 7 to "CN")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(dayItems) { (dayNum, label) ->
                        val isSelected = dayNum in repeatDays
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val next = if (isSelected) repeatDays - dayNum else repeatDays + dayNum
                                if (next.isNotEmpty()) repeatDays = next
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // 4. Nội dung tin nhắn
            OutlinedTextField(
                value = messageText,
                onValueChange = {
                    messageText = it
                    errorMessage = null
                },
                label = { Text("Nội dung tin nhắn") },
                placeholder = { Text("Nhập nội dung báo cáo công tác...") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            // 5. Hình ảnh đính kèm
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hình ảnh đính kèm (${imagePaths.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chọn ảnh")
                }
            }

            if (imagePaths.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(imagePaths) { path ->
                        Box(modifier = Modifier.size(86.dp)) {
                            AsyncImage(
                                model = File(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            IconButton(
                                onClick = { imagePaths = imagePaths - path },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Xóa ảnh",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 6. Cài đặt an toàn chống gửi nhầm
            Card(
                colors = CardDefaults.cardColors(containerColor = ZaloBlueLight.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bảo vệ chống gửi nhầm", fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Khớp chính xác 100% tên nhóm", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Tự động hủy nếu có nhiều nhóm trùng tên hoặc tên không khớp hoàn toàn",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = strictExactMatch, onCheckedChange = { strictExactMatch = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chế độ chạy thử (Dry-run)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Chỉ chọn nhóm & điền sẵn ảnh/chữ, không tự bấm nút Gửi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = dryRun, onCheckedChange = { dryRun = it })
                    }
                }
            }

            errorMessage?.let { err ->
                Text(text = err, color = DangerRed, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val trimmedGroup = targetGroupName.trim()
                    if (trimmedGroup.isEmpty()) {
                        errorMessage = "Vui lòng nhập chính xác tên nhóm Zalo."
                        return@Button
                    }
                    if (messageText.isBlank() && imagePaths.isEmpty()) {
                        errorMessage = "Vui lòng nhập nội dung tin nhắn hoặc chọn ít nhất 1 hình ảnh."
                        return@Button
                    }
                    val entity = ScheduledTaskEntity(
                        id = taskId ?: 0L,
                        platform = platform.name,
                        targetGroupName = trimmedGroup,
                        messageText = messageText.trim(),
                        imagePathsSerialized = imagePaths.joinToString("|"),
                        scheduleType = scheduleType.name,
                        targetDateEpochDay = selectedDate.toEpochDay(),
                        hour = hour,
                        minute = minute,
                        repeatDaysCsv = repeatDays.sorted().joinToString(","),
                        strictExactMatch = strictExactMatch,
                        dryRun = dryRun,
                        isEnabled = true
                    )
                    viewModel.saveTask(entity) { onBack() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(if (taskId == null) "Lưu lịch hẹn gửi" else "Cập nhật lịch gửi")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
