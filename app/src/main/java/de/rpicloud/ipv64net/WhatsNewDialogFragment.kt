package de.rpicloud.ipv64net

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import de.rpicloud.ipv64net.databinding.FragmentWhatsnewDialogBinding

class WhatsNewDialogFragment : DialogFragment(R.layout.fragment_whatsnew_dialog) {

    private var _binding: FragmentWhatsnewDialogBinding? = null
    private val binding get() = _binding!!

    private var onDismissDialog: DialogInterface.OnDismissListener? = null

    lateinit var rootView: View

    lateinit var dialogFragment: Dialog

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissDialog?.onDismiss(dialog)
    }

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener) {
        this.onDismissDialog = listener
    }

    /** The system calls this to get the DialogFragment's layout, regardless
    of whether it's being displayed as a dialog or an embedded fragment. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentWhatsnewDialogBinding.inflate(inflater, container, false)
        rootView = binding.root

        binding.topAppBarWhatsNew.setNavigationOnClickListener {
            dismiss()
        }

        initView()

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

    private fun initView() {
        val activity = requireActivity()
        dialogFragment = dialog!!

        binding.whatsnewTitle.text = "Was ist neu in\nv${BuildConfig.VERSION_NAME}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}