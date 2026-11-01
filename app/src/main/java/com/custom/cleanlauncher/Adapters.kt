package com.custom.cleanlauncher

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView

data class AppEntry(val label: String, val packageName: String, val icon: Drawable?)

class MediaPosterAdapter(
    private val items: List<MediaItem>,
    private val scale: Float = 1.0f,
    private val palette: ThemePalette,
    private val onClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaPosterAdapter.VH>() {

    class VH(val container: FrameLayout, val poster: ImageView, val label: TextView) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val w = (UiUtils.dp(ctx, 134) * scale).toInt()
        val h = (UiUtils.dp(ctx, 194) * scale).toInt()
        val pad = UiUtils.dp(ctx, 2)

        val container = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.MarginLayoutParams(w, h).apply {
                marginEnd = (UiUtils.dp(ctx, 14) * scale).toInt().coerceAtLeast(10)
                topMargin = UiUtils.dp(ctx, 8)
                bottomMargin = UiUtils.dp(ctx, 8)
            }
            setPadding(pad, pad, pad, pad)
            clipToOutline = true
        }

        val poster = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val bottomGradient = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(Color.parseColor("#E6000000"), Color.parseColor("#80000000"), Color.TRANSPARENT)
        ).apply {
            cornerRadius = UiUtils.dpFloat(ctx, 12f)
        }

        val label = TextView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            setPadding(UiUtils.dp(ctx, 10), UiUtils.dp(ctx, 18), UiUtils.dp(ctx, 10), UiUtils.dp(ctx, 8))
            background = bottomGradient
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 12f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            setTypeface(null, Typeface.BOLD)
        }

        container.addView(poster)
        container.addView(label)
        return VH(container, poster, label)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val item = items[pos]
        holder.label.text = item.title
        UiUtils.attachSingleFocus(
            view = holder.container,
            palette = palette,
            cornerRadiusDp = 14f,
            scaleUp = 1.05f
        ) { hasFocus ->
            holder.label.setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#E2E8F0"))
        }
        ImageLoader.load(item.posterUrl, holder.poster)
        holder.container.setOnClickListener { onClick(item) }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        UiUtils.resetFocusState(holder.container, palette, 14f)
    }

    override fun getItemCount() = items.size
}

class AppIconAdapter(
    private val apps: List<AppEntry>,
    private val scale: Float = 1.0f,
    private val palette: ThemePalette,
    private val onClick: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppIconAdapter.VH>() {

    class VH(val container: LinearLayout, val icon: ImageView, val title: TextView) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val w = (UiUtils.dp(ctx, 128) * scale).toInt()
        val h = (UiUtils.dp(ctx, 76) * scale).toInt()
        val iconSize = (UiUtils.dp(ctx, 34) * scale).toInt()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.MarginLayoutParams(w, h).apply {
                marginEnd = (UiUtils.dp(ctx, 12) * scale).toInt().coerceAtLeast(8)
                topMargin = UiUtils.dp(ctx, 6)
                bottomMargin = UiUtils.dp(ctx, 6)
            }
            setPadding(UiUtils.dp(ctx, 10), UiUtils.dp(ctx, 8), UiUtils.dp(ctx, 10), UiUtils.dp(ctx, 8))
        }

        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val title = TextView(ctx).apply {
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTextColor(palette.textSecondary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, UiUtils.dp(ctx, 4), 0, 0)
        }

        container.addView(icon)
        container.addView(title)
        return VH(container, icon, title)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val app = apps[pos]
        if (app.icon != null) {
            holder.icon.setImageDrawable(app.icon)
        } else {
            holder.icon.setImageResource(R.drawable.ic_default_avatar)
        }
        holder.title.text = app.label
        holder.title.setTextColor(palette.textSecondary)

        UiUtils.attachSingleFocus(
            view = holder.container,
            palette = palette,
            cornerRadiusDp = 14f,
            scaleUp = 1.05f
        ) { hasFocus ->
            holder.title.setTextColor(if (hasFocus) palette.textPrimary else palette.textSecondary)
            holder.title.setTypeface(null, if (hasFocus) Typeface.BOLD else Typeface.NORMAL)
        }

        holder.container.setOnClickListener { onClick(app) }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        UiUtils.resetFocusState(holder.container, palette, 14f)
    }

    override fun getItemCount() = apps.size
}
