package com.custom.cleanlauncher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import java.io.File
import java.lang.ref.WeakReference

data class ThemePalette(
    val isLight: Boolean,
    val bgPrimary: Int,
    val bgDialog: Int,
    val cardSurface: Int,
    val cardSurfaceFocused: Int,
    val cardBorderNormal: Int,
    val cardBorderFocused: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accentCyan: Int,
    val pillNormalBg: Int,
    val pillSelectedBg: Int,
    val pillSelectedText: Int,
    val scrimColor: Int
)

object ThemeManager {
    val DARK = ThemePalette(
        isLight = false,
        bgPrimary = Color.parseColor("#090C12"),
        bgDialog = Color.parseColor("#111622"),
        cardSurface = Color.parseColor("#141923"),
        cardSurfaceFocused = Color.parseColor("#1C2433"),
        cardBorderNormal = Color.parseColor("#2A3244"),
        cardBorderFocused = Color.parseColor("#38BDF8"),
        textPrimary = Color.parseColor("#F8FAFC"),
        textSecondary = Color.parseColor("#94A3B8"),
        accentCyan = Color.parseColor("#38BDF8"),
        pillNormalBg = Color.parseColor("#1AFFFFFF"),
        pillSelectedBg = Color.parseColor("#3338BDF8"),
        pillSelectedText = Color.parseColor("#FFFFFF"),
        scrimColor = Color.parseColor("#090C12")
    )

    val LIGHT = ThemePalette(
        isLight = true,
        bgPrimary = Color.parseColor("#F1F5F9"),
        bgDialog = Color.parseColor("#FFFFFF"),
        cardSurface = Color.parseColor("#FFFFFF"),
        cardSurfaceFocused = Color.parseColor("#F8FAFC"),
        cardBorderNormal = Color.parseColor("#CBD5E1"),
        cardBorderFocused = Color.parseColor("#0284C7"),
        textPrimary = Color.parseColor("#0F172A"),
        textSecondary = Color.parseColor("#475569"),
        accentCyan = Color.parseColor("#0284C7"),
        pillNormalBg = Color.parseColor("#E2E8F0"),
        pillSelectedBg = Color.parseColor("#0284C7"),
        pillSelectedText = Color.parseColor("#FFFFFF"),
        scrimColor = Color.parseColor("#F1F5F9")
    )

    fun getPalette(context: Context): ThemePalette {
        val prefs = context.getSharedPreferences("clean_launcher_prefs", Context.MODE_PRIVATE)
        val isWhite = prefs.getString("theme_mode", "BLACK") == "WHITE"
        return if (isWhite) LIGHT else DARK
    }
}

object UiUtils {
    private val interpolator = DecelerateInterpolator()
    private var currentFocusedRef: WeakReference<View>? = null
    private var currentResetAction: (() -> Any?)? = null

