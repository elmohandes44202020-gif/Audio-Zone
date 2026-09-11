package com.audio.editor.domain.model

import android.net.Uri

/**
 * Sealed class representing exhaustive state machine for multi-file Batch Audio Processing pipeline.
 */
sealed class AudioProcessingState {
    object Idle : AudioProcessingState()
    
    data class BatchReady(
        val totalFiles: Int,
        val queue: List<BatchAudioItemState>
    ) : AudioProcessingState()
    
    data class Preparing(
        val message: String = "Preparing audio streams..."
    ) : AudioProcessingState()
    
    data class ProcessingBatch(
        val currentFileIndex: Int,
        val totalFiles: Int,
        val currentFileName: String,
        val currentFileProgressPercent: Int,
        val overallProgressPercent: Int,
        val elapsedTimeMs: Long,
        val completedCount: Int,
        val failedCount: Int,
        val queue: List<BatchAudioItemState>
    ) : AudioProcessingState()
    
    data class CompletedBatch(
        val summary: BatchSummary
    ) : AudioProcessingState()
    
    object Cancelled : AudioProcessingState()
    
    data class Error(
        val userFriendlyMessage: String,
        val cause: Throwable? = null
    ) : AudioProcessingState()
}

/**
 * Individual item state inside a multi-file batch queue.
 */
data class BatchAudioItemState(
    val id: String,
    val fileInfo: AudioFileInfo,
    val status: ItemStatus = ItemStatus.PENDING,
    val progressPercent: Int = 0,
    val outputUri: Uri? = null,
    val outputFileName: String? = null,
    val outputSizeBytes: Long = 0L,
    val errorMessage: String? = null,
    val customSettings: AudioEditSettings? = null
)

enum class ItemStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class BatchSummary(
    val totalFiles: Int,
    val successCount: Int,
    val failedCount: Int,
    val cancelledCount: Int,
    val totalInputSizeBytes: Long,
    val totalOutputSizeBytes: Long,
    val elapsedTimeMs: Long,
    val concurrencyUsed: Int = 1,
    val items: List<BatchAudioItemState>
)

/**
 * Safely extracted metadata about selected audio file with per-file original properties.
 */
data class AudioFileInfo(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val originalBitrateKbps: Int = 128,
    val originalSampleRateHz: Int = 44100,
    val originalChannels: Int = 2,
    val originalFormat: String = "mp3",
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null
)
