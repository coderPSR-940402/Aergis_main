package com.airgesture.control

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class DiagnosticActionSummaryTest {
    @Test fun outcomesPairByRequestIdDespiteOutOfOrderCallbacksAndLegacyRecordsStayUnpaired() {
        val records = listOf(
            JSONObject("""{"event":"action_requested","action":"TAP","requestId":1}"""),
            JSONObject("""{"event":"action_requested","action":"SELECT_ITEM","requestId":2}"""),
            JSONObject("""{"event":"selection_changed","kind":"selected","selectionId":1}"""),
            JSONObject("""{"event":"selection_changed","kind":"activation_rejected","reason":"target_missing_or_changed","requestId":2}"""),
            JSONObject("""{"event":"action_outcome","outcome":"PLATFORM_UNAVAILABLE","requestId":2}"""),
            JSONObject("""{"event":"action_outcome","outcome":"COMPLETED","requestId":1}"""),
            JSONObject("""{"event":"action_outcome","outcome":"SUBMITTED"}"""))
        val result = DiagnosticActionSummary.summarize(records.asSequence())
        assertEquals(1, result.getJSONObject("selectionChanges").getInt("selected"))
        assertEquals(1, result.getJSONObject("selectionReasons").getInt("target_missing_or_changed"))
        assertEquals("COMPLETED", result.getJSONObject("byAction").getJSONObject("TAP").getJSONArray("results").getString(0))
        assertEquals("PLATFORM_UNAVAILABLE", result.getJSONObject("byAction").getJSONObject("SELECT_ITEM").getJSONArray("results").getString(0))
        assertEquals(1, result.getInt("unpairedOutcomes"))
    }
}