    fun dp(ctx: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            ctx.resources.displayMetrics
        ).toInt()
    }

    fun dpFloat(ctx: Context, value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            ctx.resources.displayMetrics
        )
    }

    fun createAdaptiveDrawable(
        ctx: Context,
        fillColor: Int,
        strokeColor: Int,
        strokeWidthDp: Float,
        cornerRadiusDp: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpFloat(ctx, cornerRadiusDp)
            setColor(fillColor)
            setStroke(dpFloat(ctx, strokeWidthDp).toInt().coerceAtLeast(1), strokeColor)
        }
    }

    fun resetFocusState(
        view: View,
        palette: ThemePalette,
        cornerRadiusDp: Float = 14f,
        normalFill: Int = palette.cardSurface,
        normalStroke: Int = palette.cardBorderNormal,
        normalStrokeDp: Float = if (palette.isLight) 1.5f else 1.2f
    ) {
        view.animate().cancel()
        view.scaleX = 1.0f
        view.scaleY = 1.0f
        view.translationZ = 0f
        view.background = createAdaptiveDrawable(
            view.context,
            normalFill,
            normalStroke,
            normalStrokeDp,
            cornerRadiusDp
        )
    }

    /**
     * Garante que apenas UM elemento tenha estado de foco ativo por vez em toda a tela.
     */
    fun attachSingleFocus(
        view: View,
        palette: ThemePalette,
        cornerRadiusDp: Float = 14f,
        scaleUp: Float = 1.05f,
        normalFill: Int = palette.cardSurface,
        focusedFill: Int = palette.cardSurfaceFocused,
        normalStroke: Int = palette.cardBorderNormal,
        focusedStroke: Int = palette.cardBorderFocused,
        onFocusChangedExtra: ((Boolean) -> Unit)? = null
    ) {
        val ctx = view.context
        val normalStrokeDp = if (palette.isLight) 1.5f else 1.2f
        val focusedStrokeDp = 2.6f

        resetFocusState(view, palette, cornerRadiusDp, normalFill, normalStroke, normalStrokeDp)
        view.isFocusable = true
        view.isClickable = true

        val applyUnfocused = {
            view.animate().cancel()
            view.background = createAdaptiveDrawable(ctx, normalFill, normalStroke, normalStrokeDp, cornerRadiusDp)
            view.translationZ = 0f
            view.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(140)
                .setInterpolator(interpolator)
                .start()
            onFocusChangedExtra?.invoke(false)
        }

        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val prevView = currentFocusedRef?.get()
                if (prevView != null && prevView != v) {
                    currentResetAction?.invoke()
                }
                currentFocusedRef = WeakReference(v)
                currentResetAction = applyUnfocused

                v.animate().cancel()
                v.background = createAdaptiveDrawable(ctx, focusedFill, focusedStroke, focusedStrokeDp, cornerRadiusDp)
                v.translationZ = dpFloat(ctx, 8f)
                v.animate()
                    .scaleX(scaleUp)
                    .scaleY(scaleUp)
                    .setDuration(160)
                    .setInterpolator(interpolator)
                    .start()
                onFocusChangedExtra?.invoke(true)
            } else {
                if (currentFocusedRef?.get() == v) {
                    currentFocusedRef = null
                    currentResetAction = null
                }
                applyUnfocused()
            }
        }
    }
}

object AvatarRenderer {
    val PRESET_KEYS = listOf("PRESET_PERSON", "PRESET_MOUNTAIN", "PRESET_GAME", "PRESET_CROWN", "PRESET_CAT")

