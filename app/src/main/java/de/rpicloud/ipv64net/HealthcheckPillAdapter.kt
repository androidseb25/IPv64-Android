package de.rpicloud.ipv64net

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.button.MaterialButton
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.adapter_detail_domain.view.*
import kotlinx.android.synthetic.main.adapter_domain.view.*
import kotlinx.android.synthetic.main.adapter_healthcheck.view.*
import kotlinx.android.synthetic.main.adapter_healthcheck_pill.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HealthcheckPillAdapter(private var dataSet: MutableList<HealthEvents>,
                             private val activity: FragmentActivity,
                             private val layoutId: Int
) : RecyclerView.Adapter<HealthcheckPillAdapter.ViewHolder>() {
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
        var cv_pill: CardView?

        init {
            // Define click listener for the ViewHolder's View.
            cv_pill = view.cv_pill
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item

        val view = LayoutInflater.from(viewGroup.context)
            .inflate(layoutId, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        @SuppressLint("RecyclerView") position: Int
    ) {
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

        viewHolder.cv_pill?.setCardBackgroundColor(ContextCompat.getColor(activity.applicationContext!!, color))
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        println(dataSet.size)
        return dataSet.size
    }

    override fun getItemViewType(position: Int): Int {
        return layoutId
    }
}