// Author: Wang Songyu, Liu Yu, Xia Zihang
package iss.nus.edu.sg.ca_application.ui.bottomnav

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.content.res.AppCompatResources
import iss.nus.edu.sg.ca_application.R
import kotlin.math.PI
import kotlin.math.cos

/**
 * Custom bottom navigation bar with bubble-pop and wave transition animations.
 * Three tabs: Home, Add, Chat. The selected tab pops up as a circular bubble
 * above the bar, with an elliptical wave below at the junctions.
 */
class AnimatedBottomNavBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val BAR_HEIGHT_DP = 56f
        private const val BUBBLE_RADIUS_DP = 24f
        private const val BUBBLE_UP_DP = 8f
        private const val WAVE_UP_DP = 6f
        private const val WAVE_HALF_WIDTH_FRAC = 0.12f
        private const val ANIM_DURATION_MS = 380L
    }

    var onTabSelected: ((Int) -> Unit)? = null

    private var selectedIndex = 0
    private var oldSelectedIndex = 0
    private var animProgress = 1f
    private var animator: ValueAnimator? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F2F2F7") }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFFFF") }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 0, 0, 0); setShadowLayer(4f, 0f, 2f, Color.argb(50, 0, 0, 0))
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F2F2F7") }

    private val density = resources.displayMetrics.density
    private val barHeight = BAR_HEIGHT_DP * density
    private val bubbleRadius = BUBBLE_RADIUS_DP * density
    private val bubbleUp = BUBBLE_UP_DP * density
    private val waveUp = WAVE_UP_DP * density
    private val cornerRadius = 16f * density

    private val icons = listOf(
        Pair(R.drawable.ic_home, R.drawable.ic_home_filled),
        Pair(R.drawable.ic_add_circle, R.drawable.ic_add_circle),
        Pair(R.drawable.ic_chat, R.drawable.ic_chat_filled)
    ).map { (outline: Int, filled: Int) ->
        Pair(AppCompatResources.getDrawable(context, outline)!!, AppCompatResources.getDrawable(context, filled)!!)
    }

    private val selectedTint = Color.parseColor("#007AFF")
    private val unselectedTint = Color.parseColor("#8E8E93")
    private val selectedBg = Color.parseColor("#FFFFFF")
    private val unselectedBg = Color.argb(0, 0, 0, 0)

    fun selectTab(index: Int, animate: Boolean = true) {
        if (index == selectedIndex) return
        oldSelectedIndex = selectedIndex
        selectedIndex = index
        if (!animate) { animProgress = 1f; invalidate(); return }
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animProgress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val idx = (event.x / (width / 3f)).toInt().coerceIn(0, 2)
            if (idx != selectedIndex) {
                selectTab(idx)
                onTabSelected?.invoke(idx)
            }
            return true
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val segW = w / 3
        val barTop = h - barHeight

        // Background bar with top rounded corners
        val barRect = RectF(0f, barTop + cornerRadius, w, h)
        val barPath = Path().apply {
            addRoundRect(barRect, floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f), Path.Direction.CW)
        }
        canvas.drawPath(barPath, barPaint)
        // Fill top edge flat
        canvas.drawRect(0f, barTop + cornerRadius, w, barTop + cornerRadius + 2f, barPaint)

        // Wave below selected tab
        drawWave(canvas, w, segW, barTop)

        // Selected bubble shadow and circle
        val bubbleCx = segW * selectedIndex + segW / 2
        val bubbleCy = barTop - bubbleUp + bubbleRadius * 0.2f
        canvas.drawCircle(bubbleCx, bubbleCy + 2f, bubbleRadius, shadowPaint)
        canvas.drawCircle(bubbleCx, bubbleCy, bubbleRadius, bubblePaint)

        // Icons
        for (i in 0..2) {
            val centerX = segW * i + segW / 2
            val isSelected = i == selectedIndex
            val (outline: android.graphics.drawable.Drawable, filled: android.graphics.drawable.Drawable) = icons[i]
            val icon = if (isSelected) filled else outline
            val cy = if (isSelected) bubbleCy else h - barHeight / 2
            drawIconCentered(canvas, icon, centerX, cy, if (isSelected) selectedTint else unselectedTint)
        }
    }

    private fun drawWave(canvas: Canvas, w: Float, segW: Float, barTop: Float) {
        when (selectedIndex) {
            0 -> {
                val rx = segW * WAVE_HALF_WIDTH_FRAC
                val ry = waveUp * 0.8f
                val cx = segW
                val wavePath = Path().apply {
                    moveTo(cx - rx, barTop)
                    arcTo(cx - rx, barTop - ry * 2, cx + rx, barTop, 270f, 90f, false)
                }
                canvas.drawPath(wavePath, wavePaint)
            }
            1 -> {
                val rx = segW * WAVE_HALF_WIDTH_FRAC * 1.3f
                val ry = waveUp
                val leftCx = segW; val rightCx = segW * 2
                val wavePath = Path().apply {
                    moveTo(leftCx - rx, barTop)
                    arcTo(leftCx - rx, barTop - ry * 2, leftCx + rx, barTop, 270f, 90f, false)
                    arcTo(rightCx - rx, barTop - ry * 2, rightCx + rx, barTop, 180f, 90f, false)
                }
                canvas.drawPath(wavePath, wavePaint)
            }
            2 -> {
                val rx = segW * WAVE_HALF_WIDTH_FRAC
                val ry = waveUp * 0.8f
                val cx = segW * 2
                val wavePath = Path().apply {
                    moveTo(cx - rx, barTop)
                    arcTo(cx - rx, barTop - ry * 2, cx + rx, barTop, 180f, 90f, false)
                }
                canvas.drawPath(wavePath, wavePaint)
            }
        }
    }

    private fun drawIconCentered(canvas: Canvas, icon: android.graphics.drawable.Drawable, cx: Float, cy: Float, tint: Int) {
        icon.setTint(tint)
        val hw = icon.intrinsicWidth / 2f
        val hh = icon.intrinsicHeight / 2f
        icon.setBounds((cx - hw).toInt(), (cy - hh).toInt(), (cx + hw).toInt(), (cy + hh).toInt())
        icon.draw(canvas)
    }
}
