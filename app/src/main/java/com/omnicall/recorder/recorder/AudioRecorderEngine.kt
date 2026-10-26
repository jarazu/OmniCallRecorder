package com.omnicall.recorder.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

class AudioRecorderEngine {

    companion object {
        private const val TAG = "AudioRecorderEngine"
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val SOFTWARE_GAIN = 1.8f // Amplifies low-volume downlink voice
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var pcmTempFile: File? = null

    @Volatile
    var currentMaxAmplitude: Int = 0
        private set

    @SuppressLint("MissingPermission")
    fun start(outputWavFile: File): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size")
            return false
        }

        val bufferSize = max(minBufferSize, 2048)

        // Attempt VOICE_COMMUNICATION first, fallback to MIC
        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create AudioRecord with VOICE_COMMUNICATION, falling back to MIC", e)
            try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to create AudioRecord with MIC", e2)
                null
            }
        }

        if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            audioRecord?.release()
            audioRecord = null
            return false
        }

        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord recording", e)
            audioRecord?.release()
            audioRecord = null
            return false
        }

        isRecording = true
        pcmTempFile = File(outputWavFile.parentFile, "temp_${System.currentTimeMillis()}.pcm")

        recordingThread = thread(name = "OmniCallAudioRecorderThread") {
            writeAudioDataToPcm(bufferSize, pcmTempFile!!)
            if (pcmTempFile?.exists() == true) {
                convertPcmToWav(pcmTempFile!!, outputWavFile)
                pcmTempFile?.delete()
            }
        }

        return true
    }

    private fun writeAudioDataToPcm(bufferSize: Int, pcmFile: File) {
        val audioData = ShortArray(bufferSize / 2)
        val byteBuffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)
        var outputStream: FileOutputStream? = null

        try {
            outputStream = FileOutputStream(pcmFile)
            while (isRecording && audioRecord != null) {
                val shortsRead = audioRecord?.read(audioData, 0, audioData.size) ?: 0
                if (shortsRead > 0) {
                    byteBuffer.clear()
                    var maxAmp = 0

                    for (i in 0 until shortsRead) {
                        // Apply software gain boost to elevate remote party audio
                        var sample = (audioData[i] * SOFTWARE_GAIN).toInt()
                        sample = max(Short.MIN_VALUE.toInt(), min(Short.MAX_VALUE.toInt(), sample))
                        val boostedShort = sample.toShort()

                        byteBuffer.putShort(boostedShort)

                        val absSample = Math.abs(boostedShort.toInt())
                        if (absSample > maxAmp) {
                            maxAmp = absSample
                        }
                    }

                    currentMaxAmplitude = maxAmp
                    outputStream.write(byteBuffer.array(), 0, shortsRead * 2)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio PCM data", e)
        } finally {
            try {
                outputStream?.flush()
                outputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing PCM stream", e)
            }
        }
    }

    fun stop(): Boolean {
        if (!isRecording) return false

        isRecording = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Exception while stopping AudioRecord", e)
        } finally {
            audioRecord?.release()
            audioRecord = null
        }

        try {
            recordingThread?.join(3000)
        } catch (e: Exception) {
            Log.w(TAG, "Interrupted while waiting for recorder thread", e)
        }
        recordingThread = null
        return true
    }

    private fun convertPcmToWav(pcmFile: File, wavFile: File) {
        val totalAudioLen = pcmFile.length()
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = (16 * SAMPLE_RATE * channels / 8).toLong()

        val data = ByteArray(4096)
        var inStream: FileInputStream? = null
        var outStream: FileOutputStream? = null

        try {
            inStream = FileInputStream(pcmFile)
            outStream = FileOutputStream(wavFile)

            writeWavHeader(outStream, totalAudioLen, totalDataLen, SAMPLE_RATE.toLong(), channels, byteRate)

            var bytesRead: Int
            while (inStream.read(data).also { bytesRead = it } != -1) {
                outStream.write(data, 0, bytesRead)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting PCM to WAV", e)
        } finally {
            try {
                inStream?.close()
                outStream?.flush()
                outStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing WAV streams", e)
            }
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
        longSampleRate: Long,
        channels: Int,
        byteRate: Long
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        out.write(header, 0, 44)
    }
}
