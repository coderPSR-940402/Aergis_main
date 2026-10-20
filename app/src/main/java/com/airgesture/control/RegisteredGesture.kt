package com.airgesture.control

/** Stable gesture IDs are independent of their assigned actions and display labels. */
enum class RegisteredGesture(val signalName: String, val defaultAction: AirAction, val legacySource: AirAction? = null) {
    THUMB_UP("Thumb_Up", AirAction.TAP, AirAction.TAP),
    VICTORY("Victory", AirAction.BACK, AirAction.BACK),
    OPEN_PALM("Open_Palm", AirAction.HOME, AirAction.HOME),
    CLOSED_FIST("Closed_Fist", AirAction.RECENTS, AirAction.RECENTS),
    POINTING_UP("Pointing_Up", AirAction.DOUBLE_TAP, AirAction.DOUBLE_TAP),
    THUMB_DOWN("Thumb_Down", AirAction.NONE),
    I_LOVE_YOU("ILoveYou", AirAction.NONE),
    OK("OK", AirAction.NONE),
    THREE_FINGERS("Three_Fingers", AirAction.NONE),
    FOUR_FINGERS("Four_Fingers", AirAction.NONE),
    SHAKA("Shaka", AirAction.NONE),
    PINKY_ONLY("Pinky_Only", AirAction.NONE),
    L_SHAPE("L_Shape", AirAction.NONE),
    THUMB_INDEX_MIDDLE("Thumb_Index_Middle", AirAction.NONE),
    HORNS("Horns", AirAction.NONE),
    FOUR_LITTLE_FOLDED("Four_Little_Folded", AirAction.NONE),
    THREE_OUTER_FINGERS("Three_Outer_Fingers", AirAction.NONE),
    FOUR_INDEX_FOLDED("Four_Index_Folded", AirAction.NONE);

    val isLandmarkPose: Boolean get() = ordinal >= OK.ordinal

    companion object {
        fun fromSignal(name: String): RegisteredGesture? = entries.firstOrNull {
            it.signalName.equals(name, ignoreCase = true)
        } ?: when (name.lowercase()) {
            "thumbs_up" -> THUMB_UP
            "peace" -> VICTORY
            "open hand" -> OPEN_PALM
            "fist" -> CLOSED_FIST
            "point" -> POINTING_UP
            else -> null
        }
    }
}
