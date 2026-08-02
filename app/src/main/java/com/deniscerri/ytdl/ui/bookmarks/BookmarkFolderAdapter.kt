package com.deniscerri.ytdl.ui.bookmarks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.BookmarkStore

class BookmarkFolderAdapter(
    private val onFolderClick: (BookmarkStore.Folder) -> Unit,
    private val onFolderLongClick: (BookmarkStore.Folder) -> Unit,
    private val onNewFolder: () -> Unit
) : RecyclerView.Adapter<BookmarkFolderAdapter.Holder>() {

    private var folders: List<BookmarkStore.Folder> = emptyList()
    private var counts: Map<Long, Int> = emptyMap()

    companion object {
        private const val TYPE_FOLDER = 0
        private const val TYPE_NEW = 1
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.folder_icon)
        val name: TextView = view.findViewById(R.id.folder_name)
        val count: TextView = view.findViewById(R.id.folder_count)
    }

    fun submit(newFolders: List<BookmarkStore.Folder>, newCounts: Map<Long, Int>) {
        folders = newFolders
        counts = newCounts
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = folders.size + 1

    override fun getItemViewType(position: Int): Int =
        if (position == folders.size) TYPE_NEW else TYPE_FOLDER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.bookmark_folder_tile, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (getItemViewType(position) == TYPE_NEW) {
            holder.icon.setImageResource(R.drawable.ic_folder_new)
            holder.name.text = holder.itemView.context.getString(R.string.new_folder)
            holder.count.text = ""
            holder.itemView.setOnClickListener { onNewFolder() }
            holder.itemView.setOnLongClickListener(null)
            return
        }

        val folder = folders[position]
        holder.icon.setImageResource(R.drawable.ic_folder_mac)
        holder.name.text = folder.name
        val c = counts[folder.id] ?: 0
        holder.count.text = holder.itemView.context.resources
            .getQuantityString(R.plurals.bookmark_items, c, c)
        holder.itemView.setOnClickListener { onFolderClick(folder) }
        holder.itemView.setOnLongClickListener {
            onFolderLongClick(folder)
            true
        }
    }
}
