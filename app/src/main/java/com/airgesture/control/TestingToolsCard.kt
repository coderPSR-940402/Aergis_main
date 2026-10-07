package com.airgesture.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@Composable
internal fun TestingToolsCard(onSave: (File) -> Unit, onShare: (DiagnosticExport) -> Unit) {
    val context = LocalContext.current
    val testing by TestingTools.state.collectAsStateWithLifecycle()
    val runtime by AirRuntime.uiState.collectAsStateWithLifecycle(initialValue = AirRuntime.uiStateSnapshot())
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.testing_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.testing_description))
            Button(onClick = { TestingTools.setMirror(!testing.mirror) },
                enabled = testing.mirror || (runtime.running && runtime.cameraReady && AirAccessibilityService.enabled())) {
                Text(stringResource(if (testing.mirror) R.string.testing_mirror_hide else R.string.testing_mirror_show))
            }
            Button(onClick = { TestingTools.setFilterMode(
                if (testing.filterMode == PointerFilterMode.CURRENT) PointerFilterMode.VC49 else PointerFilterMode.CURRENT) }) {
                Text(stringResource(R.string.testing_filter_mode, testing.filterMode.name))
            }
            Text(stringResource(R.string.testing_filter_description))
            Button(onClick = { TestingTools.nextSegment() }) {
                Text(stringResource(R.string.testing_segment, testing.segment.name))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { TestingTools.start(context) }, enabled = runtime.running && runtime.cameraReady &&
                    testing.status != RecordingStatus.RECORDING && testing.status != RecordingStatus.EXPORTING) {
                    Text(stringResource(R.string.testing_record_start))
                }
                Button(onClick = { TestingTools.stop() }, enabled = testing.status == RecordingStatus.RECORDING) {
                    Text(stringResource(R.string.testing_record_stop))
                }
            }
            testing.message?.let { Text(it) }
            if (testing.status == RecordingStatus.RECORDING || testing.frames > 0) {
                Text(stringResource(R.string.testing_record_counts, testing.frames, testing.dropped))
            }
            if (testing.status == RecordingStatus.ERROR) {
                Button(onClick = { TestingTools.retryExport() }) { Text(stringResource(R.string.testing_retry)) }
            }
            testing.export?.let { report ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(report.pdf) }) { Text(stringResource(R.string.testing_save_pdf)) }
                    Button(onClick = { onSave(report.bundle) }) { Text(stringResource(R.string.testing_save_zip)) }
                }
                Button(onClick = { onShare(report) }) { Text(stringResource(R.string.testing_share)) }
            }
            if (!runtime.running) Text(stringResource(R.string.testing_start_camera))
        }
    }
}
