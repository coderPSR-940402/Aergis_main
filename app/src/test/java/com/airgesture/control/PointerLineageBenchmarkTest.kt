package com.airgesture.control

import com.airgesture.control.filtering.Point3D
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/** Synthetic evidence, never an automatic default-filter decision. Units are screen-normalized. */
class PointerLineageBenchmarkTest {
    @Test
    fun compareRealProductionFiltersOnIdenticalMeasuredTips() {
        val report = linkedMapOf<String, Map<String, Map<String, Double>>>()
        for (scene in listOf("stationary", "travel", "fast", "reversal", "edges", "dropout")) {
            val interpreter = GestureInterpreter()
            val comparison = PointerLineageComparison()
            val current = PointerLineageMetrics(); val recovered = PointerLineageMetrics()
            var previous = PointerCoordinateMapper.Point(0.5f, 0.5f)
            var previousCurrent = previous
            val visibility = PointerVisibilityGrace()
            var wasVisible = false
            var interrupted = 0
            var invalid = 0
            var recoveryCurrent = 0.0; var recoveryVc49 = 0.0
            var fastCurrent: Int? = null; var fastVc49: Int? = null
            for (i in 0..120) {
                if (scene == "dropout" && i in 40..44) {
                    invalid++
                    val visible = visibility.heldAt(1000L + i * 33L) != null
                    if (wasVisible && !visible) interrupted++
                    wasVisible = visible
                    continue
                }
                val tip = when (scene) {
                    "stationary" -> Point3D(0.5f + 0.002f * sin(i * 1.7f), 0.5f + 0.002f * sin(i * 2.3f))
                    "travel" -> Point3D(0.2f + 0.6f * i / 120f, 0.5f)
                    "fast" -> Point3D(if (i < 40) 0.25f else 0.75f, 0.5f)
                    "reversal" -> Point3D(0.2f + 0.6f * (if (i <= 60) i else 120 - i) / 60f, 0.5f)
                    "edges" -> Point3D(if (i < 60) 0f else 1f, if (i < 60) 0f else 1f)
                    else -> Point3D(if (i < 45) 0.45f else 0.75f, 0.5f)
                }
                val hand = MutableList(21) { Point3D(0.5f, 0.5f) }.apply { this[8] = tip }
                val legacy = interpreter.processFrame(hand, 1000L + i * 33L, actionsAllowed = false)!!
                val raw = PointerCoordinateMapper.map(tip.x, tip.y)
                val sample = comparison.update(raw, PointerCoordinateMapper.map(legacy.smoothedX, legacy.smoothedY), 1000L + i * 33L)
                visibility.record(sample.current, 1000L + i * 33L); wasVisible = true
                current.sample(raw, sample.current, scene == "stationary")
                recovered.sample(raw, sample.vc49, scene == "stationary")
                assertTrue(sample.vc49.x in minOf(previous.x, raw.x)..maxOf(previous.x, raw.x))
                assertTrue(sample.vc49.y in minOf(previous.y, raw.y)..maxOf(previous.y, raw.y))
                if (scene == "fast" && i >= 40) {
                    if (fastCurrent == null && abs(sample.current.x - raw.x) < 0.02f) fastCurrent = (i - 40) * 33
                    if (fastVc49 == null && abs(sample.vc49.x - raw.x) < 0.02f) fastVc49 = (i - 40) * 33
                }
                if (scene == "dropout" && i == 45) {
                    recoveryCurrent = hypot((sample.current.x - previousCurrent.x).toDouble(), (sample.current.y - previousCurrent.y).toDouble())
                    recoveryVc49 = hypot((sample.vc49.x - previous.x).toDouble(), (sample.vc49.y - previous.y).toDouble())
                }
                previous = sample.vc49
                previousCurrent = sample.current
            }
            fun metrics(m: PointerLineageMetrics, recovery: Double, settle: Int?) = m.snapshot().apply {
                put("invalidFrames", invalid.toDouble()); put("visibilityInterruptions", interrupted.toDouble())
                put("reacquisitionDiscontinuity", recovery)
                put("fastStepSettlingMs", (settle ?: 0).toDouble())
                // Step settling is an effective-response proxy; it is not camera/display latency.
            }
            report[scene] = linkedMapOf("current" to metrics(current, recoveryCurrent, fastCurrent),
                "vc49" to metrics(recovered, recoveryVc49, fastVc49))
        }
        fun json(m: Map<String, Double>) = m.entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key}\":${it.value}" }
        val result = report.entries.joinToString(prefix = "{", postfix = "}") { scene ->
            "\"${scene.key}\":" + scene.value.entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key}\":${json(it.value)}" }
        }
        val path = File("build/reports/testing-tools/lineage-benchmark.json")
        path.parentFile.mkdirs(); path.writeText(result)
        println("LINEAGE_BENCHMARK $result")
        assertTrue(report.getValue("stationary").getValue("vc49").getValue("stationaryJitterRms") > 0.0)
        assertTrue(report.getValue("edges").getValue("vc49").getValue("maxY") > 0.98)
    }
}
