// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.ui.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/**
 * A row of 7 miniature rounded bars showing daily exercise duration.
 * Each bar uses a vertical gradient fill; inactive days render as
 * a thin outline so the week always reads as a complete set.
 */
class MiniBarRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // region style
    private val barCornerRadius = 6f.dp
    private val barGradientTop = Color.parseColor("#34C759")
    private val barGradientBottom = Color.parseColor("#059669")
    private val emptyBarColor = Color.parseColor("#D1D5DB")
    private val emptyBarHeight = 2f.dp
    // endregion

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = emptyBarColor
        style = Paint.Style.FILL
    }

    /** Raw exercise durations (minutes). Maximum is used to scale bars. */
    private var values: List<Int> = emptyList()
    private var maxValue: Float = 60f

    fun setData(data: List<Int>) {
        values = data.toList()
        maxValue = (data.maxOrNull() ?: 60).coerceAtLeast(1).toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()

        val barCount = values.size.coerceAtMost(7)
        val spacing = 3f.dp
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = ((w - totalSpacing) / barCount).coerceAtMost(18f.dp)

        // Center the row horizontally if bars are narrower than available width
        val totalWidth = barWidth * barCount + totalSpacing
        val startX = paddingLeft + (w - totalWidth) / 2f

        for (i in 0 until barCount) {
            val value = values.getOrElse(i) { 0 }
            val ratio = (value / maxValue).coerceIn(0f, 1f)

            val left = startX + i * (barWidth + spacing)
            val right = left + barWidth

            if (ratio <= 0f || value <= 0) {
                // Empty / zero day: thin line at the bottom
                val bottom = paddingTop + h
                val top = bottom - emptyBarHeight
                canvas.drawRoundRect(left, top, right, bottom, 1f.dp, 1f.dp, emptyPaint)
            } else {
                val barHeight = h * ratio
                val top = paddingTop + h - barHeight
                val bottom = paddingTop + h
                val rect = RectF(left, top, right, bottom)

                // Create vertical gradient for this bar
                val gradient = LinearGradient(
                    left, top, left, bottom,
                    barGradientTop, barGradientBottom, Shader.TileMode.CLAMP
                )
                barPaint.shader = gradient

                // Draw rounded rect with only top corners rounded
                // Use Path with 8-radii array: [tl-x, tl-y, tr-x, tr-y, br-x, br-y, bl-x, bl-y]
                val rx = barCornerRadius
                val path = android.graphics.Path()
                val radii = floatArrayOf(rx, rx, rx, rx, 0f, 0f, 0f, 0f)
                path.addRoundRect(rect, radii, android.graphics.Path.Direction.CW)
                canvas.drawPath(path, barPaint)
            }
        }
    }

    private val Float.dp: Float get() = this * context.resources.displayMetrics.density
}
