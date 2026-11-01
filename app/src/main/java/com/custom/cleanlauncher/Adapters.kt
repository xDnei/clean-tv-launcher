package com.custom.cleanlauncher

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import java.net.URL
import kotlin.concurrent.thread

data class AppEntry(val label: String, val packageName: String, val icon: Drawable)

object UiUtils {
    private val interpolator = OvershootInterpolator(1.3f)

    fun dp(ctx: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            ctx.resources.displayMetrics
        ).toInt()
    }

    fun attachFluidFocus(
        view: View,
        normalBgColor: Int = Color.parseColor("#1A1A22"),
        focusedBgColor: Int = Color.parseColor("#2E2E3A"),
        cornerRadiusDp: Int = 14,
        scaleUp: Float = 1.14f,
        onFocusExtra: ((Boolean) -> Unit)? = null
    ) {
        val ctx = view.context
        val radiusPx = dp(ctx, cornerRadiusDp).toFloat()
        val strokeWidthPx = dp(ctx, 3)

        val bg = GradientDrawable().apply {
            cornerRadius = radiusPx
            setColor(normalBgColor)
            setStroke(0, Color.TRANSPARENT)
        }
        view.background = bg
        view.isFocusable = true
        view.isClickable = true

        view.setOnFocusChangeListener { v, hasFocus ->
            v.animate().cancel()
            if (hasFocus) {
                bg.setColor(focusedBgColor)
                bg.setStroke(strokeWidthPx, Color.WHITE)
                v.translationZ = 24f
                v.animate()
                    .scaleX(scaleUp)
                    .scaleY(scaleUp)
                    .setDuration(190)
                    .setInterpolator(interpolator)
                    .start()
            } else {
                bg.setColor(normalBgColor)
                bg.setStroke(0, Color.TRANSPARENT)
                v.translationZ = 0f
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
            onFocusExtra?.invoke(hasFocus)
        }
    }
}

object ImageLoader {
    private val handler = Handler(Looper.getMainLooper())
    fun load(url: String, imageView: ImageView) {
        if (url.isEmpty() || !url.startsWith("http")) return
        thread {
            try {
                val bmp = BitmapFactory.decodeStream(URL(url).openStream())
                handler.post { imageView.setImageBitmap(bmp) }
            } catch (_: Exception) {}
        }
    }
}

class HeroBannerAdapter(
    private val items: List<MediaItem>,
    private val onActionClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<HeroBannerAdapter.VH>() {

    class VH(val root: FrameLayout, val backdrop: ImageView, val title: TextView, val overview: TextView, val btnWatch: Button) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val root = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val backdrop = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.55f
        }
        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.START
            setPadding(UiUtils.dp(ctx, 42), UiUtils.dp(ctx, 24), UiUtils.dp(ctx, 200), UiUtils.dp(ctx, 20))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clipChildren = false
            clipToPadding = false
        }
        val title = TextView(ctx).apply {
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 1
        }
        val overview = TextView(ctx).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
            maxLines = 2
            setPadding(0, UiUtils.dp(ctx, 6), 0, UiUtils.dp(ctx, 12))
        }
        val btn = Button(ctx).apply {
            text = "Abrir"
            textAllCaps = false
            setTextColor(Color.WHITE)
            setPadding(UiUtils.dp(ctx, 20), UiUtils.dp(ctx, 8), UiUtils.dp(ctx, 20), UiUtils.dp(ctx, 8))
        }
        UiUtils.attachFluidFocus(btn, Color.parseColor("#252530"), Color.parseColor("#E50914"), 10, 1.08f)

        info.addView(title)
        info.addView(overview)
        info.addView(btn)
        root.addView(backdrop)
        root.addView(info)
        return VH(root, backdrop, title, overview, btn)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val item = items[pos]
        holder.title.text = item.title
        holder.overview.text = item.overview
        holder.btnWatch.text = if (item.id == -999) "Configurar Launcher" else "Assistir no Stremio"
        ImageLoader.load(item.backdropUrl, holder.backdrop)
        holder.btnWatch.setOnClickListener { onActionClick(item) }
    }

    override fun getItemCount() = items.size
}

class MediaPosterAdapter(
    private val items: List<MediaItem>,
    private val scale: Float = 1.0f,
    private val onClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaPosterAdapter.VH>() {

    class VH(val container: FrameLayout, val poster: ImageView, val label: TextView) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val w = (UiUtils.dp(ctx, 125) * scale).toInt()
        val h = (UiUtils.dp(ctx, 185) * scale).toInt()

        val container = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.MarginLayoutParams(w, h).apply {
                marginEnd = UiUtils.dp(ctx, 18)
                topMargin = UiUtils.dp(ctx, 10)
                bottomMargin = UiUtils.dp(ctx, 10)
            }
            clipToOutline = true
        }
        val poster = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val label = TextView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
            setPadding(UiUtils.dp(ctx, 8), UiUtils.dp(ctx, 6), UiUtils.dp(ctx, 8), UiUtils.dp(ctx, 6))
            setBackgroundColor(Color.parseColor("#B3000000"))
            setTextColor(Color.parseColor("#DDDDDD"))
            textSize = 11f
            maxLines = 1
        }
        container.addView(poster)
        container.addView(label)

        UiUtils.attachFluidFocus(container, Color.parseColor("#181820"), Color.parseColor("#2A2A36"), 12, 1.12f) { hasFocus ->
            label.setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#DDDDDD"))
            label.setTypeface(null, if (hasFocus) Typeface.BOLD else Typeface.NORMAL)
        }

        return VH(container, poster, label)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val item = items[pos]
        holder.label.text = item.title
        ImageLoader.load(item.posterUrl, holder.poster)
        holder.container.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}

class AppIconAdapter(
    private val apps: List<AppEntry>,
    private val scale: Float = 1.0f,
    private val onClick: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppIconAdapter.VH>() {

    class VH(val container: LinearLayout, val icon: ImageView, val title: TextView) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val w = (UiUtils.dp(ctx, 130) * scale).toInt()
        val h = (UiUtils.dp(ctx, 105) * scale).toInt()
        val iconSize = (UiUtils.dp(ctx, 48) * scale).toInt()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.MarginLayoutParams(w, h).apply {
                marginEnd = UiUtils.dp(ctx, 16)
                topMargin = UiUtils.dp(ctx, 10)
                bottomMargin = UiUtils.dp(ctx, 10)
            }
            setPadding(UiUtils.dp(ctx, 10), UiUtils.dp(ctx, 10), UiUtils.dp(ctx, 10), UiUtils.dp(ctx, 10))
        }
        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val title = TextView(ctx).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#BBBBBB"))
            maxLines = 1
            setPadding(0, UiUtils.dp(ctx, 8), 0, 0)
        }
        container.addView(icon)
        container.addView(title)

        UiUtils.attachFluidFocus(container, Color.parseColor("#181820"), Color.parseColor("#2E2E3E"), 16, 1.15f) { hasFocus ->
            title.setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#BBBBBB"))
            title.setTypeface(null, if (hasFocus) Typeface.BOLD else Typeface.NORMAL)
        }

        return VH(container, icon, title)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val app = apps[pos]
        holder.icon.setImageDrawable(app.icon)
        holder.title.text = app.label
        holder.container.setOnClickListener { onClick(app) }
    }

    override fun getItemCount() = apps.size
}
