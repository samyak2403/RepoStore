package com.samyak.repostore.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import com.samyak.repostore.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Custom emerald-teal pill toggle switch matching the RepoStore Download Settings design:
 * rounded stadium track with subtle gradient and border, crisp white vertical ON bar ("I"),
 * muted OFF circle indicator ("O"), and an elevated circular white thumb with soft drop shadow.
 */
class EmeraldToggleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CompoundButton(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val visualTrackWidth = 58f * density
    private val visualTrackHeight = 32f * density
    private val minTouchWidth = (66f * density).roundToInt()
    private val minTouchHeight = (48f * density).roundToInt()

    private val trackBorderWidth = 1.1f * density
    private val thumbInset = 2.2f * density
    private val thumbEdgeWidth = 0.8f * density
    private val onBarHalfWidth = 1.25f * density
    private val onBarHalfHeight = 5.0f * density
    private val onBarCorner = 0.6f * density
    private val offRingRadius = 4.2f * density
    private val offRingStroke = 1.7f * density

    private val trackOnTopColor = ContextCompat.getColor(context, R.color.switch_track_on)
    private val trackOnBottomColor = ContextCompat.getColor(context, R.color.switch_track_on_end)
    private val trackOnBorderColor = ContextCompat.getColor(context, R.color.switch_track_on_border)
    private val trackOffTopColor = ContextCompat.getColor(context, R.color.switch_track_off)
    private val trackOffBottomColor = ContextCompat.getColor(context, R.color.switch_track_off_end)
    private val trackOffBorderColor = ContextCompat.getColor(context, R.color.switch_track_off_border)
    private val thumbTopColor = ContextCompat.getColor(context, R.color.switch_thumb_fill)
    private val thumbBottomColor = ContextCompat.getColor(context, R.color.switch_thumb_fill_bottom)
    private val thumbEdgeColor = ContextCompat.getColor(context, R.color.switch_thumb_edge)
    private val indicatorOnColor = ContextCompat.getColor(context, R.color.switch_indicator_on)
    private val indicatorOffColor = ContextCompat.getColor(context, R.color.switch_indicator_off)

    private val trackRect = RectF()
    private val onIndicatorRect = RectF()

    private val trackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val trackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = trackBorderWidth
    }
    private val onIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = indicatorOnColor
    }
    private val offIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = offRingStroke
        color = indicatorOffColor
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thumbEdgeWidth
        color = thumbEdgeColor
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var thumbProgress: Float = if (isChecked) 1f else 0f
    private var progressAnimator: ValueAnimator? = null
    private var thumbShader: LinearGradient? = null
    private var lastThumbShaderCenterY: Float = Float.NaN

    init {
        isClickable = true
        isFocusable = true
        background = null
    }

    override fun getAccessibilityClassName(): CharSequence = android.widget.Switch::class.java.name

    override fun setChecked(checked: Boolean) {
        val changed = isChecked != checked
        super.setChecked(checked)
        val target = if (checked) 1f else 0f
        if (!changed && thumbProgress == target && progressAnimator == null) {
            return
        }
        if (isAttachedToWindow && isLaidOut) {
            animateThumbTo(target)
        } else {
            progressAnimator?.cancel()
            progressAnimator = null
            thumbProgress = target
            invalidate()
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.5f
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        progressAnimator?.cancel()
        progressAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = max(minTouchWidth, (visualTrackWidth + 8f * density).roundToInt()) +
            paddingLeft + paddingRight
        val desiredHeight = max(minTouchHeight, (visualTrackHeight + 12f * density).roundToInt()) +
            paddingTop + paddingBottom

        val resolvedWidth = resolveSize(desiredWidth, widthMeasureSpec)
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val contentLeft = paddingLeft.toFloat()
        val contentRight = (width - paddingRight).toFloat()
        val contentTop = paddingTop.toFloat()
        val contentBottom = (height - paddingBottom).toFloat()

        val centerX = (contentLeft + contentRight) * 0.5f
        val centerY = (contentTop + contentBottom) * 0.5f

        val halfTrackW = visualTrackWidth * 0.5f
        val halfTrackH = visualTrackHeight * 0.5f
        trackRect.set(
            centerX - halfTrackW,
            centerY - halfTrackH,
            centerX + halfTrackW,
            centerY + halfTrackH
        )
        val trackCornerRadius = halfTrackH

        // 1. Track fill with smooth gradient interpolation
        val topColor = lerpColor(trackOffTopColor, trackOnTopColor, thumbProgress)
        val bottomColor = lerpColor(trackOffBottomColor, trackOnBottomColor, thumbProgress)
        trackFillPaint.shader = LinearGradient(
            trackRect.left,
            trackRect.top,
            trackRect.left,
            trackRect.bottom,
            topColor,
            bottomColor,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(trackRect, trackCornerRadius, trackCornerRadius, trackFillPaint)

        // 2. Subtle track border matching emerald/neutral depth
        trackBorderPaint.color = lerpColor(trackOffBorderColor, trackOnBorderColor, thumbProgress)
        val borderInset = trackBorderWidth * 0.5f
        trackRect.inset(borderInset, borderInset)
        canvas.drawRoundRect(
            trackRect,
            trackCornerRadius - borderInset,
            trackCornerRadius - borderInset,
            trackBorderPaint
        )
        trackRect.inset(-borderInset, -borderInset)

        // 3. Track state indicators: crisp white vertical "I" on left when ON, muted "O" on right when OFF
        val indicatorOffsetX = 15.5f * density
        val onAlpha = ((thumbProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
        if (onAlpha > 0f) {
            val onX = trackRect.left + indicatorOffsetX
            onIndicatorRect.set(
                onX - onBarHalfWidth,
                centerY - onBarHalfHeight,
                onX + onBarHalfWidth,
                centerY + onBarHalfHeight
            )
            onIndicatorPaint.alpha = (onAlpha * 255f).roundToInt()
            canvas.drawRoundRect(onIndicatorRect, onBarCorner, onBarCorner, onIndicatorPaint)
        }

        val offAlpha = ((0.85f - thumbProgress) / 0.85f).coerceIn(0f, 1f)
        if (offAlpha > 0f) {
            val offX = trackRect.right - indicatorOffsetX
            offIndicatorPaint.alpha = (offAlpha * 210f).roundToInt()
            canvas.drawCircle(offX, centerY, offRingRadius, offIndicatorPaint)
        }

        // 4. Circular elevated white thumb with hardware-safe multi-pass soft drop shadow
        val thumbRadius = halfTrackH - thumbInset
        val minThumbX = trackRect.left + thumbInset + thumbRadius
        val maxThumbX = trackRect.right - thumbInset - thumbRadius
        val thumbX = minThumbX + (maxThumbX - minThumbX) * thumbProgress

        if (isPressed || isFocused) {
            highlightPaint.color = lerpColor(trackOffBorderColor, trackOnTopColor, thumbProgress)
            highlightPaint.alpha = if (isPressed) 42 else 28
            canvas.drawCircle(thumbX, centerY, thumbRadius + 5f * density, highlightPaint)
        }

        // Multi-ring soft drop shadow (works under hardware acceleration and Robolectric)
        drawSoftShadow(canvas, thumbX, centerY, thumbRadius)

        if (thumbShader == null || lastThumbShaderCenterY != centerY) {
            lastThumbShaderCenterY = centerY
            thumbShader = LinearGradient(
                0f,
                centerY - thumbRadius,
                0f,
                centerY + thumbRadius,
                thumbTopColor,
                thumbBottomColor,
                Shader.TileMode.CLAMP
            )
        }
        thumbFillPaint.shader = thumbShader
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbFillPaint)
        canvas.drawCircle(thumbX, centerY, thumbRadius - thumbEdgeWidth * 0.5f, thumbEdgePaint)
    }

    private fun drawSoftShadow(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        shadowPaint.color = Color.BLACK
        shadowPaint.alpha = 12
        canvas.drawCircle(cx, cy + 2.8f * density, radius + 1.4f * density, shadowPaint)

        shadowPaint.alpha = 18
        canvas.drawCircle(cx, cy + 1.8f * density, radius + 0.7f * density, shadowPaint)

        shadowPaint.alpha = 26
        canvas.drawCircle(cx, cy + 1.0f * density, radius + 0.15f * density, shadowPaint)
    }

    private fun animateThumbTo(target: Float) {
        progressAnimator?.cancel()
        val start = thumbProgress
        if (start == target) {
            invalidate()
            return
        }
        progressAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                thumbProgress = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun lerpColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val a = (Color.alpha(startColor) + (Color.alpha(endColor) - Color.alpha(startColor)) * f).roundToInt()
        val r = (Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * f).roundToInt()
        val g = (Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * f).roundToInt()
        val b = (Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * f).roundToInt()
        return Color.argb(a, r, g, b)
    }

    private companion object {
        const val ANIMATION_DURATION_MS = 200L
    }
}
