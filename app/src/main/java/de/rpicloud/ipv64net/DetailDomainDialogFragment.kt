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
import androidx.recyclerview.widget.GridLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.google.gson.Gson
import de.rpicloud.ipv64net.databinding.FragmentDetailDomainDialogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class DetailDomainDialogFragment : DialogFragment(R.layout.fragment_detail_domain_dialog) {

    private var _binding: FragmentDetailDomainDialogBinding? = null
    private val binding get() = _binding!!

    private var onDismissCalDialog: DialogInterface.OnDismissListener? = null

    lateinit var rootView: View
    lateinit var domain: Domain
    private lateinit var domainTitle: String
    lateinit var myIp: MyIP
    lateinit var accountInfo: AccountInfo
    lateinit var detailDomainAdapter: DetailDomainAdapter
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
        _binding = FragmentDetailDomainDialogBinding.inflate(inflater, container, false)
        rootView = binding.root

        domain = Gson().fromJson(arguments?.getString("DOMAIN"), Domain::class.java)
        myIp = Gson().fromJson(arguments?.getString("MYIP"), MyIP::class.java)
        accountInfo = Gson().fromJson(arguments?.getString("ACCOUNTINFO"), AccountInfo::class.java)
        domainTitle = arguments?.getString("DOMAINKEY").toString()

        binding.topAppBarDetail.title = domainTitle

        binding.topAppBarDetail.setNavigationOnClickListener {
            dismiss()
        }

        spinnDialog = MaterialDialog(requireActivity())
        spinnDialog.title(null, "Daten werden gesendet...")
        spinnDialog.customView(R.layout.loading_spinner)
        spinnDialog.cancelable(false)
        spinnDialog.cancelOnTouchOutside(false)

        binding.tvUpdates.text = domain.updates.toString()

        val wildcard = if (domain.wildcard == 1) {
            "ja"
        } else {
            "nein"
        }

        binding.llcHinweis.visibility = if (CheckIfIPCorrect()) View.GONE else View.VISIBLE

        binding.tvWildcard.text = wildcard

        binding.recyclerDetailDomain.layoutManager =
            GridLayoutManager(requireActivity().applicationContext, 1)
        detailDomainAdapter = DetailDomainAdapter(domain.records!!, requireActivity())
        binding.recyclerDetailDomain.adapter = detailDomainAdapter

        binding.btnRefreshRecord.setOnClickListener {
            spinnDialog.show()
            GlobalScope.launch(Dispatchers.Default) {
                val result = ApiNetwork.UpdateDomainIp(accountInfo.update_hash!!, domainTitle)

                println(result)

                if (result.status == null) {
                    launch(Dispatchers.Main) {
                        spinnDialog.hide()
                        val fragmentManager = activity?.supportFragmentManager
                        val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
                        newFragment.show(fragmentManager!!, "dialogError")
                        fragmentManager.executePendingTransactions()
                        newFragment.setOnDismissListener { }
                    }
                    return@launch
                } else if (result.status!!.contains("500")) {
                    launch(Dispatchers.Main) {
                        spinnDialog.hide()
                        val fragmentManager = activity?.supportFragmentManager
                        val newFragment = ErrorDialogFragment(ErrorTypes.websiteRequestError)
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
                } else if (result.info!!.contains("403") && result.info!!.contains("Update Cooldown")) {
                    launch(Dispatchers.Main) {
                        spinnDialog.hide()
                        println("SHOW ERROR")
                        val fragmentManager = activity?.supportFragmentManager
                        val newFragment =
                            ErrorDialogFragment(ErrorTypes.updateCoolDown)
                        newFragment.show(fragmentManager!!, "dialogError")
                        fragmentManager.executePendingTransactions()
                        newFragment.setOnDismissListener { }
                    }
                    return@launch
                }

                launch(Dispatchers.Main) {
                    spinnDialog.hide()
                    dismiss()
                }
            }
        }

        binding.btnCopyAccountUpdateUrl.setOnClickListener {
            val clipboard: ClipboardManager =
                requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData =
                ClipData.newPlainText("Account URL kopiert!", GetAccountUpdateUrl())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                requireActivity().applicationContext,
                "Account URL kopiert!",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnCopyDomainUpdateUrl.setOnClickListener {
            val clipboard: ClipboardManager =
                requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("Domain URL kopiert!", GetDomainUpdateUrl())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                requireActivity().applicationContext,
                "Domain URL kopiert!",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.topAppBarDetail.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.new_dnsrecord -> {
                    println("new dns")
                    //saveUser()
                    val fragmentManager = requireActivity().supportFragmentManager
                    val newFragment = CreateDNSRecordDialogFragment()
                    newFragment.arguments = Bundle().apply {
                        putString("DOMAINKEY", domainTitle)
                    }
                    newFragment.show(fragmentManager, "dialogDNS")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {
                        val isDismissFromX = requireActivity().applicationContext.getSharedBool(
                            "DNSRECORDDISMISS",
                            "DNSRECORDDISMISS"
                        )
                        if (!isDismissFromX)
                            dismiss()

                        requireActivity().applicationContext.setSharedBool(
                            "DNSRECORDDISMISS",
                            "DNSRECORDDISMISS",
                            false
                        )
                    }
                    true
                }

                R.id.delete_domain -> {
                    println("deleteDomain")
                    //saveUser()

                    val fragmentManager = activity?.supportFragmentManager
                    val newFragment = ErrorDialogFragment(ErrorTypes.deleteDomain)
                    newFragment.show(fragmentManager!!, "dialogError")
                    fragmentManager.executePendingTransactions()
                    newFragment.setOnDismissListener {
                        var isCanceld =
                            requireActivity().getSharedBool("ISCANCELD", "ISCANCELD")
                        if (!isCanceld) {
                            spinnDialog.show()
                            GlobalScope.launch(Dispatchers.Default) {
                                val result = ApiNetwork.DeleteDomain(domainTitle)

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
                                        val newFragment =
                                            ErrorDialogFragment(ErrorTypes.unauthorized)
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
                                    dismiss()
                                }
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

    private fun GetAccountUpdateUrl(): String {
        return "https://ipv64.net/update.php?key=" + accountInfo.update_hash + "&domain=" + domainTitle
    }

    private fun GetDomainUpdateUrl(): String {
        return "https://ipv64.net/update.php?key=" + domain.domain_update_hash
    }

    private fun CheckIfIPCorrect(): Boolean {
        if (domain.records?.isNotEmpty()!!) {
            val firstRec = domain.records!!.firstOrNull {
                it.type == "A"
            }
            return firstRec?.content == myIp.ip
        }
        return false
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