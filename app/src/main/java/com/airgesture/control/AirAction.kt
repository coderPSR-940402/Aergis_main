package com.airgesture.control

enum class AirAction {
    NONE, TAP, DOUBLE_TAP, BACK, HOME, RECENTS, LONG_PRESS, SCROLL_UP, SCROLL_DOWN,
    NOTIFICATIONS, QUICK_SETTINGS, DISMISS_SHADE, VOLUME_UP, VOLUME_DOWN,
    MEDIA_PLAY_PAUSE, MEDIA_NEXT, MEDIA_PREVIOUS, SCREENSHOT, LOCK_SCREEN,
    APP_SEARCH, WEB_SEARCH, WIFI_CONTROLS, BLUETOOTH_SETTINGS, ROTATION_TOGGLE,
    SWIPE_LEFT, SWIPE_RIGHT, ARM_MOTION, POINTER_DRAG, FOCUS_NEXT, FOCUS_PREVIOUS, ACTIVATE_FOCUSED, NEXT_ITEM, PREVIOUS_ITEM, SELECT_ITEM, TOGGLE_POINTER;

    val minimumSdk: Int get() = when (this) {
        SCREENSHOT, LOCK_SCREEN -> 28
        DISMISS_SHADE -> 31
        else -> 26
    }
    val usesPointerTouch: Boolean get() = this in setOf(TAP, DOUBLE_TAP, LONG_PRESS, POINTER_DRAG)
    val isUserMappable: Boolean get() = this !in setOf(FOCUS_NEXT, FOCUS_PREVIOUS, ACTIVATE_FOCUSED)
}

data class ActionResult(val action: AirAction, val accepted: Boolean, val message: String = "")

data class GestureDecision(val action: AirAction, val confidence: Float) {
    val score: Float get() = confidence
}

data class GestureSignal(val name: String, val score: Float)
