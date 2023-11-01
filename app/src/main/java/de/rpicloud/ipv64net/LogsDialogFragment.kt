package de.rpicloud.ipv64net

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import de.rpicloud.ipv64net.databinding.FragmentLogsDialogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LogsDialogFragment : DialogFragment(R.layout.fragment_logs_dialog) {

    private var _binding: FragmentLogsDialogBinding? = null
    private val binding get() = _binding!!

    private var onDismissCalDialog: DialogInterface.OnDismissListener? = null
    private var logs = Logs()
    lateinit var rootView: View
    private lateinit var spinnDialog: MaterialDialog

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

        _binding = FragmentLogsDialogBinding.inflate(inflater, container, false)
        rootView = binding.root

        binding.recyclerLogs.layoutManager = GridLayoutManager(requireActivity().applicationContext, 1)

        spinnDialog = MaterialDialog(rootView.context)
        spinnDialog.title(null, "Daten werden geladen...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        getData()

        return rootView
    }

    private fun getData() {
        spinnDialog.show()
        GlobalScope.launch(Dispatchers.Default) {
            logs = ApiNetwork.GetLogs()

            if (logs.info == null) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
            } else if (logs.info!!.contains("500")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
            } else if (logs.info!!.contains("429")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    println("SHOW ERROR")
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.tooManyRequests)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {
                        getData()
                    }
                }
                return@launch
            } else if (logs.info!!.contains("401")) {
                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    println("SHOW ERROR")
                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.unauthorized)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener { }
                }
                return@launch
            }

            launch(Dispatchers.Main) {
                spinnDialog.hide()
                if (!logs.logs.isNullOrEmpty()) {
                    val adapterLogs = LogsAdapter(logs.logs!!.subList(0, 10), requireActivity())
                    binding.recyclerLogs.adapter = adapterLogs
                }
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