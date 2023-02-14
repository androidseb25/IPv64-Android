package de.rpicloud.ipv64net

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.topAppBar
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.fragment_detail_domain_dialog.view.*

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val isPortrait = resources.getBoolean(R.bool.portrait_only)
        Functions().setOrientaiton(this, isPortrait)
        topAppBar.menu.clear()

        val settingView = getSharedInt("SettingsView", "SettingsView")

        when(settingView) {
            1 -> {
                topAppBar.title = "Account Status"
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, AccountStatusFragment(), "account")
                    .commit()
            }
            2 -> {
                topAppBar.title = "Logs"
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, LogsDialogFragment(), "logs")
                    .commit()
            }
            3 -> {
                topAppBar.title = "Meine IP"
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, IPDialogFragment(), "ips")
                    .commit()
            }
            4 -> {
                topAppBar.title = "Über"
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, AboutDialogFragment(), "about")
                    .commit()
            }
            else -> {
                topAppBar.title = "Account Status"
                AccountStatusFragment()
            }
        }


        setSupportActionBar(topAppBar)

        topAppBar.setNavigationOnClickListener {
            finish()
        }
    }
}