package com.shuvostechworld.sonicmemories.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorder @Inject constructor() {

    private var recorder: MediaRecorder? = null

    fun startRecording(context: Context, outputFile: File) {
        stopRecording() // Release any existing recorder
        createRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            // Use defaults for best compatibility
            setOutputFile(outputFile.absolutePath)
            
            prepare()
            start()
            
            recorder = this
        }
    }

    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recorder?.pause()
        }
    }

    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recorder?.resume()
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun stopRecording() {
        recorder?.apply {
            try {
                stop()
            } catch (e: RuntimeException) {
                // RuntimeException is thrown if stop() is called immediately after start()
                // or if there was an error during recording. We can safely ignore this
                // as we are resetting anyway.
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    reset()
                    release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        recorder = null
    }
    
    // Helper to create recorder based on API level
    private fun createRecorder(context: Context): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
}
