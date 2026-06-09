package com.gobang.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class SoundPlayer actual constructor() {

    private fun playTone(frequency: Int, durationMs: Int, volume: Float) {
        try {
            val sampleRate = 44100f
            val format = AudioFormat(sampleRate, 8, 1, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val line = AudioSystem.getLine(info) as SourceDataLine
            line.open(format)
            line.start()
            val bufferSize = (sampleRate * durationMs / 1000).toInt()
            val buffer = ByteArray(bufferSize)
            for (i in 0 until bufferSize) {
                val t = i / sampleRate
                val sample = (volume * Math.sin(2.0 * Math.PI * frequency * t) * 127).toInt()
                buffer[i] = sample.toByte()
            }
            line.write(buffer, 0, bufferSize)
            line.drain()
            line.close()
        } catch (_: Exception) {
        }
    }

    actual fun playClick() {
        Thread { playTone(800, 50, 0.3f) }.start()
    }

    actual fun playWin() {
        Thread {
            playTone(523, 150, 0.4f)
            playTone(659, 150, 0.4f)
            playTone(784, 300, 0.4f)
        }.start()
    }

    actual fun playLose() {
        Thread {
            playTone(392, 200, 0.3f)
            playTone(330, 200, 0.3f)
            playTone(262, 400, 0.3f)
        }.start()
    }

    actual fun release() {}
}