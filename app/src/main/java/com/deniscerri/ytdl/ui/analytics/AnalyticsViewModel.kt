package com.deniscerri.ytdl.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.HistoryItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Read-only aggregation over the existing `history` table.
 * No schema changes, no writes, no interaction with download logic.
 */
class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

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
        uiState = dao.getAllHistory()
            .map { historyList -> buildUiState(historyList) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AnalyticsUiState()
            )
    }

    private fun buildUiState(historyList: List<HistoryItem>): AnalyticsUiState {
        val sorted = historyList.sortedByDescending { it.time }
        val totalDataConsumed = sorted.sumOf { it.filesize }
        val total = sorted.size.coerceAtLeast(1)

        val platformStats = sorted
            .groupBy { normalizeWebsite(it.website) }
            .map { entry -> PlatformStat(entry.key, entry.value.size, (entry.value.size * 100f) / total) }
            .sortedByDescending { it.count }

        val heatmap = sorted.groupingBy { epochSecondsToLocalDate(it.time) }.eachCount()

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
