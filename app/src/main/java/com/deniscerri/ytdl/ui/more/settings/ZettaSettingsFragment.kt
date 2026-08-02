package com.deniscerri.ytdl.ui.more.settings

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import com.deniscerri.ytdl.R

/**
 * The Settings tab: every preference in the app in one continuous scroll.
 *
 * Replaces the old More tab, which was a list of rows that each launched a separate settings
 * activity. Nothing about the preferences themselves changed -- they're the same XML blocks,
 * concatenated -- so all the existing SettingModule logic still applies via ZettaSettingsModule.
 */
class ZettaSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.settings

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.zetta_all_preferences, rootKey)

        // The four shortcuts at the top navigate rather than toggle anything, so they're wired
        // here instead of in a SettingModule.
        findPref("zetta_shortcut_queue")?.setOnPreferenceClickListener {
            navigate(R.id.downloadQueueMainFragment); true
        }
        findPref("zetta_shortcut_logs")?.setOnPreferenceClickListener {
            navigate(R.id.downloadLogListFragment); true
        }
        findPref("zetta_shortcut_cookies")?.setOnPreferenceClickListener {
            navigate(R.id.cookiesFragment); true
        }
        findPref("zetta_shortcut_templates")?.setOnPreferenceClickListener {
            navigate(R.id.commandTemplatesFragment); true
        }

        SettingsRegistry.bindFragment(this, R.xml.zetta_all_preferences)
    }

    /**
     * This fragment lives in the main nav graph rather than SettingsActivity's, so navigation has
     * to go through the host's NavController. Wrapped because a destination can be missing if the
     * graph is edited, and a settings row shouldn't be able to crash the app.
     */
    private fun navigate(destination: Int) {
        runCatching { findNavController().navigate(destination) }
    }
}
