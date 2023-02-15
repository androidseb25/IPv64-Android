package de.rpicloud.ipv64net

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.preference.ListPreference
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
        val themes: Preference? = findPreference("designList")

        //whatsnew?.summary = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        val themeArr = resources.getStringArray(R.array.arrTheme)
        val themeActive = requireActivity().applicationContext.let { activity?.applicationContext!!.getSharedInt("THEME", "THEME") } as Int
        themes!!.summary = themeArr[themeActive]

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
        themes?.setOnPreferenceChangeListener { preference, newValue ->
            val stringValue = newValue.toString()

            if (preference is ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                val index = preference.findIndexOfValue(stringValue)

                // Set the summary to reflect the new value.
                themes.summary = if (index >= 0)
                    preference.entries[index]
                else
                    preference.entries[0]
                preference.context.let { activity?.setSharedInt("THEME", "THEME", index) }
                when (index) {
                    1 -> setTheme(AppCompatDelegate.MODE_NIGHT_NO, requireActivity())
                    2 -> setTheme(AppCompatDelegate.MODE_NIGHT_YES, requireActivity())
                    0 -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            setTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, requireActivity())
                        } else {
                            setTheme(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY, requireActivity())
                        }
                    }
                }
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                themes.summary = stringValue
            }

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

    private fun setTheme(mode: Int, activity: FragmentActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            var m = mode
            if (mode == -1) {
                m = UiModeManager.MODE_NIGHT_AUTO
            }
            val man = activity.applicationContext!!.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            man.setApplicationNightMode(m)
        } else {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
        activity.applicationContext!!.setSharedBool("THEME_RESTART", "THEME_RESTART", true)
        requireActivity().recreate()
    }
}