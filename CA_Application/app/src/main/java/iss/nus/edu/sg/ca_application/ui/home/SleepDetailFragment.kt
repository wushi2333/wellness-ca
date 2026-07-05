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
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
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
import java.util.Locale

class SleepDetailFragment : Fragment() {

    // Week navigation state
    private var allDailies: List<DailyWellness> = emptyList()
    private var availableWeeks: List<java.time.LocalDate> = emptyList()
    private var currentWeekIdx = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_sleep_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.topBar).applyTopInset()
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            (activity as? iss.nus.edu.sg.ca_application.MainActivity)?.hideHomeDetail()
        }
        view.findViewById<View>(R.id.btnEdit).setOnClickListener {
            val frag = iss.nus.edu.sg.ca_application.ui.edit.RecordManageFragment()
            frag.arguments = Bundle().apply { putString("type", "sleep") }
            (activity as? iss.nus.edu.sg.ca_application.MainActivity)?.showHomeDetail(frag)
        }

        // Week navigation
        view.findViewById<ImageView>(R.id.btnPrevWeek).setOnClickListener { navigateWeek(view, +1) }
        view.findViewById<ImageView>(R.id.btnNextWeek).setOnClickListener { navigateWeek(view, -1) }

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
        val fp = dailies.joinToString { "${it.recordDate}:${it.sleep?.sleepHours}:${it.sleep?.moodScore}" }
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
        view.findViewById<TextView>(R.id.tvWeekLabel).text = WeekUtils.weekLabel(monday)
        val prevEnabled = weekIdx < availableWeeks.size - 1
        val nextEnabled = weekIdx > 0
        view.findViewById<ImageView>(R.id.btnPrevWeek).apply {
            isEnabled = prevEnabled; alpha = if (prevEnabled) 0.7f else 0.3f
        }
        view.findViewById<ImageView>(R.id.btnNextWeek).apply {
            isEnabled = nextEnabled; alpha = if (nextEnabled) 0.7f else 0.3f
        }

        // Build 7-day arrays (Mon–Sun), null = no record
        val sleepValues = WeekUtils.buildWeekArray(monday, allDailies) { it?.sleep?.sleepHours }
        val moodValues = WeekUtils.buildWeekArray(monday, allDailies) { it?.sleep?.moodScore }

        // ---- Bar chart entries ----
        val entries = sleepValues.mapIndexed { i, v ->
            BarEntry(i.toFloat(), v?.toFloat() ?: 0f)
        }

        val chart = view.findViewById<BarChart>(R.id.sleepBarChart)
        chart.setNoDataText(getString(R.string.no_data_available))
        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#1B7B9E")
            valueTextSize = 11f
            valueTextColor = Color.parseColor("#475569")
            setDrawValues(true)
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value <= 0f) "" else String.format(Locale.US, "%.1f", value)
                }
            }
        }

        val density = resources.displayMetrics.density
        chart.renderer = RoundedBarChartRenderer(
            chart, chart.animator, chart.viewPortHandler,
            gradientTop = Color.parseColor("#1B7B9E"),
            gradientBottom = Color.parseColor("#93C5FD"),
            cornerRadiusPx = 10f * density,
            density = density
        )

        val targetLine = LimitLine(7f, getString(R.string.chart_target_sleep)).apply {
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

        val validValues = sleepValues.filterNotNull()
        val maxVal = if (validValues.isNotEmpty()) (validValues.max() + 2.0).coerceAtLeast(10.0).toFloat() else 10f
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
        val best = validValues.maxOrNull() ?: 0.0
        val validMoods = moodValues.filterNotNull()
        val avgMood = if (validMoods.isNotEmpty()) validMoods.average() else 0.0
        val moodEmoji = when { avgMood >= 4.0 -> "😊"; avgMood >= 3.0 -> "😐"; avgMood > 0 -> "😟"; else -> "—" }
        view.findViewById<TextView>(R.id.tvDetailSleepAvg).text = String.format(Locale.US, "%.1fh", avg)
        view.findViewById<TextView>(R.id.tvDetailSleepBest).text = String.format(Locale.US, "%.1fh", best)
        view.findViewById<TextView>(R.id.tvDetailSleepMood).text = "$moodEmoji ${String.format("%.1f", avgMood)}"
    }
}
