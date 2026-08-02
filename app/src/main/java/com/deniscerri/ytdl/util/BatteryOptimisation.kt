package com.deniscerri.ytdl.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/**
 * Battery optimisation exemption is an OS-level allowance, not a stored flag, so it can't simply
 * be written into SharedPreferences like the other managed defaults -- Android requires the user
 * to confirm it in a system dialog.
 *
 * The settings entry for this was removed from the UI, so instead we ask once, on first launch.
 * If the user declines we never ask again; downloads still work, they just risk being throttled
 * when the screen is off.
 */
object BatteryOptimisation {

    private const val ASKED_KEY = "zetta_asked_battery_exemption"

    @SuppressLint("BatteryLife")
    fun requestOnceIfNeeded(context: Context) {
        runCatching {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (prefs.getBoolean(ASKED_KEY, false)) return

            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
                prefs.edit { putBoolean(ASKED_KEY, true) }
                return
            }

            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            // Only mark as asked once the dialog actually launched, so a failure here doesn't
            // silently burn the single attempt.
            context.startActivity(intent)
            prefs.edit { putBoolean(ASKED_KEY, true) }
        }
    }
}
