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
import kotlinx.android.synthetic.main.fragment_error_dialog.view.*
import kotlin.math.roundToInt


class ErrorDialogFragment(var errorTyp: ErrorTyp) : DialogFragment() {

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
        // Inflate the layout to use as dialog or embedded fragment
        rootView = inflater.inflate(R.layout.fragment_error_dialog, container, false)

        rootView.topAppBarDetail.title = errorTyp.navigationTitle
        rootView.iv_error_icon.setImageResource(errorTyp.icon!!)
        rootView.iv_error_icon.setColorFilter(ContextCompat.getColor(requireActivity(),
            errorTyp.iconColor!!
        ), android.graphics.PorterDuff.Mode.SRC_IN);
        rootView.tv_title_error.text = errorTyp.errorTitle
        rootView.tv_detail_error.text = errorTyp.errorDescription

        rootView.btn_error_cancel.visibility = if (errorTyp == ErrorTypes.deleteDNSRecord || errorTyp == ErrorTypes.deleteDomain || errorTyp == ErrorTypes.deletehealth) {
            View.VISIBLE
        } else {
            View.GONE
        }

        rootView.btn_error_close.text = if (errorTyp == ErrorTypes.deleteDNSRecord || errorTyp == ErrorTypes.deleteDomain || errorTyp == ErrorTypes.deletehealth) {
            "Löschen"
        } else {
            "Schließen"
        }

        if (errorTyp == ErrorTypes.tooManyRequests || errorTyp == ErrorTypes.updateCoolDown) {
            startTimer()
        }

        rootView.btn_error_close.setBackgroundColor(ContextCompat.getColor(requireActivity(),
            errorTyp.iconColor!!
        ))

        rootView.btn_error_close.setOnClickListener {
            requireActivity().setSharedBool("ISCANCELD", "ISCANCELD", false)
            dismiss()
        }

        rootView.btn_error_cancel.setOnClickListener {
            requireActivity().setSharedBool("ISCANCELD", "ISCANCELD", true)
            dismiss()
        }

        return rootView
    }

    //start timer function
    fun startTimer() {
        val cTimer = object: CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seco = millisUntilFinished / 1000
                secounds = seco.toInt()
                rootView.btn_error_close.text = "Schließen ($secounds)"
                rootView.btn_error_close.isEnabled = false
            }

            override fun onFinish() {
                rootView.btn_error_close.text = "Schließen"
                rootView.btn_error_close.isEnabled = true
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
}