    fun renderInto(
        context: Context,
        imageView: ImageView,
        avatarType: String,
        customPath: String?,
        sizeDp: Int = 64,
        isFocused: Boolean = false
    ) {
        val sizePx = UiUtils.dp(context, sizeDp).coerceAtLeast(64)
        val palette = ThemeManager.getPalette(context)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val radius = sizePx / 2f - UiUtils.dpFloat(context, 3f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Carrega foto customizada se disponível e válida
        var loadedCustom: Bitmap? = null
        if (avatarType == "CUSTOM" && !customPath.isNullOrEmpty()) {
            loadedCustom = loadCustomBitmap(context, customPath, sizePx)
        }

        if (loadedCustom != null) {
            val shader = BitmapShader(loadedCustom, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            paint.shader = shader
            canvas.drawCircle(cx, cy, radius, paint)
            paint.shader = null
        } else {
            // Fundo circular do avatar predefinido
            val bgColors = mapOf(
                "PRESET_PERSON" to Color.parseColor("#1E293B"),
                "PRESET_MOUNTAIN" to Color.parseColor("#0F2942"),
                "PRESET_GAME" to Color.parseColor("#1E1B4B"),
                "PRESET_CROWN" to Color.parseColor("#3B2110"),
                "PRESET_CAT" to Color.parseColor("#132A2E")
            )
            paint.style = Paint.Style.FILL
            paint.color = bgColors[avatarType] ?: Color.parseColor("#1E293B")
            canvas.drawCircle(cx, cy, radius, paint)

            // Desenho vetorial limpo para cada preset
            paint.color = Color.parseColor("#E2E8F0")
            drawPresetSymbol(canvas, paint, avatarType, cx, cy, radius)
        }

        // Borda circular adaptativa
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = UiUtils.dpFloat(context, if (isFocused) 2.8f else 1.5f)
        paint.color = if (isFocused) palette.cardBorderFocused else palette.cardBorderNormal
        canvas.drawCircle(cx, cy, radius, paint)

        imageView.setImageBitmap(bmp)
    }

    private fun loadCustomBitmap(context: Context, pathOrUri: String, targetSize: Int): Bitmap? {
        return try {
            val raw = if (pathOrUri.startsWith("/")) {
                val f = File(pathOrUri)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
            } else {
                context.contentResolver.openInputStream(Uri.parse(pathOrUri))?.use {
                    BitmapFactory.decodeStream(it)
                }
            } ?: return null

            val minDim = minOf(raw.width, raw.height)
            val x = (raw.width - minDim) / 2
            val y = (raw.height - minDim) / 2
            val cropped = Bitmap.createBitmap(raw, x, y, minDim, minDim)
            Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        } catch (_: Exception) {
            null
        }
    }

    private fun drawPresetSymbol(canvas: Canvas, paint: Paint, type: String, cx: Float, cy: Float, r: Float) {
        when (type) {
            "PRESET_MOUNTAIN" -> {
                val path = Path().apply {
                    moveTo(cx - r * 0.48f, cy + r * 0.28f)
                    lineTo(cx - r * 0.12f, cy - r * 0.22f)
                    lineTo(cx + r * 0.15f, cy + r * 0.12f)
                    lineTo(cx + r * 0.28f, cy - r * 0.08f)
                    lineTo(cx + r * 0.50f, cy + r * 0.28f)
                    close()
                }
                canvas.drawPath(path, paint)
                canvas.drawCircle(cx + r * 0.22f, cy - r * 0.26f, r * 0.10f, paint)
            }
            "PRESET_GAME" -> {
                val rect = RectF(cx - r * 0.44f, cy - r * 0.22f, cx + r * 0.44f, cy + r * 0.24f)
                canvas.drawRoundRect(rect, r * 0.18f, r * 0.18f, paint)
                paint.color = Color.parseColor("#1E1B4B")
                canvas.drawCircle(cx + r * 0.20f, cy - r * 0.04f, r * 0.06f, paint)
                canvas.drawCircle(cx + r * 0.28f, cy + r * 0.06f, r * 0.06f, paint)
                canvas.drawRect(cx - r * 0.28f, cy - r * 0.04f, cx - r * 0.12f, cy + r * 0.04f, paint)
                canvas.drawRect(cx - r * 0.24f, cy - r * 0.08f, cx - r * 0.16f, cy + r * 0.08f, paint)
            }
            "PRESET_CROWN" -> {
                paint.color = Color.parseColor("#FBBF24")
                val path = Path().apply {
                    moveTo(cx - r * 0.42f, cy + r * 0.24f)
                    lineTo(cx - r * 0.42f, cy - r * 0.18f)
                    lineTo(cx - r * 0.16f, cy + r * 0.04f)
                    lineTo(cx, cy - r * 0.28f)
                    lineTo(cx + r * 0.16f, cy + r * 0.04f)
                    lineTo(cx + r * 0.42f, cy - r * 0.18f)
                    lineTo(cx + r * 0.42f, cy + r * 0.24f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "PRESET_CAT" -> {
                canvas.drawCircle(cx, cy + r * 0.06f, r * 0.34f, paint)
                val ears = Path().apply {
                    moveTo(cx - r * 0.32f, cy - r * 0.06f)
                    lineTo(cx - r * 0.26f, cy - r * 0.42f)
                    lineTo(cx - r * 0.06f, cy - r * 0.20f)
                    moveTo(cx + r * 0.32f, cy - r * 0.06f)
                    lineTo(cx + r * 0.26f, cy - r * 0.42f)
                    lineTo(cx + r * 0.06f, cy - r * 0.20f)
                }
                canvas.drawPath(ears, paint)
                paint.color = Color.parseColor("#132A2E")
                canvas.drawCircle(cx - r * 0.12f, cy + r * 0.02f, r * 0.05f, paint)
                canvas.drawCircle(cx + r * 0.12f, cy + r * 0.02f, r * 0.05f, paint)
            }
            else -> {
                // PRESET_PERSON
                canvas.drawCircle(cx, cy - r * 0.16f, r * 0.22f, paint)
                val bodyRect = RectF(cx - r * 0.42f, cy + r * 0.12f, cx + r * 0.42f, cy + r * 0.65f)
                canvas.drawArc(bodyRect, 180f, 180f, true, paint)
            }
        }
    }
}
