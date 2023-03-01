package de.rpicloud.ipv64net

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.google.gson.Gson
import kotlinx.android.synthetic.main.fragment_detail_healthcheck_dialog.view.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HealthcheckDetailFragmentDialog : DialogFragment() {

    private var onDismissCalDialog: DialogInterface.OnDismissListener? = null
    lateinit var healthcheck: HealthCheck
    lateinit var rootView: View

    //lateinit var detailDomainAdapter: DetailDomainAdapter
    private lateinit var spinnDialog: MaterialDialog

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissCalDialog?.onDismiss(dialog)
    }

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener) {
        this.onDismissCalDialog = listener
    }

    private fun GetHealthcheckUrl(): String {
        return "https://ipv64.net/health.php?token=" + healthcheck.healthtoken
    }

    private fun GetUnit(unit: Int): String {
        return when (unit) {
            Unit.Minuten.Unit.unit -> {
                Unit.Minuten.name
            }
            Unit.Stunden.Unit.unit -> {
                Unit.Stunden.name
            }
            Unit.Tage.Unit.unit -> {
                Unit.Tage.name
            }
            else -> {
                ""
            }
        }
    }

    /** The system calls this to get the DialogFragment's layout, regardless
    of whether it's being displayed as a dialog or an embedded fragment. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout to use as dialog or embedded fragment
        rootView = inflater.inflate(R.layout.fragment_detail_healthcheck_dialog, container, false)


        healthcheck = Gson().fromJson(arguments?.getString("HEALTHCHECK"), HealthCheck::class.java)
        println(healthcheck)
        rootView.topAppBarHDetail.title = healthcheck.name

        rootView.topAppBarHDetail.setNavigationOnClickListener {
            dismiss()
        }

        spinnDialog = MaterialDialog(requireActivity())
        spinnDialog.title(null, "Daten werden gesendet...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        rootView.recycler_pill_big_view?.layoutManager = LinearLayoutManager(
            activity?.applicationContext,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        val healthAdapter = HealthcheckPillAdapter(
            healthcheck.events.reversed().toMutableList(),
            requireActivity(),
            R.layout.adapter_healthcheck_pill
        )
        rootView.recycler_pill_big_view?.isNestedScrollingEnabled = false;
        rootView.recycler_pill_big_view?.adapter = healthAdapter

        rootView.tv_time.text = "${healthcheck.alarm_count} ${GetUnit(healthcheck.alarm_unit)}"
        rootView.tv_karenzzeit.text =
            "${healthcheck.grace_count} ${GetUnit(healthcheck.grace_unit)}"
        rootView.tv_notification.text =
            (healthcheck.integration_id.count { it == ',' } + 1).toString()
        rootView.tv_noti_down.text = if (healthcheck.alarm_down == 0) {
            "aus"
        } else {
            "an"
        }
        rootView.tv_noti_up.text = if (healthcheck.alarm_up == 0) {
            "aus"
        } else {
            "an"
        }

        val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val localDateTime: LocalDateTime = LocalDateTime.parse(healthcheck.add_time, df)
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        val output: String = formatter.format(localDateTime)

        rootView.tv_created.text = output

        rootView.btn_copy_health_url.setOnClickListener {
            val clipboard: ClipboardManager =
                requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData =
                ClipData.newPlainText("Healthcheck URL kopiert!", GetHealthcheckUrl())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                requireActivity().applicationContext,
                "Healthcheck URL kopiert!",
                Toast.LENGTH_LONG
            ).show()
        }

        rootView.recycler_events?.layoutManager =
            GridLayoutManager(requireActivity().applicationContext, 1)
        val adapterEvents = EventAdapter(healthcheck.events, requireActivity())
        rootView.recycler_events.adapter = adapterEvents

        /*rootView.topAppBarDetail.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.new_dnsrecord -> {
                    println("new dns")
                    //saveUser()
                    val fragmentManager = requireActivity().supportFragmentManager
                    val newFragment = CreateDNSRecordDialogFragment()
                    newFragment.arguments = Bundle().apply {
                        putString("DOMAINKEY", domainTitle)
                    }
                    newFragment.show(fragmentManager, "dialogDNS")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {
                        val isDismissFromX = requireActivity().applicationContext.getSharedBool(
                            "DNSRECORDDISMISS",
                            "DNSRECORDDISMISS"
                        )
                        if (!isDismissFromX)
                            dismiss()

                        requireActivity().applicationContext.setSharedBool(
                            "DNSRECORDDISMISS",
                            "DNSRECORDDISMISS",
                            false
                        )
                    }
                    true
                }
                R.id.delete_domain -> {
                    println("deleteDomain")
                    //saveUser()

                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.deleteDomain)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {
                        var isCanceld =
                            requireActivity().getSharedBool("ISCANCELD", "ISCANCELD") as Boolean
                        if (!isCanceld) {
                            spinnDialog.show()
                            GlobalScope.launch(Dispatchers.Default) {
                                val result = ApiNetwork.DeleteDomain(domainTitle)

                                println(result)

                                if (result.status == null) {
                                    launch(Dispatchers.Main) {
                                        spinnDialog.hide()
                                        val fragmentManager = activity?.supportFragmentManager
                                        val newFragment =
                                            ErrorDialogFragment(ErrorTypes.websiteRequestError)
                                        newFragment.show(fragmentManager!!, "dialogError")
                                        fragmentManager.executePendingTransactions()
                                        newFragment.setOnDismissListener { }
                                    }
                                    return@launch
                                } else if (result.status!!.contains("500")) {
                                    launch(Dispatchers.Main) {
                                        spinnDialog.hide()
                                        val fragmentManager = activity?.supportFragmentManager
                                        val newFragment =
                                            ErrorDialogFragment(ErrorTypes.websiteRequestError)
                                        newFragment.show(fragmentManager!!, "dialogError")
                                        fragmentManager.executePendingTransactions()
                                        newFragment.setOnDismissListener { }
                                    }
                                    return@launch
                                } else if (result.status!!.contains("401")) {
                                    launch(Dispatchers.Main) {
                                        spinnDialog.hide()
                                        val fragmentManager = activity?.supportFragmentManager
                                        val newFragment =
                                            ErrorDialogFragment(ErrorTypes.unauthorized)
                                        newFragment.show(fragmentManager!!, "dialogError")
                                        fragmentManager.executePendingTransactions()
                                        newFragment.setOnDismissListener { }
                                    }
                                    return@launch
                                } else if (result.status!!.contains("429")) {
                                    launch(Dispatchers.Main) {
                                        spinnDialog.hide()
                                        println("SHOW ERROR")
                                        val fragmentManager = activity?.supportFragmentManager
                                        val newFragment =
                                            ErrorDialogFragment(ErrorTypes.tooManyRequests)
                                        newFragment.show(fragmentManager!!, "dialogError")
                                        fragmentManager.executePendingTransactions()
                                        newFragment.setOnDismissListener { }
                                    }
                                    return@launch
                                }

                                launch(Dispatchers.Main) {
                                    spinnDialog.hide()
                                    dismiss()
                                }
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }*/

        return rootView
    }

    /** The system calls this only when creating the layout in a dialog. */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        setStyle(STYLE_NO_TITLE, R.style.Theme_IPv64net)

        // The only reason you might override this method when using onCreateView() is
        // to modify any dialog characteristics. For example, the dialog includes a
        // title by default, but your custom layout might not need it. So here you can
        // remove the dialog title, but you must call the superclass to get the Dialog.
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setWindowAnimations(R.style.SlideAnimation)
        return dialog
    }
}