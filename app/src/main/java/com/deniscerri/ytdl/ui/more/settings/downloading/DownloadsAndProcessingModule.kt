package com.deniscerri.ytdl.ui.more.settings.downloading

import androidx.preference.Preference
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.ui.more.settings.processing.ProcessingSettingsModule

/**
 * The Processing screen was folded into Downloads, so a single preference XML now carries
 * preferences owned by two different modules.
 *
 * Both modules dispatch on `pref.key` and ignore anything they don't recognise, so handing every
 * preference to both is safe and keeps each module's listeners intact -- without this, the
 * processing preferences would render but do nothing when toggled.
 */
object DownloadsAndProcessingModule : SettingModule {
    override fun bindLogic(pref: Preference, host: SettingHost) {
        DownloadSettingsModule.bindLogic(pref, host)
        ProcessingSettingsModule.bindLogic(pref, host)
    }
}
