package de.rpicloud.ipv64net

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.button.MaterialButton
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.adapter_detail_domain.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DetailDomainAdapter(private var dataSet: MutableList<RecordInfos>,
                          private val activity: FragmentActivity
) : RecyclerView.Adapter<DetailDomainAdapter.ViewHolder>() {
    private var mOnChangedInRecyclerListener: OnChangedInRecyclerListener? = null
    private lateinit var spinnDialog: MaterialDialog

    fun setOnChangedInRecyclerListener(listener: OnChangedInRecyclerListener) {
        mOnChangedInRecyclerListener = listener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateDataSet(dataSet: MutableList<RecordInfos>) {
        this.dataSet.clear()
        this.dataSet.addAll(dataSet)
        notifyDataSetChanged()
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var tv_header_typ: TextView?
        var tv_ttl: TextView?
        var tv_typ: TextView?
        var tv_wert: TextView?
        var tv_datum: TextView?
        var tv_praefix: TextView?
        var btn_deleteBtn: MaterialButton?

        init {
            // Define click listener for the ViewHolder's View.
            tv_header_typ = view.tv_header_typ
            tv_ttl = view.tv_ttl
            tv_typ = view.tv_typ
            tv_wert = view.tv_wert
            tv_datum = view.tv_datum
            tv_praefix = view.tv_praefix
            btn_deleteBtn = view.btn_deleteRecord
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item

        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.adapter_detail_domain, viewGroup, false)

        spinnDialog = MaterialDialog(activity)
        spinnDialog.title(null, "Daten werden gesendet...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        @SuppressLint("RecyclerView") position: Int
    ) {
        val item = dataSet[position]
        println(item)

        viewHolder.tv_header_typ!!.text = item.type

        viewHolder.tv_ttl!!.text = item.ttl!!.toString()

        viewHolder.tv_typ!!.text = item.type

        viewHolder.tv_wert!!.text = item.content

        val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val localDateTime: LocalDateTime = LocalDateTime.parse(item.last_update, df)
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        val output: String = formatter.format(localDateTime)

        viewHolder.tv_datum!!.text = output

        viewHolder.tv_praefix!!.text = item.praefix

        viewHolder.btn_deleteBtn!!.setOnClickListener {
            val fragmentManager = activity.supportFragmentManager
            val newFragment = ErrorDialogFragment(ErrorTypes.deleteDNSRecord)
            newFragment.show(fragmentManager, "dialoDelete")
            fragmentManager.executePendingTransactions()
            newFragment.setOnDismissListener {
                val isCanceld = activity.getSharedBool("ISCANCELD", "ISCANCELD") as Boolean
                if (!isCanceld) {
                    spinnDialog.show()
                    activity.setSharedBool("ISCANCELD", "ISCANCELD", true)
                    println(item.record_id)
                    deleteRecord(item.record_id!!)
                }
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        println(dataSet.size)
        return dataSet.size
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.adapter_detail_domain
    }

    private fun deleteRecord(recordId: Int) {
        GlobalScope.launch(Dispatchers.Default) {
            val result = ApiNetwork.DeleteDNSRecord(recordId)

            println(result)

            if (result.info!!.contains("429")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    println("SHOW ERROR")
                    val fragmentManager = activity.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.tooManyRequests)
                    newFragment.show(fragmentManager, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {

                    }
                }
                return@launch
            } else if (result.info!!.contains("401")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    println("SHOW ERROR")
                    val fragmentManager = activity.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.unauthorized)
                    newFragment.show(fragmentManager, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {
                    }
                }
                return@launch
            }

            launch(Dispatchers.Main) {
                spinnDialog.hide()
                val frag = activity.supportFragmentManager.fragments.firstOrNull { it.tag == "dialogDomain" } as DialogFragment
                frag.dismiss()
            }
        }
    }
}