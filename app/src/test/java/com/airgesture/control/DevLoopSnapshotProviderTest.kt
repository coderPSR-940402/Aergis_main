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
import org.robolectric.shadows.ShadowBinder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DevLoopSnapshotProviderTest {
    private fun query(params: String = ""): JSONObject {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.contentResolver.query(Uri.parse("content://com.airgesture.control.devloop/snapshot$params"), null, null, null, null).use {
            assertNotNull(it)
            assertTrue(it!!.moveToFirst())
            assertTrue("Shell transport must stay below its output cap", it.getString(0).length < 32000)
            JSONObject(String(android.util.Base64.decode(it.getString(0), android.util.Base64.DEFAULT)))
        }
    }

    @Test fun unprivilegedAppsCannotReadRedactedEvidenceEither() {
        val uid = android.os.Process.myUid()
        try {
            ShadowBinder.setCallingUid(12345)
            assertThrows(SecurityException::class.java) { query() }
        } finally { ShadowBinder.setCallingUid(uid) }
    }

    @Test fun pathLikeSessionAndSnapshotIdentitiesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { query("?session_id=..") }
        assertThrows(IllegalArgumentException::class.java) { query("?session_id=..%2Foutside") }
        assertThrows(IllegalArgumentException::class.java) { query("?snapshot=..%2Foutside") }
        assertThrows(IllegalArgumentException::class.java) { query("?snapshot=${"a".repeat(63)}g") }
    }

    @Test fun validHistoricalSessionIdentityStillReadsItsOwnFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = java.io.File(context.filesDir, "devloop").apply { mkdirs() }
        root.resolve("session.json").writeText("""{"session_id":"current","seq":1,"state":"recording"}""")
        root.resolve("sessions").mkdirs()
        root.resolve("traces").mkdirs()
        root.resolve("sessions/prior-123.json").writeText(
            """{"schema":1,"record":"session","session_id":"prior-123","build_id":"sha:1381","seq":2,"state":"stopped","clean":true}""")
        root.resolve("traces/prior-123.json").writeText(
            """{"session_id":"prior-123","truncated":false,"records":[{"seq":1,"step":"open","request_id":42}]}""")

        val result = query("?session_id=prior-123")

        assertEquals("prior-123", result.getString("session_id"))
        assertEquals(42, result.getJSONArray("records").getJSONObject(0).getInt("request_id"))
    }

    @Test fun durableReplayPagesRemainImmutableAcrossNewCheckpoints() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = java.io.File(context.filesDir, "devloop").apply { mkdirs() }
        root.resolve("session.json").writeText("""{"schema":1,"record":"session","session_id":"pages","build_id":"sha:1381","seq":300,"state":"stopped","clean":true}""")
        val records = org.json.JSONArray()
        for (i in 1..200) records.put(JSONObject().put("record", "trace").put("session_id", "pages")
            .put("build_id", "sha:1381").put("seq", i).put("request_id", i).put("target_x", 321))
        root.resolve("trace.json").writeText(JSONObject().put("session_id", "pages").put("truncated", false).put("records", records).toString())
        val first = query()
        // The producer checkpoints between page one and page two.
        root.resolve("trace.json").writeText("""{"session_id":"pages","records":[]}""")
        val seen = mutableListOf<Int>()
        var page = first
        while (true) {
            val rows = page.getJSONArray("records")
            for (i in 0 until rows.length()) {
                assertEquals(321, rows.getJSONObject(i).getInt("target_x"))
                seen.add(rows.getJSONObject(i).getInt("request_id"))
            }
            if (page.isNull("next_offset")) break
            page = query("?offset=${page.getInt("next_offset")}&snapshot=${first.getString("snapshot_id")}")
        }
        assertEquals((1..200).toList(), seen)
        assertEquals(24, query("?snapshot=${first.getString("snapshot_id")}").getJSONArray("records").length())
        assertNotEquals(first.getString("snapshot_id"), query().getString("snapshot_id"))
    }

    @Test fun durableSnapshotRecoversAnEndedSessionWithoutClaimingACleanExit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = java.io.File(context.filesDir, "devloop").apply { mkdirs() }
        root.resolve("session.json").writeText("""{"schema":1,"record":"session","session_id":"ended","build_id":"sha:1381","seq":42,"state":"recording","clean":false,"click_success":7}""")
        context.contentResolver.query(Uri.parse("content://com.airgesture.control.devloop/snapshot"), null, null, null, null).use { cursor ->
            assertNotNull("A stopped capture must be readable without logcat", cursor)
            assertTrue(cursor!!.moveToFirst())
            val result = JSONObject(String(android.util.Base64.decode(cursor.getString(0), android.util.Base64.DEFAULT)))
            assertEquals("inactive", result.getString("capture_state"))
            val manifest = result.getJSONObject("manifest")
            assertEquals("unclean", manifest.getString("state"))
            assertEquals(7, manifest.getInt("click_success"))
            assertEquals(43, manifest.getInt("seq"))
            assertFalse(manifest.getBoolean("clean"))
        }
    }
}
