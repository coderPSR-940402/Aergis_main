package com.airgesture.control

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class HandModelBundleTest {
    private fun bundle(prefix: Int, name: String = "hand_landmarker.task"): ByteArray {
        val out = ByteArrayOutputStream()
        repeat(prefix) { out.write(0) }
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("other.task")); zip.write(byteArrayOf(9)); zip.closeEntry()
            zip.putNextEntry(ZipEntry(name)); zip.write(byteArrayOf(1, 2, 3, 4)); zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test fun extractsExactNestedModelFromPlainAndPinnedTaskHeaders() {
        for (prefix in listOf(0, 2)) {
            val result = HandModelBundle.extract(ByteArrayInputStream(bundle(prefix)))
            assertTrue(result.isDirect)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), ByteArray(result.remaining()).also(result::get))
        }
    }

    @Test fun missingOrMalformedBundleDoesNotSubstituteAnotherModel() {
        for (bytes in listOf(bundle(2, "different.task"), byteArrayOf(0, 0, 0, 0))) {
            try { HandModelBundle.extract(ByteArrayInputStream(bytes)); fail("No valid hand submodel") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
