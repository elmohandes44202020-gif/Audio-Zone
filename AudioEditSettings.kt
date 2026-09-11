package com.audio.editor.domain.model

import android.net.Uri

/**
 * Non-destructive audio editing configuration.
 * Supports individual per-file overrides and global batch settings.
 */
data class AudioEditSettings(
    val inputUri: Uri? = null,
    val speed: Float = 1.0f,
    val bitrateKbps: Int = 128,
    val sampleRateHz: Int = 44100,
    val channels: Int = 2, // 1 = Mono, 2 = Stereo
    val outputFormat: AudioOutputFormat = AudioOutputFormat.MP3,
    val outputFileName: String = "edited_audio"
) {
    companion object {
        val SUPPORTED_BITRATES = listOf(56, 70, 96, 128, 256, 320)
        val SUPPORTED_SAMPLE_RATES = listOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 88200, 96000)
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 3.00f
        const val SPEED_STEP = 0.01f
    }
}

enum class AudioOutputFormat(val extension: String, val mimeType: String, val isLossless: Boolean = false) {
    MP3("mp3", "audio/mpeg"),
    WAV("wav", "audio/wav", isLossless = true),
    AAC("aac", "audio/aac"),
    M4A("m4a", "audio/mp4"),
    OGG("ogg", "audio/ogg"),
    FLAC("flac", "audio/flac", isLossless = true)
}
