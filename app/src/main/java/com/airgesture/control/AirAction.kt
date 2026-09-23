package com.airgesture.control

enum class AirAction {
    NONE, TAP, DOUBLE_TAP, BACK, HOME, RECENTS, LONG_PRESS, SCROLL_UP, SCROLL_DOWN
}

data class ActionResult(val action: AirAction, val accepted: Boolean, val message: String = "")

data class GestureDecision(val action: AirAction, val confidence: Float)

data class GestureSignal(val name: String, val score: Float)
