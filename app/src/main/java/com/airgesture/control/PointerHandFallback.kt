package com.airgesture.control

import com.airgesture.control.filtering.Point3D

internal data class PointerHandCandidate(val index: Int, val handedness: String, val tip: Point3D?)

internal object PointerHandFallback {
    // Without reliable palm ownership, pointer feedback requires exactly one eligible tip.
    // This selection never establishes ownership for gesture commands.
    fun selectIndex(candidates: List<PointerHandCandidate>, preference: ControlHandPreference): Int? {
        var selected: Int? = null
        for (candidate in candidates) {
            val tip = candidate.tip
            if (tip == null || !tip.x.isFinite() || !tip.y.isFinite()) continue
            if (preference != ControlHandPreference.EITHER &&
                !candidate.handedness.equals(preference.name, ignoreCase = true)) continue
            if (selected != null) return null
            selected = candidate.index
        }
        return selected
    }
}
