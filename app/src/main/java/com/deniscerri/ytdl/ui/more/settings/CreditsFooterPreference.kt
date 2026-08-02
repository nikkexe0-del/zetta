package com.deniscerri.ytdl.ui.more.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.deniscerri.ytdl.R

/**
 * The zetta credits block, rendered as the last row of a settings screen.
 *
 * Implemented as a Preference rather than a static footer view so it can be appended to every
 * settings screen from [BaseSettingsFragment] without touching each preferences XML, and so it
 * scrolls with the list instead of pinning over the content.
 */
class CreditsFooterPreference(context: Context) : Preference(context) {

    init {
        layoutResource = R.layout.preference_credits_footer
        key = KEY
        isSelectable = false
        isPersistent = false
        isCopyingEnabled = false
        order = Int.MAX_VALUE
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = true
        holder.isDividerAllowedBelow = false

        holder.itemView.findViewById<View>(R.id.credits_instagram)?.setOnClickListener {
            open("https://instagram.com/nikkk.exe")
        }
        holder.itemView.findViewById<View>(R.id.credits_site)?.setOnClickListener {
            open("https://nikshep.vercel.app")
        }
    }

    private fun open(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    companion object {
        const val KEY = "zetta_credits_footer"
    }
}
