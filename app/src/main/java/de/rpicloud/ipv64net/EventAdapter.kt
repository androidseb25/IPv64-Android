package de.rpicloud.ipv64net

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.adapter_logs.view.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class EventAdapter(private var dataSet: MutableList<HealthEvents>,
                   private val activity: FragmentActivity
) : RecyclerView.Adapter<EventAdapter.ViewHolder>() {
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
        var tv_event_time: TextView?
        var tv_status: TextView?
        var tv_text: TextView?

        init {
            // Define click listener for the ViewHolder's View.
            tv_event_time = view.tv_log_date
            tv_status = view.tv_log_title
            tv_text = view.tv_log_content
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item

        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.adapter_logs, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        @SuppressLint("RecyclerView") position: Int
    ) {
        val item = dataSet[position]
        println(item)

        var text = when (item.status) {
            StatusType.Active.type.statusId -> {
                StatusType.Active.type.name!!
            }
            StatusType.Pause.type.statusId -> {
                StatusType.Pause.type.name!!
            }
            StatusType.Warning.type.statusId -> {
                StatusType.Warning.type.name!!
            }
            StatusType.Alarm.type.statusId -> {
                StatusType.Alarm.type.name!!
            }
            StatusType.Fallback.type.statusId -> {
                StatusType.Fallback.type.name!!
            }
            else -> {
                ""
            }
        }

        viewHolder.tv_status!!.text = text

        var color = R.color.ipv64_red

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
            StatusType.Fallback.type.statusId -> {
                StatusType.Fallback.type.color!!
            }
            else -> {
                R.color.ipv64_red
            }
        }

        viewHolder.tv_status!!.setTextColor(
            ContextCompat.getColor(
            activity.applicationContext!!,
            color
        ))

        viewHolder.tv_text!!.text = item.text

        val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val localDateTime: LocalDateTime = LocalDateTime.parse(item.event_time, df)
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        val output: String = formatter.format(localDateTime)

        viewHolder.tv_event_time!!.text = output
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        println(dataSet.size)
        return dataSet.size
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.adapter_logs
    }
}