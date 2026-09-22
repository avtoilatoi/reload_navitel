package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

object RootHelper {

    private var isRootCached: Boolean? = null

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (isRootCached != null) return@withContext isRootCached == true

        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        )

        for (path in paths) {
            if (File(path).exists()) {
                isRootCached = true
                return@withContext true
            }
        }

        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            val hasRoot = line != null && line.contains("uid=0")
            process.destroy()
            isRootCached = hasRoot
            return@withContext hasRoot
        } catch (_: Exception) {
            isRootCached = false
            return@withContext false
        }
    }

    suspend fun forceStop(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("am force-stop $packageName\n")
            os.writeBytes("exit\n")
            os.flush()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun startAppOnDisplayViaRoot(packageName: String, displayId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val cmd = if (displayId >= 0) {
                "am start --display $displayId $(cmd package resolve-activity --brief $packageName | tail -n 1)\n"
            } else {
                "am start $(cmd package resolve-activity --brief $packageName | tail -n 1)\n"
            }
            os.writeBytes(cmd)
            os.writeBytes("exit\n")
            os.flush()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }
}
