package com.airgesture.control
import org.junit.Assert.*
import org.junit.Test
class StaticPoseConfirmationTest {
    @Test fun transientAndWeakEvidenceCannotCompleteConfirmation() {
        val c=StaticPoseConfirmation()
        assertFalse(c.accept("Victory","a",0,0,true,100))
        assertFalse(c.accept("Palm","a",0,60,true,100))
        assertFalse(c.accept("Palm","a",0,100,false,100))
        assertFalse(c.accept("Palm","a",0,160,true,100))
        assertTrue(c.accept("Palm","a",0,260,true,100))
        assertFalse(c.accept("Palm","a",1,300,true,100))
    }
    @Test fun staleAndDuplicateSamplesDoNotAdvance() {
        val c=StaticPoseConfirmation()
        assertFalse(c.accept("a","a",0,0,true,100))
        assertFalse(c.accept("a","a",0,0,true,100))
        assertFalse(c.accept("a","a",0,1000,true,100))
    }
}
