package de.rpicloud.ipv64net

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.adapter_healthcheck.view.*
import kotlinx.android.synthetic.main.fragment_healthcheck.view.*

class HealthcheckAdapter(
    private var dataSet: MutableList<HealthCheck>,
    private val activity: FragmentActivity
) : RecyclerView.Adapter<HealthcheckAdapter.ViewHolder>() {
    private var mOnChangedInRecyclerListener: OnChangedInRecyclerListener? = null

    fun setOnChangedInRecyclerListener(listener: OnChangedInRecyclerListener) {
        mOnChangedInRecyclerListener = listener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateDataSet(dataSet: MutableList<HealthCheck>) {
        this.dataSet.clear()
        this.dataSet.addAll(dataSet)
        notifyDataSetChanged()
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        var cardView: CardView?
        var tv_healthcheck: TextView?
        var statusCircle: CardView?
        var recycler_pillView: RecyclerView?

        init {
            // Define click listener for the ViewHolder's View.
            cardView = view.card
            tv_healthcheck = view.tv_healthcheck
            statusCircle = view.statusCircle
            recycler_pillView = view.recycler_pillView
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item

        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.adapter_healthcheck, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        @SuppressLint("RecyclerView") position: Int
    ) {
        val item = dataSet[position]
        println(item)

        viewHolder.tv_healthcheck?.text = item.name

        var color = R.color.ipv64_red

        color = when (item.healthstatus) {
            StatusType.Active.type.statusId -> {
                StatusType.Active.type.color!!
            }
            StatusType.Pause.type.statusId -> {
                StatusType.Pause.type.color!!
            }
            StatusType.Warning.type.statusId -> {
                StatusType.Warning.type.color!!
            }
            StatusType.Alarm.type.statusId -> {
                StatusType.Alarm.type.color!!
            }
            else -> {
                R.color.ipv64_red
            }
        }

        viewHolder.statusCircle?.setCardBackgroundColor(
            ContextCompat.getColor(
                activity.applicationContext!!,
                color
            )
        )

        val lastXEvents = item.events.take(10).toMutableList()

        if (lastXEvents.isNotEmpty()) {
            if (lastXEvents.count() < 10) {
                val diff = 10 - lastXEvents.count()

                for (i in 1..diff) {
                    lastXEvents.add(HealthEvents("", -1, ""))
                }
            }

            viewHolder.recycler_pillView?.layoutManager = LinearLayoutManager(
                activity.applicationContext,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            val healthAdapter = HealthcheckPillAdapter(
                lastXEvents.reversed().toMutableList(),
                activity,
                R.layout.adapter_healthcheck_small_pill
            )
            viewHolder.recycler_pillView?.isNestedScrollingEnabled = false;
            viewHolder.recycler_pillView?.adapter = healthAdapter
        }

        viewHolder.cardView?.setOnClickListener {
            val fragmentManager = activity?.supportFragmentManager
            val newFragment = HealthcheckDetailFragmentDialog()
            newFragment.arguments = Bundle().apply {
                putString("HEALTHCHECK", Gson().toJson(item))
            }
            newFragment.show(fragmentManager!!, "dialogDomain")
            fragmentManager.executePendingTransactions()
            newFragment.setOnDismissListener { }
        }

        println(lastXEvents)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        println(dataSet.size)
        return dataSet.size
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.adapter_healthcheck
    }
}