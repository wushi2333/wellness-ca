// Author: Cai Peilin, Xia Zihang
package iss.nus.edu.sg.ca_application.ui.home

import android.graphics.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.agentic.HistoryAdapter
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.model.AgentHistoryItem
import iss.nus.edu.sg.ca_application.network.AgentApi
import iss.nus.edu.sg.ca_application.network.ApiErrorHandler
import iss.nus.edu.sg.ca_application.network.ApiException

class RecommendationsFragment : Fragment() {

    private lateinit var progressBar: ProgressBar
    private lateinit var resultText: TextView
    private lateinit var historyRecycler: RecyclerView
    private var allItems = listOf<AgentHistoryItem>()
    private var historyItems = listOf<AgentHistoryItem>()
    private var swipedPosition: Int? = null
    private var deleteWidth = 0f
    private var animTime = 200L
    private var density = 1f
    private var currentPage = 1
    private var totalPages = 1
    private val pageSize = 4

    private var isTrackingSwipe = false
    private var wasSwipedAtStart = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_recommendations, container, false)

        root.findViewById<ImageView>(R.id.btnRecsBack).setOnClickListener {
            (activity as? iss.nus.edu.sg.ca_application.MainActivity)?.hideHomeDetail()
        }
        progressBar = root.findViewById(R.id.progressRecs)
        resultText = root.findViewById(R.id.tvRecsResult)
        historyRecycler = root.findViewById(R.id.recsHistoryRecycler)
        historyRecycler.layoutManager = LinearLayoutManager(requireContext())

        density = resources.displayMetrics.density
        deleteWidth = 100f * density
        animTime = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

        val deleteBg = Paint().apply { color = Color.parseColor("#EF4444"); isAntiAlias = true }
        val trashIcon = resources.getDrawable(R.drawable.ic_trash, null)

        historyRecycler.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                val childCount = parent.childCount
                for (i in 0 until childCount) {
                    val child = parent.getChildAt(i)
                    val tx = child.translationX
                    if (tx < 0) {
                        val w = child.width.toFloat()
                        val right = w + tx
                        c.drawRoundRect(right, child.top.toFloat(), w, child.bottom.toFloat(), 16f * density, 16f * density, deleteBg)
                        val iconSize = (28f * density).toInt()
                        val cx = ((right + (w - right) / 2) - iconSize / 2).toInt()
                        val cy = child.top + (child.height - iconSize) / 2
                        trashIcon.setBounds(cx, cy, cx + iconSize, cy + iconSize)
                        trashIcon.setTint(Color.WHITE)
                        trashIcon.draw(c)
                    }
                }
            }
        })

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val pos = vh.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return 0
                return makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
            }

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun getSwipeThreshold(vh: RecyclerView.ViewHolder) = 1.0f
            override fun getSwipeEscapeVelocity(defaultValue: Float) = Float.MAX_VALUE
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dx: Float, dy: Float, actionState: Int, currentlyActive: Boolean) {
                val pos = vh.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val itemView = vh.itemView

                if (currentlyActive) {
                    if (!isTrackingSwipe) {
                        isTrackingSwipe = true
                        wasSwipedAtStart = swipedPosition == pos
                    }
                    val initialTx = if (wasSwipedAtStart) -deleteWidth else 0f
                    var translationX = initialTx + dx
                    translationX = Math.min(0f, Math.max(translationX, -deleteWidth))
                    itemView.translationX = translationX
                } else {
                    if (isTrackingSwipe) {
                        isTrackingSwipe = false
                        if (wasSwipedAtStart) {
                            if (dx > deleteWidth * 0.3f) {
                                if (swipedPosition == pos) swipedPosition = null
                            } else {
                                setSwipedPosition(pos, rv)
                            }
                        } else {
                            if (dx <= -deleteWidth * 0.4f) {
                                setSwipedPosition(pos, rv)
                            } else {
                                if (swipedPosition == pos) swipedPosition = null
                            }
                        }
                    }
                    itemView.translationX = if (swipedPosition == pos) -deleteWidth else 0f
                }
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                val pos = vh.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    vh.itemView.translationX = if (swipedPosition == pos) -deleteWidth else 0f
                }
                isTrackingSwipe = false
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeCallback)
        itemTouchHelper.attachToRecyclerView(historyRecycler)

        historyRecycler.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var isDragging = false
            private var activeSwipedPos = -1
            private var activeVh: RecyclerView.ViewHolder? = null

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                val action = e.actionMasked
                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        startY = e.y
                        isDragging = false
                        activeSwipedPos = swipedPosition?.let { pos ->
                            val vh = rv.findViewHolderForAdapterPosition(pos)
                            if (vh != null && e.y >= vh.itemView.top && e.y <= vh.itemView.bottom) pos else -1
                        } ?: -1
                        activeVh = if (activeSwipedPos != -1) rv.findViewHolderForAdapterPosition(activeSwipedPos) else null
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (activeSwipedPos != -1 && !isDragging) {
                            val dx = e.x - startX
                            val dy = e.y - startY
                            if (Math.abs(dx) > 10f && Math.abs(dx) > Math.abs(dy)) {
                                if (dx > 0) {
                                    isDragging = true
                                    activeVh?.let { itemTouchHelper.startSwipe(it) }
                                    return false
                                }
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (activeSwipedPos != -1 && !isDragging) {
                            val dx = e.x - startX
                            val dy = e.y - startY
                            if (Math.abs(dx) < 10f && Math.abs(dy) < 10f) {
                                if (startX > rv.width - deleteWidth) {
                                    executeDelete(activeSwipedPos)
                                } else {
                                    executeRestore(activeSwipedPos)
                                }
                                return true
                            }
                        }
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                val action = e.actionMasked
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    isDragging = false
                    activeSwipedPos = -1
                    activeVh = null
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

        root.findViewById<Button>(R.id.btnGenerateRec).setOnClickListener { generate() }

        // Pagination buttons
        root.findViewById<View>(R.id.btnFirstPage).setOnClickListener { showPage(1) }
        root.findViewById<View>(R.id.btnPrevPage).setOnClickListener { showPage(currentPage - 1) }
        root.findViewById<View>(R.id.btnNextPage).setOnClickListener { showPage(currentPage + 1) }
        root.findViewById<View>(R.id.btnLastPage).setOnClickListener { showPage(totalPages) }
        root.findViewById<View>(R.id.btnGoPage).setOnClickListener {
            val page = root.findViewById<EditText>(R.id.etPageJump).text.toString().toIntOrNull() ?: 1
            showPage(page)
        }

        loadHistory()

        return root
    }

    override fun onPause() {
        super.onPause()
        swipedPosition = null
    }

    private fun generate() {
        val token = TokenManager.getToken(requireContext())
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        progressBar.visibility = View.VISIBLE
        Thread {
            try {
                val lang = if (iss.nus.edu.sg.ca_application.SettingsActivity.getSavedLocale(requireContext())?.language == "zh") "zh" else "en"
                val rec = AgentApi.recommend(token, lang)
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    resultText.text = rec.recommendation
                    resultText.visibility = View.VISIBLE
                    loadHistory()
                }
            } catch (e: java.io.IOException) {
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (e.message?.contains("401") == true) {
                        activity?.let { ApiErrorHandler.handle(it, ApiException(401, e.message!!)) }
                    } else {
                        Toast.makeText(requireContext(), "Agent unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun loadHistory() {
        currentPage = 1
        val token = TokenManager.getToken(requireContext())
        if (token.isEmpty()) return
        Thread {
            try {
                allItems = AgentApi.history(token, 100)
                totalPages = maxOf(1, (allItems.size + pageSize - 1) / pageSize)
                activity?.runOnUiThread {
                    showPage(1)
                    updatePaginationUI()
                }
            } catch (e: java.io.IOException) {
                if (e.message?.contains("401") == true) {
                    activity?.runOnUiThread {
                        activity?.let { ApiErrorHandler.handle(it, ApiException(401, e.message!!)) }
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun setSwipedPosition(pos: Int, rv: RecyclerView) {
        swipedPosition?.let { old ->
            if (old != pos) {
                // Restore previously swiped item (faster)
                rv.findViewHolderForAdapterPosition(old)?.itemView?.animate()
                    ?.translationX(0f)?.setDuration(animTime / 2)?.start()
                swipedPosition = null
            }
        }
        swipedPosition = pos
        rv.findViewHolderForAdapterPosition(pos)?.itemView?.animate()
            ?.translationX(-deleteWidth)?.setDuration(animTime)?.start()
    }

    private fun showPage(page: Int) {
        currentPage = page.coerceIn(1, totalPages)
        val start = (currentPage - 1) * pageSize
        historyItems = allItems.drop(start).take(pageSize)
        swipedPosition = null
        setupAdapter()
        updatePaginationUI()
    }

    private fun updatePaginationUI() {
        val v = view ?: return
        val bar = v.findViewById<View>(R.id.paginationBar)
        bar.visibility = if (totalPages <= 1) View.GONE else View.VISIBLE
        if (totalPages <= 1) return

        val btnFirst = v.findViewById<TextView>(R.id.btnFirstPage)
        val btnPrev = v.findViewById<TextView>(R.id.btnPrevPage)
        val btnNext = v.findViewById<TextView>(R.id.btnNextPage)
        val btnLast = v.findViewById<TextView>(R.id.btnLastPage)
        val tvInfo = v.findViewById<TextView>(R.id.tvPageInfo)

        val enabled = 0xFF1E293B.toInt()
        val disabled = 0xFFCBD5E1.toInt()

        btnFirst.setTextColor(if (currentPage > 1) enabled else disabled)
        btnFirst.isClickable = currentPage > 1
        btnPrev.setTextColor(if (currentPage > 1) enabled else disabled)
        btnPrev.isClickable = currentPage > 1
        btnNext.setTextColor(if (currentPage < totalPages) enabled else disabled)
        btnNext.isClickable = currentPage < totalPages
        btnLast.setTextColor(if (currentPage < totalPages) enabled else disabled)
        btnLast.isClickable = currentPage < totalPages
        tvInfo.text = "$currentPage / $totalPages"
    }

    private fun setupAdapter() {
        (historyRecycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
        historyRecycler.adapter = HistoryAdapter(historyItems) { item, pos, touchX ->
            if (swipedPosition == pos) {
                val itemWidth = historyRecycler.width.toFloat()
                if (touchX > itemWidth - deleteWidth) {
                    executeDelete(pos)
                } else {
                    executeRestore(pos)
                }
            }
        }
    }

    private fun executeDelete(pos: Int) {
        if (pos < 0 || pos >= historyItems.size) return
        val item = historyItems[pos]
        swipedPosition = null
        deleteItem(item) {
            activity?.runOnUiThread { loadHistory() }
        }
    }

    private fun deleteItem(item: AgentHistoryItem, onComplete: () -> Unit) {
        val token = TokenManager.getToken(requireContext())
        if (token.isEmpty()) return
        Thread {
            try { AgentApi.deleteRecommendation(token, item.id) }
            catch (_: Exception) {}
            onComplete()
        }.start()
    }

    private fun executeRestore(pos: Int) {
        if (swipedPosition == pos) swipedPosition = null
        val view = historyRecycler.findViewHolderForAdapterPosition(pos)?.itemView
        view?.animate()?.translationX(0f)?.setDuration(animTime)?.withEndAction {
            historyRecycler.adapter?.notifyItemChanged(pos)
        }?.start()
    }
}