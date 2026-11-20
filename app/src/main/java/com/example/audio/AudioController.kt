package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "AudioController"

class AudioController {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    @Volatile
    private var activeRecord: AudioRecord? = null

    @Volatile
    private var activeTrack: AudioTrack? = null

    private val recordedPcm = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    suspend fun startRecording(): Boolean = withContext(Dispatchers.IO) {
        if (_isRecording.value) return@withContext false
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            return@withContext false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext false
        }

        recordedPcm.reset()
        activeRecord = record
        _isRecording.value = true

        try {
            record.startRecording()
            val buffer = ByteArray(minBuf)
            while (isActive && _isRecording.value) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    synchronized(recordedPcm) {
                        recordedPcm.write(buffer, 0, read)
                    }
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Recording loop error", e)
            false
        } finally {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (_: Exception) {
            }
            activeRecord = null
            _isRecording.value = false
        }
    }

    fun stopRecordingAndGetWav(): ByteArray {
        _isRecording.value = false
        try {
            activeRecord?.stop()
        } catch (_: Exception) {
        }
        val pcmBytes = synchronized(recordedPcm) { recordedPcm.toByteArray() }
        return wrapPcmInWav(pcmBytes, sampleRate = 16000, channels = 1, bitsPerSample = 16)
    }

    suspend fun playAudioBytes(bytes: ByteArray, mimeType: String) = withContext(Dispatchers.IO) {
        stopPlayback()
        if (bytes.isEmpty()) return@withContext

        var sampleRate = extractRateFromMime(mimeType)
        var pcmData = bytes

        // Detect RIFF/WAV header and strip 44 bytes if present
        if (bytes.size > 44 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
        ) {
            sampleRate = ByteBuffer.wrap(bytes, 24, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
                .coerceIn(8000, 48000)
            pcmData = bytes.copyOfRange(44, bytes.size)
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeTrack = track
        _isPlaying.value = true
        try {
            track.play()
            var offset = 0
            while (isActive && _isPlaying.value && offset < pcmData.size) {
                val chunk = minOf(minBuf, pcmData.size - offset)
                val written = track.write(pcmData, offset, chunk)
                if (written <= 0) break
                offset += written
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error", e)
        } finally {
            try {
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
            activeTrack = null
            _isPlaying.value = false
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        try {
            activeTrack?.stop()
        } catch (_: Exception) {
        }
    }

    private fun extractRateFromMime(mimeType: String): Int {
        val match = Regex("rate=(\\d+)").find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(8000, 48000) ?: 24000
    }

    private fun wrapPcmInWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM subchunk size
        header.putShort(1) // AudioFormat = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * bitsPerSample / 8).toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)
        return header.array() + pcmData
    }
}
