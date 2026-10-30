package com.airgesture.control

import org.json.JSONArray
import org.json.JSONObject

/** Derive action/selection evidence from retained logs, including recovered sessions. */
internal object DiagnosticActionSummary {
    fun summarize(events: Sequence<JSONObject>): JSONObject {
        val selections = JSONObject()
        val reasons = JSONObject()
        val actions = JSONObject()
        val requests = linkedMapOf<Long, Pair<String, MutableList<String>>>()
        var unpaired = 0
        var unreadable = 0
        fun increment(target: JSONObject, key: String) { target.put(key, target.optInt(key) + 1) }
        for (event in events) {
            val id = (event.opt("requestId") as? Number)?.toLong()
            when (event.optString("event")) {
                "unreadable" -> unreadable++
                "selection_changed" -> {
                    increment(selections, event.optString("kind", "unknown"))
                    increment(reasons, event.optString("reason", "unknown"))
                }
                "action_requested" -> {
                    val action = event.optString("action", "unknown")
                    val entry = actions.optJSONObject(action) ?: JSONObject().put("requested", 0)
                        .put("results", JSONArray()).also { actions.put(action, it) }
                    increment(entry, "requested")
                    if (id != null) requests[id] = action to mutableListOf()
                }
                "action_outcome" -> {
                    val request = id?.let(requests::get)
                    if (request == null) unpaired++ else request.second.add(event.optString("outcome", "unknown"))
                }
            }
        }
        for ((action, outcomes) in requests.values) {
            // SUBMITTED is intermediate. Keep a recorded terminal response when present.
            val result = outcomes.firstOrNull { it != "SUBMITTED" } ?: outcomes.lastOrNull() ?: "NO_RECORDED_OUTCOME"
            actions.getJSONObject(action).getJSONArray("results").put(result)
        }
        return JSONObject().put("selectionChanges", selections).put("selectionReasons", reasons)
            .put("byAction", actions).put("unpairedOutcomes", unpaired).put("unreadableEvents", unreadable)
    }
}
