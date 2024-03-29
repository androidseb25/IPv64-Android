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
import de.rpicloud.ipv64net.databinding.FragmentDetailHealthcheckDialogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class HealthcheckDetailFragmentDialog :
    DialogFragment(R.layout.fragment_detail_healthcheck_dialog) {

    private var _binding: FragmentDetailHealthcheckDialogBinding? = null
    private val binding get() = _binding!!

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
                Unit.Minuten.Unit.name!!
            }

            Unit.Stunden.Unit.unit -> {
                Unit.Stunden.Unit.name!!
            }

            Unit.Tage.Unit.unit -> {
                Unit.Tage.Unit.name!!
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
        _binding = FragmentDetailHealthcheckDialogBinding.inflate(inflater, container, false)
        rootView = binding.root

        healthcheck = Gson().fromJson(arguments?.getString("HEALTHCHECK"), HealthCheck::class.java)
        println(healthcheck)
        binding.topAppBarHDetail.title = healthcheck.name

        binding.topAppBarHDetail.setNavigationOnClickListener {
            dismiss()
        }

        spinnDialog = MaterialDialog(requireActivity())
        spinnDialog.title(null, "Daten werden gesendet...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        binding.recyclerPillBigView.layoutManager = LinearLayoutManager(
            activity?.applicationContext,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        val width =
            screenRectDp.width() - 32 //rootView.recycler_pill_big_view.layoutParams.width + 32
        val pillCount = (width / 15).roundToInt()

        println("width: $width")
        println("pillCount: $pillCount")

        val healthAdapter = HealthcheckPillAdapter(
            healthcheck.events.take(pillCount).reversed().toMutableList(),
            requireActivity()
        )
        binding.recyclerPillBigView.isNestedScrollingEnabled = false
        binding.recyclerPillBigView.adapter = healthAdapter

        binding.tvTime.text = "${healthcheck.alarm_count} ${GetUnit(healthcheck.alarm_unit)}"
        binding.tvKarenzzeit.text =
            "${healthcheck.grace_count} ${GetUnit(healthcheck.grace_unit)}"
        binding.tvNotification.text =
            (healthcheck.integration_id.count { it == ',' } + 1).toString()
        binding.tvNotiDown.text = if (healthcheck.alarm_down == 0) {
            "aus"
        } else {
            "an"
        }
        binding.tvNotiUp.text = if (healthcheck.alarm_up == 0) {
            "aus"
        } else {
            "an"
        }

        val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val localDateTime: LocalDateTime = LocalDateTime.parse(healthcheck.add_time, df)
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        val output: String = formatter.format(localDateTime)

        binding.tvCreated.text = output

        binding.btnCopyHealthUrl.setOnClickListener {
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

        binding.btnStartHc.visibility =
            if (healthcheck.healthstatus == StatusType.Pause.type.statusId) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.btnPauseHc.visibility =
            if (healthcheck.healthstatus != StatusType.Pause.type.statusId) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.btnStartHc.setOnClickListener {
            startHC()
        }

        binding.btnPauseHc.setOnClickListener {
            pauseHC()
        }

        binding.btnDeleteHc.setOnClickListener {
            val fragmentManager = activity?.supportFragmentManager
            val newFragment = ErrorDialogFragment(ErrorTypes.deletehealth)
            newFragment.show(fragmentManager!!, "dialogError")
            fragmentManager.executePendingTransactions()
            newFragment.setOnDismissListener {
                var isCanceld = requireActivity().getSharedBool("ISCANCELD", "ISCANCELD")
                if (!isCanceld) {
                    deleteHC()
                    requireActivity().setSharedBool("ISCANCELD", "ISCANCELD", false)
                }
            }
        }

        binding.recyclerEvents.layoutManager =
            GridLayoutManager(requireActivity().applicationContext, 1)

        val adapterEvents =
            EventAdapter(healthcheck.events.take(pillCount).toMutableList(), requireActivity())
        binding.recyclerEvents.adapter = adapterEvents

        binding.topAppBarHDetail.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.edit_hc -> {
                    println("edithc")
                    val fragmentManager = requireActivity().supportFragmentManager
                    val newFragment = HealthcheckEditFragmentDialog()
                    newFragment.arguments = Bundle().apply {
                        putString("HEALTHCHECK", Gson().toJson(healthcheck))
                    }
                    newFragment.show(fragmentManager, "editHC")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {
                        val isDismissFromX = requireActivity().applicationContext.getSharedBool(
                            "HCDISMISS",
                            "HCDISMISS"
                        )

                        requireActivity().applicationContext.setSharedBool(
                            "HCDISMISS",
                            "HCDISMISS",
                            false
                        )
                        if (!isDismissFromX)
                            dismiss()
                    }
                    true
                }

                else -> false
            }
        }

        return rootView
    }

    private fun startHC() {
        GlobalScope.launch(Dispatchers.Default) {
            val result = ApiNetwork.PostStartHealth(healthcheck.healthtoken)

            println(result)

            if (result?.status == null) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            } else if (result.status!!.contains("403") && result.add_domain!!.contains("domain limit reached")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.domainLimit)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            } else if (result.status!!.contains("403") && result.add_domain!!.contains("domainname rules")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.domainRules)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            } else if (result.status!!.contains("500")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            } else if (result.status!!.contains("401")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.unauthorized)
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
                Toast.makeText(
                    requireActivity().applicationContext,
                    "Healthcheck gelöscht!",
                    Toast.LENGTH_LONG
                ).show()
                dismiss()
            }
        }
    }

    private fun pauseHC() {
        GlobalScope.launch(Dispatchers.Default) {
            val result = ApiNetwork.PostPauseHealth(healthcheck.healthtoken)

            println(result)

            if (result?.status == null) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            } else if (result.status!!.contains("403") && result.add_domain!!.contains("domain limit reached")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.domainLimit)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            } else if (result.status!!.contains("403") && result.add_domain!!.contains("domainname rules")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.domainRules)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            } else if (result.status!!.contains("500")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            } else if (result.status!!.contains("401")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.unauthorized)
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
                Toast.makeText(
                    requireActivity().applicationContext,
                    "Healthcheck pausiert!",
                    Toast.LENGTH_LONG
                ).show()
                dismiss()
            }
        }
    }

    private fun deleteHC() {
        GlobalScope.launch(Dispatchers.Default) {
            val result = ApiNetwork.DeleteHealthcheck(healthcheck.healthtoken)

            println(result)

            if (result.status == null) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            } else if (result.status!!.contains("403") && result.add_domain!!.contains("domain limit reached")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.domainLimit)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            } else if (result.status!!.contains("403") && result.add_domain!!.contains("domainname rules")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.domainRules)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            } else if (result.status!!.contains("500")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            } else if (result.status!!.contains("401")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.unauthorized)
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
                Toast.makeText(
                    requireActivity().applicationContext,
                    "Healthcheck gelöscht!",
                    Toast.LENGTH_LONG
                ).show()
                dismiss()
            }
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}