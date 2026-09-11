package com.audio.editor.audio.repository

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.audio.editor.domain.model.AudioFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository for managing audio file metadata extraction safely using ContentResolver and MediaMetadataRetriever.
 *
 * Avoids loading whole files into memory or using raw file paths.
 */
class AudioRepository(private val context: Context) {

    suspend fun getAudioFileInfo(uri: Uri): Result<AudioFileInfo> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var fileName = "audio_track"
            var fileSize = 0L
            val mimeType = contentResolver.getType(uri) ?: "audio/*"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: "audio"
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            var durationMs = 0L
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var originalBitrateKbps = 128
            var originalSampleRateHz = 44100
            var originalChannels = 2

            // Extract tags and properties using MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durationStr?.toLongOrNull() ?: 0L
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                
                val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                bitrateStr?.toIntOrNull()?.let {
                    if (it > 0) originalBitrateKbps = (it / 1000).coerceIn(32, 320)
                }
            } catch (e: Exception) {
                // Non-fatal if metadata tags are absent
            } finally {
                try {
                    retriever.release()
                } catch (ignored: Exception) {}
            }

            // Extract precise sample rate and channels using MediaExtractor
            try {
                val extractor = MediaExtractor()
                extractor.setDataSource(context, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("audio/") == true) {
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            originalSampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            originalChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        break
                    }
                }
                extractor.release()
            } catch (e: Exception) {
                // Non-fatal
            }

            val formatExt = fileName.substringAfterLast(".", "mp3").lowercase()

            Result.success(
                AudioFileInfo(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = fileSize,
                    durationMs = durationMs,
                    originalBitrateKbps = originalBitrateKbps,
                    originalSampleRateHz = originalSampleRateHz,
                    originalChannels = originalChannels,
                    originalFormat = formatExt,
                    title = title ?: fileName.substringBeforeLast("."),
                    artist = artist ?: "Unknown Artist",
                    album = album ?: "Audio Collection"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMultipleAudioFileInfo(uris: List<Uri>): List<AudioFileInfo> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            getAudioFileInfo(uri).getOrNull()
        }
    }
}
