package com.airgesture.control

import kotlin.math.sqrt

/** Immutable fingertip appearance: motion-only fallback, no template drift or age expiry. */
internal class FingertipPatchTracker {
    data class Point(val x: Float, val y: Float)
    private var template: FloatArray? = null
    private var x = 0
    private var y = 0
    private var width = 0
    private var height = 0
    private var tipOffsetX = 0
    private var tipOffsetY = 0
    private val scores = FloatArray(49*49)
    private val xs = IntArray(49*49)
    private val ys = IntArray(49*49)
    private val searchPixels = FloatArray(65*65)

    fun seed(w: Int, h: Int, px: Int, py: Int, luminance: (Int, Int) -> Float) {
        reset()
        if (w < 17 || h < 17 || px !in 0 until w || py !in 0 until h) return
        // Keep the entire appearance patch in the image while retaining the exact
        // fingertip offset. Do not clamp the reported fingertip to the patch center.
        val cx = px.coerceIn(8, w - 9)
        val cy = py.coerceIn(8, h - 9)
        val patch = sample(cx, cy, luminance)
        if (energy(patch) < 1000f) return
        tipOffsetX = px - cx; tipOffsetY = py - cy
        template = patch; x = cx; y = cy; width = w; height = h
    }
    fun track(w: Int, h: Int, luminance: (Int, Int) -> Float): Point? {
        val reference = template ?: return null
        if (w != width || h != height) { reset(); return null }
        var best = -1f; var bx = x; var by = y
        var count = 0
        val referenceEnergy = energy(reference)
        val minX = maxOf(8,x-24); val maxX = minOf(w-9,x+24)
        val minY = maxOf(8,y-24); val maxY = minOf(h-9,y+24)
        val tileWidth = maxX-minX+17
        var pixel = 0
        // Overlapping candidate patches share pixels. Read RGBA/luminance once per
        // tile pixel, rather than once for every sample of every candidate.
        for (py in minY-8..maxY+8) for (px in minX-8..maxX+8)
            searchPixels[pixel++] = luminance(px,py)
        // Sampling the template every second pixel does not make candidate motion
        // even-valued. Search every center, also when clipping changes edge parity.
        for (cy in minY..maxY)
            for (cx in minX..maxX) {
                var sum = 0f; var square = 0f; var dot = 0f; var i = 0
                for (dy in -8..8 step 2) for (dx in -8..8 step 2) {
                    val value = searchPixels[(cy-minY+dy+8)*tileWidth+cx-minX+dx+8]
                    sum += value; square += value*value; dot += reference[i++]*value
                }
                val denominator = sqrt(referenceEnergy * (square-sum*sum/81f).coerceAtLeast(0f))
                val score = if (denominator > 1f) dot / denominator else -1f
                xs[count]=cx; ys[count]=cy; scores[count++]=score
                if (score > best) { best = score; bx = cx; by = cy }
            }
        var second = -1f
        for (i in 0 until count) if ((xs[i]-bx)*(xs[i]-bx)+(ys[i]-by)*(ys[i]-by) >= 64)
            second = maxOf(second,scores[i])
        if (best < .92f || best - second < .08f) { reset(); return null }
        if (bx + tipOffsetX !in 0 until w || by + tipOffsetY !in 0 until h) { reset(); return null }
        x = bx; y = by
        return Point((x + tipOffsetX).toFloat()/w, (y + tipOffsetY).toFloat()/h)
    }
    private fun sample(x: Int, y: Int, lum: (Int,Int)->Float): FloatArray {
        val values = FloatArray(81); var i=0
        for (dy in -8..8 step 2) for (dx in -8..8 step 2) values[i++] = lum(x+dx,y+dy)
        val mean=values.average().toFloat()
        for (j in values.indices) values[j]-=mean
        return values
    }
    private fun energy(p: FloatArray): Float = p.sumOf { (it*it).toDouble() }.toFloat()
    fun reset() { template=null }
}
