package com.airgesture.control

/** Display-space hotspot containment; never clamp an action onto another target. */
internal object PointerViewport {
    fun contains(x: Float, y: Float, left: Int, top: Int, width: Int, height: Int): Boolean =
        x.isFinite() && y.isFinite() && width > 0 && height > 0 &&
            x >= left && y >= top && x < left.toLong() + width && y < top.toLong() + height
}
