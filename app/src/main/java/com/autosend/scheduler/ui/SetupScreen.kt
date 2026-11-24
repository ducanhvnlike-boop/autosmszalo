package com.autosend.scheduler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.autosend.scheduler.ui.theme.SuccessGreen
import com.autosend.scheduler.ui.theme.WarningAmber
import com.autosend.scheduler.ui.theme.ZaloBlueLight
import com.autosend.scheduler.util.DeviceSetupHelper

@Composable
fun SetupScreen(
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(DeviceSetupHelper.checkStatus(context)) }

    // Tự động kiểm tra lại quyền mỗi khi người dùng quay lại từ Cài đặt Android
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        status = DeviceSetupHelper.checkStatus(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Thiết lập & Tối ưu thiết bị",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Thiết bị hiện tại: ${status.manufacturerName}. Bật đủ các mục dưới đây để gửi đúng giờ và không bị hệ thống tắt ngầm.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PermissionCard(
            title = "1. Dịch vụ Hỗ trợ thao tác (Bắt buộc)",
            description = "Cho phép ứng dụng tìm đúng nhóm Zalo, kiểm tra tên nhóm và bấm gửi tự động.",
            isGranted = status.accessibilityEnabled,
            actionLabel = "Mở Trợ năng",
            onAction = { DeviceSetupHelper.openAccessibilitySettings(context) }
        )

        PermissionCard(
            title = "2. Hẹn giờ chính xác (Bắt buộc)",
            description = "Đảm bảo báo thức kích hoạt đúng từng phút trên Android 12+.",
            isGranted = status.exactAlarmAllowed,
            actionLabel = "Cấp quyền",
            onAction = { DeviceSetupHelper.openExactAlarmSettings(context) }
        )

        PermissionCard(
            title = "3. Tắt tối ưu hóa pin",
            description = "Tránh Android đưa ứng dụng vào chế độ ngủ sâu khi màn hình tắt.",
            isGranted = status.batteryOptimizationIgnored,
            actionLabel = "Tắt tối ưu pin",
            onAction = { DeviceSetupHelper.openBatteryOptimizationSettings(context) }
        )

        PermissionCard(
            title = "4. Thông báo trạng thái & cảnh báo",
            description = "Báo kết quả gửi, cảnh báo nếu trùng tên nhóm hoặc nhắc mở khóa màn hình.",
            isGranted = status.notificationAllowed,
            actionLabel = "Bật thông báo",
            onAction = onRequestNotificationPermission
        )

        PermissionCard(
            title = "5. Hiển thị trên ứng dụng khác",
            description = "Giúp khởi chạy Zalo từ nền ổn định hơn trên Android 13/14.",
            isGranted = status.overlayAllowed,
            actionLabel = "Cấp quyền",
            onAction = { DeviceSetupHelper.openOverlaySettings(context) }
        )

        // Hướng dẫn riêng cho Xiaomi (MIUI / HyperOS)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (status.isXiaomi) ZaloBlueLight else MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tối ưu cho điện thoại Xiaomi (MIUI / HyperOS)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "• Bật 'Tự khởi chạy' (Autostart).\n" +
                        "• Vào 'Quyền khác' -> bật 'Hiển thị cửa sổ pop-up khi chạy trong nền' (bắt buộc để tự mở Zalo đúng giờ).\n" +
                        "• Nếu bật khóa màn hình, hãy mở khóa khi có thông báo để app tự gửi ngay.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { DeviceSetupHelper.openXiaomiAutostart(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mở Tự khởi chạy")
                    }
                    OutlinedButton(
                        onClick = { DeviceSetupHelper.openXiaomiOtherPermissions(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Quyền khác")
                    }
                }
            }
        }

        // Hướng dẫn riêng cho Samsung (One UI)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (status.isSamsung) ZaloBlueLight else MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tối ưu cho điện thoại Samsung (One UI)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "• Bấm nút dưới -> chọn mục Pin (Battery) -> chọn 'Không hạn chế' (Unrestricted).\n" +
                        "• Vào Cài đặt máy -> Chăm sóc thiết bị -> Pin -> Giới hạn sử dụng dưới nền -> thêm AutoSend Zalo vào 'Ứng dụng không bao giờ đặt ở chế độ ngủ'.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { DeviceSetupHelper.openAppDetails(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mở thông tin ứng dụng Samsung")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isGranted) SuccessGreen else WarningAmber
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (!isGranted) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            } else {
                Text(
                    text = "Đã bật",
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
