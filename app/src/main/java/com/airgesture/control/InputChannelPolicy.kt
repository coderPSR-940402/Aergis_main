package com.airgesture.control

internal enum class ActionOrigin { GESTURE, POINTER }

internal object InputChannelPolicy {
    fun enabled(origin: ActionOrigin, action: AirAction, pointerEnabled: Boolean,
        pointerClickingEnabled: Boolean, gesturesEnabled: Boolean): Boolean = when (origin) {
        ActionOrigin.GESTURE -> gesturesEnabled
        ActionOrigin.POINTER -> pointerClickingEnabled &&
            ((pointerEnabled && action in setOf(AirAction.TAP, AirAction.POINTER_DRAG)) || (!pointerEnabled && action == AirAction.SELECT_ITEM))
    }
}
