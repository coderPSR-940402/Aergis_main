package com.airgesture.control
import org.junit.Assert.*
import org.junit.Test
class FingertipPatchTrackerTest {
    private fun texture(x:Int,y:Int):Float = ((x*73856093 xor y*19349663) and 255).toFloat()
    @Test fun immutableTextureTracksTranslationWithoutAnAgeTimer() {
        val t=FingertipPatchTracker(); t.seed(120,120,50,50,::texture)
        repeat(30) { val p=t.track(120,120) { x,y -> texture(x-4,y-2) }; assertNotNull(p); assertEquals(54f/120,p!!.x,.001f) }
    }
    @Test fun flatOccludedOrAmbiguousEvidenceCannotMove() {
        val t=FingertipPatchTracker(); t.seed(120,120,50,50,::texture)
        assertNull(t.track(120,120) { _,_ -> 100f })
        t.seed(120,120,50,50) { _,_ -> 100f }
        assertNull(t.track(120,120,::texture))
    }
}
