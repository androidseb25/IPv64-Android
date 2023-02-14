package de.rpicloud.ipv64net

import android.content.Context


fun Context.setSharedString(prefsName: String, key: String, value: String) {
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        .edit().apply { putString(key, value); apply() }
}

fun Context.setSharedBool(prefsName: String, key: String, value: Boolean) {
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        .edit().apply { putBoolean(key, value); apply() }
}

fun Context.setSharedInt(prefsName: String, key: String, value: Int) {
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        .edit().apply { putInt(key, value); apply() }
}

fun Context.getSharedString(prefsName: String, key: String): String {
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        ?.getString(key, "Value is empty!")?.let { return it }
    return "Preference doesn't exist."
}

fun Context.getSharedBool(prefsName: String, key: String): Boolean {
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        ?.getBoolean(key, false)?.let {
            return it
        }
    return false
}

fun Context.getSharedInt(prefsName: String, key: String): Int {
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        ?.getInt(key, 0)?.let {
            return it
        }
    return 0
}