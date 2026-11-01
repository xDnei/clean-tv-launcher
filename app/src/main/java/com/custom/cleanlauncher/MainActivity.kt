package com.custom.cleanlauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var rootContainer: FrameLayout
    private lateinit var heroBackdropImage: ImageView
    private lateinit var heroGradientScrim: View
    private lateinit var heroBannerFocusArea: LinearLayout
    private lateinit var heroTitle: TextView
    private lateinit var heroOverview: TextView
    private lateinit var heroIndicatorsContainer: LinearLayout
    private lateinit var btnProfileAvatar: ImageView
    private lateinit var pillForYou: TextView
    private lateinit var pillMovies: TextView
    private lateinit var pillSeries: TextView
    private lateinit var pillApps: TextView
    private lateinit var pillSettings: TextView
    private lateinit var tvClock: TextClock
    private lateinit var mainScrollView: NestedScrollView

    private lateinit var sectionApps: LinearLayout
    private lateinit var sectionContinueWatching: LinearLayout
    private lateinit var sectionRecommendations: LinearLayout
    private lateinit var sectionTrending: LinearLayout
    private lateinit var rvContinueWatching: RecyclerView
    private lateinit var rvRecommendations: RecyclerView
    private lateinit var rvTrending: RecyclerView
    private lateinit var rvApps: RecyclerView

    private lateinit var loadingOverlay: LinearLayout
    private lateinit var loadingSymbol: ImageView

    private lateinit var profileAndSettings: ProfileAndSettingsManager
    private lateinit var repository: ContentRepository

    private var activeCategory = "ALL"
    private var carouselItems: List<MediaItem> = emptyList()
    private var currentCarouselIndex = 0
    private var isBannerFocused = false
    private var isDialogShowing = false
    private var initialLoadComplete = false
    private var lastFocusedViewBeforeDialog: View? = null
    private var pendingGalleryCallback: ((String) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val carouselRunnable = object : Runnable {
        override fun run() {
            if (!isBannerFocused && !isDialogShowing && hasWindowFocus() && carouselItems.size > 1) {
                currentCarouselIndex = (currentCarouselIndex + 1) % carouselItems.size
                renderHeroSlide(currentCarouselIndex)
            }
            handler.postDelayed(this, 7000)
        }
    }

    companion object {
        private const val REQ_PICK_AVATAR_IMAGE = 901
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.rootContainer)
        heroBackdropImage = findViewById(R.id.heroBackdropImage)
        heroGradientScrim = findViewById(R.id.heroGradientScrim)
        heroBannerFocusArea = findViewById(R.id.heroBannerFocusArea)
        heroTitle = findViewById(R.id.heroTitle)
        heroOverview = findViewById(R.id.heroOverview)
        heroIndicatorsContainer = findViewById(R.id.heroIndicatorsContainer)
        btnProfileAvatar = findViewById(R.id.btnProfileAvatar)
        pillForYou = findViewById(R.id.pillForYou)
        pillMovies = findViewById(R.id.pillMovies)
        pillSeries = findViewById(R.id.pillSeries)
        pillApps = findViewById(R.id.pillApps)
        pillSettings = findViewById(R.id.pillSettings)
        tvClock = findViewById(R.id.tvClock)
        mainScrollView = findViewById(R.id.mainScrollView)

        sectionApps = findViewById(R.id.sectionApps)
        sectionContinueWatching = findViewById(R.id.sectionContinueWatching)
        sectionRecommendations = findViewById(R.id.sectionRecommendations)
        sectionTrending = findViewById(R.id.sectionTrending)
        rvContinueWatching = findViewById(R.id.rvContinueWatching)
        rvRecommendations = findViewById(R.id.rvRecommendations)
        rvTrending = findViewById(R.id.rvTrending)
        rvApps = findViewById(R.id.rvApps)

        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingSymbol = findViewById(R.id.loadingSymbol)

        drawMinimalLoadingSymbol()

        listOf(rvContinueWatching, rvRecommendations, rvTrending, rvApps).forEach {
            it.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            it.itemAnimator = null
        }

        repository = ContentRepository(this)
        profileAndSettings = ProfileAndSettingsManager(
            context = this,
            onRequestGalleryPick = { callback ->
                pendingGalleryCallback = callback
                openDeviceGalleryPicker()
            },
            onStateChanged = { refreshLauncherUI() }
        )

        setupHeaderAndBannerInteractions()
        setupScrollListenerForBackdropFade()
        refreshLauncherUI()

        handler.postDelayed({ hideLoadingOverlaySmoothly() }, 1400)
    }

    private fun setupScrollListenerForBackdropFade() {
        // Conforme o utilizador desce na tela, a imagem de fundo do banner desvanece suavemente para a cor de fundo
        mainScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            val maxScroll = UiUtils.dp(this, 220).toFloat()
            val alpha = (1.0f - (scrollY / maxScroll)).coerceIn(0.0f, 1.0f)
            heroBackdropImage.alpha = alpha * 0.85f
        })
    }

    private fun drawMinimalLoadingSymbol() {
        val size = UiUtils.dp(this, 68).coerceAtLeast(68)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = UiUtils.dpFloat(this@MainActivity, 3.5f)
            color = Color.parseColor("#38BDF8")
        }
        val pad = UiUtils.dpFloat(this, 8f)
        val rect = RectF(pad, pad, size - pad, size - pad)
        canvas.drawRoundRect(rect, UiUtils.dpFloat(this, 16f), UiUtils.dpFloat(this, 16f), paint)

        paint.style = Paint.Style.FILL
        val playPath = Path().apply {
            moveTo(size * 0.40f, size * 0.33f)
            lineTo(size * 0.66f, size * 0.50f)
            lineTo(size * 0.40f, size * 0.67f)
            close()
        }
        canvas.drawPath(playPath, paint)
        loadingSymbol.setImageBitmap(bmp)
    }

    private fun hideLoadingOverlaySmoothly() {
        if (initialLoadComplete) return
        initialLoadComplete = true
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(280)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
                if (currentFocus == null) {
                    rvApps.getChildAt(0)?.requestFocus()
                }
            }
            .start()
    }

    private fun setupHeaderAndBannerInteractions() {
        btnProfileAvatar.setOnClickListener {
            lastFocusedViewBeforeDialog = currentFocus
            isDialogShowing = true
            profileAndSettings.showProfileDropdownMenu {
                isDialogShowing = false
                lastFocusedViewBeforeDialog?.requestFocus()
            }
        }

        val pills = listOf(
            pillForYou to "ALL",
            pillMovies to "MOVIES",
            pillSeries to "SERIES",
            pillApps to "APPS"
        )
        pills.forEach { (view, category) ->
            view.setOnClickListener {
                activeCategory = category
                refreshLauncherUI()
                if (category == "APPS") {
                    rvApps.getChildAt(0)?.requestFocus()
                }
            }
        }

        pillSettings.setOnClickListener {
            lastFocusedViewBeforeDialog = currentFocus
            isDialogShowing = true
            profileAndSettings.showLauncherSettingsDialog {
                isDialogShowing = false
                lastFocusedViewBeforeDialog?.requestFocus()
            }
        }

        heroBannerFocusArea.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && carouselItems.size > 1) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        currentCarouselIndex = (currentCarouselIndex + 1) % carouselItems.size
                        renderHeroSlide(currentCarouselIndex)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        currentCarouselIndex = if (currentCarouselIndex - 1 < 0) carouselItems.size - 1 else currentCarouselIndex - 1
                        renderHeroSlide(currentCarouselIndex)
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        heroBannerFocusArea.setOnClickListener {
            val activeItem = carouselItems.getOrNull(currentCarouselIndex) ?: return@setOnClickListener
            if (activeItem.id == -999) {
                lastFocusedViewBeforeDialog = currentFocus
                isDialogShowing = true
                profileAndSettings.showLauncherSettingsDialog {
                    isDialogShowing = false
                    lastFocusedViewBeforeDialog?.requestFocus()
                }
            } else {
                val prof = profileAndSettings.getActiveProfile()
                repository.openMediaInStremioOrApp(prof.id, activeItem)
            }
        }
    }

    private fun refreshLauncherUI() {
        val prefs = getSharedPreferences("clean_launcher_prefs", MODE_PRIVATE)
        val palette = ThemeManager.getPalette(this)
        val profile = profileAndSettings.getActiveProfile()
        val appScale = profileAndSettings.getAppIconScale()
        val posterScale = profileAndSettings.getPosterCardScale()

        rootContainer.setBackgroundColor(palette.bgPrimary)
        tvClock.setTextColor(palette.textPrimary)
        heroTitle.setTextColor(palette.textPrimary)
        heroOverview.setTextColor(palette.textSecondary)

        val scrimGradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.argb(90, Color.red(palette.scrimColor), Color.green(palette.scrimColor), Color.blue(palette.scrimColor)),
                Color.argb(190, Color.red(palette.scrimColor), Color.green(palette.scrimColor), Color.blue(palette.scrimColor)),
                palette.bgPrimary
            )
        )
        heroGradientScrim.background = scrimGradient

        listOf<TextView>(
            findViewById(R.id.titleApps),
            findViewById(R.id.titleRecommendations),
            findViewById(R.id.titleContinueWatching),
            findViewById(R.id.titleTrending)
        ).forEach { it.setTextColor(palette.textPrimary) }

        AvatarRenderer.renderInto(this, btnProfileAvatar, profile.avatarType, profile.customAvatarPath, 42, false)
        UiUtils.attachSingleFocus(
            view = btnProfileAvatar,
            palette = palette,
            cornerRadiusDp = 21f,
            scaleUp = 1.06f,
            normalFill = Color.TRANSPARENT,
            focusedFill = Color.TRANSPARENT
        ) { focused ->
            AvatarRenderer.renderInto(this, btnProfileAvatar, profile.avatarType, profile.customAvatarPath, 42, focused)
        }

        val pillMap = listOf(
            pillForYou to (activeCategory == "ALL"),
            pillMovies to (activeCategory == "MOVIES"),
            pillSeries to (activeCategory == "SERIES"),
            pillApps to (activeCategory == "APPS"),
            pillSettings to false
        )
        pillMap.forEach { (pillView, isSelected) ->
            val baseFill = if (isSelected) palette.pillSelectedBg else palette.pillNormalBg
            val baseStroke = if (isSelected) palette.cardBorderFocused else Color.TRANSPARENT
            pillView.setTextColor(if (isSelected) palette.pillSelectedText else palette.textSecondary)
            pillView.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)

            UiUtils.attachSingleFocus(
                view = pillView,
                palette = palette,
                cornerRadiusDp = 17f,
                scaleUp = 1.05f,
                normalFill = baseFill,
                focusedFill = palette.pillSelectedBg,
                normalStroke = baseStroke,
                focusedStroke = palette.cardBorderFocused
            ) { focused ->
                pillView.setTextColor(
                    when {
                        focused -> palette.pillSelectedText
                        isSelected -> palette.pillSelectedText
                        else -> palette.textSecondary
                    }
                )
            }
        }

        UiUtils.attachSingleFocus(
            view = heroBannerFocusArea,
            palette = palette,
            cornerRadiusDp = 16f,
            scaleUp = 1.01f,
            normalFill = Color.TRANSPARENT,
            focusedFill = Color.argb(28, 56, 189, 248),
            normalStroke = Color.TRANSPARENT,
            focusedStroke = palette.cardBorderFocused
        ) { focused ->
            isBannerFocused = focused
        }

        val pm = packageManager
        val apps = (
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), PackageManager.MATCH_ALL) +
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        ).distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }

        rvApps.adapter = AppIconAdapter(apps, appScale, palette) { app ->
            val launchIntent = pm.getLeanbackLaunchIntentForPackage(app.packageName) ?: pm.getLaunchIntentForPackage(app.packageName)
            launchIntent?.let {
                startActivity(it)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        if (activeCategory == "APPS") {
            sectionContinueWatching.visibility = View.GONE
            sectionRecommendations.visibility = View.GONE
            sectionTrending.visibility = View.GONE
            hideLoadingOverlaySmoothly()
            return
        }

        val continueItems = repository.getContinueWatchingList(profile.id)
        if (prefs.getBoolean("show_continue_watching", true) && continueItems.isNotEmpty()) {
            sectionContinueWatching.visibility = View.VISIBLE
            rvContinueWatching.adapter = MediaPosterAdapter(continueItems, posterScale, palette) {
                repository.openMediaInStremioOrApp(profile.id, it)
            }
        } else {
            sectionContinueWatching.visibility = View.GONE
        }

        repository.fetchPersonalizedRecommendations(profile, activeCategory) { items ->
            runOnUiThread {
                if (items.isNotEmpty()) {
                    sectionRecommendations.visibility = View.VISIBLE
                    rvRecommendations.adapter = MediaPosterAdapter(items, posterScale, palette) {
                        repository.openMediaInStremioOrApp(profile.id, it)
                    }
                    carouselItems = items.take(6)
                    currentCarouselIndex = 0
                    renderHeroSlide(0)
                } else {
                    sectionRecommendations.visibility = View.GONE
                    carouselItems = listOf(
                        MediaItem(
                            id = -999,
                            imdbId = null,
                            title = "Destaques para ${profile.name}",
                            overview = "Configure sua chave TMDb em Configurações para exibir recomendações automáticas no banner.",
                            posterUrl = "",
                            backdropUrl = prefs.getString("wallpaper_uri", "") ?: ""
                        )
                    )
                    currentCarouselIndex = 0
                    renderHeroSlide(0)
                }
                hideLoadingOverlaySmoothly()
            }
        }

        repository.fetchTrending(activeCategory) { items ->
            runOnUiThread {
                if (items.isNotEmpty()) {
                    sectionTrending.visibility = View.VISIBLE
                    rvTrending.adapter = MediaPosterAdapter(items, posterScale, palette) {
                        repository.openMediaInStremioOrApp(profile.id, it)
                    }
                } else {
                    sectionTrending.visibility = View.GONE
                }
            }
        }
    }

    private fun renderHeroSlide(index: Int) {
        if (carouselItems.isEmpty()) return
        val safeIdx = index.coerceIn(0, carouselItems.size - 1)
        val item = carouselItems[safeIdx]
        val palette = ThemeManager.getPalette(this)

        heroTitle.animate().alpha(0.3f).setDuration(120).withEndAction {
            heroTitle.text = item.title
            heroOverview.text = item.overview
            heroTitle.animate().alpha(1f).setDuration(180).start()
        }.start()

        ImageLoader.load(item.backdropUrl, heroBackdropImage, crossfade = true)

        heroIndicatorsContainer.removeAllViews()
        if (carouselItems.size > 1) {
            for (i in carouselItems.indices) {
                val isCurrent = i == safeIdx
                val dot = View(this).apply {
                    val w = UiUtils.dp(this@MainActivity, if (isCurrent) 20 else 6)
                    val h = UiUtils.dp(this@MainActivity, 6)
                    layoutParams = LinearLayout.LayoutParams(w, h).apply {
                        marginEnd = UiUtils.dp(this@MainActivity, 6)
                    }
                    background = GradientDrawable().apply {
                        cornerRadius = UiUtils.dpFloat(this@MainActivity, 3f)
                        setColor(if (isCurrent) palette.accentCyan else palette.cardBorderNormal)
                    }
                }
                heroIndicatorsContainer.addView(dot)
            }
        }
    }

    private fun openDeviceGalleryPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_PICK_AVATAR_IMAGE)
        } catch (_: Exception) {
            Toast.makeText(this, "Galeria indisponível neste dispositivo.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_AVATAR_IMAGE && resultCode == Activity.RESULT_OK) {
            val uri: Uri = data?.data ?: return
            try {
                val destFile = File(filesDir, "avatar_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                pendingGalleryCallback?.invoke(destFile.absolutePath)
            } catch (_: Exception) {
                Toast.makeText(this, "Erro ao carregar a imagem.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(carouselRunnable)
        handler.postDelayed(carouselRunnable, 7000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(carouselRunnable)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (activeCategory != "ALL") {
            activeCategory = "ALL"
            refreshLauncherUI()
        }
    }
}
