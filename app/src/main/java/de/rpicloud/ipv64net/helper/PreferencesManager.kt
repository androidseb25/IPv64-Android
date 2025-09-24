package de.rpicloud.ipv64net.helper

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferencesManager {

    companion object {
        @SuppressLint("UseKtx")
        inline fun <reified T> saveList(ctx: Context, key: String, list: List<T>) {
            val gson = Gson()
            val jsonText = gson.toJson(list)
            with(ctx.getSharedPreferences(key, Context.MODE_PRIVATE).edit()) {
                putString(key, jsonText)
                apply()
            }
        }

        @SuppressLint("UseKtx")
        fun saveString(ctx: Context, key: String, value: String) {
            with(ctx.getSharedPreferences(key, Context.MODE_PRIVATE).edit()) {
                putString(key, value)
                apply()
            }
        }

        @SuppressLint("UseKtx")
        fun saveBool(ctx: Context, key: String, value: Boolean) {
            with(ctx.getSharedPreferences(key, Context.MODE_PRIVATE).edit()) {
                putBoolean(key, value)
                apply()
            }
        }

        @SuppressLint("UseKtx")
        fun saveInt(ctx: Context, key: String, value: Int) {
            with(ctx.getSharedPreferences(key, Context.MODE_PRIVATE).edit()) {
                putInt(key, value)
                apply()
            }
        }

        @SuppressLint("UseKtx")
        fun saveFloat(ctx: Context, key: String, value: Float) {
            with(ctx.getSharedPreferences(key, Context.MODE_PRIVATE).edit()) {
                putFloat(key, value)
                apply()
            }
        }

        @SuppressLint("UseKtx")
        fun saveLong(ctx: Context, key: String, value: Long) {
            with(ctx.getSharedPreferences(key, Context.MODE_PRIVATE).edit()) {
                putLong(key, value)
                apply()
            }
        }

        inline fun <reified T> loadList(ctx: Context, key: String): List<T> {
            val gson = Gson()
            val preferences = ctx.getSharedPreferences(key, Context.MODE_PRIVATE)
            val jsonText = preferences.getString(key, "[]")
            val type = object : TypeToken<List<T>>() {}.type
            return gson.fromJson(jsonText, type)
        }

        fun loadString(ctx: Context, key: String): String {
            val preferences = ctx.getSharedPreferences(key, Context.MODE_PRIVATE)
            val value = preferences.getString(key, "")
            return value ?: ""
        }

        fun loadBool(ctx: Context, key: String): Boolean {
            val preferences = ctx.getSharedPreferences(key, Context.MODE_PRIVATE)
            val value = preferences.getBoolean(key, false)
            return value
        }

        fun loadInt(ctx: Context, key: String): Int {
            val preferences = ctx.getSharedPreferences(key, Context.MODE_PRIVATE)
            val value = preferences.getInt(key, 0)
            return value
        }

        fun loadFloat(ctx: Context, key: String): Float {
            val preferences = ctx.getSharedPreferences(key, Context.MODE_PRIVATE)
            val value = preferences.getFloat(key, 0f)
            return value
        }

        fun loadLong(ctx: Context, key: String): Long {
            val preferences = ctx.getSharedPreferences(key, Context.MODE_PRIVATE)
            val value = preferences.getLong(key, 0)
            return value
        }
    }

}