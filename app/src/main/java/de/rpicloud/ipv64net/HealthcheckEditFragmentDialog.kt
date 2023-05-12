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
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.google.gson.Gson
import de.rpicloud.ipv64net.databinding.FragmentEditHealthcheckDialogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class HealthcheckEditFragmentDialog : DialogFragment(R.layout.fragment_edit_healthcheck_dialog) {

    private var _binding: FragmentEditHealthcheckDialogBinding? = null
    private val binding get() = _binding!!

    private var onDismissCalDialog: DialogInterface.OnDismissListener? = null
    lateinit var healthcheck: HealthCheck
    lateinit var rootView: View
    private var unitList: MutableList<String> = mutableListOf()
    var integrationList = IntegrationResult()

    //lateinit var detailDomainAdapter: DetailDomainAdapter
    private lateinit var spinnDialog: MaterialDialog

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissCalDialog?.onDismiss(dialog)
    }

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener) {
        this.onDismissCalDialog = listener
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
        _binding = FragmentEditHealthcheckDialogBinding.inflate(inflater, container, false)
        rootView = binding.root

        integrationList = Gson().fromJson(
            requireActivity().applicationContext.getSharedString(
                "INTEGRATIONS",
                "INTEGRATIONS"
            ), IntegrationResult::class.java
        )
        println(integrationList)

        healthcheck = Gson().fromJson(arguments?.getString("HEALTHCHECK"), HealthCheck::class.java)

        var inteList = mutableListOf<String>()
        var selInteList = mutableListOf<Int>()

        var splitListIntegrations = healthcheck.integration_id.split(",")

        integrationList.integration.forEachIndexed { index, it ->
            inteList.add(it.integration_name!!)
            if (splitListIntegrations.contains(it.integration_id.toString())) {
                selInteList.add(index)
            }
        }

        binding.topAppBarHDetail.title = "Bearbeiten"

        binding.topAppBarHDetail.setNavigationOnClickListener {
            requireActivity().applicationContext.setSharedBool(
                "HCDISMISS",
                "HCDISMISS",
                true
            )
            dismiss()
        }

        unitList.add(Unit.Minuten.Unit.name.toString())
        unitList.add(Unit.Stunden.Unit.name.toString())
        unitList.add(Unit.Tage.Unit.name.toString())

        spinnDialog = MaterialDialog(requireActivity())
        spinnDialog.title(null, "Daten werden gesendet...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        val adapterUnits =
            SelectAdapter(requireActivity(), R.layout.list_item, strList = unitList)
        binding.actvZeitraumUnit.setAdapter(adapterUnits)
        binding.actvGraceUnit.setAdapter(adapterUnits)

        var selectedZeitraum = GetUnit(healthcheck.alarm_unit)
        var selectedGrace = GetUnit(healthcheck.grace_unit)

        binding.actvZeitraumUnit.setText(selectedZeitraum.toEditable(), false)
        binding.actvGraceUnit.setText(selectedGrace.toEditable(), false)

        binding.actvGraceUnit.setOnItemClickListener { parent, view, position, id ->
            val item = parent.getItemAtPosition(position) as String
            println(item)
            selectedGrace = item
        }
        binding.actvZeitraumUnit.setOnItemClickListener { parent, view, position, id ->
            val item = parent.getItemAtPosition(position) as String
            println(item)
            selectedZeitraum = item
        }

        binding.tieHcName.text = healthcheck.name.toEditable()
        binding.tieZeitraumValue.filters = arrayOf<InputFilter>(InputFilterMinMax(1f, 60f))
        binding.tieGraceValue.filters = arrayOf<InputFilter>(InputFilterMinMax(1f, 60f))
        binding.tieZeitraumValue.text = healthcheck.alarm_count.toString().toEditable()
        binding.tieGraceValue.text = healthcheck.grace_count.toString().toEditable()

        binding.msDown.isChecked = healthcheck.alarm_down == 1
        binding.msUp.isChecked = healthcheck.alarm_up == 1

        binding.btnNotify.setOnClickListener {
            MaterialDialog(requireActivity()).show {
                title(text = "Benachrichten auf:")
                listItemsMultiChoice(
                    items = inteList, initialSelection = selInteList.toIntArray()
                ) { _, indices, _ ->
                    healthcheck.integration_id = ""
                    indices.forEachIndexed { ind, _ ->
                        val iid = integrationList.integration[ind].integration_id
                        if (!healthcheck.integration_id.contains(iid.toString())) {
                            if (healthcheck.integration_id.isEmpty()) {
                                healthcheck.integration_id = iid.toString()
                            } else {
                                healthcheck.integration_id = "${healthcheck.integration_id},$iid"
                            }
                        }
                    }
                    println(healthcheck.integration_id)
                    inteList = mutableListOf<String>()
                    selInteList = mutableListOf<Int>()

                    splitListIntegrations = healthcheck.integration_id.trim().split(",")

                    integrationList.integration.forEachIndexed { index, it ->
                        inteList.add(it.integration_name!!)
                        if (splitListIntegrations.contains(it.integration_id.toString())) {
                            selInteList.add(index)
                        }
                    }
                }
                positiveButton(text = "Auswählen")
                negativeButton(text = "Abbrechen")
            }
        }

        binding.topAppBarHDetail.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.save -> {
                    spinnDialog.show()
                    if (selectedGrace.isEmpty()) {
                        spinnDialog.hide()
                        return@setOnMenuItemClickListener true
                    }
                    if (selectedZeitraum.isEmpty()) {
                        spinnDialog.hide()
                        return@setOnMenuItemClickListener true
                    }
                    if (binding.tieZeitraumValue.text.toString().isEmpty()) {
                        spinnDialog.hide()
                        return@setOnMenuItemClickListener true
                    }
                    if (binding.tieGraceValue.text.toString().isEmpty()) {
                        spinnDialog.hide()
                        return@setOnMenuItemClickListener true
                    }
                    if (binding.tieHcName.text.toString().isEmpty()) {
                        spinnDialog.hide()
                        return@setOnMenuItemClickListener true
                    }

                    val zeitraum_value = binding.tieZeitraumValue.text.toString().toInt()
                    val grace_value = binding.tieGraceValue.text.toString().toInt()
                    val hc_name = binding.tieHcName.text.toString()

                    val selectedGraceUnit = if (Unit.Minuten.Unit.name == selectedGrace) {
                        Unit.Minuten.Unit.unit
                    } else if (Unit.Stunden.Unit.name == selectedGrace) {
                        Unit.Stunden.Unit.unit
                    } else {
                        Unit.Tage.Unit.unit
                    }

                    val selectedZeitraumUnit = if (Unit.Minuten.Unit.name == selectedZeitraum) {
                        Unit.Minuten.Unit.unit
                    } else if (Unit.Stunden.Unit.name == selectedZeitraum) {
                        Unit.Stunden.Unit.unit
                    } else {
                        Unit.Tage.Unit.unit
                    }

                    healthcheck.grace_count = grace_value
                    healthcheck.alarm_count = zeitraum_value
                    healthcheck.name = hc_name
                    healthcheck.grace_unit = selectedGraceUnit!!
                    healthcheck.alarm_unit = selectedZeitraumUnit!!

                    val down = binding.msDown.isChecked
                    val up = binding.msUp.isChecked
                    healthcheck.alarm_down = if (down) 1 else 0
                    healthcheck.alarm_up = if (up) 1 else 0

                    println("SAVE")
                    GlobalScope.launch(Dispatchers.Default) {
                        val result = ApiNetwork.PostEditHealthcheck(
                            healthcheck,
                            splitListIntegrations.toMutableList()
                        )

                        println(result)

                        if (result?.status == null) {
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
                                ErrorDialogFragment(ErrorTypes.healthcheckUpdatedSuccesfully)
                            newFragment.show(fragmentManager!!, "dialogError")
                            fragmentManager.executePendingTransactions()
                            newFragment.setOnDismissListener {
                                requireActivity().applicationContext.setSharedBool(
                                    "HCDISMISS",
                                    "HCDISMISS",
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