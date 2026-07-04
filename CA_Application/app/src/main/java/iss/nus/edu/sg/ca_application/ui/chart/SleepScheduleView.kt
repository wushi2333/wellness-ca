// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.ui.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/**
 * A 7-day sleep schedule timeline inspired by Apple Health's sleep view.
 *
 * Shows a horizontal bar per day spanning from [sleepTime] to [wakeTime]
 * across a fixed 20:00–12:00 time axis, with a dotted midnight reference
 * line and day labels.
 */
class SleepScheduleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Immutable data class for a single day's sleep slot. */
    data class SleepSlot(
        val dayLabel: String,     // "Mon", "Tue", …
        val sleepTime: String,    // "23:00"
        val wakeTime: String      // "07:00"
    )

    // region style
    private val barGradientStart = Color.parseColor("#7C8DFF")  // soft indigo
    private val barGradientEnd = Color.parseColor("#4A6CF7")
    private val barCornerRadius = 8f.dp
    private val barHeight = 18f.dp
    private val textColor = Color.parseColor("#64748B")
    private val midnightColor = Color.parseColor("#94A3B8")
    private val textSize = 10f.sp
    private val labelMargin = 38f.dp
    // endregion

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor; textSize = this@SleepScheduleView.textSize
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
    }
    private val dayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475569"); textSize = this@SleepScheduleView.textSize
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
    }
    private val midnightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = midnightColor; strokeWidth = 1f.dp
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(6f.dp, 4f.dp), 0f)
    }
    private val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8"); textSize = 9f.sp
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val headerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0"); strokeWidth = 0.5f.dp
        style = Paint.Style.STROKE
    }

    private var slots: List<SleepSlot> = emptyList()

    // Fixed 16-hour time window: 20:00 → 12:00 next day
    private val windowStartMin = 20 * 60          // 1200
    private val windowEndMin = 12 * 60 + 24 * 60  // 2160 (next day noon)
    private val windowSpanMin = windowEndMin - windowStartMin  // 960 minutes

    fun setData(data: List<SleepSlot>) {
        slots = data.toList()
        invalidate()
    }

    /** Parse "HH:mm" into minutes from midnight (can be > 1440 for next-day times). */
    private fun parseTimeToMinutes(time: String): Int {
        val parts = time.split(":")
        if (parts.size < 2) return 0
        val hour = parts[0].toIntOrNull() ?: 0
        val min = parts[1].toIntOrNull() ?: 0
        return hour * 60 + min
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slots.isEmpty()) return

        val w = (width - paddingLeft - paddingRight).toFloat()
        val availableH = (height - paddingTop - paddingBottom).toFloat()

        // Reserve top area for time header
        val headerH = 18f.dp
        val bodyH = availableH - headerH
        val rowH = bodyH / slots.size.coerceAtLeast(1)

        val chartLeft = paddingLeft + labelMargin
        val chartW = w - labelMargin
        val chartTop = paddingTop + headerH

        // ---- Time header labels ----
        val timeLabels = arrayOf("20:00", "00:00", "04:00", "08:00", "12:00")
        val timeMinutes = arrayOf(20 * 60, 0, 4 * 60, 8 * 60, (12 + 24) * 60)
        for (i in timeLabels.indices) {
            val fraction = (timeMinutes[i] - windowStartMin).toFloat() / windowSpanMin
            val x = chartLeft + chartW * fraction
            canvas.drawText(timeLabels[i], x, paddingTop + headerH - 2f.dp, headerTextPaint)
            // Thin vertical tick
            canvas.drawLine(x, paddingTop + headerH - 0.5f.dp, x, paddingTop + headerH + bodyH, headerLinePaint)
        }

        // ---- Draw each day's sleep bar ----
        for (i in slots.indices) {
            val slot = slots[i]
            val rowTop = chartTop + i * rowH
            val rowCenterY = rowTop + rowH / 2f

            val sleepMin = parseTimeToMinutes(slot.sleepTime)
            val wakeMin = parseTimeToMinutes(slot.wakeTime)

            // Normalize times into our window
            // sleepTime (e.g. 23:00 = 1380) is in [1200, 2160], fine
            // wakeTime (e.g. 07:00 = 420) needs +1440 for next day
            val sleepInWindow = sleepMin.toFloat()
            val wakeInWindow = if (wakeMin < windowStartMin) (wakeMin + 1440).toFloat() else wakeMin.toFloat()

            if (wakeInWindow <= sleepInWindow) continue // invalid data, skip

            val barLeft = chartLeft + chartW * ((sleepInWindow - windowStartMin) / windowSpanMin)
            val barRight = chartLeft + chartW * ((wakeInWindow - windowStartMin) / windowSpanMin)
            val barTop = rowCenterY - barHeight / 2f
            val barBottom = rowCenterY + barHeight / 2f
            val rect = RectF(barLeft, barTop, barRight, barBottom)

            // Gradient fill for this sleep bar
            val gradient = LinearGradient(
                barLeft, barTop, barRight, barBottom,
                barGradientStart, barGradientEnd, Shader.TileMode.CLAMP
            )
            barPaint.shader = gradient

            // Rounded rect
            val path = Path()
            val rx = barCornerRadius
            path.addRoundRect(rect, rx, rx, Path.Direction.CW)
            canvas.drawPath(path, barPaint)

            // Day label
            val labelY = rowCenterY + textSize / 3f
            canvas.drawText(slot.dayLabel, chartLeft - 8f.dp, labelY, dayTextPaint)
        }

        // ---- Midnight reference line ----
        val midnightFraction = (0 - windowStartMin).toFloat() / windowSpanMin // 0 min = midnight
        // Actually midnight in minutes from windowStart:
        // windowStartMin = 1200 (20:00), midnight = 0 or 1440
        // relative to window: midnight is at minute 1440 which is 240 minutes from start
        val midnightInWindow = 24 * 60 - windowStartMin  // 1440 - 1200 = 240
        val mx = chartLeft + chartW * (midnightInWindow.toFloat() / windowSpanMin)
        canvas.drawLine(mx, chartTop, mx, chartTop + bodyH, midnightPaint)
    }

    private val Float.dp: Float get() = this * context.resources.displayMetrics.density
    private val Float.sp: Float get() = this * context.resources.displayMetrics.scaledDensity
}
