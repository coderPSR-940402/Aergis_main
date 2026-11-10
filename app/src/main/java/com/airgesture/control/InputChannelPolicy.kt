package com.airgesture.control

internal enum class ActionOrigin { GESTURE, POINTER, VOICE }

internal object InputChannelPolicy {
    fun enabled(origin: ActionOrigin, action: AirAction, pointerEnabled: Boolean,
        pointerClickingEnabled: Boolean, gesturesEnabled: Boolean, voiceEnabled: Boolean = false): Boolean = when (origin) {
        ActionOrigin.GESTURE -> gesturesEnabled
        ActionOrigin.VOICE -> voiceEnabled
        ActionOrigin.POINTER -> pointerClickingEnabled &&
            ((pointerEnabled && action == AirAction.TAP) || (!pointerEnabled && action == AirAction.SELECT_ITEM))
    }
}
