// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.ui.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * A minimal sparkline (mini line chart) for the home sleep card.
 * Draws a smooth cubic bezier line over 7 data points with a
 * translucent gradient fill underneath — no axes, no labels, no touch.
 */
class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // region style constants
    private val lineColor = Color.parseColor("#2563EB")
    private val lineWidth = 2.5f.dp
    private val dotRadius = 2f.dp
    private val fillTopColor = Color.parseColor("#502563EB")
    private val fillBottomColor = Color.parseColor("#002563EB")

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.STROKE
        strokeWidth = lineWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.FILL
    }
    // endregion

    /** Values between 0 and 1 representing scaled sleep hours. */
    private var values: List<Float> = emptyList()

    /** Set the 7 daily values (raw sleep hours), will be normalized internally. */
    fun setData(data: List<Float>) {
        if (data.isEmpty()) {
            values = emptyList()
            invalidate()
            return
        }
        val max = data.maxOrNull() ?: 1f
        // Normalize to [0.1, 1.0] so even small values have visual presence
        values = if (max > 0f) data.map { (it / max).coerceIn(0f, 1f) * 0.85f + 0.15f } else data.map { 0.15f }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (values.isEmpty() || width <= paddingLeft + paddingRight || height <= paddingTop + paddingBottom) return

        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()
        val count = values.size

        if (count < 2) return

        val stepX = w / (count - 1)
        val baseline = paddingTop + h

        // Build points
        val pts = values.mapIndexed { i, v ->
            val x = paddingLeft + i * stepX
            val y = paddingTop + h * (1f - v)
            floatArrayOf(x, y)
        }

        // ---- Fill path (bezier through points + close to bottom) ----
        val fillPath = Path()
        fillPath.moveTo(pts[0][0], baseline)
        fillPath.lineTo(pts[0][0], pts[0][1])

        for (i in 0 until count - 1) {
            val (x0, y0) = pts[i][0] to pts[i][1]
            val (x1, y1) = pts[i + 1][0] to pts[i + 1][1]
            val cx1 = x0 + stepX * 0.4f
            val cx2 = x1 - stepX * 0.4f
            fillPath.cubicTo(cx1, y0, cx2, y1, x1, y1)
        }

        fillPath.lineTo(pts.last()[0], baseline)
        fillPath.close()

        // Gradient shader for fill
        val gradient = LinearGradient(
            0f, paddingTop.toFloat(), 0f, baseline,
            fillTopColor, fillBottomColor, Shader.TileMode.CLAMP
        )
        fillPaint.shader = gradient
        canvas.drawPath(fillPath, fillPaint)

        // ---- Stroke path (same curve, no bottom close) ----
        val linePath = Path()
        linePath.moveTo(pts[0][0], pts[0][1])
        for (i in 0 until count - 1) {
            val (x0, y0) = pts[i][0] to pts[i][1]
            val (x1, y1) = pts[i + 1][0] to pts[i + 1][1]
            linePath.cubicTo(x0 + stepX * 0.4f, y0, x1 - stepX * 0.4f, y1, x1, y1)
        }
        canvas.drawPath(linePath, linePaint)

        // ---- Dots at data points ----
        for ((x, y) in pts.map { it[0] to it[1] }) {
            canvas.drawCircle(x, y, dotRadius, dotPaint)
        }
    }

    // dp extension
    private val Float.dp: Float get() = this * context.resources.displayMetrics.density
}
