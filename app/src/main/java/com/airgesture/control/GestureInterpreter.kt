package com.airgesture.control

class GestureInterpreter(private val mappings: ActionMappingStore) {
    fun interpret(signal: GestureSignal): GestureDecision {
        val source = when (signal.name.lowercase()) {
            "thumb_up", "thumbs_up" -> AirAction.TAP
            "victory", "peace" -> AirAction.BACK
            "open_palm", "open hand" -> AirAction.HOME
            "closed_fist", "fist" -> AirAction.RECENTS
            "pointing_up", "point" -> AirAction.DOUBLE_TAP
            else -> AirAction.NONE
        }
        return GestureDecision(mappings.mapping(source), signal.score)
    }

    data class Point(val x: Float, val y: Float)
    data class StaticSample(val timestampMs: Long, val point: Point?)
}

data class HandCandidate(val handedness: String, val score: Float)
data class HandFrame(val timestampMs: Long, val hands: List<HandCandidate>)
