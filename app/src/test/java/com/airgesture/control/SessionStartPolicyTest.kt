package com.airgesture.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStartPolicyTest {
    @Test fun deniesWithoutCameraPermission() {
        val result = SessionStartPolicy.evaluate(false, true)
        assertFalse(result.allowed)
    }

    @Test fun deniesWithoutAccessibility() {
        val result = SessionStartPolicy.evaluate(true, false)
        assertFalse(result.allowed)
    }

    @Test fun allowsReadySession() {
        val result = SessionStartPolicy.evaluate(true, true)
        assertTrue(result.allowed)
    }
}
