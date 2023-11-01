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
import de.rpicloud.ipv64net.databinding.FragmentCreateDomainDialogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CreateDomainDialogFragment : DialogFragment(R.layout.fragment_create_domain_dialog) {

    private var _binding: FragmentCreateDomainDialogBinding? = null

    // This property is only valid between onCreateView and
// onDestroyView.
    private val binding get() = _binding!!

    private var onDismissCalDialog: DialogInterface.OnDismissListener? = null

    lateinit var rootView: View
    private lateinit var spinnDialog: MaterialDialog

    private var domainList: MutableList<String> = mutableListOf()
    private var selectedDomain = ""

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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateDomainDialogBinding.inflate(inflater, container, false)
        rootView = binding.root

        binding.topAppBarDetail.title = "Neue Domain"

        binding.topAppBarDetail.setNavigationOnClickListener {
            dismiss()
        }

        spinnDialog = MaterialDialog(requireActivity())
        spinnDialog.title(null, "Daten werden gesendet...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        domainList.add("ipv64.net")
        domainList.add("ipv64.de")
        domainList.add("any64.de")
        domainList.add("eth64.de")
        domainList.add("home64.de")
        domainList.add("iot64.de")
        domainList.add("lan64.de")
        domainList.add("nas64.de")
        domainList.add("srv64.de")
        domainList.add("tcp64.de")
        domainList.add("udp64.de")
        domainList.add("vpn64.de")
        domainList.add("wan64.de")
        domainList.add("eth64.de")

        val adapterDomains =
            SelectAdapter(requireActivity(), R.layout.list_item, strList = domainList)
        binding.actvDomain.setAdapter(adapterDomains)

        binding.actvDomain.setOnItemClickListener { parent, view, position, id ->
            val item = parent.getItemAtPosition(position) as String
            println(item)
            selectedDomain = item
        }

        binding.topAppBarDetail.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.save -> {
                    spinnDialog.show()
                    if (selectedDomain.isEmpty()) {
                        spinnDialog.hide()
                        return@setOnMenuItemClickListener true
                    }

                    println("SAVE")
                    val domainReg =
                        binding.tieSubdomain.text.toString().trim() + "." + selectedDomain
                    //saveUser()
                    GlobalScope.launch(Dispatchers.Default) {
                        val result = ApiNetwork.PostDomain(domainReg)

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
                                val newFragment = ErrorDialogFragment(ErrorTypes.tooManyRequests)
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
                                ErrorDialogFragment(ErrorTypes.domainCreatedSuccesfully)
                            newFragment.show(fragmentManager!!, "dialogError")
                            fragmentManager.executePendingTransactions()
                            newFragment.setOnDismissListener {
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