package com.airgesture.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    var showRecordings by remember { mutableStateOf(false) }
    val busy = testing.status == RecordingStatus.RECORDING || testing.status == RecordingStatus.EXPORTING
    if (showRecordings) {
        AlertDialog(onDismissRequest = { showRecordings = false },
            title = { Text(stringResource(R.string.testing_library)) },
            text = {
                if (testing.recordings.isEmpty()) Text(stringResource(R.string.testing_library_empty))
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(testing.recordings, key = { it.id }) { recording ->
                        TextButton(enabled = !busy, onClick = {
                            showRecordings = false
                            TestingTools.openRecording(context, recording.id)
                        }) {
                            Text(stringResource(if (recording.complete) R.string.testing_open_recording
                                else R.string.testing_recover_recording, recording.id))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRecordings = false }) {
                Text(stringResource(R.string.testing_library_close))
            } })
    }
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
            if (testing.filterMode == PointerFilterMode.VC49)
                AergisStatusChip(stringResource(R.string.ui_experimental), AergisColors.Caution)
            AergisButton(modifier = Modifier.fillMaxWidth(), onClick = { TestingTools.setFilterMode(
                PointerFilterMode.entries[(testing.filterMode.ordinal + 1) % PointerFilterMode.entries.size]) }) {
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
            AergisButton(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = {
                TestingTools.refreshRecordings(context)
                showRecordings = true
            }) { Text(stringResource(R.string.testing_library)) }
            if (testing.status == RecordingStatus.ERROR && testing.canRetry) {
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
