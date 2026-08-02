package com.deniscerri.ytdl.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.util.DownloadTimeTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    data class PlatformSlice(
        val name: String,
        val count: Int,
        val bytes: Long
    )

    data class DownloadEntry(
        val item: HistoryItem,
        val elapsedMillis: Long
    )

    data class AnalyticsState(
        val totalDownloads: Int = 0,
        val totalBytes: Long = 0,
        val totalTimeMillis: Long = 0,
        val platforms: List<PlatformSlice> = emptyList(),
        /** day-start-millis -> number of downloads that day */
        val activity: Map<Long, Int> = emptyMap(),
        val entries: List<DownloadEntry> = emptyList()
    )

    private val historyDao = DBManager.getInstance(application).historyDao

    val state: Flow<AnalyticsState> = historyDao.getAllHistory()
        .map { items -> build(items) }
        // build() reads SharedPreferences from disk and sorts the whole history,
        // so it must not run on the main thread.
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AnalyticsState()
        )

    private fun build(items: List<HistoryItem>): AnalyticsState {
        if (items.isEmpty()) return AnalyticsState()

        val elapsedMap = DownloadTimeTracker.getAllElapsed(getApplication())

        val sorted = items.sortedByDescending { it.time }

        val platforms = items
            .groupBy { prettyPlatform(it.website) }
            .map { (name, group) ->
                PlatformSlice(
                    name = name,
                    count = group.size,
                    bytes = group.sumOf { if (it.filesize > 0) it.filesize else 0L }
                )
            }
            .sortedByDescending { it.count }

        val activity = HashMap<Long, Int>()
        items.forEach {
            val day = dayStart(it.time)
            activity[day] = (activity[day] ?: 0) + 1
        }

        val entries = sorted.map {
            DownloadEntry(it, elapsedMap[it.downloadId] ?: 0L)
        }

        return AnalyticsState(
            totalDownloads = items.size,
            totalBytes = items.sumOf { if (it.filesize > 0) it.filesize else 0L },
            totalTimeMillis = entries.sumOf { it.elapsedMillis },
            platforms = platforms,
            activity = activity,
            entries = entries
        )
    }

    companion object {
        /** History stores seconds; normalise to the millis at the start of that local day. */
        fun dayStart(unixSeconds: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = if (unixSeconds > 100_000_000_000L) unixSeconds else unixSeconds * 1000L
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun toMillis(unixSeconds: Long): Long =
            if (unixSeconds > 100_000_000_000L) unixSeconds else unixSeconds * 1000L

        fun prettyPlatform(website: String): String {
            if (website.isBlank()) return "Other"
            var w = website.trim().lowercase()
            w = w.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            w = w.substringBefore('/')
            if (w.isBlank()) return "Other"
            // youtube.com -> youtube, m.youtube.com -> youtube
            val parts = w.split('.').filter { it.isNotBlank() }
            val core = when {
                parts.size >= 2 -> parts[parts.size - 2]
                parts.isNotEmpty() -> parts[0]
                else -> w
            }
            return core.replaceFirstChar { it.uppercase() }
        }
    }
}
