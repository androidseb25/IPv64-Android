package de.rpicloud.ipv64net

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.rpicloud.ipv64net.databinding.AdapterIntegrationBinding

class IntegrationAdapter(
    private var dataSet: MutableList<Integration>,
    private val activity: FragmentActivity?
) : RecyclerView.Adapter<IntegrationAdapter.ViewHolder>() {
    private var mOnChangedInRecyclerListener: OnChangedInRecyclerListener? = null

    fun setOnChangedInRecyclerListener(listener: OnChangedInRecyclerListener) {
        mOnChangedInRecyclerListener = listener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateDataSet(dataSet: MutableList<Integration>) {
        this.dataSet.clear()
        this.dataSet.addAll(dataSet)
        notifyDataSetChanged()
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var binding = AdapterIntegrationBinding.bind(view)

//        var iv_integration_icon: ImageView?
//        var tv_integration_name: TextView?
//
//        init {
//            // Define click listener for the ViewHolder's View.
//            iv_integration_icon = view.iv_integration_icon
//            tv_integration_name = view.tv_integration_name
//        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item

        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.adapter_integration, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        @SuppressLint("RecyclerView") position: Int
    ) {
        with(viewHolder) {
            val item = dataSet[position]

            binding.tvIntegrationName.text = item.integration_name
            Glide.with(activity!!).load(GetIntegrationIcon(item.integration!!))
                .into(binding.ivIntegrationIcon)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        println(dataSet.size)
        return dataSet.size
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.adapter_integration
    }

    private fun GetIntegrationIcon(ig_name: String): Drawable? {
        return when (ig_name) {
            "discord" -> {
                activity?.resources?.getDrawable(
                    R.drawable.ic_discord,
                    activity.resources.newTheme()
                )
            }

            "mobil" -> {
                activity?.resources?.getDrawable(
                    R.drawable.round_devices_other_24,
                    activity.resources.newTheme()
                )
            }

            "email" -> {
                activity?.resources?.getDrawable(
                    R.drawable.round_email_24,
                    activity.resources.newTheme()
                )
            }

            "sms" -> {
                activity?.resources?.getDrawable(
                    R.drawable.ic_round_chat_24,
                    activity.resources.newTheme()
                )
            }

            "telegram" -> {
                activity?.resources?.getDrawable(
                    R.drawable.ic_telegram,
                    activity.resources.newTheme()
                )
            }

            "webhook" -> {
                activity?.resources?.getDrawable(
                    R.drawable.ic_webhook,
                    activity.resources.newTheme()
                )
            }

            "gotify" -> {
                activity?.resources?.getDrawable(
                    R.drawable.ic_gotify,
                    activity.resources.newTheme()
                )
            }

            "pushover" -> {
                activity?.resources?.getDrawable(
                    R.drawable.ic_pushover,
                    activity.resources.newTheme()
                )
            }

            "ntfy" -> {
                activity?.resources?.getDrawable(R.drawable.ic_ntfy, activity.resources.newTheme())
            }

            "matrix" -> {
                activity?.resources?.getDrawable(
                    R.drawable.ic_matrix,
                    activity.resources.newTheme()
                )
            }

            else -> {
                activity?.resources?.getDrawable(
                    R.drawable.round_device_unknown_24,
                    activity.resources.newTheme()
                )
            }
        }
    }
}