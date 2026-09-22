package com.example.util

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.data.AppLogRepository
import com.example.data.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class ReloadResult(
    val success: Boolean,
    val message: String,
    val displayId: Int,
    val isAppInstalled: Boolean
)

object AppLauncher {

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    suspend fun reloadApp(
        context: Context,
        packageName: String,
        targetDisplayId: Int,
        killFirst: Boolean = true,
        useRoot: Boolean = true
    ): ReloadResult = withContext(Dispatchers.IO) {
        val appName = getAppLabel(context, packageName)

        if (!isAppInstalled(context, packageName)) {
            val msg = "Ứng dụng $packageName chưa được cài đặt trên thiết bị!"
            AppLogRepository.addLog(msg, LogLevel.ERROR)
            return@withContext ReloadResult(
                success = false,
                message = msg,
                displayId = targetDisplayId,
                isAppInstalled = false
            )
        }

        AppLogRepository.addLog(
            "Bắt đầu quy trình tải lại $appName ($packageName) trên Display ID: $targetDisplayId",
            LogLevel.INFO
        )

        // Step 1: Kill/reset stuck app if requested
        if (killFirst) {
            var rootKilled = false
            if (useRoot && RootHelper.isRootAvailable()) {
                AppLogRepository.addLog("Đang tắt tiến trình đơ bằng quyền Root (am force-stop)...", LogLevel.INFO)
                rootKilled = RootHelper.forceStop(packageName)
            }

            if (!rootKilled) {
                AppLogRepository.addLog("Đang đóng tiến trình nền qua ActivityManager...", LogLevel.INFO)
                try {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    am?.killBackgroundProcesses(packageName)
                } catch (e: Exception) {
                    AppLogRepository.addLog("Không thể killBackgroundProcesses: ${e.message}", LogLevel.WARNING)
                }
            }

            // Short cool-down delay after kill
            delay(600)
        }

        // Step 2: Launch into target display
        var launchSuccess = false
        var failureReason = ""

        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

                val options = ActivityOptions.makeBasic()
                if (targetDisplayId >= 0) {
                    options.setLaunchDisplayId(targetDisplayId)
                }

                context.startActivity(intent, options.toBundle())
                launchSuccess = true
                AppLogRepository.addLog(
                    "Đã gửi lệnh khởi chạy $appName tới Màn hình [Display ID: $targetDisplayId]",
                    LogLevel.SUCCESS
                )
            } else {
                failureReason = "Không lấy được Launch Intent cho gói $packageName"
            }
        } catch (e: Exception) {
            failureReason = e.localizedMessage ?: "Lỗi ngoại lệ khi startActivity"
            AppLogRepository.addLog("Lỗi khởi chạy thông thường: $failureReason", LogLevel.WARNING)
        }

        // Step 3: Fallback using root am start if standard intent threw error and root is available
        if (!launchSuccess && useRoot && RootHelper.isRootAvailable()) {
            AppLogRepository.addLog("Thử khởi chạy qua lệnh Root (am start --display $targetDisplayId)...", LogLevel.INFO)
            val rootStartSuccess = RootHelper.startAppOnDisplayViaRoot(packageName, targetDisplayId)
            if (rootStartSuccess) {
                launchSuccess = true
                AppLogRepository.addLog("Khởi chạy qua Root thành công trên Display ID: $targetDisplayId!", LogLevel.SUCCESS)
            }
        }

        if (launchSuccess) {
            val successMsg = "Đã tải lại $appName thành công trên màn hình PIP (ID: $targetDisplayId)"
            AppLogRepository.addLog(successMsg, LogLevel.SUCCESS)
            ReloadResult(
                success = true,
                message = successMsg,
                displayId = targetDisplayId,
                isAppInstalled = true
            )
        } else {
            val errorMsg = "Tải lại thất bại: $failureReason"
            AppLogRepository.addLog(errorMsg, LogLevel.ERROR)
            ReloadResult(
                success = false,
                message = errorMsg,
                displayId = targetDisplayId,
                isAppInstalled = true
            )
        }
    }
}
