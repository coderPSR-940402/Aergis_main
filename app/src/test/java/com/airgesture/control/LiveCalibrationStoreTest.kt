package com.airgesture.control

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveCalibrationStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("aergis_calibration", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun profilesRemainIndependentForHandAndOrientation() {
        val store = PointerCalibrationStore(context)
        val left = PointerCalibrationProfile(top = 0.15f, bottom = 0.65f)
        val right = PointerCalibrationProfile(top = 0.2f, bottom = 0.7f)
        val landscape = PointerCalibrationProfile(top = 0.1f, bottom = 0.9f)
        assertTrue(store.save(left, ControlHandPreference.LEFT, false))
        assertTrue(store.save(right, ControlHandPreference.RIGHT, false))
        assertTrue(store.save(landscape, ControlHandPreference.LEFT, true))
        assertEquals(left, store.profile(ControlHandPreference.LEFT, false))
        assertEquals(right, store.profile(ControlHandPreference.RIGHT, false))
        assertEquals(landscape, store.profile(ControlHandPreference.LEFT, true))
        store.setEnabled(false, ControlHandPreference.LEFT, false)
        assertFalse(store.enabled(ControlHandPreference.LEFT, false))
        assertTrue(store.enabled(ControlHandPreference.RIGHT, false))
        store.reset(ControlHandPreference.LEFT, true)
        assertTrue(store.enabled(ControlHandPreference.LEFT, false).not())
        assertTrue(store.enabled(ControlHandPreference.RIGHT, false))
        assertFalse(store.enabled(ControlHandPreference.LEFT, true))
    }

    @Test
    fun legacyProfileRetainsItsBoundsAndLinearResponse() {
        context.getSharedPreferences("aergis_calibration", Context.MODE_PRIVATE).edit()
            .putString("pointer_profile", "2|0.2|0.8|0.15|0.65|true")
            .putBoolean("pointer_profile_enabled", true).commit()
        val store = PointerCalibrationStore(context)
        assertTrue(store.enabled())
        assertEquals(0.65f, store.profile().bottom, 0.0001f)
        assertEquals(1f, store.profile().curveY, 0f)
        assertFalse(store.enabled(ControlHandPreference.RIGHT, false))
    }
}
