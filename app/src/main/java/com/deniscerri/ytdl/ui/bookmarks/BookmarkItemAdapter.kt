package com.deniscerri.ytdl.ui.bookmarks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.BookmarkStore
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookmarkItemAdapter(
    private val onDownload: (BookmarkStore.Item) -> Unit,
    private val onRemove: (BookmarkStore.Item) -> Unit,
    private val onOpen: (BookmarkStore.Item) -> Unit
) : RecyclerView.Adapter<BookmarkItemAdapter.Holder>() {

    private var items: List<BookmarkStore.Item> = emptyList()
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.bookmark_thumbnail)
        val title: TextView = view.findViewById(R.id.bookmark_title)
        val meta: TextView = view.findViewById(R.id.bookmark_meta)
        val download: MaterialButton = view.findViewById(R.id.bookmark_download)
        val remove: MaterialButton = view.findViewById(R.id.bookmark_remove)
    }

    fun submit(newItems: List<BookmarkStore.Item>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.bookmark_item_card, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.title.ifBlank { item.url }

        val parts = mutableListOf<String>()
        if (item.platform.isNotBlank()) parts.add(item.platform)
        if (item.author.isNotBlank()) parts.add(item.author)
        parts.add(dateFormat.format(Date(item.addedAt)))
        holder.meta.text = parts.joinToString("  •  ")

        holder.thumb.loadThumbnail(false, item.thumb)

        holder.download.setOnClickListener { onDownload(item) }
        holder.remove.setOnClickListener { onRemove(item) }
        holder.itemView.setOnClickListener { onOpen(item) }
    }
}
