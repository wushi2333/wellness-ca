package iss.nus.edu.sg.ca_application.ui.home

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class ParticleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 0=line, 1=dot, 2=arc, 3=triangle, 4=square
    private data class Particle(
        var x: Float,
        var y: Float,
        var alpha: Float,
        var speedY: Float,
        var speedX: Float,
        val size: Float,
        var rotation: Float,
        val rotSpeed: Float,
        val shape: Int,
        val geomRadius: Float,
        val strokeWidth: Float     // pre-computed, stable across frames
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val particleColor = Color.parseColor("#B8BCC4")
    private val density = resources.displayMetrics.density
    private var running = false
    private val maxParticles = 30
    private val maxGeomShapes = 12
    private var spawnTimer = 0f
    private val rectF = RectF()      // reused

    // Reusable triangle path (scaled per particle)
    private val triPath = Path()

    private val currentGeomCount: Int
        get() = particles.count { it.shape in setOf(1, 3, 4) }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        stopParticles()
        super.onDetachedFromWindow()
    }

    fun startParticles() {
        if (running) return
        running = true
        particles.clear()
        postOnAnimation(updateLoop)
    }

    fun stopParticles() {
        running = false
        particles.clear()
        invalidate()
    }

    private val updateLoop = object : Runnable {
        override fun run() {
            if (!running || width == 0 || height == 0) {
                if (running) postOnAnimation(this)
                return
            }
            update()
            invalidate()
            postOnAnimation(this)
        }
    }

    private fun update() {
        val h = height.toFloat()
        val w = width.toFloat()
        val dp1 = density

        spawnTimer += 0.055f
        if (particles.size < maxParticles && spawnTimer > 0.8f) {
            spawnTimer = 0f

            val geomFull = currentGeomCount >= maxGeomShapes
            val shape = if (geomFull) listOf(0, 2).random() else Random.nextInt(5)

            val totalDeg = Random.nextFloat() * 60f + 10f
            val avgSpeed = dp1 * 1.3f
            val estLifetime = h / avgSpeed
            val rotSpeed = if (Random.nextBoolean()) {
                (if (Random.nextBoolean()) 1f else -1f) * totalDeg / estLifetime
            } else {
                0f
            }

            particles.add(Particle(
                x = Random.nextFloat() * w,
                y = h + dp1 * 8,
                alpha = 0f,
                speedY = -(dp1 * 0.8f + Random.nextFloat() * dp1 * 1.8f),
                speedX = (Random.nextFloat() - 0.5f) * dp1 * 0.4f,
                size = dp1 * (18f + Random.nextFloat() * 52f),
                rotation = Random.nextFloat() * 360f,
                rotSpeed = rotSpeed,
                shape = shape,
                geomRadius = when (shape) {
                    1 -> dp1 * (5f + Random.nextFloat() * 15f)
                    3 -> dp1 * (6f + Random.nextFloat() * 10f)
                    4 -> dp1 * (6f + Random.nextFloat() * 12f)
                    2 -> dp1 * (16f + Random.nextFloat() * 34f)
                    else -> 0f
                },
                strokeWidth = dp1 * (0.6f + Random.nextFloat() * 1.4f)
            ))
        }

        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()

            if (p.alpha < 1f && p.y > h * 0.85f) {
                p.alpha = (p.alpha + 0.04f).coerceAtMost(0.35f)
            }
            if (p.y < h * 0.06f) {
                p.alpha -= 0.03f
            }

            p.y += p.speedY
            p.x += p.speedX + sin(p.y * 0.02f) * density * 0.08f
            p.rotation += p.rotSpeed

            val offLeft = p.x < -p.size
            val offRight = p.x > w + p.size
            if (p.y < -dp1 * 15 || p.alpha <= 0f || offLeft || offRight) {
                iter.remove()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (p in particles) {
            paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 70)
            paint.color = particleColor

            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rotation)

            when (p.shape) {
                0 -> {
                    paint.strokeWidth = p.strokeWidth
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(-p.size / 2, 0f, p.size / 2, 0f, paint)
                }
                1 -> {
                    // Dot
                    paint.strokeWidth = 0.8f * density
                    paint.style = Paint.Style.FILL_AND_STROKE
                    paint.alpha = (p.alpha * 160).toInt().coerceIn(0, 45)
                    canvas.drawCircle(0f, 0f, p.geomRadius, paint)
                }
                2 -> {
                    // Arc
                    paint.strokeWidth = p.strokeWidth
                    paint.style = Paint.Style.STROKE
                    rectF.set(-p.geomRadius, -p.geomRadius, p.geomRadius, p.geomRadius)
                    canvas.drawArc(rectF, 0f, 180f, false, paint)
                }
                3 -> {
                    // Triangle
                    paint.strokeWidth = p.strokeWidth
                    paint.style = Paint.Style.STROKE
                    val r = p.geomRadius
                    triPath.rewind()
                    triPath.moveTo(0f, -r)
                    triPath.lineTo(-r * 0.866f, r * 0.5f)
                    triPath.lineTo(r * 0.866f, r * 0.5f)
                    triPath.close()
                    canvas.drawPath(triPath, paint)
                }
                4 -> {
                    // Square
                    paint.strokeWidth = p.strokeWidth
                    paint.style = Paint.Style.STROKE
                    val r = p.geomRadius
                    canvas.drawRect(-r, -r, r, r, paint)
                }
            }

            canvas.restore()
        }
    }
}
