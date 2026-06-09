package com.gobang.audio

import android.media.AudioManager
import android.media.ToneGenerator

actual class SoundPlayer actual constructor() {

    private var toneGenerator: ToneGenerator? = null

    private fun getToneGenerator(): ToneGenerator? {
        if (toneGenerator == null) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 50)
            } catch (_: Exception) {
            }
        }
        return toneGenerator
    }

    actual fun playClick() {
        getToneGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
    }

    actual fun playWin() {
        getToneGenerator()?.startTone(ToneGenerator.TONE_CDMA_HIGH_SS, 150)
    }

    actual fun playLose() {
        getToneGenerator()?.startTone(ToneGenerator.TONE_CDMA_LOW_SS, 200)
    }

    actual fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}