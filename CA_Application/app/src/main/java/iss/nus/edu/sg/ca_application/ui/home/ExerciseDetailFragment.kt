// Author: Wang Songyu
package iss.nus.edu.sg.ca_application.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import android.app.AlertDialog
import android.widget.Toast
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.applyTopInset
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.model.DailyWellness
import iss.nus.edu.sg.ca_application.network.ApiClient
import iss.nus.edu.sg.ca_application.network.ApiException
import iss.nus.edu.sg.ca_application.network.CacheManager
import iss.nus.edu.sg.ca_application.ui.chart.RoundedBarChartRenderer
import iss.nus.edu.sg.ca_application.ui.chart.WeekUtils
import iss.nus.edu.sg.ca_application.util.ExerciseTypeMap
import java.util.Locale

class ExerciseDetailFragment : Fragment() {

    // Week navigation state
    private var allDailies: List<DailyWellness> = emptyList()
    private var availableWeeks: List<java.time.LocalDate> = emptyList()
    private var currentWeekIdx = 0
    private var isPieToday = true  // true = Today, false = This Week
    private var weekActivityMap: Map<String, Int> = emptyMap()  // cached for week toggle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_exercise_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.topBar).applyTopInset()
        view.findViewById<View>(R.id.btnExBack).setOnClickListener {
            (activity as? iss.nus.edu.sg.ca_application.MainActivity)?.hideHomeDetail()
        }
        view.findViewById<View>(R.id.btnExEdit).setOnClickListener {
            val frag = iss.nus.edu.sg.ca_application.ui.edit.RecordManageFragment()
            frag.arguments = Bundle().apply { putString("type", "exercise") }
            (activity as? iss.nus.edu.sg.ca_application.MainActivity)?.showHomeDetail(frag)
        }

        // Week navigation
        view.findViewById<ImageView>(R.id.btnExPrevWeek).setOnClickListener { navigateWeek(view, +1) }
        view.findViewById<ImageView>(R.id.btnExNextWeek).setOnClickListener { navigateWeek(view, -1) }

        // Pie chart toggle: Today / This Week
        val tvToday = view.findViewById<TextView>(R.id.tvPieToggleToday)
        val tvWeek = view.findViewById<TextView>(R.id.tvPieToggleWeek)
        tvToday.setOnClickListener { isPieToday = true; updatePieToggle(view); updatePieChart(view) }
        tvWeek.setOnClickListener { isPieToday = false; updatePieToggle(view); updatePieChart(view) }

