package com.audio.editor.audio.processor

import kotlin.math.floor
import kotlin.math.min

/**
 * High-Speed Linear Interpolation Audio Resampler for 16-bit PCM.
 *
 * Includes automatic fast-path bypass when inRate == outRate.
 */
class AudioResampler {

    /**
     * Resample 16-bit interleaved PCM samples.
     *
     * @param inSamples Source 16-bit PCM samples
     * @param inRate Source sample rate (e.g. 44100)
     * @param outRate Target sample rate (e.g. 48000)
     * @param channels Channel count (1 for Mono, 2 for Stereo)
     * @return Resampled 16-bit PCM ShortArray
     */
    fun resample(
        inSamples: ShortArray,
        inRate: Int,
        outRate: Int,
        channels: Int
    ): ShortArray {
        // Fast-path bypass: No resampling required when sample rates match
        if (inRate == outRate || inSamples.isEmpty()) {
            return inSamples
        }

        val inFrames = inSamples.size / channels
        val ratio = inRate.toDouble() / outRate.toDouble()
        val outFrames = (inFrames / ratio).toInt()
        val outSamples = ShortArray(outFrames * channels)

        for (outFrame in 0 until outFrames) {
            val inPosExact = outFrame * ratio
            val inFrameIndex = floor(inPosExact).toInt()
            val fraction = (inPosExact - inFrameIndex).toFloat()

            val nextFrameIndex = min(inFrameIndex + 1, inFrames - 1)

            for (ch in 0 until channels) {
                val s1 = inSamples[inFrameIndex * channels + ch].toFloat()
                val s2 = inSamples[nextFrameIndex * channels + ch].toFloat()
                val interpolated = (s1 + (s2 - s1) * fraction).toInt().coerceIn(-32768, 32767)
                outSamples[outFrame * channels + ch] = interpolated.toShort()
            }
        }

        return outSamples
    }
}
