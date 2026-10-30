package com.airgesture.control
import org.junit.Assert.*
import org.junit.Test
class FingertipPatchTrackerTest {
    private val pixels = java.util.Random(317L).let { random -> FloatArray(120*120) { random.nextInt(256).toFloat() } }
    private fun texture(x:Int,y:Int):Float = pixels[y*120+x]
    @Test fun fullPixelSearchReadsEachSourcePixelAtMostOnce() {
        val tracker = FingertipPatchTracker()
        tracker.seed(120,120,50,50,::texture)
        var reads = 0
        val point = tracker.track(120,120) { x,y -> reads++; texture(x-1,y) }
        assertNotNull(point)
        assertTrue("A 49-center search plus 8-pixel margins fits in 65x65 source reads: $reads",reads <= 4225)
    }
    @Test fun exactTextureSurvivesOddPixelMotionOnEitherAxis() {
        for ((dx,dy) in listOf(1 to 0, 0 to -1, 3 to 5, -5 to 3)) {
            val tracker = FingertipPatchTracker()
            tracker.seed(120,120,50,50,::texture)
            val point = tracker.track(120,120) { x,y -> texture(x-dx,y-dy) }
            assertNotNull("Exact translation ($dx,$dy) must not be skipped", point)
            assertEquals((50+dx)/120f,point!!.x,.0001f)
            assertEquals((50+dy)/120f,point.y,.0001f)
        }
    }
    @Test fun clippedSearchNearEdgeStillIncludesTheLastKnownCenter() {
        val tracker = FingertipPatchTracker()
        tracker.seed(120,120,9,50,::texture)
        val point = tracker.track(120,120,::texture)
        assertNotNull("Clipping the search must not change pixel parity",point)
        assertEquals(9/120f,point!!.x,.0001f)
    }
    @Test fun clippedSearchStaysInsideSmallAndRectangularFrames() {
        for ((w,h) in listOf(17 to 17, 35 to 29, 120 to 80)) {
            for (cx in listOf(8,9,w-10,w-9).filter { it in 8 until w-8 }) {
                for (cy in listOf(8,9,h-10,h-9).filter { it in 8 until h-8 }) {
                    fun bounded(x: Int,y: Int): Float {
                        assertTrue(x in 0 until w && y in 0 until h)
                        return texture(x,y)
                    }
                    val tracker = FingertipPatchTracker()
                    tracker.seed(w,h,cx,cy,::bounded)
                    val point = tracker.track(w,h,::bounded)
                    assertNotNull(point)
                    assertEquals(cx.toFloat()/w,point!!.x,.0001f)
                    assertEquals(cy.toFloat()/h,point.y,.0001f)
                }
            }
        }
    }
    @Test fun immutableTextureTracksTranslationWithoutAnAgeTimer() {
        val t=FingertipPatchTracker(); t.seed(120,120,50,50,::texture)
        repeat(30) { val p=t.track(120,120) { x,y -> texture(x-4,y-2) }; assertNotNull(p); assertEquals(54f/120,p!!.x,.001f) }
    }
    @Test fun repeatedTextureIsRejectedInsteadOfGuessing() {
        val t=FingertipPatchTracker()
        fun repeated(x:Int,y:Int):Float = ((x*73856093 xor y*19349663) and 255).toFloat()
        t.seed(120,120,50,50,::repeated)
        assertNull(t.track(120,120) { x,y -> repeated(x-4,y-2) })
    }
    @Test fun flatOccludedOrAmbiguousEvidenceCannotMove() {
        val t=FingertipPatchTracker(); t.seed(120,120,50,50,::texture)
        assertNull(t.track(120,120) { _,_ -> 100f })
        t.seed(120,120,50,50) { _,_ -> 100f }
        assertNull(t.track(120,120,::texture))
    }
}
