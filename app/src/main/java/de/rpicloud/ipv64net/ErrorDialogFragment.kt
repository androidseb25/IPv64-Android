package de.rpicloud.ipv64net

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import de.rpicloud.ipv64net.databinding.FragmentErrorDialogBinding


class ErrorDialogFragment(var errorTyp: ErrorTyp) : DialogFragment(R.layout.fragment_error_dialog) {

    private var _binding: FragmentErrorDialogBinding? = null
    private val binding get() = _binding!!

    private var onDismissCalDialog: DialogInterface.OnDismissListener? = null

    lateinit var rootView: View

    //Declare timer
    var cTimer: CountDownTimer? = null

    var secounds: Int = 0

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
        _binding = FragmentErrorDialogBinding.inflate(inflater, container, false)
        rootView = binding.root

        binding.topAppBarDetail.title = errorTyp.navigationTitle
        binding.ivErrorIcon.setImageResource(errorTyp.icon!!)
        binding.ivErrorIcon.setColorFilter(
            ContextCompat.getColor(
                requireActivity(),
                errorTyp.iconColor!!
            ), android.graphics.PorterDuff.Mode.SRC_IN
        )
        binding.tvTitleError.text = errorTyp.errorTitle
        binding.tvDetailError.text = errorTyp.errorDescription

        binding.btnErrorCancel.visibility =
            if (errorTyp == ErrorTypes.deleteDNSRecord || errorTyp == ErrorTypes.deleteDomain || errorTyp == ErrorTypes.deletehealth) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.btnErrorClose.text =
            if (errorTyp == ErrorTypes.deleteDNSRecord || errorTyp == ErrorTypes.deleteDomain || errorTyp == ErrorTypes.deletehealth) {
                "Löschen"
            } else {
                "Schließen"
            }

        if (errorTyp == ErrorTypes.tooManyRequests || errorTyp == ErrorTypes.updateCoolDown) {
            startTimer()
        }

        binding.btnErrorClose.setBackgroundColor(
            ContextCompat.getColor(
                requireActivity(),
                errorTyp.iconColor!!
            )
        )

        binding.btnErrorClose.setOnClickListener {
            requireActivity().setSharedBool("ISCANCELD", "ISCANCELD", false)
            dismiss()
        }

        binding.btnErrorCancel.setOnClickListener {
            requireActivity().setSharedBool("ISCANCELD", "ISCANCELD", true)
            dismiss()
        }

        return rootView
    }

    //start timer function
    fun startTimer() {
        val cTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seco = millisUntilFinished / 1000
                secounds = seco.toInt()
                binding.btnErrorClose.text = "Schließen ($secounds)"
                binding.btnErrorClose.isEnabled = false
            }

            override fun onFinish() {
                binding.btnErrorClose.text = "Schließen"
                binding.btnErrorClose.isEnabled = true
                cancelTimer()
            }
        }
        cTimer.start()
    }


    //cancel timer
    fun cancelTimer() {
        if (cTimer != null) cTimer!!.cancel()
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