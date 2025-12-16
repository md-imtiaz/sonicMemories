package com.shuvostechworld.sonicmemories.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.shuvostechworld.sonicmemories.R
import java.util.LinkedList

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.parseColor("#00E5FF") 
        style = Paint.Style.FILL
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val amplitudes = LinkedList<Float>()
    private val maxSpikes = 40 // Number of bars to show
    private var spikeWidth = 10f
    private var spikeGap = 6f
    private var screenWidth = 0f
    private var screenHeight = 0f
    
    // Configurable properties
    private val minHeightPercent = 0.1f // Minimum height of a bar (10% of view height)
    private val maxHeightPercent = 0.8f // Maximum height of a bar (80% of view height)

    init {
        // Try to get primary color from theme or resources if possible
        try {
            // This is a placeholder; real color should come from theme/resource
             paint.color = ContextCompat.getColor(context, com.google.android.material.R.color.material_dynamic_primary50)
        } catch (e: Exception) {
             // Fallback already set
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()
        
        // Calculate spike width based on available width and max spikes
        val totalGapWidth = spikeGap * (maxSpikes - 1)
        val availableWidth = screenWidth - totalGapWidth - paddingStart - paddingEnd
        spikeWidth = if (availableWidth > 0) availableWidth / maxSpikes else 10f
    }

    fun addAmplitude(amplitude: Float) {
        // Normalize amplitude (0.0 to 1.0 logic handled outside usually, but let's assume input is raw maxAmplitude)
        // Standard MediaRecorder maxAmplitude is roughly 0 to 32767
        val normalized = (amplitude / 32767f).coerceIn(0f, 1f)
        
        amplitudes.add(normalized)
        if (amplitudes.size > maxSpikes) {
            amplitudes.removeFirst()
        }
        invalidate()
    }
    
    fun clear() {
        amplitudes.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        var currentX = paddingStart.toFloat()
        // Center vertically
        val centerY = screenHeight / 2f
        val maxBarHeight = screenHeight * maxHeightPercent

        for (amp in amplitudes) {
            // Calculate bar height based on amplitude, with a minimum height so it's always visible
            val barHeight = maxBarHeight * amp.coerceAtLeast(minHeightPercent)
            
            val top = centerY - (barHeight / 2f)
            val bottom = centerY + (barHeight / 2f)
            
            // Draw rounded rectangle (using lines with round caps is easier for simple bars)
            paint.strokeWidth = spikeWidth
            canvas.drawLine(currentX + spikeWidth / 2, top, currentX + spikeWidth / 2, bottom, paint)

            currentX += spikeWidth + spikeGap
        }
    }
}
