package com.airgesture.control

import com.airgesture.control.filtering.Point3D

internal data class PointerHandCandidate(val index: Int, val handedness: String, val tip: Point3D?)

internal object PointerHandFallback {
    // Without reliable palm ownership, pointer feedback requires exactly one eligible tip.
    // This selection never establishes ownership for gesture commands.
    fun selectIndex(candidates: List<PointerHandCandidate>, preference: ControlHandPreference): Int? =
        candidates.filter { candidate ->
            val tip = candidate.tip
            tip != null && tip.x.isFinite() && tip.y.isFinite() &&
                (preference == ControlHandPreference.EITHER ||
                    candidate.handedness.equals(preference.name, ignoreCase = true))
        }.singleOrNull()?.index
}

