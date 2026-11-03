package com.airgesture.control

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DevLoopSnapshotProviderTest {
    @Test fun durableSnapshotRecoversAnEndedSessionWithoutClaimingACleanExit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = java.io.File(context.filesDir, "devloop").apply { mkdirs() }
        root.resolve("session.json").writeText("""{"schema":1,"record":"session","session_id":"ended","build_id":"sha:1381","seq":42,"state":"recording","clean":false,"click_success":7}""")
        context.contentResolver.query(Uri.parse("content://com.airgesture.control.devloop/snapshot"), null, null, null, null).use { cursor ->
            assertNotNull("A stopped capture must be readable without logcat", cursor)
            assertTrue(cursor!!.moveToFirst())
            val result = JSONObject(cursor.getString(0))
            assertEquals("inactive", result.getString("capture_state"))
            val manifest = result.getJSONObject("manifest")
            assertEquals("unclean", manifest.getString("state"))
            assertEquals(7, manifest.getInt("click_success"))
            assertEquals(43, manifest.getInt("seq"))
            assertFalse(manifest.getBoolean("clean"))
        }
    }
}
