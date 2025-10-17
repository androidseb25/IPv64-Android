package de.rpicloud.ipv64net.main.startup.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.rpicloud.ipv64net.helper.PreferencesManager
import de.rpicloud.ipv64net.main.activity.MainActivity
import de.rpicloud.ipv64net.models.LaunchScreens

@SuppressLint("CustomSplashScreen")
class LaunchActivity : AppCompatActivity() {

    private lateinit var activeScreen: LaunchScreens

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apiKey: String = PreferencesManager.loadString(applicationContext, "APIKEY")

        activeScreen = if (apiKey.isEmpty())
            LaunchScreens.LOGIN
        else {
            LaunchScreens.MAIN
        }

        val intent = Intent(applicationContext, LaunchActivity::class.java)
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK)

        when (activeScreen) {
            LaunchScreens.LOGIN -> intent.setClass(applicationContext, LoginActivity::class.java)
            else -> {
                intent.setClass(applicationContext, MainActivity::class.java)
            }
        }

        applicationContext.startActivity(intent)
        finish()
    }
}