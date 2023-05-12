package de.rpicloud.ipv64net

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import de.rpicloud.ipv64net.databinding.FragmentDomainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [CalendarFragment.newInstance] factory method to
 * create an instance of this fragment.
 */

class DomainFragment : Fragment(R.layout.fragment_domain), SwipeRefreshLayout.OnRefreshListener {

    private var _binding: FragmentDomainBinding? = null
    private val binding get() = _binding!!

    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    var listOfDomains: DomainResult = DomainResult()
    var accountInfo: AccountInfo = AccountInfo()
    lateinit var domainAdapter: DomainAdapter
    private lateinit var rootView: View

    private var myIP4: MyIP = MyIP()
    private var myIP6: MyIP = MyIP()

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
        _binding = FragmentDomainBinding.inflate(inflater, container, false)
        rootView = binding.root

        ApiNetwork.context = activity?.applicationContext
        ErrorTypes.context = activity?.applicationContext
        binding.swipeLayout.setOnRefreshListener(this)
        getData()

        binding.floatingActionButton.setOnClickListener {
            val fragmentManager = activity?.supportFragmentManager
            val newFragment = CreateDomainDialogFragment()
            newFragment.show(fragmentManager!!, "dialogCreateDomain")
            fragmentManager.executePendingTransactions()
            newFragment.setOnDismissListener {
                onRefresh()
            }
        }

        binding.recyclerDomain.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            if (scrollY > oldScrollY) {
                binding.floatingActionButton.hide()
            } else {
                binding.floatingActionButton.show()
            }
        }

        return rootView
    }

    private fun getMyIPs() {
        GlobalScope.launch(Dispatchers.Default) {
            myIP4 = ApiNetwork.GetMyIP4()
            myIP6 = ApiNetwork.GetMyIP6()
            println(myIP4)
            println(myIP6)
        }
    }

    private fun getData() {
        getMyIPs()
        GlobalScope.launch(Dispatchers.Default) {
            listOfDomains = ApiNetwork.GetDomains()
            accountInfo = ApiNetwork.GetAccountStatus()

            println(listOfDomains)

            if (listOfDomains.info == null) {
                launch(Dispatchers.Main) {
                    binding.swipeLayout.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {
                        onRefresh()
                    }
                }
            } else if (listOfDomains.info!!.contains("500")) {
                launch(Dispatchers.Main) {
                    binding.swipeLayout.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {
                        onRefresh()
                    }
                }
            } else if (listOfDomains.info!!.contains("429")) {
                launch(Dispatchers.Main) {
                    println("SHOW ERROR")
                    binding.swipeLayout.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.tooManyRequests)
                    if (fragmentManager != null) {
                        newFragment.show(fragmentManager, "dialogError")
                        fragmentManager.executePendingTransactions()
                        newFragment.setOnDismissListener { }
                    }
                }
                return@launch
            } else if (listOfDomains.info!!.contains("401")) {
                launch(Dispatchers.Main) {
                    println("SHOW ERROR")
                    binding.swipeLayout.isRefreshing = false/*Toast.makeText(applicationContext, "Keine Domainen gefunden!", Toast.LENGTH_LONG)
                        .show()*/
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.unauthorized)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            }

            launch(Dispatchers.Main) {
                binding.swipeLayout.isRefreshing = false
                binding.recyclerDomain.layoutManager =
                    GridLayoutManager(activity?.applicationContext, 1)
                val sortedSubdomains = listOfDomains.subdomains!!.toSortedMap()
                if (listOfDomains.subdomains != null) {
                    domainAdapter = DomainAdapter(
                        sortedSubdomains, activity, myIP4, myIP6, accountInfo
                    )
                    binding.recyclerDomain.adapter = domainAdapter
                }
            }
        }
    }

    override fun onRefresh() {
        binding.swipeLayout.isRefreshing = true
        getData()
        getMyIPs()
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
        fun newInstance(param1: String, param2: String) = DomainFragment().apply {
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