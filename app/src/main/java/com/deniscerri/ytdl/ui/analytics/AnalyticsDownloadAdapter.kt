package com.deniscerri.ytdl.ui.analytics

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.FileUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AnalyticsDownloadAdapter(
    private val context: Context,
    private val onItemClick: (AnalyticsViewModel.DownloadEntry) -> Unit
) : ListAdapter<AnalyticsViewModel.DownloadEntry, AnalyticsDownloadAdapter.ItemHolder>(DIFF) {

    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.item_title)
        val author: TextView = view.findViewById(R.id.item_author)
        val meta: TextView = view.findViewById(R.id.item_meta)
        val thumbnail: ImageView = view.findViewById(R.id.item_thumbnail)
        val duration: TextView = view.findViewById(R.id.item_duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.analytics_download_card, parent, false)
        return ItemHolder(view)
    }

    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        val entry = getItem(position) ?: return
        val item = entry.item

        holder.title.text = item.title.ifBlank { item.url }
        holder.author.text = item.author.ifBlank {
            AnalyticsViewModel.prettyPlatform(item.website)
        }

        val millis = AnalyticsViewModel.toMillis(item.time)
        val date = Date(millis)

        val parts = mutableListOf<String>()
        parts.add(AnalyticsViewModel.prettyPlatform(item.website))
        if (item.filesize > 0) parts.add(FileUtil.convertFileSize(item.filesize))
        parts.add("${dateFormat.format(date)} · ${timeFormat.format(date)}")
        if (entry.elapsedMillis > 0) parts.add("took ${formatElapsed(entry.elapsedMillis)}")

        holder.meta.text = parts.joinToString("  •  ")

        holder.duration.isVisible = item.duration.isNotBlank()
        holder.duration.text = item.duration

        holder.thumbnail.loadThumbnail(false, item.thumb)

        holder.itemView.setOnClickListener { onItemClick(entry) }
    }

    private fun formatElapsed(millis: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(millis)
        val m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    companion object {
        private val DIFF =
            object : DiffUtil.ItemCallback<AnalyticsViewModel.DownloadEntry>() {
                override fun areItemsTheSame(
                    oldItem: AnalyticsViewModel.DownloadEntry,
                    newItem: AnalyticsViewModel.DownloadEntry
                ) = oldItem.item.id == newItem.item.id

                override fun areContentsTheSame(
                    oldItem: AnalyticsViewModel.DownloadEntry,
                    newItem: AnalyticsViewModel.DownloadEntry
                ) = oldItem == newItem
            }
    }
}
