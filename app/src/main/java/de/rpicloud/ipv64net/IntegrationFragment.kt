package de.rpicloud.ipv64net

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.Gson
import de.rpicloud.ipv64net.databinding.FragmentIntegrationsBinding
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

class IntegrationFragment : Fragment(R.layout.fragment_integrations),
    SwipeRefreshLayout.OnRefreshListener {

    private var _binding: FragmentIntegrationsBinding? = null
    private val binding get() = _binding!!

    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private var integrationResult: IntegrationResult = IntegrationResult()
    lateinit var integrationAdapter: IntegrationAdapter
    private lateinit var rootView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
        getData()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        _binding = FragmentIntegrationsBinding.inflate(inflater, container, false)
        rootView = binding.root

        ApiNetwork.context = activity?.applicationContext
        ErrorTypes.context = activity?.applicationContext
        binding.swipeLayoutIntegration.setOnRefreshListener(this)
        binding.recyclerIntegration.layoutManager =
            GridLayoutManager(activity?.applicationContext, 1)

        return rootView
    }

    private fun getData() {
        GlobalScope.launch(Dispatchers.Default) {

            val responseIntegration = ApiNetwork.GetIntegrations()

            if (responseIntegration.isEmpty()) {
                launch(Dispatchers.Main) {
                    binding.swipeLayoutIntegration.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            }
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
                        integrationResult.status = integrationObject[currentDynamicKey].toString()
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

            if (integrationResult.info == null || integrationResult.status == null) {
                launch(Dispatchers.Main) {
                    binding.swipeLayoutIntegration.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
            } else if (integrationResult.info!!.contains("500") || integrationResult.status!!.contains(
                    "500"
                )
            ) {
                launch(Dispatchers.Main) {
                    binding.swipeLayoutIntegration.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
            } else if (integrationResult.info!!.contains("429") || integrationResult.status!!.contains(
                    "429"
                )
            ) {
                launch(Dispatchers.Main) {
                    binding.swipeLayoutIntegration.isRefreshing = false
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
            } else if (integrationResult.info!!.contains("401") || integrationResult.status!!.contains(
                    "401"
                )
            ) {
                launch(Dispatchers.Main) {
                    binding.swipeLayoutIntegration.isRefreshing = false
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
                binding.swipeLayoutIntegration.isRefreshing = false
                activity?.applicationContext?.setSharedString(
                    "INTEGRATIONS", "INTEGRATIONS", Gson().toJson(integrationResult)
                )
                integrationResult.integration.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.integration_name!! })
                integrationAdapter = IntegrationAdapter(
                    integrationResult.integration, requireActivity()
                )
                binding.recyclerIntegration.adapter = integrationAdapter
            }
        }
    }

    override fun onRefresh() {
        binding.swipeLayoutIntegration.isRefreshing = true
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
        fun newInstance(param1: String, param2: String) = IntegrationFragment().apply {
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