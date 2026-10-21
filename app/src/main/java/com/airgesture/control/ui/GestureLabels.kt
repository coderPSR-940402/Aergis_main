package com.airgesture.control.ui

import com.airgesture.control.R
import com.airgesture.control.RegisteredGesture

internal fun gestureLabelRes(gesture: RegisteredGesture): Int = when (gesture) {
            RegisteredGesture.THUMB_UP -> R.string.mapping_thumb_up
            RegisteredGesture.VICTORY -> R.string.mapping_victory
            RegisteredGesture.OPEN_PALM -> R.string.mapping_open_palm
            RegisteredGesture.CLOSED_FIST -> R.string.mapping_fist
            RegisteredGesture.POINTING_UP -> R.string.mapping_pointing_up
            RegisteredGesture.THUMB_DOWN -> R.string.mapping_thumb_down
            RegisteredGesture.I_LOVE_YOU -> R.string.mapping_i_love_you
            RegisteredGesture.OK -> R.string.mapping_ok
            RegisteredGesture.THREE_FINGERS -> R.string.mapping_three
            RegisteredGesture.FOUR_FINGERS -> R.string.mapping_four
            RegisteredGesture.SHAKA -> R.string.mapping_shaka
            RegisteredGesture.PINKY_ONLY -> R.string.mapping_pinky
            RegisteredGesture.L_SHAPE -> R.string.mapping_l_shape
            RegisteredGesture.THUMB_INDEX_MIDDLE -> R.string.mapping_thumb_index_middle
            RegisteredGesture.HORNS -> R.string.mapping_horns
            RegisteredGesture.FOUR_LITTLE_FOLDED -> R.string.mapping_four_little_folded
            RegisteredGesture.THREE_OUTER_FINGERS -> R.string.mapping_three_outer_fingers
            RegisteredGesture.FOUR_INDEX_FOLDED -> R.string.mapping_four_index_folded
            RegisteredGesture.DOUBLE_GUN_LEFT -> R.string.mapping_double_gun_left
            RegisteredGesture.DOUBLE_GUN_RIGHT -> R.string.mapping_double_gun_right
            RegisteredGesture.DOUBLE_GUN_UP -> R.string.mapping_double_gun_up
            RegisteredGesture.DOUBLE_GUN_DOWN -> R.string.mapping_double_gun_down
        }

internal fun gestureHelpRes(gesture: RegisteredGesture): Int = when (gesture) {
    RegisteredGesture.L_SHAPE, RegisteredGesture.THUMB_INDEX_MIDDLE,
    RegisteredGesture.DOUBLE_GUN_LEFT, RegisteredGesture.DOUBLE_GUN_RIGHT,
    RegisteredGesture.DOUBLE_GUN_UP, RegisteredGesture.DOUBLE_GUN_DOWN -> R.string.pose_gun_help
    RegisteredGesture.OK -> R.string.pose_ok_help
    RegisteredGesture.THREE_FINGERS -> R.string.pose_three_help
    RegisteredGesture.FOUR_FINGERS -> R.string.pose_four_help
    RegisteredGesture.SHAKA -> R.string.pose_shaka_help
    RegisteredGesture.PINKY_ONLY -> R.string.pose_pinky_help
    else -> R.string.pose_builtin_help
}
