package com.airgesture.control.pointer

import kotlin.math.abs

class ClickManager(
    private val dwellTimeMs: Long = 400L,
    private val maxDwellMovePx: Float = 15.0f
) {
    private var dwellStartX: Float = 0.0f
    private var dwellStartY: Float = 0.0f
    private var dwellStartTime: Long = 0L
    private var isDwelling: Boolean = false

    fun processPosition(x: Float, y: Float, currentTimeMs: Long): Boolean {
        if (!isDwelling) {
            isDwelling = true
            dwellStartX = x
            dwellStartY = y
            dwellStartTime = currentTimeMs
            return false
        }

        val movedDistance = abs(x - dwellStartX) + abs(y - dwellStartY)

        if (movedDistance > maxDwellMovePx) {
            dwellStartX = x
            dwellStartY = y
            dwellStartTime = currentTimeMs
            return false
        }

        if (currentTimeMs - dwellStartTime >= dwellTimeMs) {
            isDwelling = false
            return true
        }

        return false
    }
}

