package com.airgesture.control.pointer

import com.airgesture.control.filtering.OneEuroFilter
import kotlin.math.pow
import kotlin.math.sqrt

data class PointerState(
    val x: Float,
    val y: Float,
    val isClicking: Boolean,
    val isDragging: Boolean
)

class PointerController(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private var sensitivity: Float = 2.2f,
    private var accelerationFactor: Float = 1.5f
) {
    private val filterX = OneEuroFilter(minCutoff = 0.8f, beta = 0.02f)
    private val filterY = OneEuroFilter(minCutoff = 0.8f, beta = 0.02f)

    var currentX: Float = screenWidth / 2.0f
        private set
    var currentY: Float = screenHeight / 2.0f
        private set

    private var anchorX: Float? = null
    private var anchorY: Float? = null

    fun updateRawInput(rawX: Float, rawY: Float, timestampMs: Long): PointerState {
        val smoothRawX = filterX.filter(rawX, timestampMs)
        val smoothRawY = filterY.filter(rawY, timestampMs)

        if (anchorX == null || anchorY == null) {
            anchorX = smoothRawX
            anchorY = smoothRawY
            return PointerState(currentX, currentY, isClicking = false, isDragging = false)
        }

        val deltaX = smoothRawX - anchorX!!
        val deltaY = smoothRawY - anchorY!!

        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
        val gain = if (distance > 0) distance.pow(accelerationFactor - 1.0f) * sensitivity else sensitivity

        currentX = (currentX + deltaX * gain).coerceIn(0.0f, screenWidth.toFloat())
        currentY = (currentY + deltaY * gain).coerceIn(0.0f, screenHeight.toFloat())

        anchorX = smoothRawX
        anchorY = smoothRawY

        return PointerState(currentX, currentY, isClicking = false, isDragging = false)
    }

    fun resetAnchor() {
        anchorX = null
        anchorY = null
    }
}

