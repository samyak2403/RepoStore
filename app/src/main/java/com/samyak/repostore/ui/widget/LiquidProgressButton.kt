package com.samyak.repostore.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.samyak.repostore.R
import kotlin.math.sin

/**
 * A custom button that shows a liquid wave filling animation during download progress.
 * The wave animation creates a visually appealing effect as the progress increases.
 */
class LiquidProgressButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatButton(context, attrs, defStyleAttr) {

    private var progress: Int = 0
    private var isAnimating: Boolean = false
    
    // Wave animation properties
    private var waveOffset: Float = 0f
    private var waveAnimator: ValueAnimator? = null
    
    // Paints
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    // Colors
    private var liquidColor: Int = ContextCompat.getColor(context, R.color.primary)
    private var liquidColorDark: Int = ContextCompat.getColor(context, R.color.primary_variant)
    
    // Wave properties
    private val waveHeight = 12f  // Height of wave peaks
    private val waveFrequency = 0.04f  // How many waves
    
    // Path for clipping
    private val clipPath = Path()
    private val wavePath = Path()
    
    init {
        // Make background transparent during animation
        setBackgroundColor(Color.TRANSPARENT)
    }
    
    override fun onDraw(canvas: Canvas) {
        if (isAnimating && progress > 0) {
            // Save canvas state
            canvas.save()
            
            // Create rounded rectangle clip path
            clipPath.reset()
            val cornerRadius = height / 2f
            clipPath.addRoundRect(
                0f, 0f, width.toFloat(), height.toFloat(),
                cornerRadius, cornerRadius,
                Path.Direction.CW
            )
            canvas.clipPath(clipPath)
            
            // Calculate fill level based on progress
            val fillHeight = height * (1 - progress / 100f)
            
            // Draw the base liquid (darker shade)
            progressPaint.color = liquidColorDark
            canvas.drawRect(0f, fillHeight, width.toFloat(), height.toFloat(), progressPaint)
            
            // Draw wave on top
            drawWave(canvas, fillHeight)
            
            // Restore canvas state
            canvas.restore()
        }
        
        // Draw the text on top
        super.onDraw(canvas)
    }
    
    private fun drawWave(canvas: Canvas, baseY: Float) {
        wavePath.reset()
        wavePath.moveTo(0f, height.toFloat())
        
        // Start from bottom-left
        wavePath.lineTo(0f, baseY)
        
        // Draw wave curve
        var x = 0f
        while (x <= width) {
            val y = baseY + waveHeight * sin((x * waveFrequency + waveOffset).toDouble()).toFloat()
            wavePath.lineTo(x, y)
            x += 1f
        }
        
        // Complete the path
        wavePath.lineTo(width.toFloat(), height.toFloat())
        wavePath.close()
        
        // Draw wave
        wavePaint.color = liquidColor
        canvas.drawPath(wavePath, wavePaint)
    }
    
    /**
     * Set the download progress (0-100)
     */
    fun setProgress(value: Int) {
        progress = value.coerceIn(0, 100)
        invalidate()
    }
    
    /**
     * Start the liquid animation
     */
    fun startAnimation() {
        if (isAnimating) return
        
        isAnimating = true
        progress = 0
        
        // Animate wave movement
        waveAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                waveOffset = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    /**
     * Stop the liquid animation
     */
    fun stopAnimation() {
        isAnimating = false
        progress = 0
        waveAnimator?.cancel()
        waveAnimator = null
        invalidate()
    }
    
    /**
     * Check if animation is currently running
     */
    fun isAnimationRunning(): Boolean = isAnimating
    
    /**
     * Set liquid colors
     */
    fun setLiquidColors(primary: Int, dark: Int) {
        liquidColor = primary
        liquidColorDark = dark
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator?.cancel()
    }
}
