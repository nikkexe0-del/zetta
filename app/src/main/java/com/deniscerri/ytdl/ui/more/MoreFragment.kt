package com.deniscerri.ytdl.ui.more

import android.content.Intent
import android.net.Uri
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.NavbarUtil
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MoreFragment : Fragment() {
    private lateinit var mainSharedPreferences: SharedPreferences
    private lateinit var mainSharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var logs: TextView
    private lateinit var downloads: TextView
    private lateinit var cookies: TextView
    private lateinit var mainActivity: MainActivity
    private lateinit var downloadViewModel: DownloadViewModel
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        return inflater.inflate(R.layout.fragment_more, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainSharedPreferences =  PreferenceManager.getDefaultSharedPreferences(requireContext())
        mainSharedPreferencesEditor = mainSharedPreferences.edit()
        logs = view.findViewById(R.id.logs)
        downloads = view.findViewById(R.id.downloads)
        cookies = view.findViewById(R.id.cookies)
        // zetta: every settings section listed inline, so this tab is one long scroll
        val sections = listOf(
            R.id.set_general     to "appearance",
            R.id.set_folders     to "folders",
            R.id.set_downloading to "downloading",
            R.id.set_processing  to "processing",
            R.id.set_updating    to "updating",
            R.id.set_advanced    to "advanced",
            R.id.set_backup      to ""
        )
        sections.forEach { (viewId, section) ->
            view.findViewById<TextView>(viewId)?.setOnClickListener {
                val intent = Intent(context, SettingsActivity::class.java)
                if (section.isNotEmpty()) intent.putExtra("section", section)
                startActivity(intent)
            }
        }

        val appIcon = view.findViewById<ImageView>(R.id.app_icon)
        if (mainSharedPreferences.getString("theme_accent", "blue") == "Default" && Build.VERSION.SDK_INT >= 32) {
            appIcon.backgroundTintList = MaterialColors.getColorStateList(requireContext(), R.attr.colorPrimary, ContextCompat.getColorStateList(requireContext(), R.color.icon_fg)!!)
        } else {
            appIcon.backgroundTintList = null
        }

        var showingDownloads = false

        NavbarUtil.getNavBarItems(requireContext()).apply {
            showingDownloads = any { n -> n.itemId == R.id.historyFragment && n.isVisible }
        }

        downloads.isVisible = !showingDownloads


        view.findViewById<View>(R.id.credits_instagram).setOnClickListener {
            openLink("https://instagram.com/nikkk.exe")
        }
        view.findViewById<View>(R.id.credits_site).setOnClickListener {
            openLink("https://nikshep.vercel.app")
        }

        logs.setOnClickListener {
            findNavController().navigate(R.id.downloadLogListFragment)
        }

        downloads.setOnClickListener {
            findNavController().navigate(R.id.historyFragment)
        }


        cookies.setOnClickListener {
            findNavController().navigate(R.id.cookiesFragment)
        }


    }

    fun showTerminateConfirmationDialog(skipPreference: Boolean = false) {
        val shouldAskToTerminate = mainSharedPreferences.getBoolean("ask_terminate_app", true)
        if (!shouldAskToTerminate && !skipPreference) {
            terminateApp()
            return
        }

        var doNotShowAgainFinalState = !shouldAskToTerminate

        lateinit var dialog: AlertDialog
        val terminateDialog = MaterialAlertDialogBuilder(requireContext())
        terminateDialog.setTitle(getString(R.string.kill_app))
        val dialogView = layoutInflater.inflate(R.layout.dialog_terminate_app, null)
        val checkbox = dialogView.findViewById<CheckBox>(R.id.doNotShowAgain)
        terminateDialog.setView(dialogView)

        checkbox.isChecked = doNotShowAgainFinalState
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            doNotShowAgainFinalState = isChecked
        }

        terminateDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface, _ ->
            dialogInterface.cancel()
        }

        terminateDialog.setPositiveButton(getString(R.string.ok), null)
        dialog = terminateDialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.setCanceledOnTouchOutside(false)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
            mainSharedPreferencesEditor.putBoolean("ask_terminate_app", !doNotShowAgainFinalState).commit()
            terminateApp()
        }
    }

    private fun openLink(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun terminateApp() {
        lifecycleScope.launch {
            downloadViewModel.pauseAllDownloads()
            mainActivity.finishAndRemoveTask()
            mainActivity.finishAffinity()
            exitProcess(0)
        }
    }

    companion object {
        const val TAG = "MoreFragment"
    }

}