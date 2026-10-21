package com.airgesture.control.gaze

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.airgesture.control.AirRuntime
import com.airgesture.control.ControlMode
import kotlin.math.hypot

/** Calibration and held-out checks only. Never calls an AccessibilityService action. */
class GazeLabActivity : Activity() {
    private val fit = GazeCalibration()
    private val samples = ArrayList<GazeCalibration.Sample>()
    private val recent = ArrayList<GazeLab.Observation>()
    private val targets = listOf(.15f,.5f,.85f).flatMap { y -> listOf(.15f,.5f,.85f).map { x -> x to y } }
    private val validationTargets = listOf(.32f to .32f, .68f to .32f, .68f to .68f, .32f to .68f)
    private var validationIndex = 0
    private var estimate: Pair<Float,Float>? = null
    private var lastSample = 0L
    private var calibrated = false
    private var active = false
    private var generation = 0L
    private lateinit var status: TextView
    private lateinit var canvasView: View
    private val staleGuard = object : Runnable {
        override fun run() {
            if (!active) return
            if (SystemClock.uptimeMillis()-lastSample > 350L) { estimate = null; recent.clear(); canvasView.invalidate() }
            canvasView.postDelayed(this, 150L)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AirRuntime.controlMode = ControlMode.READY
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,16,16,16) }
        layout.addView(TextView(this).apply { text = "Gaze Lab — experimental, local only. Start camera capture first. Look at each yellow target, keep your head comfortable, then Capture. No clicks or device actions are generated." })
        status = TextView(this); layout.addView(status)
        canvasView = object : View(this) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.rgb(15,15,24))
                val target = if (calibrated) validationTargets[validationIndex] else targets[samples.size.coerceAtMost(8)]
                paint.color = Color.YELLOW; canvas.drawCircle(target.first*width,target.second*height,18f,paint)
                estimate?.let { paint.color=Color.CYAN; canvas.drawCircle(it.first*width,it.second*height,12f,paint) }
            }
        }
        layout.addView(canvasView,LinearLayout.LayoutParams(-1,0,1f))
        layout.addView(Button(this).apply { text="Capture target / validate estimate"; setOnClickListener { capture() } })
        layout.addView(Button(this).apply { text="Reset calibration"; setOnClickListener {
            samples.clear(); recent.clear(); calibrated=false; estimate=null; status.text="Look at target 1 of 9"; canvasView.invalidate()
        } })
        setContentView(layout)
    }
    private fun capture() {
        val now = SystemClock.uptimeMillis()
        val good = recent.filter { now-it.uptimeMs <= 800L && it.features != null }
        if (good.size < 5 || now-lastSample > 250L) { status.text="Need five fresh eye samples. Keep both eyes visible."; return }
        val mean = DoubleArray(4) { i -> good.map { it.features!![i] }.sorted()[good.size/2] }
        if (good.any { o -> (0..1).any { kotlin.math.abs(o.features!![it]-mean[it]) > .04 } }) {
            status.text="Eyes moving; look steadily at the target and retry."; return
        }
        if (calibrated) {
            val p = fit.estimate(mean)
            val location = IntArray(2); canvasView.getLocationOnScreen(location)
            val expected = validationTargets[validationIndex]
            val display = resources.displayMetrics
            val targetX = location[0] + expected.first * canvasView.width
            val targetY = location[1] + expected.second * canvasView.height
            status.text = if(p == null) "Outside calibrated range; recalibrate." else
                "Held-out error: %.1f%% of screen diagonal. Experimental; no actions.".format(
                    hypot(p.first*display.widthPixels-targetX,p.second*display.heightPixels-targetY) /
                        hypot(display.widthPixels.toFloat(),display.heightPixels.toFloat())*100f)
            validationIndex = (validationIndex+1) % validationTargets.size
            recent.clear(); canvasView.invalidate()
            return
        }
        val target = targets[samples.size]
        // Model outputs use full-screen coordinates; the target canvas occupies only part of it.
        val location = IntArray(2); canvasView.getLocationOnScreen(location)
        val display = resources.displayMetrics
        val x = (location[0]+target.first*canvasView.width)/display.widthPixels
        val y = (location[1]+target.second*canvasView.height)/display.heightPixels
        samples.add(GazeCalibration.Sample(mean,x.toDouble(),y.toDouble())); recent.clear()
        if (samples.size == 9) {
            calibrated=fit.fit(samples)
            if (!calibrated) { samples.clear(); status.text="Calibration did not separate gaze targets. Retry with stable head and clear lighting." }
            else status.text="Fit ready. Cyan estimate is experimental. Look at each new yellow target and Capture to validate. Leaving stops gaze processing."
        } else status.text="Look at target ${samples.size+1} of 9"
        canvasView.invalidate()
    }
    override fun onResume() {
        super.onResume(); active=true
        val token = ++generation
        GazeLab.start(this) { observation -> runOnUiThread {
            if (active && token == generation) {
                lastSample=observation.uptimeMs
                if (observation.features == null) { recent.clear(); estimate=null }
                else {
                    recent.add(observation); while(recent.size>8) recent.removeAt(0)
                    val full=if(calibrated) fit.estimate(observation.features) else null
                    val location=IntArray(2); canvasView.getLocationOnScreen(location)
                    estimate=full?.let { ((it.first*resources.displayMetrics.widthPixels-location[0])/canvasView.width.coerceAtLeast(1)) to
                        ((it.second*resources.displayMetrics.heightPixels-location[1])/canvasView.height.coerceAtLeast(1)) }
                }
                if (!calibrated) status.text="${observation.message}. Target ${samples.size+1}/9. ${recent.size}/5 samples"
                canvasView.invalidate()
            }
        } }
        canvasView.post(staleGuard)
    }
    override fun onPause() {
        active=false; generation++; canvasView.removeCallbacks(staleGuard); GazeLab.stop(); recent.clear(); estimate=null
        super.onPause()
    }
}
