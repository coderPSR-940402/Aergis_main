package com.airgesture.control
import com.airgesture.control.filtering.Point3D
import org.junit.Assert.*
import org.junit.Test

class FingerGunTest {
    @Test fun doubleGunDirectionsFollowMirroredUprightScreenCoordinates() {
        val base = LandmarkGestureRecognizerTest.pose(RegisteredGesture.THUMB_INDEX_MIDDLE)
        val names = listOf("Double_Gun_Up", "Double_Gun_Left", "Double_Gun_Down", "Double_Gun_Right")
        for (turn in 0..3) {
            val points = base.map {
                var x = it.x - .5f; var y = it.y - .5f
                repeat(turn) { val old = x; x = -y; y = old }
                Point3D(.5f + x, .5f + y)
            }
            assertEquals(names[turn], LandmarkGestureRecognizer.recognizeDirectional(points, 1f)?.name)
        }
    }
    @Test fun diagonalDoubleGunCannotChooseAnAxisAndMalformedHandsCannotFire() {
        val base=LandmarkGestureRecognizerTest.pose(RegisteredGesture.THUMB_INDEX_MIDDLE)
        val angle=kotlin.math.PI/4
        val diagonal=base.map { val x=it.x-.5f; val y=it.y-.5f
            Point3D(.5f+(x*kotlin.math.cos(angle)-y*kotlin.math.sin(angle)).toFloat(),
                .5f+(x*kotlin.math.sin(angle)+y*kotlin.math.cos(angle)).toFloat()) }
        val signal=LandmarkGestureRecognizer.recognizeDirectional(diagonal,1f)
        assertEquals("Double_Gun_Ambiguous",signal?.name); assertEquals(0f,signal!!.score,0f)
        assertNull(LandmarkGestureRecognizer.recognizeDirectional(base.take(20),1f))
        val wide=base.map { Point3D(.5f+(it.x-.5f)/2,it.y) }
        assertEquals("Double_Gun_Up",LandmarkGestureRecognizer.recognizeDirectional(wide,2f)?.name)
    }
    @Test fun genericDoubleGunAndSingleGunKeepTheirExistingIdentity() {
        for (pose in listOf(RegisteredGesture.L_SHAPE, RegisteredGesture.THUMB_INDEX_MIDDLE))
            assertEquals(pose.signalName, LandmarkGestureRecognizer.recognize(LandmarkGestureRecognizerTest.pose(pose), 1f)?.name)
    }
}
