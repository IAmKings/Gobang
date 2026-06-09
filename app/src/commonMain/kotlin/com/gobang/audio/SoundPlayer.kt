package com.gobang.audio

expect class SoundPlayer() {
    fun playClick()
    fun playWin()
    fun playLose()
    fun release()
}