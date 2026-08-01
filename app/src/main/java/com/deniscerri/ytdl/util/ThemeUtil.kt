package com.deniscerri.ytdl.util

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Spanned
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.google.android.material.color.DynamicColors


object ThemeUtil {

    private val activities = mutableListOf<Activity>()

    fun init(app: Application) {
        app.registerActivityLifecycleCallbacks(object: Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(p0: Activity, p1: Bundle?) {
                activities.add(p0)
            }

            override fun onActivityStarted(p0: Activity) {

            }

            override fun onActivityResumed(p0: Activity) {

            }

            override fun onActivityPaused(p0: Activity) {

            }

            override fun onActivityStopped(p0: Activity) {

            }

            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {

            }

            override fun onActivityDestroyed(p0: Activity) {
                activities.remove(p0)
            }
        })

    }

    sealed class AppIcon(
        @StringRes val nameResource: Int,
        @DrawableRes val iconResource: Int,
        val activityAlias: String
    ) {
        object Default : AppIcon(R.string.auto, R.mipmap.ic_launcher, "Default")
        object Light : AppIcon(R.string.light, R.mipmap.ic_launcher_light, "LightIcon")
        object Dark : AppIcon(R.string.dark, R.mipmap.ic_launcher_dark, "DarkIcon")
        object Blue : AppIcon(R.string.blue, R.mipmap.ic_launcher_blue, "BlueIcon")
        object Green : AppIcon(R.string.green, R.mipmap.ic_launcher_green, "GreenIcon")
    }

    val availableIcons = listOf(
        AppIcon.Default,
        AppIcon.Light,
        AppIcon.Dark,
        AppIcon.Blue,
        AppIcon.Green,
    )

    fun recreateMain() {
        activities.firstOrNull { it.javaClass == MainActivity::class.java }?.recreate()
    }

    fun recreateAllActivities() {
        activities.forEach {
            it.recreate()
        }
    }

    fun updateThemes() {
        activities.forEach {
            updateTheme(it)
            it.recreate()
        }
    }

    fun updateTheme(activity: Activity) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)

        //update accent
        when (sharedPreferences.getString("theme_accent","blue")) {
            "Default" -> {
                DynamicColors.applyToActivityIfAvailable(activity)
                activity.setTheme(R.style.BaseTheme)
            }
            "blue" -> activity.setTheme(R.style.Theme_Blue)
            "red" -> activity.setTheme(R.style.Theme_Red)
            "green" -> activity.setTheme(R.style.Theme_Green)
            "purple" -> activity.setTheme(R.style.Theme_Purple)
            "yellow" -> activity.setTheme(R.style.Theme_Yellow)
            "orange" -> activity.setTheme(R.style.Theme_Orange)
            "monochrome" -> activity.setTheme(R.style.Theme_Monochrome)
        }

        //high contrast theme
        if (sharedPreferences.getBoolean("high_contrast",false)) {
            activity.theme.applyStyle(R.style.Pure, true)
        }

        val theme = sharedPreferences.getString("ytdlnis_theme", "System")!!
        when (theme) {
            "Light" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            "Dark" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            // or "System"
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }


        val iconMode = sharedPreferences.getString("ytdlnis_icon", "Default")!!
        updateAppIcon(activity,theme, iconMode)
    }

    fun getThemeColor(context: Context, colorCode: Int): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val accent = sharedPreferences.getString("theme_accent", "blue")
        return if (accent == "blue"){
            "d43c3b".toInt(16)
        }else{
            val value = TypedValue()
            context.theme.resolveAttribute(colorCode, value, true)
            value.data
        }

    }

    /**
     * Get the styled app name
     */
    fun getStyledAppName(context: Context): Spanned {
        val colorPrimary = getThemeColor(context, androidx.appcompat.R.attr.colorPrimaryDark)
        val hexColor = "#%06X".format(0xFFFFFF and colorPrimary)
        return "<span  style='color:$hexColor';>z</span>etta"
            .parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT)
    }


    fun updateAppIcon(activity: Activity, theme: String, appIconMode: String) {
        var iconMode = appIconMode
        if (appIconMode == "Default") {
            iconMode = theme
        }

        val targetAlias = when (iconMode) {
            "LightIcon" -> "com.deniscerri.ytdl.LightIcon"
            "DarkIcon" -> "com.deniscerri.ytdl.DarkIcon"
            "BlueIcon" -> "com.deniscerri.ytdl.BlueIcon"
            "GreenIcon" -> "com.deniscerri.ytdl.GreenIcon"
            // or "System"
            else -> "com.deniscerri.ytdl.Default"
        }

        val pm = activity.packageManager

        // IMPORTANT (flicker fix):
        // This used to unconditionally DISABLE every launcher alias and then re-enable
        // one, on every single activity onCreate. Each setComponentEnabledSetting call
        // triggers a package/launcher refresh, so doing it repeatedly made the screen
        // visibly flicker (dim/bright) for as long as the app was open.
        //
        // Now we first check the current state and only write when something actually
        // needs to change, which is a no-op on the overwhelmingly common path.
        // Behaviour is otherwise identical: exactly one alias ends up enabled.
        val desiredStates = mutableListOf<Pair<String, Int>>()
        for (appIcon in availableIcons) {
            val activityClass = "com.deniscerri.ytdl." + appIcon.activityAlias
            val desired = if (activityClass == targetAlias) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            desiredStates.add(activityClass to desired)
        }

        // Make sure the target is included even if it isn't in availableIcons.
        if (desiredStates.none { it.first == targetAlias }) {
            desiredStates.add(targetAlias to PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        }

        val needsChange = desiredStates.any { (activityClass, desired) ->
            val current = runCatching {
                pm.getComponentEnabledSetting(ComponentName(activity.packageName, activityClass))
            }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

            // DEFAULT means "as declared in the manifest". Treat it as already-correct
            // for the target alias so a fresh install doesn't churn the launcher.
            when {
                current == desired -> false
                current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                    desired == PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
                    activityClass == targetAlias -> false
                else -> true
            }
        }

        if (!needsChange) return

        desiredStates.forEach { (activityClass, desired) ->
            runCatching {
                pm.setComponentEnabledSetting(
                    ComponentName(activity.packageName, activityClass),
                    desired,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}