package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object NetworkChecker {

    data class NetworkStatus(
        val isConnected: Boolean,
        val isValidated: Boolean,
        val connectionType: String,
        val hasInternetAccess: Boolean,
        val latencyMs: Long
    )

    fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isNetworkValidated(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun getConnectionType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Không rõ"
        val activeNetwork = cm.activeNetwork ?: return "Không có mạng"
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "Không có mạng"

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dữ liệu di động (4G/LTE)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Cáp mạng (Ethernet)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth Tethering"
            else -> "Mạng khác"
        }
    }

    suspend fun verifyRealInternetAccess(
        targetUrl: String = "https://clients3.google.com/generate_204",
        timeoutMs: Int = 2500
    ): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        try {
            val url = URL(targetUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                useCaches = false
                requestMethod = "GET"
            }
            connection.connect()
            val code = connection.responseCode
            val duration = System.currentTimeMillis() - start
            val success = code in 200..299
            Pair(success, duration)
        } catch (_: Exception) {
            Pair(false, -1L)
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun checkFullStatus(context: Context): NetworkStatus {
        val isConnected = isNetworkConnected(context)
        val isValidated = isNetworkValidated(context)
        val type = getConnectionType(context)

        if (!isConnected) {
            return NetworkStatus(
                isConnected = false,
                isValidated = false,
                connectionType = type,
                hasInternetAccess = false,
                latencyMs = -1L
            )
        }

        val (hasRealInternet, latency) = verifyRealInternetAccess()
        return NetworkStatus(
            isConnected = true,
            isValidated = isValidated,
            connectionType = type,
            hasInternetAccess = hasRealInternet,
            latencyMs = latency
        )
    }
}
