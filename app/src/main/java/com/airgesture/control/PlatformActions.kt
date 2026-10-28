package com.airgesture.control

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager

/** Public Android capabilities only; submission is not proof of visible completion. */
internal class PlatformActions(private val context: Context,
    private val record: (ActionDispatchOutcome) -> Unit = AirRuntime::recordActionDispatchOutcome) {
    fun execute(action: AirAction) {
        val outcome = try {
            val submitted = when (action) {
                AirAction.APP_SEARCH -> launch(Intent(context, AppSearchActivity::class.java))
                AirAction.CALIBRATE_POINTER -> {
                    AirRuntime.controlMode = ControlMode.READY
                    launch(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CALIBRATION)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
                AirAction.WEB_SEARCH -> launch(Intent(Intent.ACTION_WEB_SEARCH))
                AirAction.WIFI_CONTROLS -> launch(Intent(Settings.ACTION_WIFI_SETTINGS))
                AirAction.BLUETOOTH_SETTINGS -> launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                AirAction.ROTATION_TOGGLE -> rotate()
                else -> false
            }
            if (submitted) ActionDispatchOutcome.SUBMITTED else ActionDispatchOutcome.PLATFORM_REJECTED
        } catch (_: android.content.ActivityNotFoundException) {
            ActionDispatchOutcome.PLATFORM_UNAVAILABLE
        } catch (_: SecurityException) {
            ActionDispatchOutcome.PLATFORM_REJECTED
        } catch (_: RuntimeException) {
            ActionDispatchOutcome.EXECUTION_FAILED
        }
        record(outcome)
    }
    private fun launch(intent: Intent): Boolean {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
    @Suppress("DEPRECATION")
    private fun rotate(): Boolean {
        if (!Settings.System.canWrite(context)) {
            launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            return false // Permission screen opening is not a rotation.
        }
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val current = display.rotation
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val naturalLandscape = landscape.xor(current == Surface.ROTATION_90 || current == Surface.ROTATION_270)
        val target = if (landscape == naturalLandscape) Surface.ROTATION_90 else Surface.ROTATION_0
        val resolver = context.contentResolver
        val oldAuto = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        val oldRotation = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, current)
        try {
            check(Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0))
            check(Settings.System.putInt(resolver, Settings.System.USER_ROTATION, target))
        } catch (failure: RuntimeException) {
            runCatching { Settings.System.putInt(resolver, Settings.System.USER_ROTATION, oldRotation) }
            runCatching { Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, oldAuto) }
            throw failure
        }
        // Apps with a fixed orientation can override USER_ROTATION.
        return true
    }
}
