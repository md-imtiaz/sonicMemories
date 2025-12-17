package com.shuvostechworld.sonicmemories.utils

import android.media.MediaPlayer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AmbientSoundManager @Inject constructor() {

    private var mediaPlayer: MediaPlayer? = null

    fun playLooping(url: String, onStart: (() -> Unit)? = null) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                reset()
                setDataSource(url)
                isLooping = true
                setVolume(1.0f, 1.0f) // Increased volume for audibility
                setOnPreparedListener {
                    start()
                    android.util.Log.d("AmbientSoundManager", "Started playing from 0")
                    onStart?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e("AmbientSoundManager", "Error playing ambient: $what, $extra")
                    onStart?.invoke() // Fallback: allow flow to continue even if ambient fails
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onStart?.invoke()
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
