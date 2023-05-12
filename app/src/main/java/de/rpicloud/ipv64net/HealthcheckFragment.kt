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
import de.rpicloud.ipv64net.databinding.FragmentHealthcheckBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [CalendarFragment.newInstance] factory method to
 * create an instance of this fragment.
 */

class HealthcheckFragment : Fragment(R.layout.fragment_healthcheck),
    SwipeRefreshLayout.OnRefreshListener {

    private var _binding: FragmentHealthcheckBinding? = null
    private val binding get() = _binding!!

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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHealthcheckBinding.inflate(inflater, container, false)
        rootView = binding.root

        // Inflate the layout for this fragment
        ApiNetwork.context = activity?.applicationContext
        ErrorTypes.context = activity?.applicationContext
        binding.swipeLayoutHc.setOnRefreshListener(this)

        spinnDialog = MaterialDialog(requireActivity())
        spinnDialog.title(null, "Daten werden geladen...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        onRefresh()

        binding.floatingActionButtonHc.setOnClickListener {
            val fragmentManager = activity?.supportFragmentManager
            val newFragment = CreateHealthcheckDialogFragment()
            newFragment.show(fragmentManager!!, "dialogCreateHealthcheck")
            fragmentManager.executePendingTransactions()
            newFragment.setOnDismissListener {
                onRefresh()
            }
        }

        binding.nsvHealthcheck.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            if (scrollY > oldScrollY) {
                binding.floatingActionButtonHc.hide()
            } else {
                binding.floatingActionButtonHc.show()
            }
        }

        return rootView
    }

    private fun getData() {
        binding.swipeLayoutHc.isRefreshing = true
        spinnDialog.show()
        GlobalScope.launch(Dispatchers.Default) {
            val response = ApiNetwork.GetHealthchecks()
            var responseIntegration = ""
            var setByApi = false

            val sharedStorageIntegration =
                activity?.applicationContext?.getSharedString("INTEGRATIONS", "INTEGRATIONS")

            if (sharedStorageIntegration.isNullOrEmpty()) {
                setByApi = true
                responseIntegration = ApiNetwork.GetIntegrations()
            }

            if (response.isEmpty()) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    binding.swipeLayoutHc.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            }

            if (responseIntegration.isEmpty() && setByApi) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    binding.swipeLayoutHc.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            }

            val healthCheckObject = JSONObject(response)

            healthCheckResult = HealthCheckResult()
            println(healthCheckObject.keys())
            val keys = healthCheckObject.keys()

            var inKeys = false
            while (keys.hasNext()) { // loop to get the dynamic key
                val currentDynamicKey = keys.next() as String
                inKeys = true
                // get the value of the dynamic key
                println("currentDynamicKey")
                println(currentDynamicKey)
                if (currentDynamicKey != "info" && currentDynamicKey != "status" && currentDynamicKey != "get_account_info") {
                    val currentDynamicValue: JSONObject =
                        healthCheckObject.getJSONObject(currentDynamicKey)
                    val hc =
                        Gson().fromJson(currentDynamicValue.toString(), HealthCheck::class.java)
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
                        healthCheckResult.get_account_info =
                            healthCheckObject[currentDynamicKey].toString()
                    }
                }
            }

            if (setByApi) {
                val integrationObject = JSONObject(responseIntegration)
                integrationResult = IntegrationResult()
                println(integrationObject.keys())
                val keysIntegration = integrationObject.keys()
                var inIntegrationKeys = false
                while (keysIntegration.hasNext()) { // loop to get the dynamic key
                    val currentDynamicKey = keysIntegration.next() as String
                    inIntegrationKeys = true
                    // get the value of the dynamic key
                    println("currentDynamicKey")
                    println(currentDynamicKey)
                    if (currentDynamicKey != "info" && currentDynamicKey != "status" && currentDynamicKey != "get_account_info") {
                        val currentDynamicValue: JSONObject =
                            integrationObject.getJSONObject(currentDynamicKey)
                        val ig =
                            Gson().fromJson(currentDynamicValue.toString(), Integration::class.java)
                        // do something here with the value... or either make another while loop to Iterate further
                        integrationResult.integration.add(ig)
                        println("currentDynamicValue.toString()")
                        println(currentDynamicValue.toString())
                    } else {
                        if (currentDynamicKey == "info") {
                            integrationResult.info = integrationObject[currentDynamicKey].toString()
                        }
                        if (currentDynamicKey == "status") {
                            integrationResult.status =
                                integrationObject[currentDynamicKey].toString()
                        }
                        if (currentDynamicKey == "get_account_info") {
                            integrationResult.get_account_info =
                                integrationObject[currentDynamicKey].toString()
                        }
                    }
                }

                if (!inIntegrationKeys) {
                    val gson = Gson()
                    println(responseIntegration)
                    integrationResult =
                        gson.fromJson(responseIntegration, IntegrationResult::class.java)
                }
                println("integrationResult")
                println(integrationResult)
            }

            println("healthCheckResult")
            println(healthCheckResult)
            if (healthCheckResult.info == null || healthCheckResult.status == null || ((integrationResult.info == null || integrationResult.status == null) && setByApi)) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    binding.swipeLayoutHc.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
            } else if (healthCheckResult.info!!.contains("500") || healthCheckResult.status!!.contains(
                    "500"
                ) || ((integrationResult.info!!.contains("500") || integrationResult.status!!.contains(
                    "500"
                )) && setByApi)
            ) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    binding.swipeLayoutHc.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
            } else if (healthCheckResult.info!!.contains("429") || healthCheckResult.status!!.contains(
                    "429"
                ) || ((integrationResult.info!!.contains("429") || integrationResult.status!!.contains(
                    "429"
                )) && setByApi)
            ) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    binding.swipeLayoutHc.isRefreshing = false
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
            } else if (healthCheckResult.info!!.contains("401") || healthCheckResult.status!!.contains(
                    "401"
                ) || ((integrationResult.info!!.contains("401") || integrationResult.status!!.contains(
                    "401"
                )) && setByApi)
            ) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    binding.swipeLayoutHc.isRefreshing = false
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
                binding.swipeLayoutHc.isRefreshing = false
                val activeCount = healthCheckResult.domain.count { it.healthstatus == 1 }
                val pausedCount = healthCheckResult.domain.count { it.healthstatus == 2 }
                val warningCount = healthCheckResult.domain.count { it.healthstatus == 3 }
                val alarmCount = healthCheckResult.domain.count { it.healthstatus == 4 }

                binding.tvActiveHealthcheck.text = activeCount.toString()
                binding.tvPauseHealthcheck.text = pausedCount.toString()
                binding.tvWarnHealthcheck.text = warningCount.toString()
                binding.tvAlarmHealthcheck.text = alarmCount.toString()

                binding.recyclerHealthcheckview.layoutManager =
                    GridLayoutManager(activity?.applicationContext, 1)
                healthCheckResult.domain.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                val healthAdapter = HealthcheckAdapter(
                    healthCheckResult.domain, requireActivity()
                )
                binding.recyclerHealthcheckview.adapter = healthAdapter

                if (setByApi) {
                    activity?.applicationContext?.setSharedString(
                        "INTEGRATIONS", "INTEGRATIONS", Gson().toJson(integrationResult)
                    )
                }

//                healthAdapter.registerAdapterDataObserver(object :
//                    RecyclerView.AdapterDataObserver() {
//                    override fun onChanged() {
//                        //Do some task.
//                        onRefresh()
//                    }
//                })
            }
        }
    }

    override fun onRefresh() {
        binding.swipeLayoutHc.isRefreshing = true
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}