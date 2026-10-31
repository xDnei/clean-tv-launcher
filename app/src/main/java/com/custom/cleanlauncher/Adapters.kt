package com.custom.cleanlauncher

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.net.URL
import kotlin.concurrent.thread

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

object ImageLoader {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(url: String, imageView: ImageView) {
        if (url.isEmpty() || !url.startsWith("http")) return
        thread {
            try {
                val bmp = BitmapFactory.decodeStream(URL(url).openStream())
                mainHandler.post { imageView.setImageBitmap(bmp) }
            } catch (_: Exception) {}
        }
    }
}

class HeroBannerAdapter(
    private val items: List<MediaItem>,
    private val onWatchClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<HeroBannerAdapter.HeroViewHolder>() {

    class HeroViewHolder(
        val root: FrameLayout,
        val backdrop: ImageView,
        val title: TextView,
        val overview: TextView,
        val btnWatch: Button
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
        val ctx = parent.context
        val root = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val backdrop = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.65f
        }

        val infoColumn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.START
            setPadding(72, 40, 420, 36)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(ctx).apply {
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val overview = TextView(ctx).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#DDDDDD"))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 8, 0, 16)
        }

        val btnWatch = Button(ctx).apply {
            text = "Assistir no Stremio"
            isFocusable = true
        }

        infoColumn.addView(title)
        infoColumn.addView(overview)
        infoColumn.addView(btnWatch)
        root.addView(backdrop)
        root.addView(infoColumn)

        return HeroViewHolder(root, backdrop, title, overview, btnWatch)
    }

    override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.overview.text = item.overview
        ImageLoader.load(item.backdropUrl, holder.backdrop)
        holder.btnWatch.setOnClickListener { onWatchClick(item) }
    }

    override fun getItemCount(): Int = items.size
}

class MediaPosterAdapter(
    private val items: List<MediaItem>,
    private val scaleFactor: Float = 1.0f,
    private val onClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaPosterAdapter.PosterViewHolder>() {

    class PosterViewHolder(val container: FrameLayout, val poster: ImageView, val label: TextView) :
        RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterViewHolder {
        val ctx = parent.context
        val width = (220 * scaleFactor).toInt()
        val height = (330 * scaleFactor).toInt()

        val container = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.MarginLayoutParams(width, height).apply {
                marginEnd = 24
            }
            isFocusable = true
            isClickable = true
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#1E1E1E"))
            }
        }

        val poster = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val label = TextView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.parseColor("#99000000"))
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        container.addView(poster)
        container.addView(label)
        return PosterViewHolder(container, poster, label)
    }

    override fun onBindViewHolder(holder: PosterViewHolder, position: Int) {
        val item = items[position]
        holder.label.text = item.title
        ImageLoader.load(item.posterUrl, holder.poster)
        holder.container.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}

class AppIconAdapter(
    private val apps: List<AppEntry>,
    private val scaleFactor: Float = 1.0f,
    private val onClick: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppIconAdapter.AppViewHolder>() {

    class AppViewHolder(val container: LinearLayout, val icon: ImageView, val title: TextView) :
        RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val ctx = parent.context
        val width = (200 * scaleFactor).toInt()
        val height = (160 * scaleFactor).toInt()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.MarginLayoutParams(width, height).apply {
                marginEnd = 20
            }
            isFocusable = true
            isClickable = true
            setPadding(16, 16, 16, 16)
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#222222"))
            }
        }

        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80)
        }

        val title = TextView(ctx).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 8, 0, 0)
        }

        container.addView(icon)
        container.addView(title)
        return AppViewHolder(container, icon, title)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.title.text = app.label
        holder.container.setOnClickListener { onClick(app) }
    }

    override fun getItemCount(): Int = apps.size
}
