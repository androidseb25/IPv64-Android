package de.rpicloud.ipv64net.helper

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

internal const val SCREENSHOT_PROTECTION_ENABLED = "SCREENSHOT_PROTECTION_ENABLED"

@Composable
internal fun rememberScreenshotProtectionEnabled(): Boolean {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(SCREENSHOT_PROTECTION_ENABLED, Context.MODE_PRIVATE)
    }
    var enabled by remember(preferences) {
        mutableStateOf(preferences.getBoolean(SCREENSHOT_PROTECTION_ENABLED, true))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
            enabled = prefs.getBoolean(SCREENSHOT_PROTECTION_ENABLED, true)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        enabled = preferences.getBoolean(SCREENSHOT_PROTECTION_ENABLED, true)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}
