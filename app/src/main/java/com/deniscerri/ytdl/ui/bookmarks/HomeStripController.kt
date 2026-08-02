package com.deniscerri.ytdl.ui.bookmarks

import android.content.Context
import android.view.View
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.BookmarkStore
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Everything the home screen needs for folders and the quick quality picker, kept in one place
 * so HomeFragment only has to call [attach] and [refresh]. Touches nothing to do with yt-dlp.
 */
class HomeStripController(
    private val context: Context,
    private val root: View,
    private val navController: NavController
) {

    private var foldersRecycler: RecyclerView? = null
    private var qualityChips: ChipGroup? = null
    private var folderAdapter: BookmarkFolderAdapter? = null

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    /** value stored in the existing `video_quality` preference -> label shown on the chip */
    private val qualities = listOf(
        "best" to "Best",
        "1080p_ytdlnisgeneric" to "1080p",
        "720p_ytdlnisgeneric" to "720p",
        "480p_ytdlnisgeneric" to "480p",
        "360p_ytdlnisgeneric" to "360p",
        "worst" to "Data saver"
    )

    fun attach() {
        val strip = root.findViewById<View>(R.id.home_bookmarks_strip) ?: return
        foldersRecycler = strip.findViewById(R.id.folders_recycler)
        qualityChips = strip.findViewById(R.id.quality_chips)

        buildQualityChips()

        folderAdapter = BookmarkFolderAdapter(
            onFolderClick = { folder ->
                runCatching {
                    navController.navigate(
                        R.id.bookmarkFolderFragment,
                        bundleOf("folderId" to folder.id)
                    )
                }
            },
            onFolderLongClick = { folder -> showFolderMenu(folder) },
            onNewFolder = { promptNewFolder() }
        )

        foldersRecycler?.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = folderAdapter
        }

        refresh()
    }

    fun refresh() {
        val folders = BookmarkStore.getFolders(context)
        val counts = folders.associate { it.id to BookmarkStore.countIn(context, it.id) }
        folderAdapter?.submit(folders, counts)
    }

    /** Hidden while search results are on screen so the list stays clean. */
    fun setVisible(visible: Boolean) {
        root.findViewById<View>(R.id.home_bookmarks_strip)?.isVisible = visible
    }

    private fun buildQualityChips() {
        val group = qualityChips ?: return
        if (group.childCount > 0) return

        val current = prefs.getString("video_quality", "best")
        qualities.forEach { (value, label) ->
            val chip = Chip(context)
            chip.text = label
            chip.isCheckable = true
            chip.isChecked = value == current
            chip.tag = value
            chip.setOnClickListener {
                prefs.edit().putString("video_quality", value).apply()
            }
            group.addView(chip)
        }
    }

    private fun promptNewFolder() {
        val input = EditText(context).apply {
            hint = context.getString(R.string.folder_name)
            setPadding(60, 40, 60, 40)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.new_folder)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().ifBlank { context.getString(R.string.new_folder) }
                BookmarkStore.createFolder(context, name)
                refresh()
            }
            .show()
    }

    private fun showFolderMenu(folder: BookmarkStore.Folder) {
        MaterialAlertDialogBuilder(context)
            .setTitle(folder.name)
            .setItems(
                arrayOf(
                    context.getString(R.string.rename),
                    context.getString(R.string.delete)
                )
            ) { _, which ->
                when (which) {
                    0 -> promptRename(folder)
                    1 -> {
                        BookmarkStore.deleteFolder(context, folder.id)
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun promptRename(folder: BookmarkStore.Folder) {
        val input = EditText(context).apply {
            setText(folder.name)
            setPadding(60, 40, 60, 40)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.rename)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                BookmarkStore.renameFolder(context, folder.id, input.text.toString())
                refresh()
            }
            .show()
    }
}
