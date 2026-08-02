package com.deniscerri.ytdl.ui.more.settings

import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.SearchSettingsItem
import com.deniscerri.ytdl.ui.more.settings.advanced.AdvancedSettingsModule
import com.deniscerri.ytdl.ui.more.settings.downloading.DownloadsAndProcessingModule
import com.deniscerri.ytdl.ui.more.settings.folder.FolderSettingsModule
import com.deniscerri.ytdl.ui.more.settings.general.GeneralSettingsModule
import com.deniscerri.ytdl.ui.more.settings.updating.UpdateSettingsModule

object SettingsRegistry {
    private val xmlToModule = mapOf(
        R.xml.zetta_all_preferences to ZettaSettingsModule,
        R.xml.general_preferences to GeneralSettingsModule,
        R.xml.folders_preference to FolderSettingsModule,
        // Downloads now also hosts the old Processing preferences
        R.xml.downloading_preferences to DownloadsAndProcessingModule,
        // Slimmed to component/package management only -- the auto-update toggles are pinned in
        // ManagedDefaults and no longer shown. Users still need a way to pull a missing or newer
        // yt-dlp / ffmpeg build, which is what this screen is now for.
        R.xml.updating_preferences to UpdateSettingsModule,
        R.xml.advanced_preferences to AdvancedSettingsModule
    )

    fun getModuleForXml(xmlRes: Int) = xmlToModule[xmlRes]

    fun bindFragment(fragment: BaseSettingsFragment, xmlRes: Int) {
        val module = getModuleForXml(xmlRes) ?: return
        val allPrefs = mutableListOf<Preference>()
        fragment.getPreferences(fragment.preferenceScreen, allPrefs).forEach {
            module.bindLogic(it, fragment)
        }
    }

    fun indexAll(context: Context): List<SearchSettingsItem> {
        val manager = PreferenceManager(context)
        val results = mutableListOf<SearchSettingsItem>()

        // Only the flattened screen is indexed. The per-section XMLs are still in xmlToModule so
        // their fragments keep binding correctly, but crawling them too would return every
        // preference two or three times in settings search.
        val screen = manager.inflateFromResource(context, R.xml.zetta_all_preferences, null)
        results.addAll(crawl(screen, R.xml.zetta_all_preferences, ZettaSettingsModule, null))
        return results
    }

    private fun crawl(
        group: PreferenceGroup,
        xmlId: Int,
        module: SettingModule?,
        parentTitle: String? = null
    ): List<SearchSettingsItem> {
        val list = mutableListOf<SearchSettingsItem>()

        if (!group.title.isNullOrBlank()) {
            list.add(SearchSettingsItem(
                preference = group,
                xmlId = xmlId,
                module = module,
                groupTitle = group.title.toString(),
                isHeader = true,
                canRebind = true
            ))
        }

        val preferencesWithoutRebindingLogic = listOf("ytdl-version", CreditsFooterPreference.KEY)

        for (i in 0 until group.preferenceCount) {
            val p = group.getPreference(i)
            if (p is PreferenceGroup) {
                list.addAll(crawl(p, xmlId, module, group.title?.toString()))
            } else if (p.key != null && p.key != "reset_preferences") {
                list.add(SearchSettingsItem(
                    preference = p,
                    xmlId = xmlId,
                    module = module,
                    groupTitle = group.title?.toString() ?: parentTitle,
                    isHeader = false,
                    canRebind = !preferencesWithoutRebindingLogic.contains(p.key)
                ))
            }
        }
        return list
    }
}