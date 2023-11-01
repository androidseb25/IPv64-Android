package de.rpicloud.ipv64net

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.rpicloud.ipv64net.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        setContentView(R.layout.activity_settings)

        val isPortrait = resources.getBoolean(R.bool.portrait_only)
        Functions().setOrientaiton(this, isPortrait)
        binding.topAppBar.menu.clear()

        val settingView = getSharedInt("SettingsView", "SettingsView")

        when (settingView) {
            1 -> {
                binding.topAppBar.title = "Account Status"
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, AccountStatusFragment(), "account")
                    .commit()
            }

            2 -> {
                binding.topAppBar.title = "Logs"
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, LogsDialogFragment(), "logs")
                    .commit()
            }

            3 -> {
                binding.topAppBar.title = "Meine IP"
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, IPDialogFragment(), "ips")
                    .commit()
            }

            4 -> {
                binding.topAppBar.title = "Über"
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, AboutDialogFragment(), "about")
                    .commit()
            }

            else -> {
                binding.topAppBar.title = "Account Status"
                AccountStatusFragment()
            }
        }


        setSupportActionBar(binding.topAppBar)

        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }
    }
}