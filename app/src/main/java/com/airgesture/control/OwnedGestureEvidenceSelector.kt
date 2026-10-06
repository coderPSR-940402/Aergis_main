package com.airgesture.control

/** Selects classifier evidence from the same current detection index as the tracked owner. */
internal object OwnedGestureEvidenceSelector {
    fun <T> select(perHandEvidence: List<List<T>>, ownerIndex: Int?): T? {
        val index = ownerIndex ?: return null
        return perHandEvidence.getOrNull(index)?.firstOrNull()
    }
}
