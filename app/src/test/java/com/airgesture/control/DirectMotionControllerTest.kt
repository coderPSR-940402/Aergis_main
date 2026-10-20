package com.airgesture.control
import com.airgesture.control.filtering.Point3D
import org.junit.Assert.*
import org.junit.Test

class DirectMotionControllerTest {
    @Test fun unarmedRepositionNeverDispatches() {
        val c = DirectMotionController()
        repeat(100) { assertNull(c.process(Point3D(it / 100f, .5f), .2f, 1f, it * 20L, "a", 0L, true)) }
    }
    @Test fun everyDirectionRequiresClutchAndFiresOnce() {
        for ((dx, dy, action) in listOf(Triple(-.2f,0f,AirAction.SWIPE_LEFT),Triple(.2f,0f,AirAction.SWIPE_RIGHT),
            Triple(0f,-.2f,AirAction.SCROLL_UP),Triple(0f,.2f,AirAction.SCROLL_DOWN))) {
            val c = DirectMotionController(); c.arm("a", 0, 0)
            for (t in listOf(0L, 100L, 150L)) assertNull(c.process(Point3D(.5f,.5f), .2f, 1f, t,"a",0,true))
            assertEquals(action,c.process(Point3D(.5f+dx,.5f+dy),.2f,1f,300,"a",0,true))
            assertNull(c.process(Point3D(.5f+dx,.5f+dy),.2f,1f,400,"a",0,true))
        }
    }
    @Test fun lossOwnerEpochAndStalenessCancel() {
        for (mode in 0..3) {
            val c=DirectMotionController(); c.arm("a",0,0)
            c.process(Point3D(.5f,.5f),.2f,1f,0,"a",0,true)
            c.process(Point3D(.5f,.5f),.2f,1f,150,"a",0,true)
            assertNull(c.process(Point3D(.8f,.5f),.2f,1f,if(mode==3) 500 else 250,
                if(mode==0) "b" else "a",if(mode==1) 1 else 0,mode!=2))
            assertFalse(c.active)
        }
    }
}
