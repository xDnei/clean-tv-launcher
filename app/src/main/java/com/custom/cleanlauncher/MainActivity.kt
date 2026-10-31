package com.custom.cleanlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    private lateinit var rootContainer: FrameLayout
    private lateinit var wallpaperBackground: ImageView
    private lateinit var heroViewPager: ViewPager2
    private lateinit var btnProfileAvatar: ImageButton
    private lateinit var sectionContinueWatching: LinearLayout

    private lateinit var rvContinueWatching: RecyclerView
    private lateinit var rvRecommendations: RecyclerView
    private lateinit var rvTrending: RecyclerView
    private lateinit var rvApps: RecyclerView

    private lateinit var profileAndSettings: ProfileAndSettingsManager
    private lateinit var repository: ContentRepository

    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            val count = heroViewPager.adapter?.itemCount ?: 0
            if (count > 1) {
                heroViewPager.currentItem = (heroViewPager.currentItem + 1) % count
            }
            autoScrollHandler.postDelayed(this, 7000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.rootContainer)
        wallpaperBackground = findViewById(R.id.wallpaperBackground)
        heroViewPager = findViewById(R.id.heroViewPager)
        btnProfileAvatar = findViewById(R.id.btnProfileAvatar)
        sectionContinueWatching = findViewById(R.id.sectionContinueWatching)

        rvContinueWatching = findViewById(R.id.rvContinueWatching)
        rvRecommendations = findViewById(R.id.rvRecommendations)
        rvTrending = findViewById(R.id.rvTrending)
        rvApps = findViewById(R.id.rvApps)

        setupHorizontalRow(rvContinueWatching)
        setupHorizontalRow(rvRecommendations)
        setupHorizontalRow(rvTrending)
        setupHorizontalRow(rvApps)

        repository = ContentRepository(this)
        profileAndSettings = ProfileAndSettingsManager(this) {
            refreshLauncherUI()
        }

        btnProfileAvatar.setOnClickListener {
            profileAndSettings.showProfileDropdownMenu()
        }

        refreshLauncherUI()
    }

    private fun setupHorizontalRow(rv: RecyclerView) {
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun refreshLauncherUI() {
        val prefs = getSharedPreferences("clean_launcher_prefs", MODE_PRIVATE)
        val activeProfile = profileAndSettings.getActiveProfile()
        val scaleOffset = prefs.getInt("icon_scale_offset", 20)
        val scaleFactor = 0.8f + (scaleOffset / 100f)

        val isWhiteMode = prefs.getString("theme_mode", "BLACK") == "WHITE"
        val bgColor = if (isWhiteMode) Color.parseColor("#F5F5F7") else Color.parseColor("#000000")
        val textColor = if (isWhiteMode) Color.parseColor("#121212") else Color.parseColor("#FFFFFF")

        rootContainer.setBackgroundColor(bgColor)
        listOf<TextView>(
            findViewById(R.id.titleContinueWatching),
            findViewById(R.id.titleRecommendations),
            findViewById(R.id.titleTrending),
            findViewById(R.id.titleApps)
        ).forEach { it.setTextColor(textColor) }

        val wallpaperUrl = prefs.getString("wallpaper_uri", "") ?: ""
        if (wallpaperUrl.isNotEmpty()) {
            wallpaperBackground.visibility = View.VISIBLE
            ImageLoader.load(wallpaperUrl, wallpaperBackground)
        } else {
            wallpaperBackground.visibility = View.GONE
        }

        val showContinue = prefs.getBoolean("show_continue_watching", true)
        if (showContinue) {
            val continueItems = repository.getContinueWatchingList(activeProfile.id)
            sectionContinueWatching.visibility = View.VISIBLE
            rvContinueWatching.adapter = MediaPosterAdapter(continueItems, scaleFactor) { item ->
                repository.openMediaInStremioOrApp(activeProfile.id, item)
            }
        } else {
            sectionContinueWatching.visibility = View.GONE
        }

        repository.fetchPersonalizedRecommendations(activeProfile) { items ->
            runOnUiThread {
                heroViewPager.adapter = HeroBannerAdapter(items.take(5)) { item ->
                    repository.openMediaInStremioOrApp(activeProfile.id, item)
                }
                rvRecommendations.adapter = MediaPosterAdapter(items, scaleFactor) { item ->
                    repository.openMediaInStremioOrApp(activeProfile.id, item)
                }
            }
        }

        repository.fetchTrending { trendingItems ->
            runOnUiThread {
                rvTrending.adapter = MediaPosterAdapter(trendingItems, scaleFactor) { item ->
                    repository.openMediaInStremioOrApp(activeProfile.id, item)
                }
            }
        }

        loadInstalledApps(scaleFactor)
    }

    private fun loadInstalledApps(scaleFactor: Float) {
        val pm = packageManager
        val tvIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val generalIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)

        val combined = (pm.queryIntentActivities(tvIntent, PackageManager.MATCH_ALL) +
                pm.queryIntentActivities(generalIntent, PackageManager.MATCH_ALL))
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm)
                )
            }.sortedBy { it.label.lowercase() }

        rvApps.adapter = AppIconAdapter(combined, scaleFactor) { app ->
            val launch = pm.getLeanbackLaunchIntentForPackage(app.packageName)
                ?: pm.getLaunchIntentForPackage(app.packageName)
            launch?.let { startActivity(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        autoScrollHandler.postDelayed(autoScrollRunnable, 7000)
    }

    override fun onPause() {
        super.onPause()
        autoScrollHandler.removeCallbacks(autoScrollRunnable)
    }
}
