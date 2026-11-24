package com.autosend.scheduler.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.autosend.scheduler.MainActivity

object NotificationHelper {
    const val CHANNEL_ALERTS = "autosend_alerts"
    const val CHANNEL_STATUS = "autosend_status"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Cảnh báo & Đánh thức hẹn giờ",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Đánh thức màn hình khi đến giờ gửi tin và cảnh báo chống gửi nhầm"
        }

        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            "Kết quả gửi tin tự động",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Thông báo trạng thái gửi tin nhắn thành công hoặc chế độ chạy thử"
        }

        manager.createNotificationChannel(alertChannel)
        manager.createNotificationChannel(statusChannel)
    }

    fun notifyFullScreenWakeup(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        fullScreenIntent: Intent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannels(context)

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun notify(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        isHighPriority: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            if (isHighPriority) CHANNEL_ALERTS else CHANNEL_STATUS
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (isHighPriority) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
