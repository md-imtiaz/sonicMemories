package com.shuvostechworld.sonicmemories.utils

import android.util.Log

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.PresetReverb
import android.view.View
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var mediaPlayer: MediaPlayer? = null
    private var reverb: PresetReverb? = null
    private var onCompletionVerifier: (() -> Unit)? = null

    fun playFile(pathOrUrl: String, onCompletion: () -> Unit) {
        stop() // Stop any previous playback
        
        onCompletionVerifier = onCompletion
        
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            try {
                reset() // Crucial for re-use state
                Log.d("AudioPlayer", "Attempting to play: $pathOrUrl")

                val file = java.io.File(pathOrUrl)
                if (file.exists() && file.isFile) {
                   // Using path directly is often more robust than FD management for MediaPlayer
                   setDataSource(file.absolutePath)
                } else {
                   setDataSource(pathOrUrl) 
                }
                
                prepareAsync()
                setOnPreparedListener { 
                    Log.d("AudioPlayer", "MediaPlayer prepared, starting")
                    start()
                }
                setOnCompletionListener { 
                    Log.d("AudioPlayer", "Playback completed")
                    onCompletionVerifier?.invoke()
                }
                setOnErrorListener { mp, what, extra ->
                     val errorMsg = "Err: $what, $extra"
                     Log.e("AudioPlayer", errorMsg)
                     android.os.Handler(android.os.Looper.getMainLooper()).post {
                         android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                     }
                     onCompletionVerifier?.invoke()
                     true
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Exception playing file", e)
                e.printStackTrace()
                onCompletionVerifier?.invoke() // Reset UI on error
            }
        }
    }

    fun applyReverb(preset: Short) {
        mediaPlayer?.let { mp ->
            try {
                 if (reverb == null) {
                     reverb = PresetReverb(0, mp.audioSessionId)
                     reverb?.enabled = true
                 }
                 reverb?.preset = preset
                 mp.attachAuxEffect(reverb!!.id)
                 mp.setAuxEffectSendLevel(1.0f)
                 Log.d("AudioPlayer", "Applied Reverb Preset: $preset")
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error applying reverb", e)
            }
        }
    }

    fun stop() {
        releaseReverb()
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }
    
    private fun releaseReverb() {
        try {
            reverb?.enabled = false
            reverb?.release()
            reverb = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        mediaPlayer?.apply {
            if (isPlaying) pause()
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
}
