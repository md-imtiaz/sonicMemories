package com.shuvostechworld.sonicmemories.utils

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.util.Log

class SoundManager(private val context: Context) {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            Log.e("SoundManager", "Failed to create ToneGenerator", e)
        }
    }

    fun playSound(resourceId: Int, fallbackTone: Int = ToneGenerator.TONE_PROP_BEEP) {
        try {
            // Try to play the resource file
            val mediaPlayer = MediaPlayer.create(context, resourceId)
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener {
                    it.release()
                }
                mediaPlayer.start()
            } else {
                // If creation fails (e.g. resource not found/invalid), use fallback
                playFallback(fallbackTone)
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing sound resource: $resourceId", e)
            playFallback(fallbackTone)
        }
    }

    private fun playFallback(toneType: Int) {
        try {
            toneGenerator?.startTone(toneType, 150) // Play for 150ms
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing fallback tone", e)
        }
    }
    
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }

    companion object {
        // Define tone constants for easy mapping if we want specific beeps for different actions
        const val TONE_START = ToneGenerator.TONE_SUP_PIP
        const val TONE_STOP = ToneGenerator.TONE_SUP_PIP
        const val TONE_PAUSE = ToneGenerator.TONE_PROP_PROMPT
        const val TONE_RESUME = ToneGenerator.TONE_PROP_ACK
    }
}
