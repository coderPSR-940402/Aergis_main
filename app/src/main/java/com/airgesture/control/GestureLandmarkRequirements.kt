package com.airgesture.control

/** Visibility follows the joints actually needed to distinguish a pose. */
internal object GestureLandmarkRequirements {
    private val fingers = intArrayOf(0, 5, 6, 8, 9, 10, 12, 13, 14, 16, 17, 18, 20)
    private val tuckedThumb = fingers + intArrayOf(4)
    private val extendedThumb = fingers + intArrayOf(1, 2, 4)
    private val classifierThumb = fingers + intArrayOf(1, 2, 3, 4)

    fun joints(gesture: RegisteredGesture?): IntArray = when (gesture) {
        RegisteredGesture.OK, RegisteredGesture.THREE_FINGERS, RegisteredGesture.FOUR_FINGERS,
        RegisteredGesture.HORNS, RegisteredGesture.THREE_OUTER_FINGERS, RegisteredGesture.PINKY_ONLY,
        RegisteredGesture.VICTORY, RegisteredGesture.OPEN_PALM, RegisteredGesture.CLOSED_FIST,
        RegisteredGesture.POINTING_UP -> tuckedThumb
        RegisteredGesture.SHAKA, RegisteredGesture.L_SHAPE, RegisteredGesture.THUMB_INDEX_MIDDLE,
        RegisteredGesture.FOUR_LITTLE_FOLDED, RegisteredGesture.FOUR_INDEX_FOLDED,
        RegisteredGesture.DOUBLE_GUN_LEFT, RegisteredGesture.DOUBLE_GUN_RIGHT,
        RegisteredGesture.DOUBLE_GUN_UP, RegisteredGesture.DOUBLE_GUN_DOWN -> extendedThumb
        RegisteredGesture.THUMB_UP, RegisteredGesture.THUMB_DOWN, RegisteredGesture.I_LOVE_YOU,
        null -> classifierThumb
    }
}
