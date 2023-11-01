package de.rpicloud.ipv64net

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import de.rpicloud.ipv64net.databinding.AdapterHealthcheckBinding

class HealthcheckAdapter(
    private var dataSet: MutableList<HealthCheck>, private val activity: FragmentActivity
) : RecyclerView.Adapter<HealthcheckAdapter.ViewHolder>() {
    private var mOnChangedInRecyclerListener: OnChangedInRecyclerListener? = null

    fun setOnChangedInRecyclerListener(listener: () -> kotlin.Unit): kotlin.Unit {
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

        val binding = AdapterHealthcheckBinding.bind(view)

//        var cardView: CardView?
//        var tv_healthcheck: TextView?
//        var statusCircle: CardView?
//        var recycler_pillView: RecyclerView?
//
//        init {
//            // Define click listener for the ViewHolder's View.
//            cardView = view.card
//            tv_healthcheck = view.tv_healthcheck
//            statusCircle = view.statusCircle
//            recycler_pillView = view.recycler_pillView
//        }
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
        viewHolder: ViewHolder, @SuppressLint("RecyclerView") position: Int
    ) {
        with(viewHolder) {

            val item = dataSet[position]
            println(item)

            binding.tvHealthcheck.text = item.name

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

            binding.statusCircle.setCardBackgroundColor(
                ContextCompat.getColor(
                    activity.applicationContext!!, color
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

                binding.recyclerPillView.layoutManager = LinearLayoutManager(
                    activity.applicationContext, LinearLayoutManager.HORIZONTAL, false
                )
                val healthAdapter = HealthcheckSmallPillAdapter(
                    lastXEvents.reversed().toMutableList(),
                    activity
                )
                binding.recyclerPillView.isNestedScrollingEnabled = false
                binding.recyclerPillView.adapter = healthAdapter
            }

            binding.card.setOnClickListener {
                val fragmentManager = activity.supportFragmentManager
                val newFragment = HealthcheckDetailFragmentDialog()
                newFragment.arguments = Bundle().apply {
                    putString("HEALTHCHECK", Gson().toJson(item))
                }
                newFragment.show(fragmentManager, "dialogDomain")
                fragmentManager.executePendingTransactions()
                newFragment.setOnDismissListener {
                    notifyDataSetChanged()
                }
            }

            println(lastXEvents)
        }
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