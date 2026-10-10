package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerReplayEvidenceTest {
    private val report = PointerReplayReport(
        validation = PointerReplayValidation(emptyList()),
        metrics = PointerReplayMetrics(
            traceId = "trace=corner\\v1",
            frameCount = 3,
            durationMs = 32L,
            meanAbsoluteError = 0.01f,
            p95AbsoluteError = 0.02f,
            maxAbsoluteError = 0.03f,
            meanOutputStep = 0.4f,
            maxOutputStep = 0.8f
        )
    )

    @Test
    fun reportConvertsToPrivacySafeEvidenceAndRoundTrips() {
        val evidence = PointerReplayEvidenceCodec.fromReport(
            report = report,
            commitSha = "abc123",
            mapperProfile = "legacy=default",
            evidenceType = PointerReplayEvidence.EvidenceType.REPOSITORY_COMPLETE,
            deviceModel = "Pixel\nLab"
        )

        val decoded = PointerReplayEvidenceCodec.decode(
            PointerReplayEvidenceCodec.encode(evidence!!)
        )
        assertEquals(evidence, decoded)
        assertTrue(PointerReplayEvidenceCodec.encode(evidence).length < 8_192)
    }

    @Test
    fun invalidReportDoesNotProduceEvidence() {
        val invalid = report.copy(metrics = null)
        assertNull(
            PointerReplayEvidenceCodec.fromReport(
                invalid,
                "abc123",
                "legacy",
                PointerReplayEvidence.EvidenceType.REPOSITORY_COMPLETE
            )
        )
    }

    @Test
    fun invalidValidationCannotBeOverriddenByPresentMetrics() {
        assertNull(PointerReplayEvidenceCodec.fromReport(
            report.copy(validation = PointerReplayValidation(listOf("Timestamp reversal"))),
            "abc123", "legacy", PointerReplayEvidence.EvidenceType.REPOSITORY_COMPLETE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateFieldsAreRejected() {
        val evidence = PointerReplayEvidenceCodec.fromReport(report, "abc123", "legacy",
            PointerReplayEvidence.EvidenceType.REPOSITORY_COMPLETE)!!
        PointerReplayEvidenceCodec.decode(PointerReplayEvidenceCodec.encode(evidence) + "frameCount=999\n")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedEscapesAreRejected() {
        val evidence = PointerReplayEvidenceCodec.fromReport(report, "abc123", "legacy",
            PointerReplayEvidence.EvidenceType.REPOSITORY_COMPLETE)!!
        PointerReplayEvidenceCodec.decode(PointerReplayEvidenceCodec.encode(evidence).replace("abc123", "abc\\q"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun infiniteToleranceCannotDisableRegressionGate() {
        PointerMappingRegressionThresholds(maxMeanErrorIncrease = Float.POSITIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedMetricFailsClosed() {
        PointerReplayEvidenceCodec.decode(
            "schemaVersion=1\ncommitSha=abc\ntraceId=t\nmapperProfile=m\n" +
                "evidenceType=REPOSITORY_COMPLETE\nframeCount=1\ndurationMs=0\n" +
                "meanAbsoluteError=not-a-number\n"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedPayloadIsRejected() {
        PointerReplayEvidenceCodec.decode("x=" + "a".repeat(8_192))
    }
}
