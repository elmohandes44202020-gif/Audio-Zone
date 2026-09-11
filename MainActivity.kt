package com.audio.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audio.editor.domain.model.AudioProcessingState
import com.audio.editor.ui.components.AudioSettingsSection
import com.audio.editor.ui.components.BatchQueueList
import com.audio.editor.ui.components.BatchSummaryDialog
import com.audio.editor.ui.components.FilePickerSection
import com.audio.editor.ui.components.PersistentBottomActionBar
import com.audio.editor.ui.theme.AudioEditorTheme
import com.audio.editor.ui.theme.ErrorRose
import com.audio.editor.ui.theme.PrimaryIndigo
import com.audio.editor.viewmodel.AudioEditorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AudioEditorViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AudioEditorTheme {
                val uiState by viewModel.uiState.collectAsState()
                val selectedFiles by viewModel.selectedFiles.collectAsState()
                val settings by viewModel.globalSettings.collectAsState()

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = PrimaryIndigo,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "محرر الصوتيات الأصلي",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        PersistentBottomActionBar(
                            fileCount = selectedFiles.size,
                            processingState = uiState,
                            onStartBatch = { viewModel.startBatchProcessing() },
                            onCancel = { viewModel.cancelProcessing() }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Error Banner
                        if (uiState is AudioProcessingState.Error) {
                            val errorState = uiState as AudioProcessingState.Error
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ErrorRose.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = ErrorRose)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = errorState.userFriendlyMessage,
                                        color = ErrorRose,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // 2. Storage Access Framework (SAF) File Picker
                        FilePickerSection(
                            selectedFiles = selectedFiles,
                            onFilesSelected = { uris -> viewModel.onMultipleFilesSelected(uris) },
                            onClearAll = { viewModel.resetBatchState() }
                        )

                        // 3. Audio Edit Settings (Speed, Bitrate, SampleRate, Channels, Format)
                        AudioSettingsSection(
                            settings = settings,
                            onSpeedChange = { viewModel.setSpeed(it) },
                            onSpeedAdjustStep = { viewModel.adjustSpeedStep(it) },
                            onSpeedReset = { viewModel.resetSpeed() },
                            onBitrateChange = { viewModel.setBitrate(it) },
                            onBitrateReset = { viewModel.resetBitrateToOriginal() },
                            onSampleRateChange = { viewModel.setSampleRate(it) },
                            onSampleRateReset = { viewModel.resetSampleRateToOriginal() },
                            onChannelsChange = { viewModel.setChannels(it) },
                            onFormatChange = { viewModel.setOutputFormat(it) }
                        )

                        // 4. Batch Queue List
                        if (selectedFiles.isNotEmpty()) {
                            val queueItems = when (uiState) {
                                is AudioProcessingState.BatchReady -> (uiState as AudioProcessingState.BatchReady).queue
                                is AudioProcessingState.ProcessingBatch -> (uiState as AudioProcessingState.ProcessingBatch).queue
                                is AudioProcessingState.CompletedBatch -> (uiState as AudioProcessingState.CompletedBatch).summary.items
                                else -> emptyList()
                            }

                            if (queueItems.isNotEmpty()) {
                                BatchQueueList(
                                    queueItems = queueItems,
                                    onRemoveItem = { fileId -> viewModel.removeItemFromBatch(fileId) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 5. Completion Summary Dialog
                    if (uiState is AudioProcessingState.CompletedBatch) {
                        val summary = (uiState as AudioProcessingState.CompletedBatch).summary
                        BatchSummaryDialog(
                            summary = summary,
                            onDismiss = { viewModel.resetBatchState() }
                        )
                    }
                }
            }
        }
    }
}
