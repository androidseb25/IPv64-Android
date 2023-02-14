package de.rpicloud.ipv64net

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import kotlinx.android.synthetic.main.fragment_domain.view.*

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [SettingsActivity.newInstance] factory method to
 * create an instance of this fragment.
 */

class AccountFragment : PreferenceFragmentCompat() {

    lateinit var fm: FragmentManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        /*val isPortrait = resources.getBoolean(R.bool.portrait_only)

        if (isPortrait) {*/
            setPreferencesFromResource(R.xml.main_preferences, rootKey)
        /*} else {
            setPreferencesFromResource(R.xml.main_preferences_tablet, rootKey)
        }*/

        fm = activity?.supportFragmentManager!!

        // Get the switch preference
        val account: Preference? = findPreference("account")
        val logs: Preference? = findPreference("logs")
        val displaylock: SwitchPreference? = findPreference("displaylock")
        val ip: Preference? = findPreference("ip")
        val about: Preference? = findPreference("about")
        val youtube: Preference? = findPreference("youtube")
        val discord: Preference? = findPreference("discord")
        val logout: Preference? = findPreference("logout")

        //whatsnew?.summary = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        // Switch preference click listener
        account?.setOnPreferenceClickListener{
            activity?.setSharedInt("SettingsView", "SettingsView", 1)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setClass(activity?.applicationContext!!, SettingsActivity::class.java)
            startActivity(intent)
            true
        }
        logs?.setOnPreferenceClickListener{
            activity?.setSharedInt("SettingsView", "SettingsView", 2)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setClass(activity?.applicationContext!!, SettingsActivity::class.java)
            startActivity(intent)
            true
        }
        displaylock?.setOnPreferenceChangeListener { preference, newValue ->
            println(newValue)
            true
        }
        ip?.setOnPreferenceClickListener{
            activity?.setSharedInt("SettingsView", "SettingsView", 3)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setClass(activity?.applicationContext!!, SettingsActivity::class.java)
            startActivity(intent)
            true
        }
        about?.setOnPreferenceClickListener{
            activity?.setSharedInt("SettingsView", "SettingsView", 4)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setClass(activity?.applicationContext!!, SettingsActivity::class.java)
            startActivity(intent)
            true
        }
        youtube?.setOnPreferenceClickListener{
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://www.youtube.com/c/RaspberryPiCloud")
            startActivity(intent)
            true
        }
        discord?.setOnPreferenceClickListener{
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://discord.gg/rpicloud")
            startActivity(intent)
            true
        }
        logout?.setOnPreferenceClickListener{
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity?.setSharedBool("ISLOGGEDIN", "ISLOGGEDIN", false)
            activity?.setSharedString("APIKEY", "APIKEY", "")
            intent.setClass(activity?.applicationContext!!, LaunchActivity::class.java)
            activity?.finish()
            startActivity(intent)
            true
        }
    }
}