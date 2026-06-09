package com.gobang.audio

import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSettings
import platform.Foundation.NSNumber

actual class SoundPlayer actual constructor() {

    private val engine = AVAudioEngine()
    private val playerNode = AVAudioPlayerNode()

    init {
        engine.attachNode(playerNode)
        val format = AVAudioFormat(44100.0, 1)
        engine.connect(playerNode, format, 0)
        try {
            engine.startAndReturnError(null)
        } catch (_: Exception) {
        }
    }

    private fun playTone(frequency: Double, durationMs: Int, volume: Float = 0.3f) {
        try {
            val sampleRate = 44100.0
            val frameCount = (sampleRate * durationMs / 1000.0).toLong()
            val format = AVAudioFormat(sampleRate, 1)
            val buffer = AVAudioPCMBuffer(format, frameCount.toUInteger())
            val floatChannelData = buffer.floatChannelData
            if (floatChannelData != null) {
                val channelData = floatChannelData[0]
                for (i in 0 until frameCount.toInt()) {
                    val t = i / sampleRate
                    val sample = volume * sin(2.0 * Math.PI * frequency * t)
                    channelData[i] = sample.toFloat()
                }
            }
            buffer.frameLength = frameCount.toUInteger()
            playerNode.scheduleBuffer(buffer, null, true, null)
            playerNode.play()
        } catch (_: Exception) {
        }
    }

    actual fun playClick() {
        playTone(800.0, 50)
    }

    actual fun playWin() {
        playTone(523.0, 150)
        playTone(659.0, 150)
        playTone(784.0, 300)
    }

    actual fun playLose() {
        playTone(392.0, 200)
        playTone(330.0, 200)
        playTone(262.0, 400)
    }

    actual fun release() {
        engine.stop()
    }
}