package com.airgesture.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import com.airgesture.control.ui.*

@Composable
internal fun TestingToolsCard(onSave: (File) -> Unit, onShare: (DiagnosticExport) -> Unit) {
    val context = LocalContext.current
    val testing by TestingTools.state.collectAsStateWithLifecycle()
    val readiness = remember { AirRuntime.uiState.map { it.running to it.cameraReady }.distinctUntilChanged() }
    val runtime by readiness.collectAsStateWithLifecycle(initialValue = AirRuntime.running to AirRuntime.cameraReady)
    AergisPanel {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.testing_title), style = MaterialTheme.typography.titleMedium)
            AergisStatusChip(stringResource(when (testing.status) {
                RecordingStatus.RECORDING -> R.string.ui_recording_active
                RecordingStatus.EXPORTING -> R.string.ui_recording_exporting
                RecordingStatus.READY -> R.string.ui_recording_ready
                RecordingStatus.ERROR -> R.string.ui_recording_error
                RecordingStatus.IDLE -> R.string.ui_recording_idle
            }), when (testing.status) {
                RecordingStatus.RECORDING -> AergisColors.Cyan
                RecordingStatus.ERROR -> AergisColors.Error
                else -> AergisColors.Muted
            })
            Text(stringResource(R.string.testing_description))
            AergisButton(modifier = Modifier.fillMaxWidth(), onClick = { TestingTools.setMirror(!testing.mirror) },
                enabled = testing.mirror || (runtime.first && runtime.second && AirAccessibilityService.enabled())) {
                Text(stringResource(if (testing.mirror) R.string.testing_mirror_hide else R.string.testing_mirror_show))
            }
            AergisStatusChip(stringResource(R.string.ui_experimental), AergisColors.Caution)
            AergisButton(modifier = Modifier.fillMaxWidth(), onClick = { TestingTools.setFilterMode(
                if (testing.filterMode == PointerFilterMode.CURRENT) PointerFilterMode.VC49 else PointerFilterMode.CURRENT) }) {
                Text(stringResource(R.string.testing_filter_mode, testing.filterMode.name))
            }
            Text(stringResource(R.string.testing_filter_description))
            AergisButton(modifier = Modifier.fillMaxWidth(), onClick = { TestingTools.nextSegment() }) {
                Text(stringResource(R.string.testing_segment, testing.segment.name))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AergisButton(modifier = Modifier.fillMaxWidth(), onClick = { TestingTools.start(context) }, enabled = runtime.first && runtime.second &&
                    testing.status != RecordingStatus.RECORDING && testing.status != RecordingStatus.EXPORTING) {
                    Text(stringResource(R.string.testing_record_start))
                }
                AergisButton(modifier = Modifier.fillMaxWidth(), onClick = { TestingTools.stop() }, enabled = testing.status == RecordingStatus.RECORDING) {
                    Text(stringResource(R.string.testing_record_stop))
                }
            }
            testing.message?.let { Text(it) }
            if (testing.status == RecordingStatus.RECORDING || testing.frames > 0) {
                Text(stringResource(R.string.testing_record_counts, testing.frames, testing.dropped))
            }
            if (testing.status == RecordingStatus.ERROR) {
                AergisButton(modifier = Modifier.fillMaxWidth(), onClick = { TestingTools.retryExport() }) { Text(stringResource(R.string.testing_retry)) }
            }
            testing.export?.let { report ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AergisButton(modifier = Modifier.fillMaxWidth(), onClick = { onSave(report.pdf) }) { Text(stringResource(R.string.testing_save_pdf)) }
                    AergisButton(modifier = Modifier.fillMaxWidth(), onClick = { onSave(report.bundle) }) { Text(stringResource(R.string.testing_save_zip)) }
                }
                AergisButton(modifier = Modifier.fillMaxWidth(), onClick = { onShare(report) }) { Text(stringResource(R.string.testing_share)) }
            }
            if (!runtime.first) Text(stringResource(R.string.testing_start_camera))
        }
    }
}
