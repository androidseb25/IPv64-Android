package de.rpicloud.ipv64net.helper

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Date.formatGerman(): String {
    val format = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    return format.format(this)
}

fun String.parseDbDate(): String {
    val dbFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val date: Date? = runCatching { dbFormat.parse(this) }.getOrNull()
    return date?.formatGerman() ?: "01.01.0001"
}