package com.custom.cleanlauncher

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {
    private lateinit var rootContainer: FrameLayout
    private lateinit var wallpaperBackground: ImageView
    private lateinit var heroViewPager: ViewPager2
    private lateinit var btnProfileAvatar: ImageButton
    private lateinit var btnActivateHome: Button
    private lateinit var sectionContinueWatching: LinearLayout
    private lateinit var sectionRecommendations: LinearLayout
    private lateinit var sectionTrending: LinearLayout
    private lateinit var rvContinueWatching: RecyclerView
    private lateinit var rvRecommendations: RecyclerView
    private lateinit var rvTrending: RecyclerView
    private lateinit var rvApps: RecyclerView
    private lateinit var profileAndSettings: ProfileAndSettingsManager
    private lateinit var repository: ContentRepository

    private val handler = Handler(Looper.getMainLooper())
    private val autoScroll = object : Runnable {
        override fun run() {
            val count = heroViewPager.adapter?.itemCount ?: 0
            if (count > 1) {
                heroViewPager.currentItem = (heroViewPager.currentItem + 1) % count
            }
            handler.postDelayed(this, 7000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.rootContainer)
        wallpaperBackground = findViewById(R.id.wallpaperBackground)
        heroViewPager = findViewById(R.id.heroViewPager)
        btnProfileAvatar = findViewById(R.id.btnProfileAvatar)
        btnActivateHome = findViewById(R.id.btnActivateHome)
        sectionContinueWatching = findViewById(R.id.sectionContinueWatching)
        sectionRecommendations = findViewById(R.id.sectionRecommendations)
        sectionTrending = findViewById(R.id.sectionTrending)
        rvContinueWatching = findViewById(R.id.rvContinueWatching)
        rvRecommendations = findViewById(R.id.rvRecommendations)
        rvTrending = findViewById(R.id.rvTrending)
        rvApps = findViewById(R.id.rvApps)

        listOf(rvContinueWatching, rvRecommendations, rvTrending, rvApps).forEach {
            it.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        }

        repository = ContentRepository(this)
        profileAndSettings = ProfileAndSettingsManager(this) { refreshLauncherUI() }

        UiUtils.attachFluidFocus(btnProfileAvatar, Color.parseColor("#252530"), Color.parseColor("#3E3E50"), 24, 1.15f)
        UiUtils.attachFluidFocus(btnActivateHome, Color.parseColor("#1E3A5F"), Color.parseColor("#2563EB"), 12, 1.10f)

        btnProfileAvatar.setOnClickListener {
            profileAndSettings.showProfileDropdownMenu()
        }

        btnActivateHome.setOnClickListener {
            showHomeActivationDialog()
        }

        refreshLauncherUI()
    }

    private fun isHomeServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun showHomeActivationDialog() {
        val options = arrayOf(
            "1. Ativar Botão Home (Acessibilidade da TV)",
            "2. Escolher App de Início Padrão do Android"
        )
        AlertDialog.Builder(this)
            .setTitle("Definir Clean Launcher como Padrão")
            .setItems(options) { _, which ->
                if (which == 0) {
                    try {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        Toast.makeText(this, "Ative 'Clean Launcher - Botão Home' na lista", Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } else {
                    try {
                        startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        val selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(Intent.createChooser(selector, "Selecione o Clean Launcher"))
                    }
                }
            }
            .show()
    }

    private fun refreshLauncherUI() {
        val prefs = getSharedPreferences("clean_launcher_prefs", MODE_PRIVATE)
        val profile = profileAndSettings.getActiveProfile()
        val scale = 0.85f + (prefs.getInt("icon_scale_offset", 15) / 100f)
        val isWhite = prefs.getString("theme_mode", "BLACK") == "WHITE"

        btnActivateHome.visibility = if (isHomeServiceEnabled()) View.GONE else View.VISIBLE

        rootContainer.setBackgroundColor(if (isWhite) Color.parseColor("#F5F5F7") else Color.parseColor("#08080A"))
        val textColor = if (isWhite) Color.parseColor("#121212") else Color.parseColor("#FFFFFF")
        listOf<TextView>(
            findViewById(R.id.titleContinueWatching),
            findViewById(R.id.titleRecommendations),
            findViewById(R.id.titleTrending),
            findViewById(R.id.titleApps)
        ).forEach { it.setTextColor(textColor) }

        val wallpaper = prefs.getString("wallpaper_uri", "") ?: ""
        if (wallpaper.isNotEmpty()) {
            wallpaperBackground.visibility = View.VISIBLE
            ImageLoader.load(wallpaper, wallpaperBackground)
        } else {
            wallpaperBackground.visibility = View.GONE
        }

        val continueItems = repository.getContinueWatchingList(profile.id)
        if (prefs.getBoolean("show_continue_watching", true) && continueItems.isNotEmpty()) {
            sectionContinueWatching.visibility = View.VISIBLE
            rvContinueWatching.adapter = MediaPosterAdapter(continueItems, scale) {
                repository.openMediaInStremioOrApp(profile.id, it)
            }
        } else {
            sectionContinueWatching.visibility = View.GONE
        }

        repository.fetchPersonalizedRecommendations(profile) { items ->
            runOnUiThread {
                if (items.isNotEmpty()) {
                    sectionRecommendations.visibility = View.VISIBLE
                    heroViewPager.adapter = HeroBannerAdapter(items.take(5)) {
                        repository.openMediaInStremioOrApp(profile.id, it)
                    }
                    rvRecommendations.adapter = MediaPosterAdapter(items, scale) {
                        repository.openMediaInStremioOrApp(profile.id, it)
                    }
                } else {
                    sectionRecommendations.visibility = View.GONE
                    val welcomeBanner = listOf(
                        MediaItem(
                            id = -999,
                            imdbId = null,
                            title = "Clean TV Launcher",
                            overview = "Navegação fluida ativa. Adicione sua chave TMDb nas configurações de perfil para carregar filmes e séries em destaque.",
                            posterUrl = "",
                            backdropUrl = ""
                        )
                    )
                    heroViewPager.adapter = HeroBannerAdapter(welcomeBanner) {
                        profileAndSettings.showProfileDropdownMenu()
                    }
                }
            }
        }

        repository.fetchTrending { items ->
            runOnUiThread {
                if (items.isNotEmpty()) {
                    sectionTrending.visibility = View.VISIBLE
                    rvTrending.adapter = MediaPosterAdapter(items, scale) {
                        repository.openMediaInStremioOrApp(profile.id, it)
                    }
                } else {
                    sectionTrending.visibility = View.GONE
                }
            }
        }

        val pm = packageManager
        val apps = (
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), PackageManager.MATCH_ALL) +
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        ).distinctBy { it.activityInfo.packageName }
         .filter { it.activityInfo.packageName != packageName }
         .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
         .sortedBy { it.label.lowercase() }

        rvApps.adapter = AppIconAdapter(apps, scale) { app ->
            val launchIntent = pm.getLeanbackLaunchIntentForPackage(app.packageName) ?: pm.getLaunchIntentForPackage(app.packageName)
            launchIntent?.let {
                startActivity(it)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        rvApps.postDelayed({
            if ( currentFocus == null) {
                rvApps.getChildAt(0)?.requestFocus()
            }
        }, 250)
    }

    override fun onResume() {
        super.onResume()
        btnActivateHome.visibility = if (isHomeServiceEnabled()) View.GONE else View.VISIBLE
        handler.postDelayed(autoScroll, 7000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoScroll)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Impede que o botão Voltar feche o Launcher na TV
    }
}
