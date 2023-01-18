package de.sr.ipv64net

import android.content.Context
import androidx.preference.PreferenceManager

class SPreferences {
    fun savePreferences(context: Context, key: String, `object`: Any) {

        val sharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()

        when (`object`) {
            is String -> {
                editor.putString(key, `object`)
            }
            is Int -> {
                editor.putInt(key, `object`)
            }
            is Boolean -> {
                editor.putBoolean(key, `object`)
            }
            is Float -> {
                editor.putFloat(key, `object`)
            }
            is Long -> {
                editor.putLong(key, `object`)
            }
            else -> {
                editor.putString(key, `object`.toString())
            }
        }

        editor.apply()
    }

    fun loadPreferences(context: Context, key: String, defaultObject: Any?): Any? {

        val sharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(context)

        when (defaultObject) {
            is String -> {
                return sharedPreferences.getString(key, defaultObject)
            }
            is Int -> {
                return sharedPreferences.getInt(key, defaultObject)
            }
            is Boolean -> {
                return sharedPreferences.getBoolean(key, defaultObject)
            }
            is Float -> {
                return sharedPreferences.getFloat(key, defaultObject)
            }
            is Long -> {
                return sharedPreferences.getLong(key, defaultObject)
            }
            else -> return null
        }

    }

    fun deletePreferences(context: Context, key: String) {
        val sharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(context)
        sharedPreferences.edit().remove(key).apply()
    }

    fun saveSecretKey(context: Context, value: String) {

        val sharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(context)
        val editor = sharedPreferences!!.edit()
        editor.putString("KEY", value)
        editor.apply()
    }

    fun getSecretKey(context: Context): String? {
        val sharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(context)
        return sharedPreferences!!.getString("KEY", null)
    }

}