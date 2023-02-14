package de.rpicloud.ipv64net

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.android.synthetic.main.fragment_domain.*
import kotlinx.android.synthetic.main.fragment_domain.view.*
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

class DomainFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        rootView = inflater.inflate(R.layout.fragment_domain, container, false)
        ApiNetwork.context = activity?.applicationContext
        ErrorTypes.context = activity?.applicationContext
        rootView.swipe_layout.setOnRefreshListener(this)
        getData()

        rootView.floating_action_button.setOnClickListener {
            val fragmentManager = activity?.supportFragmentManager
            val newFragment = CreateDomainDialogFragment()
            newFragment.show(fragmentManager!!, "dialogCreateDomain")
            fragmentManager.executePendingTransactions()
            newFragment.setOnDismissListener {
                onRefresh()
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
                    rootView.swipe_layout.isRefreshing = false
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
                    rootView.swipe_layout.isRefreshing = false
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
                    rootView.swipe_layout.isRefreshing = false
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.tooManyRequests)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {
                        onRefresh()
                    }
                }
                return@launch
            } else if (listOfDomains.info!!.contains("401")) {
                launch(Dispatchers.Main) {
                    println("SHOW ERROR")
                    rootView.swipe_layout.isRefreshing = false
                    /*Toast.makeText(applicationContext, "Keine Domainen gefunden!", Toast.LENGTH_LONG)
                        .show()*/
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.unauthorized)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {
                        onRefresh()
                    }
                }
                return@launch
            }

            launch(Dispatchers.Main) {
                rootView.swipe_layout.isRefreshing = false
                rootView.recycler_domain?.layoutManager =
                    GridLayoutManager(activity?.applicationContext, 1)
                domainAdapter = DomainAdapter(listOfDomains.subdomains!!, activity, myIP4, myIP6, accountInfo)
                rootView.recycler_domain?.adapter = domainAdapter
            }
        }
    }

    override fun onRefresh() {
        swipe_layout.isRefreshing = true
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
        fun newInstance(param1: String, param2: String) =
            DomainFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}