package com.deniscerri.ytdl.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * All analytics shown here are derived purely from the existing `history` table
 * (read-only aggregation). No database schema changes were needed.
 */
class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HistoryRepository

    data class PlatformStat(
        val website: String,
        val count: Int,
        val percent: Float
    )

    data class AnalyticsUiState(
        val loading: Boolean = true,
        val totalDownloads: Int = 0,
        val totalDataConsumed: Long = 0L,
        val platformStats: List<PlatformStat> = emptyList(),
        val heatmap: Map<LocalDate, Int> = emptyMap(),
        val maxHeatmapCount: Int = 0,
        val recentItems: List<HistoryItem> = emptyList()
    )

    val uiState: StateFlow<AnalyticsUiState>

    init {
        val dao = DBManager.getInstance(application).historyDao
        repository = HistoryRepository(dao)

        uiState = dao.getAllHistory()
            .map { historyList -> buildUiState(historyList) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AnalyticsUiState()
            )
    }

    private fun buildUiState(historyList: List<HistoryItem>): AnalyticsUiState {
        // most recent first
        val sorted = historyList.sortedByDescending { it.time }

        val totalDataConsumed = sorted.sumOf { it.filesize }

        val platformStats = sorted
            .groupBy { normalizeWebsite(it.website) }
            .map { (website, items) -> website to items.size }
            .sortedByDescending { it.second }
            .let { grouped ->
                val total = sorted.size.coerceAtLeast(1)
                grouped.map { (website, count) ->
                    PlatformStat(
                        website = website,
                        count = count,
                        percent = (count * 100f) / total
                    )
                }
            }

        val heatmap = sorted
            .groupingBy { epochSecondsToLocalDate(it.time) }
            .eachCount()

        return AnalyticsUiState(
            loading = false,
            totalDownloads = sorted.size,
            totalDataConsumed = totalDataConsumed,
            platformStats = platformStats,
            heatmap = heatmap,
            maxHeatmapCount = heatmap.values.maxOrNull() ?: 0,
            recentItems = sorted
        )
    }

    private fun epochSecondsToLocalDate(epochSeconds: Long): LocalDate {
        // HistoryItem.time is stored in epoch seconds elsewhere in the app
        // (e.g. multiplied by 1000L when formatted for display).
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun normalizeWebsite(website: String): String {
        if (website.isBlank()) return "Other"
        return website
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")
            .ifBlank { "Other" }
    }
}
