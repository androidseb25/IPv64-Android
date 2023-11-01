package de.rpicloud.ipv64net

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import de.rpicloud.ipv64net.databinding.AdapterLogsBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LogsAdapter(
    private var dataSet: MutableList<MyLogs>, private val activity: FragmentActivity
) : RecyclerView.Adapter<LogsAdapter.ViewHolder>() {
    private var mOnChangedInRecyclerListener: OnChangedInRecyclerListener? = null

    fun setOnChangedInRecyclerListener(listener: OnChangedInRecyclerListener) {
        mOnChangedInRecyclerListener = listener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateDataSet(dataSet: MutableList<MyLogs>) {
        this.dataSet.clear()
        this.dataSet.addAll(dataSet)
        notifyDataSetChanged()
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var binding = AdapterLogsBinding.bind(view)

//        var tv_log_date: TextView?
//        var tv_log_title: TextView?
//        var tv_log_content: TextView?
//
//        init {
//            // Define click listener for the ViewHolder's View.
//            tv_log_date = view.tv_log_date
//            tv_log_title = view.tv_log_title
//            tv_log_content = view.tv_log_content
//        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item

        val view =
            LayoutInflater.from(viewGroup.context).inflate(R.layout.adapter_logs, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(
        viewHolder: ViewHolder, @SuppressLint("RecyclerView") position: Int
    ) {
        with(viewHolder) {
            val item = dataSet[position]
            println(item)

            binding.tvLogTitle.text = item.header

            binding.tvLogContent.text = item.content

            val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val localDateTime: LocalDateTime = LocalDateTime.parse(item.time, df)
            val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            val output: String = formatter.format(localDateTime)

            binding.tvLogDate.text = output
        }
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