package de.rpicloud.ipv64net

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [SettingsActivity.newInstance] factory method to
 * create an instance of this fragment.
 */

class AccountStatusFragment : PreferenceFragmentCompat() {

    lateinit var fm: FragmentManager
    var accountInfo: AccountInfo = AccountInfo()
    private lateinit var spinnDialog: MaterialDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        spinnDialog = MaterialDialog(requireActivity())
        spinnDialog.title(null, "Daten werden geladen...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        getData()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        /*val isPortrait = resources.getBoolean(R.bool.portrait_only)

        if (isPortrait) {*/
        setPreferencesFromResource(R.xml.accountstatus_preferences, rootKey)
        /*} else {
            setPreferencesFromResource(R.xml.main_preferences_tablet, rootKey)
        }*/

        fm = activity?.supportFragmentManager!!
    }

    private fun setUpViews() {
        // Get the switch preference
        val accountstatus: Preference? = findPreference("accountstatus")
        val accountclass: Preference? = findPreference("accountclass")
        val email: Preference? = findPreference("email")
        val registered: Preference? = findPreference("registered")
        val dyndns: Preference? = findPreference("dyndns")
        val domains: Preference? = findPreference("domains")
        val dyndns_update_limit: Preference? = findPreference("dyndns_update_limit")
        val healthchecks: Preference? = findPreference("healthchecks")
        val healthchecks_updates: Preference? = findPreference("healthchecks_updates")
        val api_limits: Preference? = findPreference("api_limits")
        val sms_limits: Preference? = findPreference("sms_limits")
        val dyndns_updatehash: Preference? = findPreference("dyndns_updatehash")
        val apikey: Preference? = findPreference("apikey")

        accountstatus?.summary = if (accountInfo.account_status == 1) "Aktiviert" else "Inaktiv"
        accountclass?.summary = accountInfo.account_class?.class_name.toString()
        email?.summary = accountInfo.email
        val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val localDateTime: LocalDateTime = LocalDateTime.parse(accountInfo.reg_date, df)
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        val output: String = formatter.format(localDateTime)
        registered?.summary = output
        dyndns?.summary =
            "${accountInfo.dyndns_subdomains} / ${accountInfo.account_class?.dyndns_domain_limit}"
        domains?.summary =
            "${accountInfo.owndomains} / ${accountInfo.account_class?.owndomain_limit}"
        dyndns_update_limit?.summary =
            "${accountInfo.dyndns_updates} / ${accountInfo.account_class?.dyndns_update_limit}"

        healthchecks?.summary =
            "${accountInfo.healthchecks} / ${accountInfo.account_class?.healthcheck_limit}"
        healthchecks_updates?.summary =
            "${accountInfo.healthchecks_updates} / ${accountInfo.account_class?.healthcheck_update_limit}"

        val unendlich =
            if (accountInfo.account_class?.api_limit == 0) "∞" else "" + accountInfo.account_class?.api_limit
        api_limits?.summary = "${accountInfo.api_updates} / $unendlich"
        sms_limits?.summary = "${accountInfo.sms_count} / ${accountInfo.account_class?.sms_limit}"

        dyndns_updatehash?.setOnPreferenceClickListener {
            val clipboard: ClipboardManager =
                requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData =
                ClipData.newPlainText("DynDNS Updatehash kopiert!", accountInfo.update_hash)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                requireActivity().applicationContext,
                "DynDNS Updatehash kopiert!",
                Toast.LENGTH_LONG
            ).show()
            true
        }
        apikey?.setOnPreferenceClickListener {
            val clipboard: ClipboardManager =
                requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("API Key kopiert!", accountInfo.api_key)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                requireActivity().applicationContext,
                "API Key kopiert!",
                Toast.LENGTH_LONG
            ).show()
            true
        }
    }

    private fun getData() {
        spinnDialog.show()
        GlobalScope.launch(Dispatchers.Default) {
            accountInfo = ApiNetwork.GetAccountStatus()

            println(accountInfo)

            if (accountInfo.info == null) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
            } else if (accountInfo.info!!.contains("500")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
            } else if (accountInfo.info!!.contains("429")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    println("SHOW ERROR")
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.tooManyRequests)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {
                        getData()
                    }
                }
                return@launch
            } else if (accountInfo.info!!.contains("401")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    println("SHOW ERROR")
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.unauthorized)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            }

            launch(Dispatchers.Main) {
                spinnDialog.hide()
                setUpViews()
            }
        }
    }
}