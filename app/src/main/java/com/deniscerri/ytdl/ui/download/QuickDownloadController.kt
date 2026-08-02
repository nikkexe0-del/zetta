package com.deniscerri.ytdl.ui.download

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.Extensions.extractURL
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick download flow: paste → quality + format + subtitles → hit Download → goes into queue.
 * No BottomSheetDialog, no separate screen. All inline on Home.
 * Touches NOTHING to do with yt-dlp settings or the download pipeline internals.
 */
class QuickDownloadController(
    private val context: Context,
    private val root: View,
    private val resultViewModel: ResultViewModel,
    private val downloadViewModel: DownloadViewModel,
    private val scope: LifecycleCoroutineScope
) {

    private var urlInput: TextInputEditText? = null
    private var titleInput: TextInputEditText? = null
    private var qualityChips: ChipGroup? = null
    private var formatChips: ChipGroup? = null
    private var subtitlesCheck: MaterialCheckBox? = null
    private var downloadBtn: MaterialButton? = null

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    private val qualities = listOf(
        "best" to "Best",
        "1080p_ytdlnisgeneric" to "1080p",
        "720p_ytdlnisgeneric" to "720p",
        "480p_ytdlnisgeneric" to "480p",
        "360p_ytdlnisgeneric" to "360p",
        "worst" to "Data saver"
    )

    private val formats = listOf("mp4" to "MP4", "mp3" to "MP3")

    fun attach() {
        urlInput = root.findViewById(R.id.flow_url_input)
        titleInput = root.findViewById(R.id.flow_title_input)
        qualityChips = root.findViewById(R.id.flow_quality_chips)
        formatChips = root.findViewById(R.id.flow_format_chips)
        subtitlesCheck = root.findViewById(R.id.flow_subtitles)
        downloadBtn = root.findViewById(R.id.flow_download_btn)

        // pre-fill URL from clipboard
        runCatching {
            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            if (text.extractURL().isNotBlank()) urlInput?.setText(text.extractURL())
        }

        buildQualityChips()
        buildFormatChips()

        downloadBtn?.setOnClickListener { handleDownload() }
    }

    private fun buildQualityChips() {
        val group = qualityChips ?: return
        if (group.childCount > 0) return
        group.isSingleSelection = true

        val current = prefs.getString("video_quality", "best")
        qualities.forEach { (value, label) ->
            val chip = Chip(context)
            chip.text = label
            chip.isCheckable = true
            chip.isChecked = value == current
            chip.tag = value
            chip.id = View.generateViewId()
            chip.setOnClickListener {
                prefs.edit().putString("video_quality", value).apply()
            }
            group.addView(chip)
        }
    }

    private fun buildFormatChips() {
        val group = formatChips ?: return
        if (group.childCount > 0) return
        group.isSingleSelection = true

        formats.forEach { (value, label) ->
            val chip = Chip(context)
            chip.text = label
            chip.isCheckable = true
            chip.isChecked = value == "mp4"
            chip.tag = value
            chip.id = View.generateViewId()
            group.addView(chip)
        }
    }

    /**
     * ChipGroup exposes the checked child's id, not its tag, so resolve the view first.
     * Returns null when nothing is checked.
     */
    private fun checkedTagOf(group: ChipGroup?): String? {
        val g = group ?: return null
        val id = g.checkedChipId
        if (id == View.NO_ID) return null
        return g.findViewById<Chip>(id)?.tag as? String
    }

    private fun handleDownload() {
        val url = urlInput?.text.toString().extractURL()
        if (url.isBlank()) {
            Toast.makeText(context, R.string.paste_link, Toast.LENGTH_SHORT).show()
            return
        }

        val titleOverride = titleInput?.text.toString()
        val selectedFormat = checkedTagOf(formatChips) ?: "mp4"
        val wantSubtitles = subtitlesCheck?.isChecked ?: false

        downloadBtn?.isEnabled = false
        downloadBtn?.text = context.getString(R.string.downloading)

        scope.launch {
            runCatching {
                // check if we've already seen this URL
                val existing = withContext(Dispatchers.IO) {
                    resultViewModel.getAllByURL(url)
                }

                val result = if (existing.size == 1) existing.first()
                else downloadViewModel.createEmptyResultItem(url)

                // apply title override if provided
                if (titleOverride.isNotBlank()) {
                    result.title = titleOverride
                }

                // MP3 means an audio download outright -- there is no "audioOnly" flag on
                // DownloadItem, the distinction is carried by DownloadType.
                val type = if (selectedFormat == "mp3") DownloadType.audio
                           else downloadViewModel.getDownloadType(url = result.url)

                val downloadItem = withContext(Dispatchers.IO) {
                    downloadViewModel.createDownloadItemFromResult(
                        result = result,
                        givenType = type
                    )
                }

                // Container follows the chip; subtitles live on videoPreferences, not the item.
                downloadItem.container = selectedFormat
                downloadItem.videoPreferences.writeSubs = wantSubtitles
                downloadItem.videoPreferences.embedSubs = wantSubtitles

                // queue it
                withContext(Dispatchers.IO) {
                    downloadViewModel.queueDownloads(listOf(downloadItem))
                }

                // clear the form
                withContext(Dispatchers.Main) {
                    urlInput?.text?.clear()
                    titleInput?.text?.clear()
                    subtitlesCheck?.isChecked = false
                    downloadBtn?.isEnabled = true
                    downloadBtn?.text = context.getString(R.string.download)
                    Toast.makeText(
                        context,
                        context.getString(R.string.saved_to_folder),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    downloadBtn?.isEnabled = true
                    downloadBtn?.text = context.getString(R.string.download)
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }
    }
}
