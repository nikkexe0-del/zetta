package com.deniscerri.ytdl.ui.analytics

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.FileUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AnalyticsFragment : Fragment() {

    private lateinit var viewModel: AnalyticsViewModel
    private lateinit var adapter: AnalyticsDownloadAdapter

    private lateinit var pie: PlatformPieView
    private lateinit var legend: LinearLayout
    private lateinit var heatmap: ActivityHeatmapView
    private lateinit var heatmapHint: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var recentHeader: TextView

    private lateinit var statDownloads: View
    private lateinit var statData: View
    private lateinit var statTime: View

    private lateinit var platformCard: View
    private lateinit var heatmapCard: View

    private val dayFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(this)[AnalyticsViewModel::class.java]
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pie = view.findViewById(R.id.platform_pie)
        legend = view.findViewById(R.id.platform_legend)
        heatmap = view.findViewById(R.id.heatmap)
        heatmapHint = view.findViewById(R.id.heatmap_hint)
        recycler = view.findViewById(R.id.analytics_recycler)
        empty = view.findViewById(R.id.analytics_empty)
        recentHeader = view.findViewById(R.id.recent_header)
        statDownloads = view.findViewById(R.id.stat_downloads)
        statData = view.findViewById(R.id.stat_data)
        statTime = view.findViewById(R.id.stat_time)
        platformCard = view.findViewById(R.id.platform_card)
        heatmapCard = view.findViewById(R.id.heatmap_card)

        setStat(statDownloads, "0", getString(R.string.total_downloads))
        setStat(statData, "0 B", getString(R.string.data_consumed))
        setStat(statTime, "0s", getString(R.string.time_spent))

        // zetta analytics uses a fixed dark palette rather than the theme, so the tab reads as one
        // consistent surface regardless of the accent the rest of the app resolves to.
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.void_black))
        val textPrimary = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val textSecondary = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        pie.holeColor = Color.TRANSPARENT
        pie.titleColor = textPrimary
        pie.subtitleColor = textSecondary
        heatmap.rampColor = ContextCompat.getColor(requireContext(), R.color.accent_purple)
        heatmap.emptyColor = Color.parseColor("#1FFFFFFF")

        listOf(statDownloads, statData, statTime).forEach { tile ->
            tile.findViewById<TextView>(R.id.stat_value).setTextColor(textPrimary)
            tile.findViewById<TextView>(R.id.stat_label).setTextColor(textSecondary)
        }
        recentHeader.setTextColor(textPrimary)
        empty.setTextColor(textSecondary)
        heatmapHint.setTextColor(textSecondary)
        heatmap.onCellTapped = { day, count ->
            heatmapHint.text = if (count == 0) {
                dayFormat.format(Date(day))
            } else {
                "${dayFormat.format(Date(day))} · $count"
            }
        }

        adapter = AnalyticsDownloadAdapter(requireContext()) { entry ->
            showDetails(entry)
        }
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { render(it) }
            }
        }
    }

    private fun render(state: AnalyticsViewModel.AnalyticsState) {
        val hasData = state.totalDownloads > 0

        empty.isVisible = !hasData
        platformCard.isVisible = hasData
        heatmapCard.isVisible = hasData
        recentHeader.isVisible = hasData
        recycler.isVisible = hasData

        setStat(statDownloads, state.totalDownloads.toString(), getString(R.string.total_downloads))
        setStat(
            statData,
            if (state.totalBytes > 1) FileUtil.convertFileSize(state.totalBytes) else "0 B",
            getString(R.string.data_consumed)
        )
        setStat(statTime, formatElapsed(state.totalTimeMillis), getString(R.string.time_spent))

        if (!hasData) {
            adapter.submitList(emptyList())
            return
        }

        // ---- pie + legend ----
        val palette = palette()
        val top = state.platforms.take(5)
        val rest = state.platforms.drop(5)
        val slices = ArrayList<PlatformPieView.Slice>()
        top.forEachIndexed { index, p ->
            slices.add(
                PlatformPieView.Slice(p.name, p.count.toFloat(), palette[index % palette.size])
            )
        }
        if (rest.isNotEmpty()) {
            slices.add(
                PlatformPieView.Slice(
                    getString(R.string.others),
                    rest.sumOf { it.count }.toFloat(),
                    palette[top.size % palette.size]
                )
            )
        }
        pie.centerTitle = state.totalDownloads.toString()
        pie.centerSubtitle = getString(R.string.total_downloads).lowercase()
        pie.setSlices(slices)

        legend.removeAllViews()
        slices.forEachIndexed { _, slice ->
            val row = layoutInflater.inflate(R.layout.analytics_legend_row, legend, false)
            val dot = row.findViewById<View>(R.id.legend_dot)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(slice.color)
            }
            dot.background = bg
            row.findViewById<TextView>(R.id.legend_label).text = slice.label
            val pct = if (state.totalDownloads > 0) {
                (slice.value * 100f / state.totalDownloads).toInt()
            } else 0
            row.findViewById<TextView>(R.id.legend_value).text =
                "${slice.value.toInt()}  ($pct%)"
            legend.addView(row)
        }

        // ---- heatmap ----
        heatmap.setData(state.activity, weeksToShow = 26)

        // ---- cards ----
        adapter.submitList(state.entries)
    }

    private fun setStat(container: View, value: String, label: String) {
        container.findViewById<TextView>(R.id.stat_value).text = value
        container.findViewById<TextView>(R.id.stat_label).text = label
    }

    private fun showDetails(entry: AnalyticsViewModel.DownloadEntry) {
        val item = entry.item
        val millis = AnalyticsViewModel.toMillis(item.time)
        val body = buildString {
            appendLine(AnalyticsViewModel.prettyPlatform(item.website))
            appendLine(SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis)))
            if (item.filesize > 0) appendLine(FileUtil.convertFileSize(item.filesize))
            if (item.duration.isNotBlank()) appendLine(item.duration)
            if (entry.elapsedMillis > 0) appendLine(formatElapsed(entry.elapsedMillis))
            appendLine()
            append(item.url)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.title.ifBlank { item.url })
            .setMessage(body)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun formatElapsed(millis: Long): String {
        if (millis <= 0) return "0s"
        val h = TimeUnit.MILLISECONDS.toHours(millis)
        val m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    /** zetta analytics accents: purple, blue, orange, then softened repeats for the long tail. */
    private fun palette(): IntArray {
        val ctx = requireContext()
        return intArrayOf(
            ContextCompat.getColor(ctx, R.color.accent_purple),
            ContextCompat.getColor(ctx, R.color.accent_blue),
            ContextCompat.getColor(ctx, R.color.accent_orange),
            Color.parseColor("#8E5CD9"),
            Color.parseColor("#2B7FC4"),
            Color.parseColor("#C4501A")
        )
    }
}
