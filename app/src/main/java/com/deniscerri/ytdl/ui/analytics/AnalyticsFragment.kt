package com.deniscerri.ytdl.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.FileUtil
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import kotlinx.coroutines.launch

class AnalyticsFragment : Fragment() {

    private lateinit var viewModel: AnalyticsViewModel
    private lateinit var videoAdapter: AnalyticsVideoAdapter

    private val platformColors = intArrayOf(
        Color.parseColor("#EF5350"),
        Color.parseColor("#42A5F5"),
        Color.parseColor("#FFCA28"),
        Color.parseColor("#66BB6A"),
        Color.parseColor("#AB47BC"),
        Color.parseColor("#26A69A"),
        Color.parseColor("#FF7043"),
        Color.parseColor("#8D6E63")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[AnalyticsViewModel::class.java]

        val totalDownloads = view.findViewById<TextView>(R.id.analytics_total_downloads)
        val totalData = view.findViewById<TextView>(R.id.analytics_total_data)
        val pieChart = view.findViewById<PieChart>(R.id.analytics_pie_chart)
        val heatmap = view.findViewById<HeatmapView>(R.id.analytics_heatmap)
        val emptyState = view.findViewById<TextView>(R.id.analytics_empty_state)
        val recyclerView = view.findViewById<RecyclerView>(R.id.analytics_video_recycler)
        val glassCard = view.findViewById<com.deniscerri.ytdl.ui.components.LiquidGlassCardView>(R.id.analytics_summary_glass_card)

        setupPieChart(pieChart)

        videoAdapter = AnalyticsVideoAdapter { /* room to hook up a details view later */ }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = videoAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.loading) return@collect

                    totalDownloads.text = state.totalDownloads.toString()
                    totalData.text = if (state.totalDataConsumed > 0)
                        FileUtil.convertFileSize(state.totalDataConsumed) else "0 B"

                    updatePieChart(pieChart, state.platformStats)
                    heatmap.setData(state.heatmap, state.maxHeatmapCount)

                    videoAdapter.submitList(state.recentItems)
                    emptyState.visibility = if (state.recentItems.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility = if (state.recentItems.isEmpty()) View.GONE else View.VISIBLE

                    glassCard.post { glassCard.refreshGlass() }
                }
            }
        }
    }

    private fun setupPieChart(pieChart: PieChart) {
        pieChart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            setUsePercentValues(true)
            setEntryLabelTextSize(11f)
            setEntryLabelColor(Color.WHITE)
            setHoleColor(Color.TRANSPARENT)
            holeRadius = 45f
            transparentCircleRadius = 48f
            setNoDataText(getString(R.string.no_data_to_display))
        }
    }

    private fun updatePieChart(pieChart: PieChart, stats: List<AnalyticsViewModel.PlatformStat>) {
        if (stats.isEmpty()) {
            pieChart.clear()
            pieChart.invalidate()
            return
        }

        val entries = stats.map { PieEntry(it.count.toFloat(), it.website) }
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = platformColors.toList()
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 12f
        dataSet.sliceSpace = 2f

        val pieData = PieData(dataSet)
        pieData.setValueFormatter(PercentFormatter())

        pieChart.data = pieData
        pieChart.invalidate()
    }
}
