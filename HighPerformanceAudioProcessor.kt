package com.audio.editor.audio.processor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.audio.editor.domain.model.AudioEditSettings
import com.audio.editor.domain.model.AudioOutputFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * High-Performance Native Audio Engine.
 *
 * Pipeline:
 * Input URI -> MediaExtractor -> MediaCodec Decoder -> PCM 16-bit ->
 * Sonic WSOLA (Speed) -> AudioResampler (SampleRate) -> ChannelConverter (Channels) ->
 * MediaCodec Encoder (AAC/MP3) / PCM Writer (WAV) -> MediaMuxer -> Output File.
 */
class HighPerformanceAudioProcessor(private val context: Context) {

    private val resampler = AudioResampler()
    private val channelConverter = ChannelConverter()

    suspend fun processSingleFile(
        sourceUri: Uri,
        outputFile: File,
        settings: AudioEditSettings,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null

        try {
            coroutineContext.ensureActive()
            onProgress(5)

            extractor = MediaExtractor()
            extractor.setDataSource(context, sourceUri, null)

            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) {
                return@withContext Result.failure(IllegalStateException("No valid audio track found in file."))
            }

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)

            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
            val inputSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val inputChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L

            val targetSampleRate = settings.sampleRateHz
            val targetChannels = settings.channels
            val targetSpeed = settings.speed

            // Initialize Decoder
            decoder = MediaCodec.createDecoderByType(mimeType)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            // Initialize Sonic DSP for Speed
            val sonic = SonicAudioProcessor(inputSampleRate, inputChannels)
            sonic.setSpeed(targetSpeed)

            // Direct PCM file accumulator
            val rawPcmFile = File(context.cacheDir, "temp_raw_${System.currentTimeMillis()}.pcm")
            val pcmOut = FileOutputStream(rawPcmFile)

            val bufferInfo = MediaCodec.BufferInfo()
            var isExtractorEOS = false
            var isDecoderEOS = false
            val timeoutUs = 5000L

            onProgress(15)

            // Decoding loop
            while (!isDecoderEOS) {
                coroutineContext.ensureActive()

                // Feed input to decoder
                if (!isExtractorEOS) {
                    val inIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isExtractorEOS = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Retrieve decoded PCM output
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderEOS = true
                    }

                    val outputBuffer = decoder.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val shortCount = bufferInfo.size / 2
                        val shortArray = ShortArray(shortCount)
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArray)

                        // 1. Sonic WSOLA Speed Processing
                        if (targetSpeed != 1.0f) {
                            sonic.writeShorts(shortArray, 0, shortCount / inputChannels)
                            val outShorts = ShortArray(shortCount * 4)
                            var readCount: Int
                            while (sonic.readShorts(outShorts, 0, outShorts.size / inputChannels).also { readCount = it } > 0) {
                                // 2. Resampling & Channel Conversion
                                val resampled = resampler.resample(outShorts.copyOf(readCount * inputChannels), inputSampleRate, targetSampleRate, inputChannels)
                                val converted = channelConverter.convertChannels(resampled, inputChannels, targetChannels)
                                writeShortsToStream(pcmOut, converted)
                            }
                        } else {
                            // Fast Path: Direct Resampling & Channel Conversion
                            val resampled = resampler.resample(shortArray, inputSampleRate, targetSampleRate, inputChannels)
                            val converted = channelConverter.convertChannels(resampled, inputChannels, targetChannels)
                            writeShortsToStream(pcmOut, converted)
                        }

