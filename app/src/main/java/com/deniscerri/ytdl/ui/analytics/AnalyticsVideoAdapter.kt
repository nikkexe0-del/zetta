package com.deniscerri.ytdl.ui.analytics

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.FileUtil
import java.text.SimpleDateFormat
import java.util.Locale

class AnalyticsVideoAdapter(
    private val onItemClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, AnalyticsVideoAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analytics_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.analytics_video_title)
        private val platform: TextView = itemView.findViewById(R.id.analytics_video_platform)
        private val meta: TextView = itemView.findViewById(R.id.analytics_video_meta)
        private val thumbnail: AppCompatImageView = itemView.findViewById(R.id.analytics_video_thumbnail)

        fun bind(item: HistoryItem, onItemClick: (HistoryItem) -> Unit) {
            title.text = item.title
            platform.text = item.website.ifBlank { "-" }

            val dateStr = runCatching {
                SimpleDateFormat(
                    DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"),
                    Locale.getDefault()
                ).format(item.time * 1000L)
            }.getOrDefault("")

            val sizeStr = if (item.filesize > 0) FileUtil.convertFileSize(item.filesize) else null
            meta.text = listOfNotNull(dateStr.ifBlank { null }, sizeStr).joinToString("  \u2022  ")

            thumbnail.loadThumbnail(false, item.thumb)

            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem) = oldItem == newItem
    }
}
