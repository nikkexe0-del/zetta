package com.deniscerri.ytdl.util

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R

/**
 * zetta ships opinionated defaults.
 *
 * Every value below has had its toggle removed from the settings UI, so it must be written here
 * instead — otherwise the underlying feature would keep reading whatever stale value happened to
 * be in SharedPreferences from a previous install.
 *
 * This runs on every launch (cheap, one batched commit) so the state can never drift, and so a
 * user upgrading from a build where they had flipped one of these ends up on the managed value.
 *
 * Nothing here changes what the download engine is *capable* of. It only fixes the inputs.
 */
object ManagedDefaults {

    private val booleans = mapOf(
        // ---- appearance ----
        "high_contrast" to true,

        // ---- interaction ----
        // NOTE: "swipe_gesture" is NOT here -- it is a MultiSelectListPreference (StringSet).
        // See stringSets below.
        "swipe_gestures_download_card" to true,
        "show_count_downloads" to true,
        "use_code_color_highlighter" to false,

        // ---- terminal is backend-only now ----
        "show_terminal" to false,

        // ---- downloading ----
        "incognito" to false,
        "force_ipv4" to false,
        "metered_networks" to true,
        "silent_share_download" to true,

        // ---- processing ----
        "mtime" to false,
        "write_description" to false,
        "recode_video" to false,
        "force_keyframes" to false,
        "compatible_video" to false,
        "embed_metadata" to true,
        "embed_thumbnail" to true,
        "video_embed_thumbnail" to true,

        // ---- audio ----
        "crop_thumbnail" to true,
        "playlist_as_album" to true,
        "prefer_drc_audio" to false,
        "write_thumbnail" to false,

        // ---- downloading ----
        "log_downloads" to false,

        // ---- updates: all on, no UI, no first-run prompt ----
        // The Updating settings screen and the first-run "i" dialog are both gone, so these are
        // pinned here instead. Note the read sites in MainActivity use `false` as their fallback,
        // which means without this block a cleared-prefs state would silently disable updates with
        // no way for the user to turn them back on.
        "update_app" to true,             // periodic + on-launch check for a new zetta APK
        "auto_update_ytdlp" to true,      // pull the latest yt-dlp on launch
        "update_formats" to true,         // refresh stale format lists
        "update_beta" to false,           // stable releases only
        // Suppresses the first-run update dialog. Everything it used to set is pinned above.
        "asked_auto_update_preferences" to true,

        // ---- advanced (removed from UI) ----
        "no_check_certificates" to false,
        "disable_write_info_json" to true
    )

    private val strings = mapOf(
        // Output container: MP4. This becomes `--merge-output-format mp4`.
        "video_format" to "mp4",
        // Video codec: AVC / H.264.
        "video_codec" to "avc|h264",
        // Audio codec: AAC. The value is a yt-dlp *stream selector* (m4a/mp4a/aac all denote AAC),
        // NOT a container -- the container is the "mp4" above.
        "audio_codec" to "m4a|mp4a|aac",
        "audio_format" to "mp3",

        // no per-format overrides; the engine picks from the codec/container above
        "format_id" to "",
        "format_id_audio" to "",

        // finished files are stamped: "Some Video Title-by-zetta.mp4"
        "file_name_template" to "%(title).170B-by-zetta",
        "file_name_template_audio" to "%(title).170B-by-zetta",

        // dedupe on url + download type
        "prevent_duplicate_downloads" to "url_type",

        // SponsorBlock endpoint is not user configurable
        "sponsorblock_url" to "",

        // no proxy
        "proxy" to "",

        // advanced YouTube extractor knobs are not user configurable
        "youtube_data_sync_id" to "",
        "youtube_other_extractor_args" to ""
    )

    /**
     * These two are MultiSelectListPreference, so SharedPreferences stores them as a Set<String>.
     * Writing them as booleans throws ClassCastException the moment anything calls getStringSet().
     */
    private fun stringSets(context: Context) = mapOf(
        // swipe gestures enabled on every screen that supports them
        "swipe_gesture" to context.resources
            .getStringArray(R.array.swipe_gestures_values).toSet(),
        // never hide thumbnails anywhere
        "hide_thumbnails" to emptySet<String>(),
        // SponsorBlock filter list stays empty
        "sponsorblock_filters" to emptySet<String>()
    )

    private val ints = mapOf(
        "concurrent_downloads" to 5
    )

    fun apply(context: Context) {
        runCatching {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit {
                booleans.forEach { (key, value) -> putBoolean(key, value) }
                strings.forEach { (key, value) -> putString(key, value) }
                ints.forEach { (key, value) -> putInt(key, value) }
                stringSets(context).forEach { (key, value) -> putStringSet(key, value) }
            }
        }
    }
}
