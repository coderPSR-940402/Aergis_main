package com.airgesture.control
import org.junit.Assert.*
import org.junit.Test
class FingertipPatchTrackerTest {
    @Test fun normalizedRightAndBottomEndpointsCanRecoverBackIntoTheImage() {
        for ((nx,ny,dx,dy) in listOf(
            listOf(1f,.5f,-3f,0f), listOf(.5f,1f,0f,-3f), listOf(1f,1f,-3f,-3f))) {
            val tracker = FingertipPatchTracker()
            tracker.seedNormalized(120,120,nx,ny,::texture)
            val point = tracker.track(120,120) { x,y ->
                texture((x-dx.toInt()).coerceIn(0,119),(y-dy.toInt()).coerceIn(0,119))
            }
            assertNotNull("An accepted normalized border tip ($nx,$ny) must seed recovery",point)
            val expectedX = if (nx == 1f) 116f/120f else .5f
            val expectedY = if (ny == 1f) 116f/120f else .5f
            assertEquals(expectedX,point!!.x,.0001f)
            assertEquals(expectedY,point.y,.0001f)
        }
    }

    @Test fun invalidNormalizedTipRevokesThePreviousTemplateWithoutReadingPixels() {
        for ((nx,ny) in listOf(Float.NaN to .5f,.5f to Float.POSITIVE_INFINITY,
            -.001f to .5f,1.001f to .5f,.5f to -.001f,.5f to 1.001f)) {
            val tracker = FingertipPatchTracker()
            tracker.seed(120,120,50,50,::texture)
            tracker.seedNormalized(120,120,nx,ny) { _,_ -> fail("Invalid model input cannot read pixels"); 0f }
            assertNull("Invalid input must not retain an earlier template",tracker.track(120,120,::texture))
        }
    }

    @Test fun visibleFingertipAtAnyImageBorderRetainsItsExactOffset() {
        for ((px,py) in listOf(0 to 50, 119 to 50, 50 to 0, 50 to 119, 0 to 0, 119 to 119)) {
            val tracker = FingertipPatchTracker()
            fun bounded(x:Int,y:Int):Float {
                assertTrue(x in 0..119 && y in 0..119)
                return texture(x,y)
            }
            tracker.seed(120,120,px,py,::bounded)
            val still = tracker.track(120,120,::bounded)
            assertNotNull("Visible border tip ($px,$py) needs an in-bounds appearance patch",still)
            assertEquals(px/120f,still!!.x,.0001f)
            assertEquals(py/120f,still.y,.0001f)
        }
    }
    @Test fun edgeTemplateCanFollowVisibleFingertipBackIntoTheFrame() {
        val tracker = FingertipPatchTracker()
        tracker.seed(120,120,0,50,::texture)
        val point = tracker.track(120,120) { x,y -> texture((x-3).coerceAtLeast(0),y) }
        assertNotNull(point)
        assertEquals(3/120f,point!!.x,.0001f)
        assertEquals(50/120f,point.y,.0001f)
    }
    @Test fun incompleteImagesAndOffCameraTipsCannotSeedAppearanceEvidence() {
        for ((w,h,px,py) in listOf(listOf(16,120,8,50),listOf(120,16,50,8),
            listOf(120,120,-1,50),listOf(120,120,120,50))) {
            val tracker = FingertipPatchTracker()
            tracker.seed(w,h,px,py) { _,_ -> fail("Invalid seed cannot read camera pixels"); 0f }
            assertNull(tracker.track(w,h,::texture))
        }
    }
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
