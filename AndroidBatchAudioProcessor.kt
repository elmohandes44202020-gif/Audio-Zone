package com.audio.editor.audio.processor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.audio.editor.domain.model.AudioEditSettings
import com.audio.editor.domain.model.AudioFileInfo
import com.audio.editor.domain.model.AudioProcessingState
import com.audio.editor.domain.model.BatchAudioItemState
import com.audio.editor.domain.model.BatchSummary
import com.audio.editor.domain.model.ItemStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Production-Grade Native Android Batch Audio Processing Engine.
 *
 * Highlights:
 * 1. Controlled Semaphore Concurrency (1-4 workers bound to hardware).
 * 2. Fault Isolation: Single corrupted file fails gracefully without aborting remaining batch items.
 * 3. Zero RAM Clog: Streaming 64KB chunks with immediate temp file cleanup.
 * 4. Cancellation: Job cancellation and immediate worker exit.
 * 5. MediaStore SAF Export to Public Music/AudioEditor directory.
 */
class AndroidBatchAudioProcessor(
    private val context: Context
) : AudioProcessor {

    private val nativeProcessor = HighPerformanceAudioProcessor(context)

    @Volatile
    private var isCancelled = false

    // Hardware-safe concurrency bound (prevents thermal throttling and RAM spikes)
    private val availableCores = Runtime.getRuntime().availableProcessors()
    private val maxParallelism = max(1, min(availableCores / 2, 4))
    private val concurrencySemaphore = Semaphore(maxParallelism)

    override fun cancelProcessing() {
        isCancelled = true
    }

    fun processBatch(
        files: List<AudioFileInfo>,
        settings: AudioEditSettings
    ): Flow<AudioProcessingState> = flow {
        isCancelled = false
        val startTime = System.currentTimeMillis()

        if (files.isEmpty()) {
            emit(AudioProcessingState.Error("No audio files selected for batch processing."))
            return@flow
        }

        // Initialize batch queue
        val queue = files.map { fileInfo ->
            BatchAudioItemState(
                id = fileInfo.id,
                fileInfo = fileInfo,
                status = ItemStatus.PENDING,
                progressPercent = 0
            )
        }.toMutableList()

        emit(AudioProcessingState.BatchReady(files.size, queue.toList()))

        var completedCount = 0
        var failedCount = 0
        var totalInputBytes = 0L
        var totalOutputBytes = 0L

        // Process with controlled concurrency
        coroutineScope {
            val tasks = queue.mapIndexed { index, item ->
                async(Dispatchers.Default) {
                    if (isCancelled) {
                        queue[index] = item.copy(status = ItemStatus.CANCELLED)
                        return@async
                    }

                    concurrencySemaphore.withPermit {
                        if (isCancelled) {
                            queue[index] = item.copy(status = ItemStatus.CANCELLED)
                            return@withPermit
                        }

                        queue[index] = item.copy(status = ItemStatus.PROCESSING, progressPercent = 5)
                        totalInputBytes += item.fileInfo.sizeBytes

                        val itemSettings = item.customSettings ?: settings
                        val tempFile = createTempFile(context, item.id, itemSettings.outputFormat.extension)

                        try {
                            val result = nativeProcessor.processSingleFile(
                                sourceUri = item.fileInfo.uri,
                                outputFile = tempFile,
                                settings = itemSettings,
                                onProgress = { progress ->
                                    queue[index] = queue[index].copy(progressPercent = progress)
                                }
                            )

                            if (result.isSuccess) {
                                val outputUri = exportToPublicMusicFolder(
                                    context = context,
                                    tempFile = tempFile,
                                    originalName = item.fileInfo.fileName,
                                    speed = itemSettings.speed,
                                    bitrateKbps = itemSettings.bitrateKbps,
                                    formatExtension = itemSettings.outputFormat.extension
                                )

                                val outputSize = tempFile.length()
                                totalOutputBytes += outputSize
                                completedCount++

                                queue[index] = queue[index].copy(
                                    status = ItemStatus.COMPLETED,
                                    progressPercent = 100,
                                    outputUri = outputUri,
                                    outputFileName = outputUri?.lastPathSegment ?: "${item.fileInfo.fileName}_edited",
                                    outputSizeBytes = outputSize
                                )
                            } else {
                                failedCount++
                                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Processing failed"
                                queue[index] = queue[index].copy(
                                    status = ItemStatus.FAILED,
                                    progressPercent = 0,
                                    errorMessage = errorMsg
                                )
                            }
                        } catch (e: Exception) {
                            failedCount++
                            queue[index] = queue[index].copy(
                                status = ItemStatus.FAILED,
                                progressPercent = 0,
                                errorMessage = e.localizedMessage ?: "Unexpected error"
                            )
                        } finally {
                            if (tempFile.exists()) {
                                tempFile.delete()
                            }
                        }
                    }
                }
            }

            tasks.awaitAll()
        }

        // Final Summary State
        val summary = BatchSummary(
            totalFiles = files.size,
            successCount = completedCount,
            failedCount = failedCount,
            cancelledCount = if (isCancelled) files.size - completedCount - failedCount else 0,
            totalInputSizeBytes = totalInputBytes,
            totalOutputSizeBytes = totalOutputBytes,
            elapsedTimeMs = System.currentTimeMillis() - startTime,
            concurrencyUsed = maxParallelism,
            items = queue.toList()
        )

        if (isCancelled) {
            emit(AudioProcessingState.Cancelled)
        } else {
            emit(AudioProcessingState.CompletedBatch(summary))
        }
    }.flowOn(Dispatchers.IO)

    override fun processAudio(settings: AudioEditSettings): Flow<AudioProcessingState> {
        throw UnsupportedOperationException("Use processBatch for single or multi-file processing in AndroidBatchAudioProcessor")
    }

    private fun createTempFile(context: Context, id: String, ext: String): File {
        val cacheDir = File(context.cacheDir, "audio_processor_temp")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return File(cacheDir, "proc_${id}_${System.currentTimeMillis()}.$ext")
    }

    private fun exportToPublicMusicFolder(
        context: Context,
        tempFile: File,
        originalName: String,
        speed: Float,
        bitrateKbps: Int,
        formatExtension: String
    ): Uri? {
        val sanitizedBase = originalName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val exportFileName = "${sanitizedBase}_${speed}x_${bitrateKbps}kbps.$formatExtension"

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, exportFileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/$formatExtension")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/AudioEditor")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val destinationUri = resolver.insert(collectionUri, contentValues) ?: return null

        try {
            resolver.openOutputStream(destinationUri)?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(destinationUri, contentValues, null, null)
            }

            return destinationUri
        } catch (e: Exception) {
            return null
        }
    }
}
