package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppLogRepository
import com.example.data.AppSettings
import com.example.data.LogLevel
import com.example.service.NetworkMonitorService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val settings = AppSettings.getInstance(context)

        AppLogRepository.addLog("Nhận sự kiện hệ thống: $action", LogLevel.INFO)

        if (settings.serviceEnabled && settings.autoStartOnBoot) {
            AppLogRepository.addLog("Tự động khởi động dịch vụ giám sát khi thiết bị bật nguồn", LogLevel.SUCCESS)
            try {
                NetworkMonitorService.start(context)
            } catch (e: Exception) {
                AppLogRepository.addLog("Lỗi khởi động dịch vụ từ BootReceiver: ${e.message}", LogLevel.ERROR)
            }
        } else {
            AppLogRepository.addLog("Cấu hình tự khởi động đang tắt, không khởi động dịch vụ", LogLevel.INFO)
        }
    }
}
