package com.example.data

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var targetPackage: String
        get() = prefs.getString(KEY_TARGET_PACKAGE, DEFAULT_TARGET_PACKAGE) ?: DEFAULT_TARGET_PACKAGE
        set(value) = prefs.edit().putString(KEY_TARGET_PACKAGE, value.trim()).apply()

    var autoDetectPip: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DETECT_PIP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DETECT_PIP, value).apply()

    var manualDisplayId: Int
        get() = prefs.getInt(KEY_MANUAL_DISPLAY_ID, 2)
        set(value) = prefs.edit().putInt(KEY_MANUAL_DISPLAY_ID, value).apply()

    var delaySeconds: Int
        get() = prefs.getInt(KEY_DELAY_SECONDS, 3)
        set(value) = prefs.edit().putInt(KEY_DELAY_SECONDS, value.coerceIn(0, 30)).apply()

    var killBeforeLaunch: Boolean
        get() = prefs.getBoolean(KEY_KILL_BEFORE_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_KILL_BEFORE_LAUNCH, value).apply()

    var useRootIfAvailable: Boolean
        get() = prefs.getBoolean(KEY_USE_ROOT, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_ROOT, value).apply()

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START_BOOT, value).apply()

    var lastReloadTimestamp: Long
        get() = prefs.getLong(KEY_LAST_RELOAD_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_RELOAD_TIMESTAMP, value).apply()

    var lastReloadDisplayId: Int
        get() = prefs.getInt(KEY_LAST_RELOAD_DISPLAY_ID, -1)
        set(value) = prefs.edit().putInt(KEY_LAST_RELOAD_DISPLAY_ID, value).apply()

    var lastReloadStatus: String
        get() = prefs.getString(KEY_LAST_RELOAD_STATUS, "Chưa thực hiện") ?: "Chưa thực hiện"
        set(value) = prefs.edit().putString(KEY_LAST_RELOAD_STATUS, value).apply()

    companion object {
        private const val PREFS_NAME = "navitel_pip_autoreload_prefs"
        const val DEFAULT_TARGET_PACKAGE = "com.navitel"

        private const val KEY_TARGET_PACKAGE = "target_package"
        private const val KEY_AUTO_DETECT_PIP = "auto_detect_pip"
        private const val KEY_MANUAL_DISPLAY_ID = "manual_display_id"
        private const val KEY_DELAY_SECONDS = "delay_seconds"
        private const val KEY_KILL_BEFORE_LAUNCH = "kill_before_launch"
        private const val KEY_USE_ROOT = "use_root"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_AUTO_START_BOOT = "auto_start_boot"
        private const val KEY_LAST_RELOAD_TIMESTAMP = "last_reload_timestamp"
        private const val KEY_LAST_RELOAD_DISPLAY_ID = "last_reload_display_id"
        private const val KEY_LAST_RELOAD_STATUS = "last_reload_status"

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
        }
    }
}
