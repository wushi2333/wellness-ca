// Author: Wang Songyu, Liu Yu, Huang Qianer, Xia Zihang
package iss.nus.edu.sg.ca_application

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import iss.nus.edu.sg.ca_application.ui.add.AddOptionsSheet
import iss.nus.edu.sg.ca_application.ui.bottomnav.AnimatedBottomNavBar
import iss.nus.edu.sg.ca_application.ui.chat.ChatFragment
import iss.nus.edu.sg.ca_application.ui.common.ViewPagerAdapter
import iss.nus.edu.sg.ca_application.ui.chart.WeekUtils
import iss.nus.edu.sg.ca_application.ui.home.HomeFragment

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: AnimatedBottomNavBar
    private lateinit var addSheetContent: View
    private lateinit var addScrim: View
    lateinit var homeFragment: HomeFragment
        private set
    private var addSheetOpen = false
    private var lastTabIndex = 0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SettingsActivity.wrapContextForLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forceLocale()
        window.statusBarColor = getColor(R.color.background)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNavBar)
        addSheetContent = findViewById(R.id.addSheetContent)
        addScrim = findViewById(R.id.addScrim)

        homeFragment = HomeFragment()
        val chatFragment = ChatFragment()
        viewPager.adapter = ViewPagerAdapter(this, listOf(homeFragment, chatFragment))
        viewPager.offscreenPageLimit = 1
        viewPager.isUserInputEnabled = false

        // Pre-inflate add options sheet
        val sheetView = LayoutInflater.from(this).inflate(R.layout.sheet_add_options, addSheetContent as android.view.ViewGroup, false)
        (addSheetContent as android.view.ViewGroup).addView(sheetView)
        sheetView.findViewById<View>(R.id.cardAddSleep).setOnClickListener { openAddSleepSheet() }
        sheetView.findViewById<View>(R.id.cardAddExercise).setOnClickListener { openAddExerciseSheet() }

        addScrim.setOnClickListener { dismissAddSheet() }

        bottomNav.onTabSelected = { index ->
            if (index == 1) showAddSheet()
            else { dismissAddSheet(); viewPager.setCurrentItem(if (index == 0) 0 else 1, false); lastTabIndex = index }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.selectTab(if (position == 0) 0 else 2)
                lastTabIndex = if (position == 0) 0 else 2
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (addSheetOpen) { dismissAddSheet(); return }
                val fm = supportFragmentManager
                if (fm.backStackEntryCount > 0) {
                    fm.popBackStack()
                    // Wait for fragment transaction to complete before loading data
                    viewPager.post { homeFragment.loadData() }
                    return
                }
                if (lastTabIndex != 0) { bottomNav.selectTab(0); viewPager.setCurrentItem(0, true); return }
                finish()
            }
        })
    }

    private fun forceLocale() {
        val locale = SettingsActivity.getSavedLocale(this) ?: return
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        WeekUtils.setLocale(locale)
    }

    private fun showAddSheet() {
        if (addSheetOpen) return
        addSheetOpen = true
        addScrim.visibility = View.VISIBLE
        addSheetContent.visibility = View.VISIBLE
    }

    private fun dismissAddSheet() {
        addSheetOpen = false
        addScrim.visibility = View.GONE
        addSheetContent.visibility = View.GONE
        bottomNav.selectTab(lastTabIndex)
        homeFragment.loadData()
    }

    private fun openAddSleepSheet() {
        iss.nus.edu.sg.ca_application.ui.add.AddSleepSheet().show(supportFragmentManager, "add_sleep")
    }

    private fun openAddExerciseSheet() {
        iss.nus.edu.sg.ca_application.ui.add.AddExerciseSheet().show(supportFragmentManager, "add_exercise")
    }

    fun showHomeDetail(fragment: Fragment) {
        findViewById<View>(R.id.detailOverlay).visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.detailOverlay, fragment)
            .addToBackStack("detail")
            .commit()
    }

    fun hideHomeDetail() {
        findViewById<View>(R.id.detailOverlay).visibility = View.GONE
        supportFragmentManager.popBackStack()
        viewPager.post { homeFragment.loadData() }
    }

    fun navigateTo(target: String) {
        when (target) {
            "sleep_detail" -> showHomeDetail(iss.nus.edu.sg.ca_application.ui.home.SleepDetailFragment())
            "exercise_detail" -> showHomeDetail(iss.nus.edu.sg.ca_application.ui.home.ExerciseDetailFragment())
            "wellness_entry" -> showAddSheet()
            "wellness_insights" -> showHomeDetail(iss.nus.edu.sg.ca_application.ui.home.RecommendationsFragment())
            "dashboard" -> { bottomNav.selectTab(0); viewPager.setCurrentItem(0, true) }
            else -> { bottomNav.selectTab(0); viewPager.setCurrentItem(0, true) }
        }
    }
}
