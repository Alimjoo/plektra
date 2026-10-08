package rocks.pkb.plektra.inputmethod.statusbar.button

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import androidx.core.view.ViewCompat
import rocks.pkb.plektra.R
import rocks.pkb.plektra.inputmethod.statusbar.ButtonCreationResult
import rocks.pkb.plektra.inputmethod.statusbar.ButtonState
import rocks.pkb.plektra.inputmethod.statusbar.StatusBarCallbacks
import rocks.pkb.plektra.inputmethod.statusbar.StatusBarButtonStyles

/**
 * Factory for creating the microphone button with audio level feedback.
 * 
 * When speech recognition is active, the button background changes to red
 * and the intensity varies based on the audio input level.
 */
class MicrophoneButtonFactory : StatusBarButtonFactory {
    
    companion object {
        // Audio level mapping constants
        private const val MIN_RMS_DB = -10f
        private const val MAX_RMS_DB = 0f
    }
    
    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = createButton(context, size)
        
        // Set up click listener using the microphone-specific callback
        button.setOnClickListener {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            callbacks.onSpeechRecognitionRequested?.invoke()
        }
        button.setOnLongClickListener {
            context.startActivity(android.content.Intent(context, rocks.pkb.plektra.SettingsActivity::class.java).apply {
                data = android.net.Uri.parse("plektra://setting/main.speech_recognition")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        }
        
        // Initialize state holder
        val stateHolder = MicrophoneStateHolder(baseHeightPx = size)
        button.setTag(R.id.tag_microphone_state, stateHolder)
        
        return ButtonCreationResult(view = button)
    }
    
    override fun update(view: View, state: ButtonState) {
        if (state !is ButtonState.MicrophoneState) return
        val button = view as? ImageView ?: return
        val stateHolder = view.getTag(R.id.tag_microphone_state) as? MicrophoneStateHolder ?: return
        
        val wasActive = stateHolder.isActive
        stateHolder.isActive = state.isActive
        
        when {
            state.isActive && !wasActive -> {
                // Started recording - switch to red background
                startAudioFeedback(button, stateHolder)
            }
            !state.isActive && wasActive -> {
                // Stopped recording - restore normal background
                stopAudioFeedback(button, stateHolder)
            }
            state.isActive -> {
                // Update audio level while recording
                updateAudioLevel(button, stateHolder, state.audioLevelDb)
            }
        }
    }
    
    internal fun setBackgroundRenderer(view: View, renderer: (Int?) -> Unit) {
        val holder = view.getTag(R.id.tag_microphone_state) as? MicrophoneStateHolder ?: return
        holder.backgroundRenderer = renderer
    }

    override fun cleanup(view: View) {
        val stateHolder = view.getTag(R.id.tag_microphone_state) as? MicrophoneStateHolder
        stateHolder?.pulseAnimator?.cancel()
        stateHolder?.currentDrawable = null
        stateHolder?.backgroundRenderer = null
    }
    
    /**
     * Sets the active state of the microphone button.
     * Call this when speech recognition starts/stops.
     */
    fun setActive(view: View, isActive: Boolean) {
        if (!isActive) setSpeechFeedback(view, false, null)
        update(view, ButtonState.MicrophoneState(isActive = isActive))
    }

    fun setSpeechFeedback(view: View, processing: Boolean, countdown: Int?) {
        val button = view as? ImageView ?: return
        val holder = view.getTag(R.id.tag_microphone_state) as? MicrophoneStateHolder ?: return
        holder.processing = processing
        holder.countdown = countdown
        if (countdown != null) {
            button.setImageDrawable(CountdownIcon(button.context.getDrawable(R.drawable.ic_baseline_mic_24)!!, countdown))
        } else button.setImageResource(R.drawable.ic_baseline_mic_24)
        if (!holder.isActive) return
        val color = when {
            processing -> Color.rgb(36, 106, 180)
            countdown != null -> Color.rgb(174, 103, 0)
            else -> StatusBarButtonStyles.RECOGNITION_RED
        }
        holder.backgroundRenderer?.invoke(color) ?: holder.currentDrawable?.setColor(color)
        setStateDescriptionIfChanged(button, button.context.getString(when {
            processing -> R.string.speech_processing_state
            countdown != null -> R.string.speech_pause_countdown
            else -> R.string.speech_recording_state
        }, countdown ?: 0))
    }
    
    /**
     * Updates the audio level visualization.
     * Call this with RMS dB values during speech recognition.
     * 
     * @param view The microphone button view
     * @param rmsdB The RMS audio level in decibels (typically -10 to 0)
     */
    fun updateAudioLevel(view: View, rmsdB: Float) {
        val stateHolder = view.getTag(R.id.tag_microphone_state) as? MicrophoneStateHolder ?: return
        if (!stateHolder.isActive) return
        if (stateHolder.processing || stateHolder.countdown != null) return
        
        val button = view as? ImageView ?: return
        updateAudioLevel(button, stateHolder, rmsdB)
    }
    
    private fun createButton(context: Context, size: Int): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_mic_24)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.status_bar_button_microphone_description)
            setStateDescriptionIfChanged(this, context.getString(R.string.status_bar_state_off))
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            // layoutParams will be set by VariationBarView for consistency
        }
    }
    
    private fun startAudioFeedback(button: ImageView, stateHolder: MicrophoneStateHolder) {
        setStateDescriptionIfChanged(button, button.context.getString(R.string.status_bar_state_on))
        // Stop any existing animation
        stateHolder.pulseAnimator?.cancel()
        stateHolder.pulseAnimator = null
        
        stateHolder.backgroundRenderer?.let { render ->
            render(StatusBarButtonStyles.RECOGNITION_RED)
            button.alpha = 1f
            return
        }

        // Create base drawable with initial red color (medium intensity)
        val radius = StatusBarButtonStyles.cornerRadiusForSize(resolveHeightPx(button, stateHolder))
        val redDrawable = GradientDrawable().apply {
            setColor(StatusBarButtonStyles.RECOGNITION_RED)
            cornerRadius = radius
        }
        stateHolder.currentDrawable = redDrawable
        
        // Pressed state stays blue
        val pressedDrawable = GradientDrawable().apply {
            setColor(StatusBarButtonStyles.PRESSED_BLUE)
            cornerRadius = radius
        }
        
        // Create state list with red as normal state
        val stateList = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), redDrawable)
        }
        button.background = stateList
        button.alpha = 1f
    }
    
    private fun stopAudioFeedback(button: ImageView, stateHolder: MicrophoneStateHolder) {
        setStateDescriptionIfChanged(button, button.context.getString(R.string.status_bar_state_off))
        // Cancel any pulse animation if still running
        stateHolder.pulseAnimator?.cancel()
        stateHolder.pulseAnimator = null
        
        // Reset alpha
        button.alpha = 1f
        
        // Clear reference to drawable
        stateHolder.currentDrawable = null
        
        stateHolder.backgroundRenderer?.let { render ->
            render(null)
            return
        }
        // Restore normal state
        button.background = StatusBarButtonStyles.createButtonDrawable(resolveHeightPx(button, stateHolder))
    }
    
    private fun updateAudioLevel(button: ImageView, stateHolder: MicrophoneStateHolder, rmsdB: Float) {
        val drawable = stateHolder.currentDrawable
        if (drawable == null && stateHolder.backgroundRenderer == null) return
        
        // Map RMS value (-10 to 0) to a normalized value (0.0 to 1.0)
        val normalizedLevel = ((rmsdB - MIN_RMS_DB) / (MAX_RMS_DB - MIN_RMS_DB)).coerceIn(0f, 1f)
        
        // Map to color intensity: darker red at 0.0, brighter red at 1.0
        // Use a curve to make the effect more visible (power of 2)
        val intensity = normalizedLevel * normalizedLevel // Quadratic curve for more noticeable effect
        
        // Calculate red color values: from dark red (128, 0, 0) to bright red (255, 50, 50)
        // Keep it red by maintaining lower G and B values relative to R
        val r = (128 + (255 - 128) * intensity).toInt()
        val g = (0 + (50 - 0) * intensity).toInt()
        val b = (0 + (50 - 0) * intensity).toInt()
        val color = Color.rgb(r, g, b)
        
        // Update the drawable color
        stateHolder.backgroundRenderer?.invoke(color) ?: drawable?.setColor(color)
        button.background?.invalidateSelf()
    }
    
    /**
     * Internal state holder for microphone button.
     * Stored in the view's tag to maintain state across updates.
     */
    private class MicrophoneStateHolder(
        val baseHeightPx: Int
    ) {
        var backgroundRenderer: ((Int?) -> Unit)? = null
        var isActive: Boolean = false
        var currentDrawable: GradientDrawable? = null
        var pulseAnimator: ValueAnimator? = null
        var processing = false
        var countdown: Int? = null
    }

    private class CountdownIcon(private val icon: Drawable, private val seconds: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.RIGHT; isFakeBoldText = true }
        override fun draw(canvas: Canvas) {
            icon.setBounds(bounds.left, bounds.top, bounds.left + bounds.width() * 3 / 4, bounds.bottom)
            icon.draw(canvas)
            paint.textSize = bounds.height() * 0.52f
            canvas.drawText(seconds.toString(), bounds.right.toFloat(), bounds.bottom.toFloat() - bounds.height() * 0.04f, paint)
        }
        override fun setAlpha(alpha: Int) { icon.alpha = alpha; paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { icon.colorFilter = filter; paint.colorFilter = filter }
        @Deprecated("Deprecated in Android")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth() = icon.intrinsicWidth
        override fun getIntrinsicHeight() = icon.intrinsicHeight
    }

    private fun resolveHeightPx(button: View, stateHolder: MicrophoneStateHolder): Int {
        return if (button.height > 0) button.height else stateHolder.baseHeightPx
    }

    private fun setStateDescriptionIfChanged(view: View, stateDescription: String) {
        if (ViewCompat.getStateDescription(view)?.toString() == stateDescription) {
            return
        }
        ViewCompat.setStateDescription(view, stateDescription)
    }
}
