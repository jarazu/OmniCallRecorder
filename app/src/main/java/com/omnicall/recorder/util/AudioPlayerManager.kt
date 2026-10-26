package com.omnicall.recorder.util

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

class AudioPlayerManager {

    companion object {
        private const val TAG = "AudioPlayerManager"
    }

    private var mediaPlayer: MediaPlayer? = null
    var currentPlayingPath: String? = null
        private set

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }

    val currentPosition: Int
        get() = try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }

    val duration: Int
        get() = try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            0
        }

    fun play(filePath: String, onCompletion: () -> Unit): Boolean {
        stop()

        return try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    currentPlayingPath = null
                    onCompletion()
                }
                start()
            }
            currentPlayingPath = filePath
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file: $filePath", e)
            release()
            false
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing player", e)
        }
    }

    fun resume() {
        try {
            if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
                mediaPlayer?.start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resuming player", e)
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.w(TAG, "Error seeking player", e)
        }
    }

    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping player", e)
        } finally {
            currentPlayingPath = null
        }
    }

    fun release() {
        try {
            stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing player", e)
        } finally {
            mediaPlayer = null
            currentPlayingPath = null
        }
    }
}
