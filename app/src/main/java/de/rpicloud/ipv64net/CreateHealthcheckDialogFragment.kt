package de.rpicloud.ipv64net

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import de.rpicloud.ipv64net.databinding.FragmentCreateHealthcheckDialogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CreateHealthcheckDialogFragment :
    DialogFragment(R.layout.fragment_create_healthcheck_dialog) {

    private var _binding: FragmentCreateHealthcheckDialogBinding? = null
    private val binding get() = _binding!!

    private var onDismissCalDialog: DialogInterface.OnDismissListener? = null

    lateinit var rootView: View
    private lateinit var spinnDialog: MaterialDialog
    private var unitList: MutableList<String> = mutableListOf()

    private var selectedUnit = ""

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
        _binding = FragmentCreateHealthcheckDialogBinding.inflate(inflater, container, false)
        rootView = binding.root

        binding.topAppBarDetail.title = "Neuer Healthcheck"

        binding.topAppBarDetail.setNavigationOnClickListener {
            dismiss()
        }

        spinnDialog = MaterialDialog(requireActivity())
        spinnDialog.title(null, "Daten werden gesendet...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        unitList.add(Unit.Minuten.Unit.name.toString())
        unitList.add(Unit.Stunden.Unit.name.toString())
        unitList.add(Unit.Tage.Unit.name.toString())

        val adapterDomains =
            SelectAdapter(requireActivity(), R.layout.list_item, strList = unitList)
        binding.actvUnit.setAdapter(adapterDomains)

        binding.tieUnitvalue.filters = arrayOf<InputFilter>(InputFilterMinMax(1f, 60f))

        binding.actvUnit.setOnItemClickListener { parent, view, position, id ->
            val item = parent.getItemAtPosition(position) as String
            println(item)
            selectedUnit = item
        }

        binding.topAppBarDetail.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.save -> {
                    spinnDialog.show()
                    if (selectedUnit.isEmpty()) {
                        spinnDialog.hide()
                        return@setOnMenuItemClickListener true
                    }
                    if (binding.tieUnitvalue.text.toString().isEmpty()) {
                        spinnDialog.hide()
                        return@setOnMenuItemClickListener true
                    }
                    if (binding.tieHcName.text.toString().isEmpty()) {
                        spinnDialog.hide()
                        return@setOnMenuItemClickListener true
                    }

                    val unitValue = binding.tieUnitvalue.text.toString().toInt()
                    val hc_name = binding.tieHcName.text.toString()
                    val unit = if (Unit.Minuten.Unit.name == selectedUnit) {
                        Unit.Minuten.Unit.unit
                    } else if (Unit.Stunden.Unit.name == selectedUnit) {
                        Unit.Stunden.Unit.unit
                    } else {
                        Unit.Tage.Unit.unit
                    }

                    println("SAVE")
//                    val domainReg = rootView.tie_subdomain.text.toString().trim() + "." + selectedDomain
//                    //saveUser()
                    GlobalScope.launch(Dispatchers.Default) {
                        val result = ApiNetwork.PostHealth(hc_name, unitValue, unit!!.toInt())

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
                                ErrorDialogFragment(ErrorTypes.healthcheckCreatedSuccesfully)
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