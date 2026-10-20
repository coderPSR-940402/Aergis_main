package com.airgesture.control.filtering
import org.junit.Assert.*
import org.junit.Test
class PartialPinchEvidenceTest {
    private fun pose() = MutableList(21) { Point3D(.5f,.5f) }.apply {
        this[5]=Point3D(.4f,.5f); this[17]=Point3D(.6f,.5f)
        this[4]=Point3D(.5f,.4f); this[12]=Point3D(.51f,.4f); this[8]=Point3D(.5f,.3f)
    }
    @Test fun irrelevantMissingLandmarksDoNotVetoPointerContact() {
        for(i in listOf(0,1,2,3,6,7,9,10,11,13,14,15,16,18,19,20)) {
            val p=pose(); p[i]=Point3D(Float.NaN,Float.NaN,Float.NaN)
            assertTrue("landmark $i",PoseGeometryEvidenceEvaluator().evaluatePointer(p).accepted)
            assertFalse(PoseGeometryEvidenceEvaluator().evaluate(p).accepted)
        }
    }
    @Test fun foreshortenedPalmDepthPreservesAnatomicalScale() {
        val p=pose(); val q=p.toMutableList()
        q[5]=Point3D(.45f,.5f,-.08660254f); q[17]=Point3D(.55f,.5f,.08660254f)
        assertEquals(KinematicValidator().calculateNormalizedFingerDistance(p),
            KinematicValidator().calculateNormalizedFingerDistance(q),.0001f)
    }
}
