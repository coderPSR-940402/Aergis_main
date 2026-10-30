package com.airgesture.control

/** Explicit setup aid: preserve every assigned gesture and fill only disabled slots. */
internal object TouchFreeNavigationPreset {
    val bindings: Map<RegisteredGesture, AirAction> = linkedMapOf(
        RegisteredGesture.THUMB_DOWN to AirAction.SCROLL_DOWN,
        RegisteredGesture.I_LOVE_YOU to AirAction.QUICK_SETTINGS,
        RegisteredGesture.OK to AirAction.SELECT_ITEM,
        RegisteredGesture.THREE_FINGERS to AirAction.NEXT_ITEM,
        RegisteredGesture.FOUR_FINGERS to AirAction.PREVIOUS_ITEM,
        RegisteredGesture.SHAKA to AirAction.APP_SEARCH,
        RegisteredGesture.PINKY_ONLY to AirAction.TOGGLE_POINTER,
        RegisteredGesture.L_SHAPE to AirAction.SCROLL_UP,
        RegisteredGesture.FOUR_LITTLE_FOLDED to AirAction.NOTIFICATIONS,
        RegisteredGesture.THUMB_INDEX_MIDDLE to AirAction.ARM_MOTION
    )

    fun apply(store: ActionMappingStore): List<AirAction> {
        store.fillUnassigned(bindings)
        return missingActions(store)
    }

    fun missingActions(store: ActionMappingStore): List<AirAction> {
        val assigned = RegisteredGesture.entries.map(store::mapping).toSet()
        return bindings.values.distinct().filterNot { it in assigned }
    }
}
