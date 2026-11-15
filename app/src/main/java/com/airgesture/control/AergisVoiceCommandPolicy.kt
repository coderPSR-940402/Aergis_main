package com.airgesture.control

/** Only explicitly supported, non-coordinate voice commands enter accessibility dispatch. */
internal object AergisVoiceCommandPolicy {
    private val allowed = setOf(
        AirAction.HOME, AirAction.BACK, AirAction.RECENTS,
        AirAction.SCROLL_UP, AirAction.SCROLL_DOWN,
        AirAction.NOTIFICATIONS, AirAction.QUICK_SETTINGS, AirAction.DISMISS_SHADE,
        AirAction.VOLUME_UP, AirAction.VOLUME_DOWN,
        AirAction.MEDIA_PLAY_PAUSE, AirAction.MEDIA_NEXT, AirAction.MEDIA_PREVIOUS,
        AirAction.APP_SEARCH, AirAction.TOGGLE_POINTER,
        AirAction.NEXT_ITEM, AirAction.PREVIOUS_ITEM, AirAction.SELECT_ITEM
    )
    fun parse(value: String?): AirAction? {
        val action = AirAction.entries.firstOrNull { it.name == value?.trim() } ?: return null
        return action.takeIf { it in allowed }
    }
    fun isSessionControl(value: String?): Boolean = value in setOf("PAUSE", "DISARM", "STOP")
}
