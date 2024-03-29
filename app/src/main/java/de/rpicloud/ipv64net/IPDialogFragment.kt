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
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import de.rpicloud.ipv64net.databinding.FragmentIpDialogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class IPDialogFragment : DialogFragment(R.layout.fragment_ip_dialog) {

    private var _binding: FragmentIpDialogBinding? = null
    private val binding get() = _binding!!

    private var onDismissCalDialog: DialogInterface.OnDismissListener? = null
    private var myIP4 = MyIP()
    private var myIP6 = MyIP()
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

        _binding = FragmentIpDialogBinding.inflate(inflater, container, false)
        rootView = binding.root

        spinnDialog = MaterialDialog(rootView.context)
        spinnDialog.title(null, "Daten werden geladen...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        getData()

        binding.cvIpv4.setOnClickListener {
            val clipboard: ClipboardManager =
                requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("IPv4 Adresse kopiert!", myIP4.ip)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                requireActivity().applicationContext,
                "IPv4 Adresse kopiert!",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.cvIpv6.setOnClickListener {
            val clipboard: ClipboardManager =
                requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("IPv6 Adresse kopiert!", myIP6.ip)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                requireActivity().applicationContext,
                "IPv6 Adresse kopiert!",
                Toast.LENGTH_LONG
            ).show()
        }

        return rootView
    }

    private fun getData() {
        spinnDialog.show()
        GlobalScope.launch(Dispatchers.Default) {
            myIP4 = ApiNetwork.GetMyIP4()
            myIP6 = ApiNetwork.GetMyIP6()

            launch(Dispatchers.Main) {
                spinnDialog.hide()
                binding.tvIp4.text = if (myIP4.ip?.isEmpty()!!) "0.0.0.0" else myIP4.ip
                binding.tvIp6.text = if (myIP6.ip?.isEmpty()!!) "0.0.0.0" else myIP6.ip
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