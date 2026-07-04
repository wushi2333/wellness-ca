// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.ui.chart

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.ViewPortHandler

/**
 * Custom [BarChartRenderer] that draws bars with rounded top corners
 * and a vertical gradient fill instead of flat colours.
 *
 * Usage:
 * ```
 * chart.renderer = RoundedBarChartRenderer(
 *     chart, chart.animator, chart.viewPortHandler,
 *     gradientTop = Color.parseColor("#1B7B9E"),
 *     gradientBottom = Color.parseColor("#93C5FD"),
 *     density = resources.displayMetrics.density
 * )
 * ```
 */
class RoundedBarChartRenderer(
    chart: BarChart,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler,
    private val gradientTop: Int,
    private val gradientBottom: Int,
    private val cornerRadiusPx: Float = 16f,
    private val density: Float = 2.625f
) : BarChartRenderer(chart, animator, viewPortHandler) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun drawDataSet(c: Canvas, dataSet: IBarDataSet, index: Int) {
        val trans = mChart.getTransformer(dataSet.axisDependency)

        mBarBorderPaint.color = dataSet.barBorderColor
        mBarBorderPaint.strokeWidth =
            com.github.mikephil.charting.utils.Utils.convertDpToPixel(dataSet.barBorderWidth)
        val drawBorder = dataSet.barBorderWidth > 0f

        val phaseX = mAnimator.phaseX
        val phaseY = mAnimator.phaseY

        val buffer = mBarBuffers[index]
        buffer.setPhases(phaseX, phaseY)
        buffer.setDataSet(index)
        buffer.setBarWidth(mChart.barData.barWidth)
        buffer.setInverted(mChart.isInverted(dataSet.axisDependency))
        buffer.feed(dataSet)

        trans.pointValuesToPixel(buffer.buffer)

        var j = 0
        while (j < buffer.size()) {
            if (!mViewPortHandler.isInBoundsLeft(buffer.buffer[j + 2])) {
                j += 4
                continue
            }
            if (!mViewPortHandler.isInBoundsRight(buffer.buffer[j])) break

            val left = buffer.buffer[j]
            val top = buffer.buffer[j + 1]
            val right = buffer.buffer[j + 2]
            val bottom = buffer.buffer[j + 3]

            // Create vertical gradient for this bar
            val gradient = LinearGradient(
                left, top, left, bottom,
                gradientTop, gradientBottom,
                Shader.TileMode.CLAMP
            )
            barPaint.shader = gradient

            // Draw rounded-top rect: top corners rounded, bottom corners square
            val radii = floatArrayOf(
                cornerRadiusPx, cornerRadiusPx,  // top-left
                cornerRadiusPx, cornerRadiusPx,  // top-right
                0f, 0f,                          // bottom-right
                0f, 0f                           // bottom-left
            )
            path.reset()
            path.addRoundRect(left, top, right, bottom, radii, Path.Direction.CW)
            c.drawPath(path, barPaint)

            if (drawBorder) {
                c.drawPath(path, mBarBorderPaint)
            }

            j += 4
        }
    }

    /**
     * Override to draw values above bars with a small gap rather than inside.
     */
    override fun drawValues(c: Canvas) {
        val dataSets = mChart.barData.dataSets
        val valueOffsetPlus: Float = 6f * density
        var dataSetIndex = 0

        for (i in dataSets.indices) {
            val dataSet = dataSets[i] as? IBarDataSet ?: continue
            if (!shouldDrawValues(dataSet) || dataSet.entryCount < 1) continue

            applyValueTextStyle(dataSet)

            // Copy the value paint style for our custom positioning
            valuePaint.set(mValuePaint)

            val buffer = mBarBuffers[dataSetIndex]
            val formatter = dataSet.valueFormatter
            val valueTextHeight = com.github.mikephil.charting.utils.Utils.calcTextHeight(
                mValuePaint, "8"
            )

            var j = 0
            while (j < buffer.size()) {
                if (!mViewPortHandler.isInBoundsRight(buffer.buffer[j])) break

                if (!mViewPortHandler.isInBoundsLeft(buffer.buffer[j + 2])
                    || !mViewPortHandler.isInBoundsY(buffer.buffer[j + 1])
                ) {
                    j += 4
                    continue
                }

                val entry = dataSet.getEntryForIndex(j / 4) as? BarEntry ?: run { j += 4; continue }
                val valStr = formatter.getFormattedValue(entry.y, entry, i, mViewPortHandler)

                val x = buffer.buffer[j] + (buffer.buffer[j + 2] - buffer.buffer[j]) / 2f
                val y = if (entry.y >= 0f) {
                    // Position above the bar top
                    buffer.buffer[j + 1] - valueOffsetPlus
                } else {
                    buffer.buffer[j + 3] + valueTextHeight + valueOffsetPlus
                }

                // Use MPPointF for text anchor (center-top)
                com.github.mikephil.charting.utils.Utils.drawXAxisValue(
                    c, valStr, x, y, mValuePaint,
                    MPPointF.getInstance(x, y), 0f
                )

                j += 4
            }
            dataSetIndex++
        }
    }
}
