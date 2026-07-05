// Author: Wang Songyu
package iss.nus.edu.sg.ca_application.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.graphics.BitmapFactory
import android.graphics.Outline
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.applyTopInset
import iss.nus.edu.sg.ca_application.model.DailyWellness
import iss.nus.edu.sg.ca_application.ProfileActivity
import iss.nus.edu.sg.ca_application.SettingsActivity
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.network.ApiClient
import iss.nus.edu.sg.ca_application.network.ApiErrorHandler
import iss.nus.edu.sg.ca_application.network.ApiException
import iss.nus.edu.sg.ca_application.network.BASE_URL
import iss.nus.edu.sg.ca_application.network.CacheManager
import iss.nus.edu.sg.ca_application.network.ProfileApi
import iss.nus.edu.sg.ca_application.ui.chart.MiniBarRowView
import iss.nus.edu.sg.ca_application.ui.chart.SparklineView
import iss.nus.edu.sg.ca_application.ui.chart.WeekUtils
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var tvGreeting: TextView
    private lateinit var tvHomeUsername: TextView
    private lateinit var tvSleepAvg: TextView
    private lateinit var tvSleepTrend: TextView
    private lateinit var tvExerciseAvg: TextView
    private lateinit var tvExerciseDays: TextView
    private lateinit var tvLatestTip: TextView
    private lateinit var ivHomeAvatar: ImageView
    private lateinit var sparklineSleep: SparklineView
    private lateinit var miniBarExercise: MiniBarRowView
    private lateinit var particleView: ParticleView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvHomeUsername = view.findViewById(R.id.tvHomeUsername)
        tvSleepAvg = view.findViewById(R.id.tvSleepAvg)
        tvSleepTrend = view.findViewById(R.id.tvSleepTrend)
        tvExerciseAvg = view.findViewById(R.id.tvExerciseAvg)
        tvExerciseDays = view.findViewById(R.id.tvExerciseDays)
        tvLatestTip = view.findViewById(R.id.tvLatestTip)
        ivHomeAvatar = view.findViewById(R.id.ivHomeAvatar)
        sparklineSleep = view.findViewById(R.id.sparklineSleep)
        miniBarExercise = view.findViewById(R.id.miniBarExercise)

        // Particle effect — extend into status bar
        particleView = view.findViewById(R.id.particleView)
        val statusBarH = view.rootWindowInsets?.systemWindowInsetTop ?: 0
        (particleView.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.topMargin = -statusBarH
        applyParticleSetting()

        // Handle status bar inset for top bar
        view.findViewById<View>(R.id.homeTopBar).applyTopInset()

        // Clip avatar to circle
        ivHomeAvatar.clipToOutline = true
        ivHomeAvatar.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val size = minOf(view.width, view.height)
                val left = (view.width - size) / 2
                val top = (view.height - size) / 2
                outline.setOval(left, top, left + size, top + size)
            }
        }

        // Load user profile (avatar + username) from server
        loadUserProfile()
        // Also set from local cache immediately
        val username = TokenManager.getUsername(requireContext()).ifEmpty { "User" }
        tvHomeUsername.text = username
        tvGreeting.text = getGreeting()

        view.findViewById<ImageView>(R.id.ivSettings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // Avatar click → ProfileActivity
        view.findViewById<ImageView>(R.id.ivHomeAvatar).setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        view.findViewById<View>(R.id.cardSleep).setOnClickListener {
            (activity as? iss.nus.edu.sg.ca_application.MainActivity)?.showHomeDetail(SleepDetailFragment())
        }
        view.findViewById<View>(R.id.cardExercise).setOnClickListener {
            (activity as? iss.nus.edu.sg.ca_application.MainActivity)?.showHomeDetail(ExerciseDetailFragment())
        }
        view.findViewById<View>(R.id.cardRecommendation).setOnClickListener {
            (activity as? iss.nus.edu.sg.ca_application.MainActivity)?.showHomeDetail(RecommendationsFragment())
        }

        loadData()
    }

    private fun applyParticleSetting() {
        if (iss.nus.edu.sg.ca_application.SettingsActivity.isParticlesEnabled(requireContext())) {
            particleView.visibility = View.VISIBLE
            particleView.startParticles()
        } else {
            particleView.stopParticles()
            particleView.visibility = View.GONE
        }
    }

    private fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> getString(R.string.greeting_morning)
            in 12..17 -> getString(R.string.greeting_afternoon)
            else -> getString(R.string.greeting_evening)
        }
    }

    fun loadData() {
        val ctx = context ?: return
        val token = TokenManager.getToken(ctx)
        if (token.isEmpty()) return
        val cacheKey = "records"

        // Show cached data immediately; fall back to in-memory data if cache miss
        val cached = CacheManager.get<Pair<List<DailyWellness>, Boolean>>(cacheKey)
        if (cached != null && cached.first.isNotEmpty()) {
            if (isAdded) activity?.runOnUiThread { renderHome(cached.first) }
        } else if (lastHomeDailies.isNotEmpty() && isAdded) {
            activity?.runOnUiThread { renderHome(lastHomeDailies) }
        }

        Thread {
            try {
                val (dailies, _) = ApiClient.getDailyRecords(token, 0, 90)
                CacheManager.put(cacheKey, Pair(dailies, true))
                if (!isAdded) return@Thread
                val fp = dailies.joinToString { "${it.recordDate}:${it.sleep?.sleepHours}:${it.exercises.sumOf { e -> e.exerciseDuration }}" }
                if (fp != homeDataFingerprint) {
                    activity?.runOnUiThread { renderHome(dailies) }
                }
            } catch (e: ApiException) {
                if (isAdded) activity?.runOnUiThread { activity?.let { ApiErrorHandler.handle(it, e) } }
            } catch (_: Exception) {}
        }.start()
    }

    private var homeDataFingerprint = ""
    private var lastHomeDailies: List<DailyWellness> = emptyList()

    private fun renderHome(dailies: List<DailyWellness>) {
        if (!isAdded || dailies.isEmpty()) return
        lastHomeDailies = dailies
        homeDataFingerprint = dailies.joinToString { "${it.recordDate}:${it.sleep?.sleepHours}:${it.exercises.sumOf { e -> e.exerciseDuration }}" }
        val currentMonday = WeekUtils.currentWeekMonday()
        val prevMonday = currentMonday.minusDays(7)

                    // Build week arrays (Mon–Sun) from DailyWellness
                    val thisWeekSleep = WeekUtils.buildWeekArray(currentMonday, dailies) { d ->
                        d?.sleep?.sleepHours
                    }
                    val prevWeekSleep = WeekUtils.buildWeekArray(prevMonday, dailies) { d ->
                        d?.sleep?.sleepHours
                    }
                    val thisWeekEx = WeekUtils.buildWeekArray(currentMonday, dailies) { d ->
                        d?.exercises?.sumOf { it.exerciseDuration } ?: 0
                    }

                    // Sleep stats
                    val validSleep = thisWeekSleep.filterNotNull()
                    val avgSleep = if (validSleep.isNotEmpty()) validSleep.average() else 0.0
                    val prevValidSleep = prevWeekSleep.filterNotNull()
                    val prevAvgSleep = if (prevValidSleep.isNotEmpty()) prevValidSleep.average() else 0.0
                    tvSleepAvg.text = String.format(Locale.US, "%.1f h", avgSleep)
                    val trend = if (prevAvgSleep > 0) avgSleep - prevAvgSleep else 0.0
                    tvSleepTrend.text = if (trend >= 0) "+${String.format("%.1f", trend)}h" else "${String.format("%.1f", trend)}h"

                    // Exercise stats
                    val validEx = thisWeekEx.filterNotNull().filter { it > 0 }
                    val avgEx = if (validEx.isNotEmpty()) validEx.average() else 0.0
                    tvExerciseAvg.text = String.format(Locale.US, "%.0f min", avgEx)
                    tvExerciseDays.text = "${validEx.size} days"

                    // Populate mini visualizations (always 7 points Mon–Sun)
                    sparklineSleep.setData(thisWeekSleep.map { (it ?: 0.0).toFloat() })
                    miniBarExercise.setData(thisWeekEx.map { it ?: 0 })

                    // Latest tip
                    val tips = listOf(
                        getString(R.string.tip_hydrate),
                        getString(R.string.tip_walk),
                        getString(R.string.tip_sleep),
                        getString(R.string.tip_stretch),
                        getString(R.string.tip_breathe)
                    )
                    tvLatestTip.text = tips[(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) % tips.size)]
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
        if (::particleView.isInitialized) applyParticleSetting()
    }

    /** Load avatar and username from server. */
    private fun loadUserProfile() {
        val ctx = context ?: return
        val token = TokenManager.getToken(ctx)
        if (token.isEmpty()) return
        Thread {
            try {
                val data = ProfileApi.getProfile(token)
                if (!isAdded) return@Thread
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    tvHomeUsername.text = data.username
                    TokenManager.saveUsername(ctx, data.username)
                    if (!data.avatarUrl.isNullOrEmpty()) {
                        loadAvatarToView(data.avatarUrl)
                    }
                }
            } catch (e: ApiException) {
                if (isAdded) activity?.runOnUiThread { activity?.let { ApiErrorHandler.handle(it, e) } }
            } catch (_: Exception) {}
        }.start()
    }

    private fun loadAvatarToView(avatarUrl: String) {
        Thread {
            try {
                val fullUrl = if (avatarUrl.startsWith("http")) avatarUrl else "$BASE_URL$avatarUrl"
                val url = URL(fullUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doInput = true
                conn.connect()
                if (conn.responseCode == 200) {
                    val bmp = BitmapFactory.decodeStream(conn.inputStream)
                    activity?.runOnUiThread { ivHomeAvatar.setImageBitmap(bmp) }
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }.start()
    }
}
