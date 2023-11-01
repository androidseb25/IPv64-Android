package de.rpicloud.ipv64net

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import de.rpicloud.ipv64net.databinding.FragmentAboutDialogBinding

class AboutDialogFragment : DialogFragment(R.layout.fragment_about_dialog) {

    private var _binding: FragmentAboutDialogBinding? = null

    // This property is only valid between onCreateView and
// onDestroyView.
    private val binding get() = _binding!!

    private var onDismissCalDialog: DialogInterface.OnDismissListener? = null
    lateinit var rootView: View

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
        _binding = FragmentAboutDialogBinding.inflate(inflater, container, false)
        rootView = binding.root

        // Inflate the layout to use as dialog or embedded fragment
        //rootView = inflater.inflate(R.layout.fragment_about_dialog, container, false)

        binding.tvWhatIs.text =
            Html.fromHtml("<b>IPv64</b> ist natürlich kein neues Internet-Protokoll (64), sondern einfach eine deduplizierte Kurzform von IPv6 und IPv4. Auf der Seite von IPv64 findest du einen <b>Dynamischen DNS</b> Dienst (DynDNS) und viele weitere nützliche Tools für dein tägliches Interneterlebnis. <br><br>Mit dem <b>dynamischen DNS Dienst</b> von IPv64 kannst du dir kostenfreie Subdomains registrieren und nutzen. Das Update der Domain übernimmt voll automatisch dein eigener Router oder alternative Hardware / Software. <br><br>Über den Youtube Kanal RaspberryPi Cloud wirst du ganz sicher noch viel mehr über die Welt der IT kennenlernen dürfen.")

        binding.tvContact.text =
            Html.fromHtml("Ein Produkt der Prox IT UG (haftungsbeschränkt). <br><br><b>Angaben gemäß § 5 TMG</b><br>Prox IT UG (haftungsbeschränkt)<br>Am Eisenstein 10<br>45470 Mülheim an der Ruhr<br><br><b>Vertreten durch</b><br>Dennis Schröder (Geschäftsführer)<br><br>Registergericht: Amtsgericht Duisburg<br>Registernummer: HRB 35106")

        binding.tvAbout.text =
            Html.fromHtml("Diese App ist mithilfe der Community von RaspberryPi Cloud entstanden. <br><br>Alle Rechte vorbehalten, bei Dennis Schröder.")

        binding.tvAboutVer.text = "Version: ${BuildConfig.VERSION_NAME}"
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