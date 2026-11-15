package de.rpicloud.ipv64net.helper

import android.content.Context
import android.util.Log
import com.secntfy.android.NtfyException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object SecNtfyLog {
    private const val KEY = "SECNTFY_LOG"
    private const val LIMIT = 100
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()

    enum class Status(val code: String, val label: String) {
        INFO("I", "Info"), SUCCESS("S", "Success"), FAILED("F", "Failed"), SKIPPED("K", "Skipped")
    }

    data class Entry(val time: Long, val message: String, val status: Status)

    fun entries(context: Context): List<Entry> = PreferencesManager.loadString(context, KEY)
        .lineSequence().mapNotNull(::parseEntry).toList()

    internal fun parseEntry(line: String): Entry? {
        val fields = line.split('\t', limit = 3)
        val time = fields.firstOrNull()?.toLongOrNull() ?: return null
        if (fields.size != 3) return null
        val message = fields[2].replace("SecNtfy ", "").replaceFirstChar { it.uppercase() }
        val status = when (fields[1]) {
            // Earlier builds stored both successful operations and informational events as I.
            "I" -> when (message) {
                "Delivery receipt accepted", "Delivery receipt already accepted", "Device registered",
                "Notification posted", "Notification image posted" -> Status.SUCCESS
                else -> Status.INFO
            }
            "W" -> if (message.startsWith("Device registration skipped") ||
                message.startsWith("Receipt unavailable") || message == "Notification permission missing" ||
                message == "Configuration missing"
            ) Status.SKIPPED else Status.FAILED
            else -> Status.entries.firstOrNull { it.code == fields[1] } ?: Status.INFO
        }
        return Entry(time, message, status)
    }

    // Only pass fixed event descriptions. Exception messages may include device keys or payloads.
    @Synchronized
    fun record(context: Context, message: String, error: Throwable? = null, status: Status = if (error == null) Status.INFO else Status.FAILED) {
        val detail = when (error) {
            null -> message
            is NtfyException.ApiError -> "$message (status ${error.statusCode})"
            is NtfyException.HttpError -> "$message (HTTP ${error.statusCode})"
            else -> "$message (${error.javaClass.simpleName})"
        }
        if (status == Status.FAILED || status == Status.SKIPPED) Log.w("SecNtfy", detail) else Log.i("SecNtfy", detail)
        try {
            val line = "${System.currentTimeMillis()}\t${status.code}\t$detail"
            val previous = PreferencesManager.loadString(context, KEY).lineSequence().filter { it.isNotEmpty() }
            val history = (sequenceOf(line) + previous).take(LIMIT).joinToString("\n")
            PreferencesManager.saveString(context, KEY, history)
            changes.value++
        } catch (_: RuntimeException) {
            Log.w("SecNtfy", "Could not save the notification log")
        }
    }
}