                        if (durationUs > 0) {
                            val currentProg = 15 + ((bufferInfo.presentationTimeUs.toFloat() / durationUs) * 55).toInt().coerceIn(0, 55)
                            onProgress(currentProg)
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                }
            }

            // Flush Sonic stream if active
            if (targetSpeed != 1.0f) {
                sonic.flush()
                val outShorts = ShortArray(4096 * inputChannels)
                var readCount: Int
                while (sonic.readShorts(outShorts, 0, outShorts.size / inputChannels).also { readCount = it } > 0) {
                    val resampled = resampler.resample(outShorts.copyOf(readCount * inputChannels), inputSampleRate, targetSampleRate, inputChannels)
                    val converted = channelConverter.convertChannels(resampled, inputChannels, targetChannels)
                    writeShortsToStream(pcmOut, converted)
                }
            }

            pcmOut.flush()
            pcmOut.close()

            onProgress(75)

            // Step 2: Encode to target format (WAV or AAC/M4A/MP3)
            when (settings.outputFormat) {
                AudioOutputFormat.WAV -> {
                    encodePcmToWav(rawPcmFile, outputFile, targetSampleRate, targetChannels)
                }
                AudioOutputFormat.AAC, AudioOutputFormat.M4A, AudioOutputFormat.MP3 -> {
                    encodePcmToAacMuxer(rawPcmFile, outputFile, targetSampleRate, targetChannels, settings.bitrateKbps, settings.outputFormat)
                }
                else -> {
                    encodePcmToWav(rawPcmFile, outputFile, targetSampleRate, targetChannels)
                }
            }

            rawPcmFile.delete()
            onProgress(100)

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
                extractor?.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }

    private fun writeShortsToStream(stream: FileOutputStream, shorts: ShortArray) {
        val byteBuffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in shorts) {
            byteBuffer.putShort(s)
        }
        stream.write(byteBuffer.array())
    }

    /**
     * Encode raw PCM to canonical standard WAV format with 44-byte RIFF header.
     */
    private fun encodePcmToWav(pcmFile: File, wavFile: File, sampleRate: Int, channels: Int) {
        val pcmSize = pcmFile.length()
        val totalDataLen = pcmSize + 36
        val byteRate = sampleRate * channels * 2

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalDataLen.toInt())
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1) // AudioFormat 1 = PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((channels * 2).toShort()) // BlockAlign
        buffer.putShort(16) // BitsPerSample
        buffer.put("data".toByteArray())
        buffer.putInt(pcmSize.toInt())

        FileOutputStream(wavFile).use { out ->
            out.write(header)
            pcmFile.inputStream().use { inp ->
                inp.copyTo(out)
            }
        }
    }

    /**
     * Encode raw PCM to AAC / MP4 Audio container using MediaCodec Encoder + MediaMuxer.
     */
    private fun encodePcmToAacMuxer(
        pcmFile: File,
        outFile: File,
        sampleRate: Int,
        channels: Int,
        bitrateKbps: Int,
        format: AudioOutputFormat
    ) {
        val mimeType = "audio/mp4a-latm"
        val targetBitrate = bitrateKbps * 1000

        val audioFormat = MediaFormat.createAudioFormat(mimeType, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }

        val encoder = MediaCodec.createEncoderByType(mimeType)
        encoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val pcmInputStream = pcmFile.inputStream()
        val buffer = ByteArray(16384)
        var isPcmEOS = false
        var isEncoderEOS = false
        var presentationTimeUs = 0L

        try {
            while (!isEncoderEOS) {
                if (!isPcmEOS) {
                    val inIndex = encoder.dequeueInputBuffer(5000L)
                    if (inIndex >= 0) {
                        val inBuffer = encoder.getInputBuffer(inIndex)
                        if (inBuffer != null) {
                            val bytesRead = pcmInputStream.read(buffer)
                            if (bytesRead < 0) {
                                encoder.queueInputBuffer(inIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isPcmEOS = true
                            } else {
                                inBuffer.clear()
                                inBuffer.put(buffer, 0, bytesRead)
                                encoder.queueInputBuffer(inIndex, 0, bytesRead, presentationTimeUs, 0)
                                val samples = bytesRead / (channels * 2)
                                presentationTimeUs += (samples * 1_000_000L) / sampleRate
                            }
                        }
                    }
                }

                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 5000L)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) throw IllegalStateException("Format changed twice in muxer")
                        audioTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val encodedBuffer = encoder.getOutputBuffer(outIndex)
                        if (encodedBuffer != null && bufferInfo.size > 0 && muxerStarted) {
                            encodedBuffer.position(bufferInfo.offset)
                            encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(audioTrackIndex, encodedBuffer, bufferInfo)
                        }
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isEncoderEOS = true
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                    }
                }
            }
        } finally {
            pcmInputStream.close()
            try {
                encoder.stop()
                encoder.release()
            } catch (ignored: Exception) {}
            try {
                if (muxerStarted) {
                    muxer.stop()
                }
                muxer.release()
            } catch (ignored: Exception) {}
        }
    }
}