        lastDataFingerprint = "" // reset — view may have been recreated
        loadData(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadData(it) }
    }

    private fun loadData(view: View) {
        val token = TokenManager.getToken(requireContext())
        if (token.isEmpty()) return
        val cacheKey = "records"

        // Show cached data immediately; fall back to in-memory data if cache miss
        val cached = CacheManager.get<Pair<List<DailyWellness>, Boolean>>(cacheKey)
        if (cached != null && cached.first.isNotEmpty()) {
            renderData(view, cached.first)
        } else if (allDailies.isNotEmpty()) {
            renderData(view, allDailies)
        }

        Thread {
            try {
                val (dailies, _) = ApiClient.getDailyRecords(token, 0, 90)
                CacheManager.put(cacheKey, Pair(dailies, true))
                if (isAdded) activity?.runOnUiThread { renderData(view, dailies) }
            } catch (e: ApiException) {
                if (isAdded) activity?.runOnUiThread { iss.nus.edu.sg.ca_application.network.ApiErrorHandler.handle(requireActivity(), e) }
            } catch (_: Exception) {}
        }.start()
    }

    private var lastDataFingerprint = ""

    private fun renderData(view: View, dailies: List<DailyWellness>) {
        val fp = dailies.joinToString { "${it.recordDate}:${it.exercises.joinToString { "${it.exerciseActivity}:${it.exerciseDuration}" }}" }
        if (fp == lastDataFingerprint && allDailies.isNotEmpty()) return
        lastDataFingerprint = fp
        allDailies = dailies
        availableWeeks = WeekUtils.availableWeeks(dailies)
        val currentMonday = WeekUtils.currentWeekMonday()
        val idx = if (availableWeeks.contains(currentMonday)) availableWeeks.indexOf(currentMonday) else 0
        showWeek(view, idx)
    }

    private fun navigateWeek(view: View, delta: Int) {
        val newIdx = currentWeekIdx + delta
        if (newIdx in availableWeeks.indices) {
            showWeek(view, newIdx)
        }
    }

    private fun showWeek(view: View, weekIdx: Int) {
        if (availableWeeks.isEmpty() || weekIdx !in availableWeeks.indices) return
        currentWeekIdx = weekIdx
        val monday = availableWeeks[weekIdx]

        // Update navigation UI
        view.findViewById<TextView>(R.id.tvExWeekLabel).text = WeekUtils.weekLabel(monday)
        val prevEnabled = weekIdx < availableWeeks.size - 1
        val nextEnabled = weekIdx > 0
        view.findViewById<ImageView>(R.id.btnExPrevWeek).apply {
            isEnabled = prevEnabled; alpha = if (prevEnabled) 0.7f else 0.3f
        }
        view.findViewById<ImageView>(R.id.btnExNextWeek).apply {
            isEnabled = nextEnabled; alpha = if (nextEnabled) 0.7f else 0.3f
        }

        // Build 7-day arrays (Mon–Sun), null = no record
        val exValues = WeekUtils.buildWeekArray(monday, allDailies) { d ->
            d?.exercises?.sumOf { it.exerciseDuration } ?: 0
        }
        // Activity breakdown: iterate exercises within this week
        val activityMap = mutableMapOf<String, Int>()
        for (d in allDailies) {
            try {
                val date = java.time.LocalDate.parse(d.recordDate)
                if (date >= monday && date <= monday.plusDays(6)) {
                    for (e in d.exercises) {
                        val displayName = ExerciseTypeMap.toDisplay(requireContext(), e.exerciseActivity)
                        activityMap[displayName] = (activityMap[displayName] ?: 0) + e.exerciseDuration
                    }
                }
            } catch (_: Exception) {}
        }

        // ---- Bar chart entries ----
        val entries = exValues.mapIndexed { i, v ->
            BarEntry(i.toFloat(), v?.toFloat() ?: 0f)
        }

        val chart = view.findViewById<BarChart>(R.id.exerciseBarChart)
        chart.setNoDataText(getString(R.string.no_data_available))
        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#34C759")
            valueTextSize = 11f
            valueTextColor = Color.parseColor("#475569")
            setDrawValues(true)
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value <= 0f) "" else String.format(Locale.US, "%.0f", value)
                }
            }
        }

        val density = resources.displayMetrics.density
        chart.renderer = RoundedBarChartRenderer(
            chart, chart.animator, chart.viewPortHandler,
            gradientTop = Color.parseColor("#34C759"),
            gradientBottom = Color.parseColor("#86EFAC"),
            cornerRadiusPx = 10f * density,
            density = density
        )

        val targetLine = LimitLine(30f, getString(R.string.chart_target_exercise)).apply {
            lineWidth = 1.5f
            lineColor = Color.parseColor("#F59E0B")
            textColor = Color.parseColor("#F59E0B")
            textSize = 11f
            enableDashedLine(8f * density, 5f * density, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }

        chart.data = BarData(dataSet).apply { barWidth = 0.6f }
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.setTouchEnabled(false)
        chart.isHighlightPerTapEnabled = false
        chart.setDrawBarShadow(false)
        chart.setDrawGridBackground(false)

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            textColor = Color.parseColor("#64748B")
            textSize = 11f
            granularity = 1f
            valueFormatter = IndexAxisValueFormatter(WeekUtils.dayLabels)
        }

        val validValues = exValues.filterNotNull()
        val maxVal = if (validValues.isNotEmpty()) (validValues.max() + 15).coerceAtLeast(60).toFloat() else 60f
        chart.axisLeft.apply {
            setDrawGridLines(true)
            gridColor = Color.parseColor("#E2E8F0")
            gridLineWidth = 0.5f
            textColor = Color.parseColor("#94A3B8")
            textSize = 10f
            axisMinimum = 0f
            axisMaximum = maxVal
            setDrawAxisLine(false)
            removeAllLimitLines()
            addLimitLine(targetLine)
        }
        chart.axisRight.isEnabled = false

        chart.animateY(800, com.github.mikephil.charting.animation.Easing.EaseOutQuart)
        chart.invalidate()

        // ---- Stat cards ----
        val avg = if (validValues.isNotEmpty()) validValues.average() else 0.0
        val activeDays = exValues.count { it != null && it > 0 }
        view.findViewById<TextView>(R.id.tvDetailExAvg).text = String.format(Locale.US, "%.0f", avg)
        view.findViewById<TextView>(R.id.tvDetailExActive).text = "$activeDays " + getString(R.string.chart_unit_days)

        // Cache week activity map and build pie chart
        weekActivityMap = activityMap
        updatePieToggle(view)
        updatePieChart(view)
    }

    private fun updatePieToggle(view: View) {
        val tvToday = view.findViewById<TextView>(R.id.tvPieToggleToday)
        val tvWeek = view.findViewById<TextView>(R.id.tvPieToggleWeek)
        if (isPieToday) {
            tvToday.setTextColor(android.graphics.Color.WHITE)
            tvToday.setBackgroundResource(iss.nus.edu.sg.ca_application.R.drawable.bg_btn_primary)
            tvWeek.setTextColor(android.graphics.Color.parseColor("#64748B"))
            tvWeek.setBackgroundResource(iss.nus.edu.sg.ca_application.R.drawable.bg_input_field)
        } else {
            tvWeek.setTextColor(android.graphics.Color.WHITE)
            tvWeek.setBackgroundResource(iss.nus.edu.sg.ca_application.R.drawable.bg_btn_primary)
            tvToday.setTextColor(android.graphics.Color.parseColor("#64748B"))
            tvToday.setBackgroundResource(iss.nus.edu.sg.ca_application.R.drawable.bg_input_field)
        }
    }

    private fun updatePieChart(view: View) {
        val pieChart = view.findViewById<PieChart>(R.id.activityPieChart)
        pieChart.setNoDataText(getString(R.string.no_data_available))
        val activityMap: Map<String, Int> = if (isPieToday) {
            // Today's exercises only
            val today = java.time.LocalDate.now().toString()
            val todayDaily = allDailies.firstOrNull { it.recordDate == today }
            if (todayDaily != null) {
                todayDaily.exercises.groupBy { ExerciseTypeMap.toDisplay(requireContext(), it.exerciseActivity) }
                    .mapValues { (_, list) -> list.sumOf { it.exerciseDuration } }
            } else emptyMap()
        } else {
            weekActivityMap
        }

        if (activityMap.isNotEmpty()) {
            val pieEntries = activityMap.map { (activity, minutes) ->
                PieEntry(minutes.toFloat(), activity)
            }
            val pieColors = listOf(
                android.graphics.Color.parseColor("#FF6B6B"), android.graphics.Color.parseColor("#4ECDC4"),
                android.graphics.Color.parseColor("#FFD93D"), android.graphics.Color.parseColor("#6C5CE7"),
                android.graphics.Color.parseColor("#A8E6CF"), android.graphics.Color.parseColor("#FF8A5C"),
                android.graphics.Color.parseColor("#B0BEC5"), android.graphics.Color.parseColor("#81D4FA")
            )
            pieChart.data = PieData(PieDataSet(pieEntries, "").apply {
                colors = pieColors; valueTextSize = 11f; sliceSpace = 3f; selectionShift = 5f
                valueTextColor = android.graphics.Color.parseColor("#475569")
                valueFormatter = PercentFormatter(pieChart)
            })
            pieChart.description.isEnabled = false
            pieChart.legend.apply {
                textColor = android.graphics.Color.parseColor("#64748B"); textSize = 11f; isWordWrapEnabled = true
            }
            pieChart.setUsePercentValues(true)
            pieChart.setTouchEnabled(false)
            pieChart.setRotationEnabled(false)
            pieChart.holeRadius = 55f; pieChart.transparentCircleRadius = 60f; pieChart.setDrawEntryLabels(false)
            pieChart.centerText = "${activityMap.values.sum()} " + getString(R.string.chart_unit_min)
            pieChart.setCenterTextColor(android.graphics.Color.parseColor("#1E293B"))
            pieChart.setCenterTextSize(14f)
        } else {
            pieChart.clear()
            pieChart.centerText = getString(iss.nus.edu.sg.ca_application.R.string.chart_no_activity)
            pieChart.setCenterTextColor(android.graphics.Color.parseColor("#94A3B8"))
            pieChart.setCenterTextSize(13f)
        }
        pieChart.animateY(600, com.github.mikephil.charting.animation.Easing.EaseOutQuart)
        pieChart.invalidate()
    }
}
