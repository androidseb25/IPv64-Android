package de.rpicloud.ipv64net

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.adapter_domain.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class DomainAdapter(
    private var dataSet: MutableMap<String, Domain>,
    private val activity: FragmentActivity?,
    private val ipv4: MyIP,
    private val ipv6: MyIP,
    private val accountInfo: AccountInfo
) : RecyclerView.Adapter<DomainAdapter.ViewHolder>() {
    private var mOnChangedInRecyclerListener: OnChangedInRecyclerListener? = null

    fun setOnChangedInRecyclerListener(listener: OnChangedInRecyclerListener) {
        mOnChangedInRecyclerListener = listener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateDataSet(dataSet: MutableMap<String, Domain>) {
        this.dataSet.clear()
        this.dataSet.putAll(dataSet)
        notifyDataSetChanged()
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var tv_domain: TextView?
        var card: CardView?
        var ipCircle: CardView?
        /*var iv_icon: AppCompatImageView?
        var tv_value: TextView?
        var tv_date: TextView?
        var cv_card: MaterialCardView?*/

        init {
            // Define click listener for the ViewHolder's View.
            tv_domain = view.tv_domain
            card = view.card
            ipCircle = view.ipCircle
            /*iv_icon = view.iv_icon
            tv_value = view.tv_value
            tv_date = view.tv_date
            cv_card = view.card*/
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item

        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.adapter_domain, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        @SuppressLint("RecyclerView") position: Int
    ) {
        val key = dataSet.keys.toMutableList()[position]
        val domain = dataSet[key]
        println("$position. $key")
        println(domain)
        viewHolder.tv_domain!!.text = key

        var color = R.color.ipv64_red
        if (domain != null) {
            if (domain.records?.isNotEmpty()!!) {
                var firstRec = domain.records!!.firstOrNull {
                    it.type == "A"
                }
                color = if (firstRec?.content == ipv4.ip) {
                    R.color.ipv64_error_green
                } else {
                    R.color.ipv64_red
                }
            }
        }

        viewHolder.ipCircle?.setCardBackgroundColor(ContextCompat.getColor(activity?.applicationContext!!, color))
        viewHolder.card?.setOnClickListener {
            println(domain)
            val fragmentManager = activity?.supportFragmentManager
            val newFragment = DetailDomainDialogFragment()
            newFragment.arguments = Bundle().apply {
                putString("DOMAIN", Gson().toJson(domain))
                putString("MYIP", Gson().toJson(ipv4))
                putString("DOMAINKEY", key)
                putString("ACCOUNTINFO", Gson().toJson(accountInfo))
            }
            newFragment.show(fragmentManager!!, "dialogDomain")
            fragmentManager.executePendingTransactions()
            newFragment.setOnDismissListener {
                getData()
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        println(dataSet.keys.size)
        return dataSet.keys.size
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.adapter_domain
    }

    private fun getData() {
        GlobalScope.launch(Dispatchers.Default) {
            val listOfDomains = ApiNetwork.GetDomains()
            println(listOfDomains)
            if (listOfDomains.subdomains == null) {
                launch(Dispatchers.Main) {
                    Toast.makeText(activity, "Keine Domainen gefunden!", Toast.LENGTH_LONG)
                        .show()
                    UpdateDataSet(mutableMapOf())
                }
                return@launch
            }

            launch(Dispatchers.Main) {
                UpdateDataSet(listOfDomains.subdomains!!)
            }
        }
    }
}