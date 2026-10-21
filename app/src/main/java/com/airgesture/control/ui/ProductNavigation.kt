package com.airgesture.control.ui

import com.airgesture.control.R

internal enum class AergisDestination(val label: Int, val glyph: AergisGlyph, val root: Boolean = false) {
    CONTROL(R.string.product_control, AergisGlyph.CONTROL, true),
    GESTURES(R.string.product_gestures, AergisGlyph.GESTURES, true),
    SETTINGS(R.string.product_settings, AergisGlyph.SETTINGS, true),
    WELCOME(R.string.product_welcome, AergisGlyph.GESTURES),
    PERMISSIONS(R.string.product_permissions, AergisGlyph.SETTINGS),
    PRECISION(R.string.product_precision, AergisGlyph.POINTER),
    CALIBRATION(R.string.product_calibration, AergisGlyph.TRACKING),
    PRACTICE(R.string.product_practice, AergisGlyph.TRACKING),
    LIVE(R.string.product_live, AergisGlyph.TRACKING),
    MOTION(R.string.product_motion, AergisGlyph.MOTION),
    MOTION_PRACTICE(R.string.product_motion_practice, AergisGlyph.MOTION),
    LEARN(R.string.product_learn, AergisGlyph.GESTURES),
    DIAGNOSTICS(R.string.product_diagnostics, AergisGlyph.TRACKING),
    ADVANCED(R.string.product_advanced, AergisGlyph.SETTINGS),
    ABOUT(R.string.product_about, AergisGlyph.CONTROL);

    val disarmsOnEntry: Boolean get() = this in setOf(CALIBRATION, PRACTICE, LEARN, MOTION_PRACTICE)
}

/** String IDs survive recreation without making navigation an engine dependency. */
internal data class ProductNavigation(val routes: List<String> = listOf(AergisDestination.CONTROL.name)) {
    val current: AergisDestination get() = AergisDestination.entries.firstOrNull { it.name == routes.lastOrNull() }
        ?: AergisDestination.CONTROL
    val canGoBack: Boolean get() = routes.size > 1
    fun open(destination: AergisDestination): ProductNavigation = when {
        destination == current -> this
        destination.root -> ProductNavigation(listOf(destination.name))
        else -> ProductNavigation(routes + destination.name)
    }
    fun back(): ProductNavigation = if (canGoBack) ProductNavigation(routes.dropLast(1)) else ProductNavigation()
}
