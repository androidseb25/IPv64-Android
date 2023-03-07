package de.rpicloud.ipv64net

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.google.gson.Gson
import kotlinx.android.synthetic.main.fragment_domain.*
import kotlinx.android.synthetic.main.fragment_domain.view.*
import kotlinx.android.synthetic.main.fragment_healthcheck.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Objects

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [CalendarFragment.newInstance] factory method to
 * create an instance of this fragment.
 */

class HealthcheckFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private lateinit var rootView: View
    private lateinit var spinnDialog: MaterialDialog

    private var healthCheckResult: HealthCheckResult = HealthCheckResult()
    private var integrationResult: IntegrationResult = IntegrationResult()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        rootView = inflater.inflate(R.layout.fragment_healthcheck, container, false)
        ApiNetwork.context = activity?.applicationContext
        ErrorTypes.context = activity?.applicationContext
        rootView.swipe_layout_hc.setOnRefreshListener(this)

        spinnDialog = MaterialDialog(requireActivity())
        spinnDialog.title(null, "Daten werden geladen...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        onRefresh()

        rootView.floating_action_button_hc.setOnClickListener {
            val fragmentManager = activity?.supportFragmentManager
            val newFragment = CreateHealthcheckDialogFragment()
            newFragment.show(fragmentManager!!, "dialogCreateHealthcheck")
            fragmentManager.executePendingTransactions()
            newFragment.setOnDismissListener {
                onRefresh()
            }
        }

        return rootView
    }

    private fun getData() {
        rootView.swipe_layout_hc.isRefreshing = true
        spinnDialog.show()
        GlobalScope.launch(Dispatchers.Default) {
            val response = ApiNetwork.GetHealthchecks()
            val responseIntegration = ApiNetwork.GetIntegrations()

            if (response.isEmpty()) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    rootView.swipe_layout_hc.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            }

            if (responseIntegration.isEmpty()) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    rootView.swipe_layout_hc.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            }

            val healthCheckObject = JSONObject(response)
            val integrationObject = JSONObject(responseIntegration)

            healthCheckResult = HealthCheckResult()
            integrationResult = IntegrationResult()
            println(healthCheckObject.keys())
            println(integrationObject.keys())
            val keys = healthCheckObject.keys()
            val keysIntegration = integrationObject.keys()
            var inKeys = false
            var inIntegrationKeys = false
            while (keys.hasNext()) { // loop to get the dynamic key
                val currentDynamicKey = keys.next() as String
                inKeys = true
                // get the value of the dynamic key
                println("currentDynamicKey")
                println(currentDynamicKey)
                if (currentDynamicKey != "info" && currentDynamicKey != "status" && currentDynamicKey != "get_account_info") {
                    val currentDynamicValue: JSONObject =
                        healthCheckObject.getJSONObject(currentDynamicKey)
                    val hc = Gson().fromJson(currentDynamicValue.toString(), HealthCheck::class.java)
                    // do something here with the value... or either make another while loop to Iterate further
                    healthCheckResult.domain.add(hc)
                    println("currentDynamicValue.toString()")
                    println(currentDynamicValue.toString())
                } else {
                    if (currentDynamicKey == "info") {
                        healthCheckResult.info = healthCheckObject[currentDynamicKey].toString()
                    }
                    if (currentDynamicKey == "status") {
                        healthCheckResult.status = healthCheckObject[currentDynamicKey].toString()
                    }
                    if (currentDynamicKey == "get_account_info") {
                        healthCheckResult.get_account_info = healthCheckObject[currentDynamicKey].toString()
                    }
                }
            }

            while (keysIntegration.hasNext()) { // loop to get the dynamic key
                val currentDynamicKey = keysIntegration.next() as String
                inIntegrationKeys = true
                // get the value of the dynamic key
                println("currentDynamicKey")
                println(currentDynamicKey)
                if (currentDynamicKey != "info" && currentDynamicKey != "status" && currentDynamicKey != "get_account_info") {
                    val currentDynamicValue: JSONObject =
                        integrationObject.getJSONObject(currentDynamicKey)
                    val ig = Gson().fromJson(currentDynamicValue.toString(), Integration::class.java)
                    // do something here with the value... or either make another while loop to Iterate further
                    integrationResult.integration.add(ig)
                    println("currentDynamicValue.toString()")
                    println(currentDynamicValue.toString())
                } else {
                    if (currentDynamicKey == "info") {
                        integrationResult.info = integrationObject[currentDynamicKey].toString()
                    }
                    if (currentDynamicKey == "status") {
                        integrationResult.status = integrationObject[currentDynamicKey].toString()
                    }
                    if (currentDynamicKey == "get_account_info") {
                        integrationResult.get_account_info = integrationObject[currentDynamicKey].toString()
                    }
                }
            }

            if (!inIntegrationKeys) {
                val gson = Gson()
                println(response)
                integrationResult = gson.fromJson(response, IntegrationResult::class.java)
            }

            println("healthCheckResult")
            println(healthCheckResult)
            println("integrationResult")
            println(integrationResult)
            if (healthCheckResult.info == null || healthCheckResult.status == null || integrationResult.info == null || integrationResult.status == null) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    rootView.swipe_layout_hc.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
            } else if (healthCheckResult.info!!.contains("500") || healthCheckResult.status!!.contains("500") || integrationResult.info!!.contains("500") || integrationResult.status!!.contains("500")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    rootView.swipe_layout_hc.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
            } else if (healthCheckResult.info!!.contains("429") || healthCheckResult.status!!.contains("429") || integrationResult.info!!.contains("429") || integrationResult.status!!.contains("429")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    rootView.swipe_layout_hc.isRefreshing = false
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
            } else if (healthCheckResult.info!!.contains("401") || healthCheckResult.status!!.contains("401") || integrationResult.info!!.contains("401") || integrationResult.status!!.contains("401")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    rootView.swipe_layout_hc.isRefreshing = false
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
                rootView.swipe_layout_hc.isRefreshing = false
                val activeCount = healthCheckResult.domain.count { it.healthstatus == 1 }
                val pausedCount = healthCheckResult.domain.count { it.healthstatus == 2 }
                val warningCount = healthCheckResult.domain.count { it.healthstatus == 3 }
                val alarmCount = healthCheckResult.domain.count { it.healthstatus == 4 }

                rootView.tv_active_healthcheck.text = activeCount.toString()
                rootView.tv_pause_healthcheck.text = pausedCount.toString()
                rootView.tv_warn_healthcheck.text = warningCount.toString()
                rootView.tv_alarm_healthcheck.text = alarmCount.toString()

                rootView.recycler_healthcheckview?.layoutManager = GridLayoutManager(activity?.applicationContext, 1)
                val healthAdapter = HealthcheckAdapter(
                    healthCheckResult.domain,
                    requireActivity()
                )
                rootView.recycler_healthcheckview?.adapter = healthAdapter

                activity?.applicationContext?.setSharedString("INTEGRATIONS","INTEGRATIONS", Gson().toJson(integrationResult))

                healthAdapter.registerAdapterDataObserver(object :  RecyclerView.AdapterDataObserver() {
                    override fun onChanged() {
                        //Do some task.
                        onRefresh()
                    }
                })
            }
        }
    }

    override fun onRefresh() {
        rootView.swipe_layout_hc.isRefreshing = true
        getData()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment CalendarFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) = HealthcheckFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PARAM1, param1)
                putString(ARG_PARAM2, param2)
            }
        }
    }
}