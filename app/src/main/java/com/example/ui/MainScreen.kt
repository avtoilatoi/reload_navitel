package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppLogRepository
import com.example.data.AppSettings
import com.example.data.LogLevel
import com.example.service.NetworkMonitorService
import com.example.util.AppLauncher
import com.example.util.DisplayHelper
import com.example.util.DisplayInfo
import com.example.util.NetworkChecker
import com.example.util.RootHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings.getInstance(context) }

    // State flows from service & data
    val isServiceRunning by NetworkMonitorService.isServiceRunning.collectAsState()
    val serviceNetworkStatus by NetworkMonitorService.currentInternetStatus.collectAsState()
    val detectedPipDisplayId by NetworkMonitorService.lastDetectedDisplayId.collectAsState()
    val logList by AppLogRepository.logs.collectAsState()

    // Local mutable states for settings
    var targetPackage by remember { mutableStateOf(settings.targetPackage) }
    var autoDetectPip by remember { mutableStateOf(settings.autoDetectPip) }
    var manualDisplayId by remember { mutableStateOf(settings.manualDisplayId) }
    var delaySeconds by remember { mutableStateOf(settings.delaySeconds) }
    var killBeforeLaunch by remember { mutableStateOf(settings.killBeforeLaunch) }
    var useRoot by remember { mutableStateOf(settings.useRootIfAvailable) }
    var serviceEnabled by remember { mutableStateOf(settings.serviceEnabled) }
    var autoStartOnBoot by remember { mutableStateOf(settings.autoStartOnBoot) }

    // Live displays
    var displays by remember { mutableStateOf(DisplayHelper.getAllDisplays(context)) }
    var isCheckingNetwork by remember { mutableStateOf(false) }
    var networkCheckResult by remember { mutableStateOf<NetworkChecker.NetworkStatus?>(null) }
    var isReloadingTest by remember { mutableStateOf(false) }
    var hasRootAccess by remember { mutableStateOf<Boolean?>(null) }

    // Check root status on startup
    LaunchedEffect(Unit) {
        hasRootAccess = RootHelper.isRootAvailable()
        val netStatus = NetworkChecker.checkFullStatus(context)
        networkCheckResult = netStatus
    }

    // Periodically refresh display list in case car launcher spawns PIP 2
    LaunchedEffect(Unit) {
        while (true) {
            displays = DisplayHelper.getAllDisplays(context)
            delay(4000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Car Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Navitel PIP AutoReload",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Khởi chạy Navitel khi có Internet trên Car Box",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            displays = DisplayHelper.getAllDisplays(context)
                            scope.launch {
                                isCheckingNetwork = true
                                networkCheckResult = NetworkChecker.checkFullStatus(context)
                                isCheckingNetwork = false
                            }
                            Toast.makeText(context, "Đã làm mới thông tin", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("refresh_status_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Làm mới trạng thái")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // SECTION 1: Status & Master Switch
            item {
                ServiceStatusCard(
                    isServiceRunning = isServiceRunning,
                    serviceEnabled = serviceEnabled,
                    detectedPipDisplayId = if (detectedPipDisplayId >= 0) detectedPipDisplayId else DisplayHelper.resolveTargetDisplayId(context, autoDetectPip, manualDisplayId),
                    onToggleService = { enabled ->
                        serviceEnabled = enabled
                        settings.serviceEnabled = enabled
                        if (enabled) {
                            NetworkMonitorService.start(context)
                            Toast.makeText(context, "Đã bật dịch vụ giám sát", Toast.LENGTH_SHORT).show()
                        } else {
                            NetworkMonitorService.stop(context)
                            Toast.makeText(context, "Đã dừng dịch vụ", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isReloadingTest = isReloadingTest,
                    onTriggerManualReload = {
                        scope.launch {
                            isReloadingTest = true
                            val targetId = DisplayHelper.resolveTargetDisplayId(context, autoDetectPip, manualDisplayId)
                            val result = AppLauncher.reloadApp(
                                context = context,
                                packageName = targetPackage,
                                targetDisplayId = targetId,
                                killFirst = killBeforeLaunch,
                                useRoot = useRoot
                            )
                            isReloadingTest = false
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }

            // SECTION 2: Network Connectivity Status Card
            item {
                NetworkStatusCard(
                    isChecking = isCheckingNetwork,
                    networkStatus = networkCheckResult,
                    serviceNetworkStatus = serviceNetworkStatus,
                    onCheckNow = {
                        scope.launch {
                            isCheckingNetwork = true
                            networkCheckResult = NetworkChecker.checkFullStatus(context)
                            isCheckingNetwork = false
                        }
                    }
                )
            }

            // SECTION 3: PIP 2 Display Management (Dynamic ID support)
            item {
                PipDisplayManagementCard(
                    autoDetectPip = autoDetectPip,
                    manualDisplayId = manualDisplayId,
                    displays = displays,
                    onToggleAutoDetect = { auto ->
                        autoDetectPip = auto
                        settings.autoDetectPip = auto
                    },
                    onSelectManualDisplayId = { id ->
                        manualDisplayId = id
                        settings.manualDisplayId = id
                    },
                    onTestDisplay = { targetDisplayId ->
                        scope.launch {
                            isReloadingTest = true
                            val res = AppLauncher.reloadApp(
                                context = context,
                                packageName = targetPackage,
                                targetDisplayId = targetDisplayId,
                                killFirst = killBeforeLaunch,
                                useRoot = useRoot
                            )
                            isReloadingTest = false
                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // SECTION 4: Advanced App Settings
            item {
                SettingsCard(
                    targetPackage = targetPackage,
                    onTargetPackageChange = {
                        targetPackage = it
                        settings.targetPackage = it
                    },
                    delaySeconds = delaySeconds,
                    onDelayChange = {
                        delaySeconds = it
                        settings.delaySeconds = it
                    },
                    killBeforeLaunch = killBeforeLaunch,
                    onKillToggle = {
                        killBeforeLaunch = it
                        settings.killBeforeLaunch = it
                    },
                    useRoot = useRoot,
                    hasRoot = hasRootAccess == true,
                    onRootToggle = {
                        useRoot = it
                        settings.useRootIfAvailable = it
                    },
                    autoStartOnBoot = autoStartOnBoot,
                    onAutoStartToggle = {
                        autoStartOnBoot = it
                        settings.autoStartOnBoot = it
                    },
                    onOpenBatterySettings = {
                        openBatteryOptimizationSettings(context)
                    }
                )
            }

            // SECTION 5: Real-time Event Logs
            item {
                LiveLogsCard(
                    logs = logList,
                    onClearLogs = { AppLogRepository.clear() }
                )
            }
        }
    }
}

@Composable
fun ServiceStatusCard(
    isServiceRunning: Boolean,
    serviceEnabled: Boolean,
    detectedPipDisplayId: Int,
    onToggleService: (Boolean) -> Unit,
    isReloadingTest: Boolean,
    onTriggerManualReload: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dịch vụ tự động chạy ngầm",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isServiceRunning) "Đang hoạt động - Sẵn sàng reload khi có mạng" else "Dịch vụ đang dừng",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isServiceRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
                    )
                }

                Switch(
                    checked = serviceEnabled,
                    onCheckedChange = onToggleService,
                    modifier = Modifier.testTag("service_master_switch")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip(
                    icon = Icons.Default.CheckCircle,
                    label = if (isServiceRunning) "Dịch vụ: BẬT" else "Dịch vụ: TẮT",
                    color = if (isServiceRunning) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier = Modifier.weight(1f)
                )

                StatusChip(
                    icon = Icons.Default.Tv,
                    label = "PIP ID: $detectedPipDisplayId",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manual Test Reload Button
            Button(
                onClick = onTriggerManualReload,
                enabled = !isReloadingTest,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("reload_now_button")
            ) {
                if (isReloadingTest) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Đang thực hiện reload...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TẢI LẠI NAVITEL NGAY (TEST PIP)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun NetworkStatusCard(
    isChecking: Boolean,
    networkStatus: NetworkChecker.NetworkStatus?,
    serviceNetworkStatus: String,
    onCheckNow: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Network",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Trạng thái Internet & Mạng",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                TextButton(
                    onClick = onCheckNow,
                    enabled = !isChecking,
                    modifier = Modifier.testTag("check_network_button")
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Kiểm tra lại")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val isConnected = networkStatus?.isConnected == true
            val hasInternet = networkStatus?.hasInternetAccess == true
            val connType = networkStatus?.connectionType ?: "Đang xác định..."
            val latency = networkStatus?.latencyMs ?: -1L

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (hasInternet) Color(0xFF1B5E20).copy(alpha = 0.15f)
                        else if (isConnected) Color(0xFFE65100).copy(alpha = 0.15f)
                        else Color(0xFFB71C1C).copy(alpha = 0.15f)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasInternet) Color(0xFF2E7D32)
                            else if (isConnected) Color(0xFFEF6C00)
                            else Color(0xFFC62828)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (hasInternet) "Đã thông Internet ($connType)"
                        else if (isConnected) "Đã kết nối $connType (Đang kiểm tra thông mạng)"
                        else "Chưa có kết nối Internet",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (latency >= 0) {
                        Text(
                            text = "Độ trễ phản hồi (Ping Google): ${latency}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Giám sát nền: $serviceNetworkStatus",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun PipDisplayManagementCard(
    autoDetectPip: Boolean,
    manualDisplayId: Int,
    displays: List<DisplayInfo>,
    onToggleAutoDetect: (Boolean) -> Unit,
    onSelectManualDisplayId: (Int) -> Unit,
    onTestDisplay: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "Displays",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Màn hình PIP 2 & Display ID Động",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Android Box ô tô thường gán ID màn hình phụ linh hoạt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Switch Auto Detect
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tự động phát hiện ID động (Khuyên dùng)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Tự động tìm kiếm và gán vào màn hình PIP 2 khi Launcher khởi tạo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = autoDetectPip,
                    onCheckedChange = onToggleAutoDetect,
                    modifier = Modifier.testTag("auto_detect_pip_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Danh sách màn hình đang nhận diện trên thiết bị (${displays.size}):",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (displays.isEmpty()) {
                Text(
                    text = "Chưa phát hiện màn hình phụ nào. Khi Launcher hiển thị PIP, màn hình sẽ xuất hiện tại đây.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    displays.forEach { display ->
                        val isSelected = (!autoDetectPip && manualDisplayId == display.id) || (autoDetectPip && display.isLikelyPip)
                        DisplayItemRow(
                            display = display,
                            isSelected = isSelected,
                            isAutoMode = autoDetectPip,
                            onSelect = { onSelectManualDisplayId(display.id) },
                            onTest = { onTestDisplay(display.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DisplayItemRow(
    display: DisplayInfo,
    isSelected: Boolean,
    isAutoMode: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surface,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ID: ${display.id}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (display.isDefault) {
                        BadgeLabel(text = "Chính (Default)", color = Color(0xFF1565C0))
                    }
                    if (display.isLikelyPip) {
                        Spacer(modifier = Modifier.width(4.dp))
                        BadgeLabel(text = "Mục tiêu PIP 2", color = Color(0xFF2E7D32))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${display.name} (${display.width}x${display.height} px)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isAutoMode) {
                    RadioButton(
                        selected = isSelected,
                        onClick = onSelect,
                        modifier = Modifier.testTag("select_display_${display.id}")
                    )
                }

                IconButton(
                    onClick = onTest,
                    modifier = Modifier.testTag("test_display_${display.id}")
                ) {
                    Icon(
                        Icons.Default.PlayCircleOutline,
                        contentDescription = "Chạy thử lên màn hình này",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    targetPackage: String,
    onTargetPackageChange: (String) -> Unit,
    delaySeconds: Int,
    onDelayChange: (Int) -> Unit,
    killBeforeLaunch: Boolean,
    onKillToggle: (Boolean) -> Unit,
    useRoot: Boolean,
    hasRoot: Boolean,
    onRootToggle: (Boolean) -> Unit,
    autoStartOnBoot: Boolean,
    onAutoStartToggle: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cài đặt nâng cao & Khởi động",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Package Name field
            OutlinedTextField(
                value = targetPackage,
                onValueChange = onTargetPackageChange,
                label = { Text("Tên gói ứng dụng (Package Name)") },
                leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("package_name_input")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Quick suggestion chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = { onTargetPackageChange("com.navitel") },
                    label = { Text("Navitel") }
                )
                SuggestionChip(
                    onClick = { onTargetPackageChange("com.google.android.apps.maps") },
                    label = { Text("Google Maps") }
                )
                SuggestionChip(
                    onClick = { onTargetPackageChange("com.waze") },
                    label = { Text("Waze") }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Delay seconds selector
            Text(
                text = "Thời gian chờ ổn định mạng trước khi tải lại: $delaySeconds giây",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Slider(
                value = delaySeconds.toFloat(),
                onValueChange = { onDelayChange(it.toInt()) },
                valueRange = 0f..15f,
                steps = 14,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delay_seconds_slider")
            )
            Text(
                text = "Giúp mạng 4G/Wi-Fi kịp cấp phát IP và thông kết nối đến máy chủ bản đồ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Auto-start on boot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Tự khởi động cùng Android Box", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Kích hoạt dịch vụ giám sát ngay khi thiết bị mở nguồn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoStartOnBoot,
                    onCheckedChange = onAutoStartToggle,
                    modifier = Modifier.testTag("auto_start_switch")
                )
            }

            Divider(modifier = Modifier.padding(vertical = 10.dp))

            // Kill stuck process
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Đóng tiến trình đơ logo trước khi tải lại", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Giải phóng logo Navitel bị treo để khởi động lại sạch sẽ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = killBeforeLaunch,
                    onCheckedChange = onKillToggle,
                    modifier = Modifier.testTag("kill_process_switch")
                )
            }

            Divider(modifier = Modifier.padding(vertical = 10.dp))

            // Use Root if available
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Sử dụng quyền Root (nếu có)", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        if (hasRoot) {
                            BadgeLabel(text = "Có Root", color = Color(0xFF2E7D32))
                        } else {
                            BadgeLabel(text = "Không Root", color = Color(0xFF757575))
                        }
                    }
                    Text(
                        text = "Cho phép force-stop và điều hướng màn hình PIP với quyền quản trị",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useRoot,
                    onCheckedChange = onRootToggle,
                    modifier = Modifier.testTag("use_root_switch")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Battery optimization intent button
            OutlinedButton(
                onClick = onOpenBatterySettings,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_battery_settings_button")
            ) {
                Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Quyền tự chạy & Không tối ưu hóa pin")
            }
        }
    }
}

@Composable
fun LiveLogsCard(
    logs: List<com.example.data.LogEntry>,
    onClearLogs: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E) // terminal dark
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Logs",
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nhật ký hoạt động (Logs)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                TextButton(
                    onClick = onClearLogs,
                    modifier = Modifier.testTag("clear_logs_button")
                ) {
                    Text("Xóa log", color = Color(0xFFB0BEC5))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 260.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF121212))
                    .padding(10.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "Chưa có sự kiện nào được ghi nhận. Dịch vụ sẽ ghi log tự động khi mạng kết nối hoặc reload.",
                        color = Color(0xFF757575),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    LazyColumn(
                        reverseLayout = false,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            val color = when (log.level) {
                                LogLevel.SUCCESS -> Color(0xFF81C784)
                                LogLevel.WARNING -> Color(0xFFFFB74D)
                                LogLevel.ERROR -> Color(0xFFE57373)
                                LogLevel.INFO -> Color(0xFFE0E0E0)
                            }
                            Text(
                                text = "[${log.formattedTime}] ${log.message}",
                                color = color,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BadgeLabel(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, color)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val packageName = context.packageName
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                context.startActivity(intent)
                return
            }
        }
    } catch (_: Exception) {}

    // Fallback to app details
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Vui lòng kiểm tra quyền tự khởi chạy trong Cài đặt hệ thống", Toast.LENGTH_LONG).show()
    }
}
