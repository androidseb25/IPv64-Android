package de.rpicloud.ipv64net

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import de.rpicloud.ipv64net.databinding.AdapterHealthcheckPillBinding
import de.rpicloud.ipv64net.databinding.AdapterHealthcheckSmallPillBinding

class HealthcheckSmallPillAdapter(
    private var dataSet: MutableList<HealthEvents>,
    private val activity: FragmentActivity
) : RecyclerView.Adapter<HealthcheckSmallPillAdapter.ViewHolder>() {
    private var mOnChangedInRecyclerListener: OnChangedInRecyclerListener? = null

    fun setOnChangedInRecyclerListener(listener: OnChangedInRecyclerListener) {
        mOnChangedInRecyclerListener = listener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateDataSet(dataSet: MutableList<HealthEvents>) {
        this.dataSet.clear()
        this.dataSet.addAll(dataSet)
        notifyDataSetChanged()
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var binding: AdapterHealthcheckSmallPillBinding =
            AdapterHealthcheckSmallPillBinding.bind(view)

//        var cv_pill: CardView?
//
//        init {
//            // Define click listener for the ViewHolder's View.
//            cv_pill = view.cv_pill
//        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item

        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.adapter_healthcheck_small_pill, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(
        viewHolder: ViewHolder, @SuppressLint("RecyclerView") position: Int
    ) {
        with(viewHolder) {
            val item = dataSet[position]
            println(item)

            var color = StatusType.Fallback.type.color!!

            color = when (item.status) {
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

                StatusType.Transparent.type.statusId -> {
                    StatusType.Transparent.type.color!!
                }

                else -> {
                    StatusType.Fallback.type.color!!
                }
            }

            binding.cvPill.setCardBackgroundColor(
                ContextCompat.getColor(
                    activity.applicationContext!!, color
                )
            )
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        println(dataSet.size)
        return dataSet.size
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.adapter_healthcheck_small_pill
    }
}