package com.deniscerri.ytdl.ui.bookmarks

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.BookmarkStore
import com.deniscerri.ytdl.util.Extensions.extractURL
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarkFolderFragment : Fragment() {

    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var adapter: BookmarkItemAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var toolbar: MaterialToolbar

    private var folderId: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        return inflater.inflate(R.layout.fragment_bookmark_folder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        folderId = arguments?.getLong("folderId") ?: 0L

        toolbar = view.findViewById(R.id.folder_toolbar)
        recycler = view.findViewById(R.id.bookmark_recycler)
        empty = view.findViewById(R.id.bookmark_empty)

        toolbar.title = BookmarkStore.getFolders(requireContext())
            .firstOrNull { it.id == folderId }?.name ?: getString(R.string.folders)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = BookmarkItemAdapter(
            onDownload = { queueDownload(it) },
            onRemove = { item ->
                BookmarkStore.deleteItem(requireContext(), item.id)
                refresh()
            },
            onOpen = { item -> openExternally(item.url) }
        )
        recycler.adapter = adapter

        view.findViewById<ExtendedFloatingActionButton>(R.id.add_link_fab)
            .setOnClickListener { promptForLink() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = BookmarkStore.getItems(requireContext(), folderId)
        adapter.submit(items)
        empty.isVisible = items.isEmpty()
    }

    private fun promptForLink() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.paste_link)
            setPadding(60, 40, 60, 40)
            // pre-fill from the clipboard, which is where a shared link usually is
            runCatching {
                val clip = requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                if (text.extractURL().isNotBlank()) setText(text.extractURL())
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_link)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val url = input.text.toString().extractURL()
                if (url.isBlank()) {
                    Toast.makeText(requireContext(), R.string.paste_link, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val item = BookmarkStore.addItem(requireContext(), folderId, url)
                refresh()
                fetchMetadata(item)
            }
            .show()
    }

    /**
     * Best effort title/thumbnail lookup. If anything fails the bookmark simply keeps the raw
     * URL as its title, so a network hiccup can never lose the saved link.
     */
    private fun fetchMetadata(item: BookmarkStore.Item) {
        lifecycleScope.launch {
            runCatching {
                val existing = withContext(Dispatchers.IO) {
                    resultViewModel.getAllByURL(item.url)
                }
                val result = existing.firstOrNull() ?: return@runCatching
                BookmarkStore.updateItemMeta(
                    requireContext(),
                    item.id,
                    result.title,
                    result.author,
                    result.thumb
                )
                refresh()
            }
        }
    }

    /**
     * Hands the link to the existing download pipeline untouched. Same call path the share
     * sheet uses, so yt-dlp behaviour is identical.
     */
    private fun queueDownload(item: BookmarkStore.Item) {
        lifecycleScope.launch {
            runCatching {
                val existing = withContext(Dispatchers.IO) {
                    resultViewModel.getAllByURL(item.url)
                }
                val result = if (existing.size == 1) existing.first()
                else downloadViewModel.createEmptyResultItem(item.url)

                val type = DownloadType.valueOf(
                    downloadViewModel.getDownloadType(url = result.url).toString()
                )

                withContext(Dispatchers.IO) {
                    val downloadItem = downloadViewModel.createDownloadItemFromResult(
                        result = result,
                        givenType = type
                    )
                    downloadViewModel.queueDownloads(listOf(downloadItem))
                }
            }.onFailure { it.printStackTrace() }
        }
    }

    private fun openExternally(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
