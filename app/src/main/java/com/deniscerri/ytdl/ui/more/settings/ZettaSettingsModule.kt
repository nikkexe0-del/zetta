package com.deniscerri.ytdl.ui.more.settings

import androidx.preference.Preference
import com.deniscerri.ytdl.ui.more.settings.advanced.AdvancedSettingsModule
import com.deniscerri.ytdl.ui.more.settings.downloading.DownloadSettingsModule
import com.deniscerri.ytdl.ui.more.settings.folder.FolderSettingsModule
import com.deniscerri.ytdl.ui.more.settings.general.GeneralSettingsModule
import com.deniscerri.ytdl.ui.more.settings.processing.ProcessingSettingsModule

/**
 * One screen now holds preferences that used to be spread across five modules, so every
 * preference is offered to all of them.
 *
 * Every module dispatches on `pref.key` in a `when` and ignores keys it doesn't own, which makes
 * this safe: at most one module reacts to any given preference. Without it the flattened screen
 * would render correctly but nothing would respond to being tapped.
 */
object ZettaSettingsModule : SettingModule {
    private val modules = listOf(
        GeneralSettingsModule,
        FolderSettingsModule,
        DownloadSettingsModule,
        ProcessingSettingsModule,
        AdvancedSettingsModule
    )

    override fun bindLogic(pref: Preference, host: SettingHost) {
        modules.forEach { module ->
            // One module throwing must not stop the rest of the screen from binding.
            runCatching { module.bindLogic(pref, host) }
        }
    }
}
