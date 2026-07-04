// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.live2d

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import com.live2d.sdk.cubism.framework.CubismDefaultParameterId
import com.live2d.sdk.cubism.framework.CubismFramework

class Live2DCharacterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val glView: GLSurfaceView
    private var delegate: LAppMinimumDelegate? = null
    private var glStarted = false

    init {
        glView = GLSurfaceView(context)
        glView.setEGLContextClientVersion(2)
        // Don't set renderer or add to layout yet — wait for onStart() with valid Activity
    }

    var onModelReady: (() -> Unit)? = null

    private fun ensureGLStarted(activity: android.app.Activity) {
        if (glStarted) return
        glStarted = true
        delegate = LAppMinimumDelegate.getInstance()
        delegate?.onStart(activity)
        glView.setRenderer(GLRendererMinimum())
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        addView(glView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun onStart(activity: android.app.Activity) {
        ensureGLStarted(activity)
    }

    /** Convenience for when context IS an Activity (legacy callers). */
    fun onStart() {
        ensureGLStarted(context as android.app.Activity)
    }

    /** Whether model data is already cached from a previous session. */
    fun isModelCached(): Boolean = LAppMinimumLive2DManager.isModelCached()

    fun onStop() { delegate?.onStop() }
    fun onDestroy() { delegate?.onDestroy() }

    /** Set expression by name (e.g. "f01", "f02" — defined in model's .exp3.json). */
    fun setExpression(name: String) {
        val model = LAppMinimumLive2DManager.getInstance().getModel(0) as? LAppMinimumModel
        model?.startExpression(name)
    }

    /** Direct parameter control: mouth open for lip sync (0.0–1.0). */
    fun setMouthOpen(value: Float) {
        val model = LAppMinimumLive2DManager.getInstance().getModel(0)
        val id = CubismFramework.getIdManager()
            .getId(CubismDefaultParameterId.ParameterId.MOUTH_OPEN_Y.id)
        model?.model?.setParameterValue(id, value, 1.0f)
    }

    /** Emotion to expression mapping using YouXiaoMiao's actual toggle params. */
    fun setEmotion(emotion: String) {
        val model = LAppMinimumLive2DManager.getInstance().getModel(0) as? LAppMinimumModel ?: return
        // Reset per-frame overrides
        model.eyeSmileOverride = 0f
        model.mouthOpenOverride = 0f

        val name = when (emotion) {
            "happy" -> {
                model.eyeSmileOverride = 1.5f  // stronger squint
                "blush"
            }
            "listening" -> "starEyes"
            "surprised" -> {
                model.mouthOpenOverride = 1.0f
                "leanForward"  // body tilt + mouth open
            }
            "focused" -> "notes"
            "confused" -> "dizzy"
            "thinking" -> "phone"
            else -> null
        }
        name?.let { setExpression(it) }
    }

    /** Set mode-specific idle expression. */
    fun setModeExpression(mode: String) {
        val name = when (mode) {
            "chat" -> "phone"
            "agent" -> "notes"
            else -> null
        }
        name?.let { setExpression(it) }
    }

    // Track touch start for tap vs swipe detection
    private var touchStartX = 0f
    private var touchStartY = 0f
    private val TAP_THRESHOLD = 0.05f  // normalized coords, ~2% of screen

    // Auto-reset to default expression after idle timeout
    private var currentTapExpression: String? = null
    private val idleResetHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val idleResetRunnable = Runnable { resetToIdleExpression() }
    private val IDLE_RESET_MS = 5000L

    private fun resetIdleTimer() {
        idleResetHandler.removeCallbacks(idleResetRunnable)
        idleResetHandler.postDelayed(idleResetRunnable, IDLE_RESET_MS)
    }

    private fun resetToIdleExpression() {
        val model = LAppMinimumLive2DManager.getInstance().getModel(0) as? LAppMinimumModel ?: return
        currentTapExpression = null
        model.stopAllExpressions()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val d = delegate ?: return false
        val px = event.x / width * 2f - 1f
        val py = event.y / height * 2f - 1f
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = px
                touchStartY = py
                d.onTouchBegan(px, py)
                resetIdleTimer()
            }
            MotionEvent.ACTION_MOVE -> {
                d.onTouchMoved(px, py)
                resetIdleTimer()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = px - touchStartX
                val dy = py - touchStartY
                val dist = dx * dx + dy * dy
                if (dist < TAP_THRESHOLD * TAP_THRESHOLD) {
                    onTap()
                }
                d.onTouchEnd(px, py)
                resetIdleTimer()
            }
        }
        return true
    }

    private fun onTap() {
        val model = LAppMinimumLive2DManager.getInstance().getModel(0) as? LAppMinimumModel ?: return
        val candidates = listOf("blackFace", "tears", "cry").filter { it != currentTapExpression }
        val name = if (candidates.isNotEmpty()) candidates.random() else listOf("blackFace", "tears", "cry").random()
        currentTapExpression = name
        model.startExpression(name)
        resetIdleTimer()
    }

    fun onSurfaceChanged(w: Int, h: Int) { delegate?.onSurfaceChanged(w, h) }

    /** Set background transparency so the model blends with the UI behind it. */
    fun setTransparentBackground() {
        glView.setZOrderOnTop(true)
        glView.holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
    }
}
