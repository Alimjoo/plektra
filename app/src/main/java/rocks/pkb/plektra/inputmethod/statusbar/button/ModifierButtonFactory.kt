package rocks.pkb.plektra.inputmethod.statusbar.button

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import rocks.pkb.plektra.R
import rocks.pkb.plektra.inputmethod.StatusBarController
import rocks.pkb.plektra.inputmethod.statusbar.*

/** One dynamic menu button contains all currently active modifier indicators. */
internal class ModifierButtonFactory : StatusBarButtonFactory {
    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks) = ButtonCreationResult(
        ImageView(context).apply {
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = false
        }
    )

    override fun update(view: View, state: ButtonState) {
        if (state !is ButtonState.ModifierState || view !is ImageView) return
        view.setImageDrawable(ModifierIconsDrawable(view.context, state.indicators))
        view.contentDescription = state.indicators.joinToString(", ") { it.description }
    }

    companion object {
        val id = StatusBarButtonId.Custom("active_modifiers")
        const val ICON_STEP_DP = 28f

        fun active(snapshot: StatusBarController.StatusSnapshot): List<ModifierIndicator> {
            val result = mutableListOf<ModifierIndicator>()
            fun add(visible: Boolean, locked: Boolean, icon: Int, description: String, label: String? = null) {
                if (visible) result += ModifierIndicator(icon, locked, description, label)
            }
            add(snapshot.capsLockEnabled || snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot,
                snapshot.capsLockEnabled, if (snapshot.capsLockEnabled) R.drawable.shift_filled_24 else R.drawable.shift_24, "Shift")
            add(snapshot.ctrlLatchActive || snapshot.ctrlPhysicallyPressed || snapshot.ctrlOneShot,
                snapshot.ctrlLatchActive, R.drawable.keyboard_control_key_24, "Ctrl")
            add(snapshot.altLatchActive || snapshot.altPhysicallyPressed || snapshot.altOneShot,
                snapshot.altLatchActive, R.drawable.keyboard_option_key_24, "Alt")
            add(snapshot.symPage > 0 || snapshot.symPhysicallyPressed,
                snapshot.symPage == 2, 0, "SYM", "SYM")
            return result
        }
    }
}

/** The host positions and scales the combined icon as it does every other menu icon. */
internal class ModifierIconsDrawable(context: Context, private val indicators: List<ModifierIndicator>) : Drawable() {
    private val size = (24f * context.resources.displayMetrics.density).toInt()
    private val gap = (4f * context.resources.displayMetrics.density).toInt()
    private val icons = indicators.map { if (it.iconResId != 0) ContextCompat.getDrawable(context, it.iconResId)!!.mutate() else null }
    private val colors = IntArray(indicators.size)
    private val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * context.resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
    }
    override fun getIntrinsicWidth() = indicators.size * size + (indicators.size - 1).coerceAtLeast(0) * gap
    override fun getIntrinsicHeight() = size

    fun setIndicatorColors(active: Int, locked: Int) {
        icons.forEachIndexed { index, icon ->
            colors[index] = if (indicators[index].locked) locked else active
            icon?.setTint(colors[index])
        }
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (intrinsicWidth <= 0) return
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width().toFloat() / intrinsicWidth, bounds.height().toFloat() / intrinsicHeight)
        icons.forEachIndexed { index, icon ->
            val left = index * (size + gap)
            if (icon != null) {
                icon.setBounds(left, 0, left + size, size)
                icon.draw(canvas)
            } else {
                textPaint.color = colors[index]
                val metrics = textPaint.fontMetrics
                canvas.drawText(indicators[index].label.orEmpty(), left + size / 2f,
                    size / 2f - (metrics.ascent + metrics.descent) / 2f, textPaint)
            }
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { icons.forEach { it?.alpha = alpha }; textPaint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { icons.forEach { it?.colorFilter = colorFilter }; textPaint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
