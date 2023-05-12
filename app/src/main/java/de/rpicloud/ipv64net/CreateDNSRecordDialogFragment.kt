package de.rpicloud.ipv64net

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import de.rpicloud.ipv64net.databinding.FragmentCreateDnsrecordDialogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CreateDNSRecordDialogFragment : DialogFragment(R.layout.fragment_create_dnsrecord_dialog) {

    private var _binding: FragmentCreateDnsrecordDialogBinding? = null

    // This property is only valid between onCreateView and
// onDestroyView.
    private val binding get() = _binding!!

    private var onDismissCalDialog: DialogInterface.OnDismissListener? = null

    lateinit var rootView: View
    private lateinit var spinnDialog: MaterialDialog

    private var typList: MutableList<String> = mutableListOf()
    private var selectedTyp = ""

    private var domainKey = ""

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissCalDialog?.onDismiss(dialog)
    }

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener) {
        this.onDismissCalDialog = listener
    }

    /** The system calls this to get the DialogFragment's layout, regardless
    of whether it's being displayed as a dialog or an embedded fragment. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateDnsrecordDialogBinding.inflate(inflater, container, false)
        rootView = binding.root

        binding.topAppBarDetail.title = "Neuer DNS-Record"

        binding.topAppBarDetail.setNavigationOnClickListener {
            requireActivity().applicationContext.setSharedBool(
                "DNSRECORDDISMISS",
                "DNSRECORDDISMISS",
                true
            )
            dismiss()
        }

        domainKey = arguments?.getString("DOMAINKEY")!!

        spinnDialog = MaterialDialog(requireActivity())
        spinnDialog.title(null, "Daten werden gesendet...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        typList.add("A")
        typList.add("AAAA")
        typList.add("TXT")
        typList.add("MX")
        typList.add("NS")
        typList.add("CNAME")

        val adapterDomains =
            SelectAdapter(requireActivity(), R.layout.list_item, strList = typList)
        binding.actvTyp.setAdapter(adapterDomains)

        binding.actvTyp.setOnItemClickListener { parent, view, position, id ->
            val item = parent.getItemAtPosition(position) as String
            println(item)
            selectedTyp = item
        }

        binding.topAppBarDetail.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.save -> {
                    spinnDialog.show()
                    println("SAVE")
                    val praefix = binding.tiePraefix.text.toString().trim()
                    val wert = binding.tieWert.text.toString().trim()
                    val typ = selectedTyp

                    if (wert.isEmpty() || typ.isEmpty()) {
                        spinnDialog.hide()
                        return@setOnMenuItemClickListener true
                    }

                    //saveUser()
                    GlobalScope.launch(Dispatchers.Default) {
                        val result = ApiNetwork.PostDNSRecord(domainKey, praefix, typ, wert)

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
                            val fragmentManager = activity?.supportFragmentManager
                            val newFragment =
                                ErrorDialogFragment(ErrorTypes.dnsRecordSuccesfullyCreated)
                            newFragment.show(fragmentManager!!, "dialogError")
                            fragmentManager.executePendingTransactions()
                            newFragment.setOnDismissListener {
                                requireActivity().applicationContext.setSharedBool(
                                    "DNSRECORDDISMISS",
                                    "DNSRECORDDISMISS",
                                    false
                                )
                                dismiss()
                            }
                        }
                    }
                    true
                }

                else -> false
            }
        }



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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}