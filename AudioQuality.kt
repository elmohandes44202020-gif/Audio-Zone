package com.audio.editor.domain.model

/**
 * Audio Quality settings model representing Bitrate, Sample Rate, Channels & Encoding.
 */
data class AudioQuality(
    val name: String,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channels: Int,
    val description: String
) {
    companion object {
        val LOW = AudioQuality("Low Quality", 96, 22050, 1, "Compact size (~96 kbps, Mono)")
        val STANDARD = AudioQuality("Standard Quality", 192, 44100, 2, "Balanced size & fidelity (~192 kbps, Stereo)")
        val HIGH = AudioQuality("High Quality", 320, 48000, 2, "Maximum audio clarity (~320 kbps, Studio Quality)")
        
        fun getDefaultPresets(): List<AudioQuality> = listOf(LOW, STANDARD, HIGH)
    }
}
