package com.airgesture.control

import com.airgesture.control.filtering.Point3D

internal data class PointerHandCandidate(val index: Int, val handedness: String, val tip: Point3D?)

internal object PointerHandFallback {
    fun selectIndex(candidates: List<PointerHandCandidate>, preference: ControlHandPreference): Int? = null
}
