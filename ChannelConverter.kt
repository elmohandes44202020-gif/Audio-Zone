package com.audio.editor.audio.processor

/**
 * Direct Channel Converter (Mono <-> Stereo) for 16-bit PCM streaming.
 *
 * Fast-path bypass when inChannels == outChannels.
 */
class ChannelConverter {

    fun convertChannels(
        inSamples: ShortArray,
        inChannels: Int,
        outChannels: Int
    ): ShortArray {
        // Fast-path bypass
        if (inChannels == outChannels || inSamples.isEmpty()) {
            return inSamples
        }

        val frames = inSamples.size / inChannels

        return when {
            // Stereo (2) -> Mono (1): Average left & right channels
            inChannels == 2 && outChannels == 1 -> {
                val outSamples = ShortArray(frames)
                for (i in 0 until frames) {
                    val left = inSamples[i * 2].toInt()
                    val right = inSamples[i * 2 + 1].toInt()
                    outSamples[i] = ((left + right) / 2).toShort()
                }
                outSamples
            }

            // Mono (1) -> Stereo (2): Duplicate single channel into L and R
            inChannels == 1 && outChannels == 2 -> {
                val outSamples = ShortArray(frames * 2)
                for (i in 0 until frames) {
                    val mono = inSamples[i]
                    outSamples[i * 2] = mono
                    outSamples[i * 2 + 1] = mono
                }
                outSamples
            }

            else -> inSamples
        }
    }
}
