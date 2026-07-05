// Author: Wang Songyu, Liu Yu, Xia Zihang
package iss.nus.edu.sg.ca_application.ui.bottomnav

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.content.res.AppCompatResources
import iss.nus.edu.sg.ca_application.R

class AnimatedBottomNavBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val BASE_HEIGHT_DP = 75f
    }

    var onTabSelected: ((Int) -> Unit)? = null

    private var selectedIndex = 0
    private var oldSelectedIndex = 0
    private var animProgress = 1f
    private var animator: ValueAnimator? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B7B9E") }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B7B9E") }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(25, 0, 0, 0)
        setShadowLayer(6f, 0f, 4f, Color.argb(35, 0, 0, 0))
    }

    private val density = resources.displayMetrics.density

    private val icons = listOf(
        Pair(R.drawable.ic_home, R.drawable.ic_home_filled),
        Pair(R.drawable.ic_add_circle, R.drawable.ic_add_circle),
        Pair(R.drawable.ic_chat, R.drawable.ic_chat_filled)
    ).map { (outline: Int, filled: Int) ->
        Pair(AppCompatResources.getDrawable(context, outline)!!, AppCompatResources.getDrawable(context, filled)!!)
    }

    private val selectedIconTint = Color.parseColor("#FFFFFF")
    private val unselectedIconTint = Color.parseColor("#B3E5FC")

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = BASE_HEIGHT_DP * density
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val finalHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight.toInt(), heightSize)
            else -> desiredHeight.toInt()
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), finalHeight)
    }

    fun selectTab(index: Int, animate: Boolean = true) {
        if (index == selectedIndex) return
        oldSelectedIndex = selectedIndex
        selectedIndex = index

        if (!animate) {
            animProgress = 1f
            invalidate()
            return
        }

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420L
            interpolator = OvershootInterpolator(1.3f)
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
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
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val segW = w / 3

        val barTop = h * 0.40f
        val dipDepth = h * 0.50f
        val bubbleRadius = h * 0.28f

        val oldX = segW * oldSelectedIndex + segW / 2f
        val targetX = segW * selectedIndex + segW / 2f
        val currentWaveX = oldX + (targetX - oldX) * animProgress

        val barPath = Path().apply {
            moveTo(0f, barTop)

            val waveWidth = segW
            val waveStart = currentWaveX - waveWidth / 2f
            val waveEnd = currentWaveX + waveWidth / 2f

            lineTo(waveStart, barTop)
            cubicTo(
                waveStart + waveWidth * 0.25f, barTop,
                currentWaveX - waveWidth * 0.25f, barTop + dipDepth,
                currentWaveX, barTop + dipDepth
            )
            cubicTo(
                currentWaveX + waveWidth * 0.25f, barTop + dipDepth,
                waveEnd - waveWidth * 0.25f, barTop,
                waveEnd, barTop
            )

            lineTo(w, barTop)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        canvas.drawPath(barPath, barPaint)

        val normalY = h * 0.70f
        val activeY = h * 0.60f
        val bubbleMinY = h + bubbleRadius

        for (i in 0..2) {
            val cx = segW * i + segW / 2f

            val p = when (i) {
                selectedIndex -> animProgress
                oldSelectedIndex -> 1f - animProgress
                else -> 0f
            }

            val bubbleP = if (i == selectedIndex) p else p.coerceIn(0f, 1f)

            val bubbleCy = bubbleMinY + (activeY - bubbleMinY) * bubbleP
            val iconCy = normalY + (activeY - normalY) * bubbleP

            if (bubbleP > 0f) {
                canvas.drawCircle(cx, bubbleCy, bubbleRadius, shadowPaint)
                canvas.drawCircle(cx, bubbleCy, bubbleRadius, bubblePaint)
            }

            val (outline, filled) = icons[i]
            val icon = if (bubbleP > 0.5f) filled else outline
            val tintColor = interpolateColor(unselectedIconTint, selectedIconTint, bubbleP)

            drawIconCentered(canvas, icon, cx, iconCy, tintColor)
        }
    }

    private fun drawIconCentered(canvas: Canvas, icon: android.graphics.drawable.Drawable, cx: Float, cy: Float, tint: Int) {
        icon.setTint(tint)
        val size = (24f * density).toInt()
        val left = (cx - size / 2f).toInt()
        val top = (cy - size / 2f).toInt()
        icon.setBounds(left, top, left + size, top + size)
        icon.draw(canvas)
    }

    private fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val a = (Color.alpha(color1) + f * (Color.alpha(color2) - Color.alpha(color1))).toInt()
        val r = (Color.red(color1) + f * (Color.red(color2) - Color.red(color1))).toInt()
        val g = (Color.green(color1) + f * (Color.green(color2) - Color.green(color1))).toInt()
        val b = (Color.blue(color1) + f * (Color.blue(color2) - Color.blue(color1))).toInt()
        return Color.argb(a, r, g, b)
    }
}