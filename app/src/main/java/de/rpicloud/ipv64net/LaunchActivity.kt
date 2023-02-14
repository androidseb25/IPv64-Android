package de.rpicloud.ipv64net

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LaunchActivity : AppCompatActivity() {
    private lateinit var activeScreen: LaunchScreens

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isPortrait = resources.getBoolean(R.bool.portrait_only)
        Functions().setOrientaiton(this, isPortrait)

        val isLoggedIn: Boolean = applicationContext.getSharedBool("ISLOGGEDIN", "ISLOGGEDIN")
        val APIKEY = this.getSharedString("APIKEY", "APIKEY")

        activeScreen = if (!isLoggedIn)
            LaunchScreens.LOGIN
        else {
            LaunchScreens.MAIN
        }

        println("APIKEY: ${APIKEY}")
        println(activeScreen)

        val intent = Intent(applicationContext, MainActivity::class.java)
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