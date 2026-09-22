package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppLogRepository
import com.example.data.AppSettings
import com.example.data.LogLevel
import com.example.util.AppLauncher
import com.example.util.DisplayHelper
import com.example.util.NetworkChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkMonitorService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var settings: AppSettings
    private var connectivityManager: ConnectivityManager? = null
    private var displayManager: DisplayManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    private var reloadJob: Job? = null
    private var lastReloadAttemptTime: Long = 0L
    private var wasDisconnected: Boolean = true

    companion object {
        const val CHANNEL_ID = "navitel_pip_monitor_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.example.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
        const val ACTION_TRIGGER_RELOAD = "com.example.action.TRIGGER_RELOAD"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _currentInternetStatus = MutableStateFlow("Đang khởi tạo...")
        val currentInternetStatus: StateFlow<String> = _currentInternetStatus.asStateFlow()

        private val _lastDetectedDisplayId = MutableStateFlow(-1)
        val lastDetectedDisplayId: StateFlow<Int> = _lastDetectedDisplayId.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun triggerManualReload(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java).apply {
                action = ACTION_TRIGGER_RELOAD
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings.getInstance(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Dịch vụ đang khởi động...", -1))
        _isServiceRunning.value = true

        AppLogRepository.addLog("Dịch vụ giám sát nền đã khởi động thành công", LogLevel.SUCCESS)

        setupDisplayListener()
        setupNetworkCallback()
        updateCurrentDisplayInfo()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                AppLogRepository.addLog("Người dùng yêu cầu dừng dịch vụ", LogLevel.INFO)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_RELOAD -> {
                AppLogRepository.addLog("Nhận lệnh tải lại thủ công từ UI / Thông báo", LogLevel.INFO)
                triggerReload("Thủ công")
            }
            else -> {
                // Ensure notification and active checks are running
                updateNotificationStatus("Đang giám sát mạng...")
            }
        }
        return START_STICKY
    }

    private fun setupDisplayListener() {
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                val displays = DisplayHelper.getAllDisplays(this@NetworkMonitorService)
                val newDisplay = displays.firstOrNull { it.id == displayId }
                val name = newDisplay?.name ?: "Màn hình #$displayId"
                AppLogRepository.addLog(
                    "Phát hiện màn hình mới được thêm vào hệ thống: ID $displayId ($name)",
                    LogLevel.INFO
                )
                updateCurrentDisplayInfo()
            }

            override fun onDisplayRemoved(displayId: Int) {
                AppLogRepository.addLog("Màn hình ID $displayId đã bị đóng / gỡ bỏ", LogLevel.INFO)
                updateCurrentDisplayInfo()
            }

            override fun onDisplayChanged(displayId: Int) {
                updateCurrentDisplayInfo()
            }
        }

        displayManager?.registerDisplayListener(displayListener, null)
    }

    private fun updateCurrentDisplayInfo() {
        val targetId = DisplayHelper.resolveTargetDisplayId(
            this,
            settings.autoDetectPip,
            settings.manualDisplayId
        )
        _lastDetectedDisplayId.value = targetId
    }

    private fun setupNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val type = NetworkChecker.getConnectionType(this@NetworkMonitorService)
                _currentInternetStatus.value = "Đã có kết nối: $type"
                AppLogRepository.addLog("Tín hiệu mạng khả dụng ($type)", LogLevel.INFO)

                // Schedule reload check
                serviceScope.launch {
                    handleNetworkAvailable()
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, capabilities)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (isValidated) {
                    _currentInternetStatus.value = "Internet đã sẵn sàng & thông suốt"
                    serviceScope.launch {
                        handleNetworkValidated()
                    }
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                wasDisconnected = true
                _currentInternetStatus.value = "Mất kết nối Internet"
                AppLogRepository.addLog("Mất kết nối Internet", LogLevel.WARNING)
                updateNotificationStatus("Mất kết nối Internet")
            }
        }

        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            AppLogRepository.addLog("Lỗi đăng ký NetworkCallback: ${e.message}", LogLevel.ERROR)
        }
    }

    private suspend fun handleNetworkAvailable() {
        if (!settings.serviceEnabled) {
            AppLogRepository.addLog("Dịch vụ bị tắt trong cài đặt, bỏ qua reload", LogLevel.INFO)
            return
        }

        // Verify if internet actually transmits data
        val (hasRealInternet, pingMs) = NetworkChecker.verifyRealInternetAccess()
        if (hasRealInternet) {
            AppLogRepository.addLog("Đã kiểm tra kết nối Internet thành công (Ping: ${pingMs}ms)", LogLevel.SUCCESS)
            triggerReload("Có Internet")
        } else {
            AppLogRepository.addLog("Đã kết nối Wi-Fi/4G nhưng chưa thông Internet, đang chờ...", LogLevel.INFO)
        }
    }

    private suspend fun handleNetworkValidated() {
        if (wasDisconnected) {
            triggerReload("Internet đã xác thực")
        }
    }

    private fun triggerReload(reason: String) {
        val now = System.currentTimeMillis()
        // Prevent rapid repeated reloads within 12 seconds
        if (now - lastReloadAttemptTime < 12_000 && reason != "Thủ công") {
            AppLogRepository.addLog("Bỏ qua reload vì vừa mới thực hiện cách đây ít giây", LogLevel.INFO)
            return
        }

        lastReloadAttemptTime = now
        wasDisconnected = false

        reloadJob?.cancel()
        reloadJob = serviceScope.launch {
            val delaySec = settings.delaySeconds
            if (delaySec > 0) {
                AppLogRepository.addLog(
                    "Đang chờ $delaySec giây để mạng ổn định trước khi reload Navitel...",
                    LogLevel.INFO
                )
                updateNotificationStatus("Đang chờ $delaySec giây để tải lại Navitel...")
                delay(delaySec * 1000L)
            }

            // Resolve target display dynamically
            val targetDisplayId = DisplayHelper.resolveTargetDisplayId(
                this@NetworkMonitorService,
                settings.autoDetectPip,
                settings.manualDisplayId
            )
            _lastDetectedDisplayId.value = targetDisplayId

            AppLogRepository.addLog(
                "Thực hiện reload Navitel (Lý do: $reason) lên Display ID: $targetDisplayId",
                LogLevel.INFO
            )
            updateNotificationStatus("Đang tải lại Navitel trên Display ID $targetDisplayId...")

            val result = AppLauncher.reloadApp(
                context = this@NetworkMonitorService,
                packageName = settings.targetPackage,
                targetDisplayId = targetDisplayId,
                killFirst = settings.killBeforeLaunch,
                useRoot = settings.useRootIfAvailable
            )

            settings.lastReloadTimestamp = System.currentTimeMillis()
            settings.lastReloadDisplayId = targetDisplayId
            settings.lastReloadStatus = if (result.success) "Thành công" else "Thất bại"

            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val statusText = if (result.success) {
                "Đã reload lúc $timeStr (Display $targetDisplayId)"
            } else {
                "Lỗi reload lúc $timeStr: ${result.message}"
            }
            updateNotificationStatus(statusText)
        }
    }

    private fun updateNotificationStatus(message: String) {
        val targetId = _lastDetectedDisplayId.value
        val notification = buildNotification(message, targetId)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String, targetDisplayId: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val reloadIntent = Intent(this, NetworkMonitorService::class.java).apply {
            action = ACTION_TRIGGER_RELOAD
        }
        val reloadPendingIntent = PendingIntent.getService(
            this,
            1,
            reloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayInfo = if (targetDisplayId >= 0) " | PIP Display ID: $targetDisplayId" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Navitel PIP AutoReload")
            .setContentText(statusText + displayInfo)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_play,
                "Tải lại ngay",
                reloadPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dịch vụ giám sát Navitel PIP",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Giám sát trạng thái kết nối mạng để tự động tải lại Navitel trên màn hình PIP"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _isServiceRunning.value = false
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            displayListener?.let { displayManager?.unregisterDisplayListener(it) }
        } catch (_: Exception) {}
        serviceScope.cancel()
        AppLogRepository.addLog("Dịch vụ giám sát đã dừng", LogLevel.WARNING)
        super.onDestroy()
    }
}
