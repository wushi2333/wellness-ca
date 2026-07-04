// Author: Xia Zihang
package iss.nus.edu.sg.ca_application

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class CircleCropActivity : AppCompatActivity() {

    private var sourceUri: Uri? = null
    private var sourceBitmap: Bitmap? = null
    private lateinit var cropView: CropView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF000000.toInt()

        val uriStr = intent.getStringExtra("image_uri") ?: run { finish(); return }
        sourceUri = Uri.parse(uriStr)

        try {
            val input = contentResolver.openInputStream(sourceUri!!)
            sourceBitmap = BitmapFactory.decodeStream(input)
            input?.close()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (sourceBitmap == null) { finish(); return }

        // Layout: CropView fills screen, toolbar overlay at top, button bar at bottom
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }

        cropView = CropView(this).apply {
            setBitmap(sourceBitmap!!)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(cropView)

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 48, 48, 48)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP }
        }
        val cancelBtn = TextView(this).apply {
            text = getString(android.R.string.cancel)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setOnClickListener { setResult(RESULT_CANCELED); finish() }
        }
        topBar.addView(cancelBtn)
        val title = TextView(this).apply {
            text = getString(R.string.crop_avatar)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        }
        topBar.addView(title)
        root.addView(topBar)

        // Bottom button
        val doneBtn = TextView(this).apply {
            text = getString(R.string.save)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setBackgroundColor(0xFF1B7B9E.toInt())
            setPadding(64, 32, 64, 32)
            setOnClickListener { onDone() }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 64
            }
        }
        root.addView(doneBtn)

        setContentView(root)
    }

    private fun onDone() {
        try {
            val croppedBitmap = cropView.getCroppedCircleBitmap(512)
            if (croppedBitmap == null) {
                Toast.makeText(this, "Failed to crop", Toast.LENGTH_SHORT).show()
                return
            }

            // Save to cache file as JPEG (circle clip hides corners anyway)
            val file = File(cacheDir, "cropped_avatar_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { fos ->
                // Fill background white then compress — corners invisible due to circular clip
                val flattened = flattenToWhiteBg(croppedBitmap)
                flattened.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }

            val result = Intent().apply {
                putExtra("cropped_uri", Uri.fromFile(file).toString())
            }
            setResult(RESULT_OK, result)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Crop failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sourceBitmap?.recycle()
    }

    companion object {
        /** Fill transparent pixels with white so JPEG compression looks clean. */
        fun flattenToWhiteBg(input: Bitmap): Bitmap {
            val out = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(0xFFFFFFFF.toInt())
            canvas.drawBitmap(input, 0f, 0f, null)
            return out
        }
    }
}

/** Custom view that displays a bitmap with pinch-to-zoom/pan and a circular crop overlay. */
class CropView(context: android.content.Context) : ImageView(context) {

    private var bmp: Bitmap? = null
    private val displayMatrix = Matrix()
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFFFFFFFF.toInt()
        alpha = 200
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x88000000.toInt()
    }
    private val clipPath = Path()

    private var baseScale = 1f
    private var currentScale = 1f
    private var translateX = 0f
    private var translateY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            currentScale *= detector.scaleFactor
            currentScale = currentScale.coerceIn(1f, 5f)
            clampTranslation()
            invalidate()
            return true
        }
    })

    private var lastX = 0f
    private var lastY = 0f

    fun setBitmap(bitmap: Bitmap) {
        bmp = bitmap
        // Calculate base scale to fit image in screen
        val vw = context.resources.displayMetrics.widthPixels.toFloat()
        val vh = context.resources.displayMetrics.heightPixels.toFloat()
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        baseScale = minOf(vw / bw, vh / bh)
        currentScale = baseScale
        translateX = 0f
        translateY = 0f
        invalidate()
    }

    private fun clampTranslation() {
        val b = bmp ?: return
        val r = minOf(width, height) / 2f * 0.85f
        val bw = b.width * currentScale
        val bh = b.height * currentScale
        // Maximum translation before the image edge leaves the circle
        val maxX = (bw / 2f - r).coerceAtLeast(0f)
        val maxY = (bh / 2f - r).coerceAtLeast(0f)
        translateX = translateX.coerceIn(-maxX, maxX)
        translateY = translateY.coerceIn(-maxY, maxY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                translateX += dx
                translateY += dy
                // Clamp to prevent panning beyond image bounds
                clampTranslation()
                lastX = event.x
                lastY = event.y
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = bmp ?: return

        canvas.save()

        // Draw dimmed background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f * 0.85f

        // Clip to circle
        val path = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
        canvas.clipPath(path)

        // Draw the bitmap with transforms
        canvas.save()
        canvas.translate(cx + translateX, cy + translateY)
        canvas.scale(currentScale, currentScale)
        canvas.translate(-b.width / 2f, -b.height / 2f)
        canvas.drawBitmap(b, 0f, 0f, null)
        canvas.restore()

        // Draw circle border
        canvas.drawCircle(cx, cy, radius, circlePaint)

        canvas.restore()
    }

    /** Extract the circular region of the bitmap at the given output size. */
    fun getCroppedCircleBitmap(outputSize: Int): Bitmap? {
        val b = bmp ?: return null

        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f * 0.85f

        // Map screen circle center back to bitmap coordinates
        val effectiveScale = currentScale
        val bitmapCx = (b.width / 2f) - (translateX / effectiveScale)
        val bitmapCy = (b.height / 2f) - (translateY / effectiveScale)
        val bitmapRadius = radius / effectiveScale

        // Create output bitmap
        val out = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(out)

        // Scale to fit the circle region into output
        val scale = outputSize.toFloat() / (bitmapRadius * 2)
        val m = Matrix()
        m.postTranslate(-bitmapCx + bitmapRadius, -bitmapCy + bitmapRadius)
        m.postScale(scale, scale)
        m.postTranslate(0f, 0f)

        // Clip to circle
        val circlePath = Path().apply {
            addCircle(outputSize / 2f, outputSize / 2f, outputSize / 2f, Path.Direction.CW)
        }
        outCanvas.clipPath(circlePath)
        outCanvas.drawBitmap(b, m, null)

        return out
    }
}
