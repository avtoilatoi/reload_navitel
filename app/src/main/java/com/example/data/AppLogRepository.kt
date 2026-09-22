package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val message: String,
    val level: LogLevel
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}

object AppLogRepository {
    private val idCounter = AtomicLong(1)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private const val MAX_LOGS = 200

    fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            message = message,
            level = level
        )
        synchronized(this) {
            val current = _logs.value.toMutableList()
            current.add(0, entry) // newest first
            if (current.size > MAX_LOGS) {
                current.removeAt(current.lastIndex)
            }
            _logs.value = current
        }
    }

    fun clear() {
        synchronized(this) {
            _logs.value = emptyList()
        }
    }
}
