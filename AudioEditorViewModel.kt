package com.audio.editor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audio.editor.audio.processor.AndroidBatchAudioProcessor
import com.audio.editor.audio.repository.AudioRepository
import com.audio.editor.domain.model.AudioEditSettings
import com.audio.editor.domain.model.AudioFileInfo
import com.audio.editor.domain.model.AudioOutputFormat
import com.audio.editor.domain.model.AudioProcessingState
import com.audio.editor.domain.model.BatchAudioItemState
import com.audio.editor.domain.model.ItemStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioEditorViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = AudioRepository(application.applicationContext)
    private val batchProcessor = AndroidBatchAudioProcessor(application.applicationContext)

    private val _uiState = MutableStateFlow<AudioProcessingState>(AudioProcessingState.Idle)
    val uiState: StateFlow<AudioProcessingState> = _uiState.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<AudioFileInfo>>(emptyList())
    val selectedFiles: StateFlow<List<AudioFileInfo>> = _selectedFiles.asStateFlow()

    private val _globalSettings = MutableStateFlow(AudioEditSettings())
    val globalSettings: StateFlow<AudioEditSettings> = _globalSettings.asStateFlow()

    private val _activeFileIndex = MutableStateFlow(0)
    val activeFileIndex: StateFlow<Int> = _activeFileIndex.asStateFlow()

    fun onMultipleFilesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) {
            _uiState.value = AudioProcessingState.Error("لم يتم اختيار أي ملفات صوتية.")
            return
        }

        viewModelScope.launch {
            _uiState.value = AudioProcessingState.Preparing("جاري قراءة خصائص ${uris.size} ملفات صوتية...")

            val fileInfos = repository.getMultipleAudioFileInfo(uris)
            if (fileInfos.isEmpty()) {
                _uiState.value = AudioProcessingState.Error("تعذر قراءة بيانات الملفات الصوتية المحددة.")
                return@launch
            }

            _selectedFiles.value = fileInfos
            _activeFileIndex.value = 0

            // Set initial defaults from the first selected file
            val firstFile = fileInfos.first()
            _globalSettings.value = _globalSettings.value.copy(
                bitrateKbps = firstFile.originalBitrateKbps,
                sampleRateHz = firstFile.originalSampleRateHz,
                channels = firstFile.originalChannels
            )

            val queue = fileInfos.map {
                BatchAudioItemState(
                    id = it.id,
                    fileInfo = it,
                    status = ItemStatus.PENDING
                )
            }

            _uiState.value = AudioProcessingState.BatchReady(
                totalFiles = fileInfos.size,
                queue = queue
            )
        }
    }

    fun startBatchProcessing() {
        val files = _selectedFiles.value
        if (files.isEmpty()) return

        viewModelScope.launch {
            batchProcessor.processBatch(files, _globalSettings.value).collect { state ->
                _uiState.value = state
            }
        }
    }

    fun cancelProcessing() {
        batchProcessor.cancelProcessing()
        _uiState.value = AudioProcessingState.Cancelled
    }

    fun adjustSpeedStep(delta: Float) {
        val current = _globalSettings.value.speed
        val newSpeed = Math.round((current + delta) * 100f) / 100f
        setSpeed(newSpeed)
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(AudioEditSettings.MIN_SPEED, AudioEditSettings.MAX_SPEED)
        _globalSettings.value = _globalSettings.value.copy(speed = clamped)
    }

    fun setBitrate(bitrateKbps: Int) {
        _globalSettings.value = _globalSettings.value.copy(bitrateKbps = bitrateKbps)
    }

    fun setSampleRate(sampleRateHz: Int) {
        _globalSettings.value = _globalSettings.value.copy(sampleRateHz = sampleRateHz)
    }

    fun setChannels(channels: Int) {
        _globalSettings.value = _globalSettings.value.copy(channels = channels)
    }

    fun setOutputFormat(format: AudioOutputFormat) {
        _globalSettings.value = _globalSettings.value.copy(outputFormat = format)
    }

    fun resetSpeed() {
        _globalSettings.value = _globalSettings.value.copy(speed = 1.00f)
    }

    fun resetBitrateToOriginal(fileId: String? = null) {
        val targetFile = if (fileId != null) {
            _selectedFiles.value.find { it.id == fileId }
        } else {
            _selectedFiles.value.getOrNull(_activeFileIndex.value) ?: _selectedFiles.value.firstOrNull()
        }

        if (targetFile != null) {
            _globalSettings.value = _globalSettings.value.copy(bitrateKbps = targetFile.originalBitrateKbps)
        } else {
            _globalSettings.value = _globalSettings.value.copy(bitrateKbps = 128)
        }
    }

    fun resetSampleRateToOriginal(fileId: String? = null) {
        val targetFile = if (fileId != null) {
            _selectedFiles.value.find { it.id == fileId }
        } else {
            _selectedFiles.value.getOrNull(_activeFileIndex.value) ?: _selectedFiles.value.firstOrNull()
        }

        if (targetFile != null) {
            _globalSettings.value = _globalSettings.value.copy(sampleRateHz = targetFile.originalSampleRateHz)
        } else {
            _globalSettings.value = _globalSettings.value.copy(sampleRateHz = 44100)
        }
    }

    fun removeItemFromBatch(fileId: String) {
        val updated = _selectedFiles.value.filterNot { it.id == fileId }
        _selectedFiles.value = updated

        if (updated.isEmpty()) {
            _uiState.value = AudioProcessingState.Idle
        } else {
            val queue = updated.map {
                BatchAudioItemState(
                    id = it.id,
                    fileInfo = it,
                    status = ItemStatus.PENDING
                )
            }
            _uiState.value = AudioProcessingState.BatchReady(
                totalFiles = updated.size,
                queue = queue
            )
        }
    }

    fun resetBatchState() {
        _uiState.value = AudioProcessingState.Idle
        _selectedFiles.value = emptyList()
        _globalSettings.value = AudioEditSettings()
    }
}
