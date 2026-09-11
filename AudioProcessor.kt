package com.audio.editor.audio.processor

import com.audio.editor.domain.model.AudioEditSettings
import com.audio.editor.domain.model.AudioProcessingState
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for Audio Engine to allow swapping implementation (e.g., FFmpeg, MediaCodec, Sonic)
 * without touching UI or ViewModel.
 */
interface AudioProcessor {
    fun processAudio(
        settings: AudioEditSettings
    ): Flow<AudioProcessingState>
    
    fun cancelProcessing()
}
