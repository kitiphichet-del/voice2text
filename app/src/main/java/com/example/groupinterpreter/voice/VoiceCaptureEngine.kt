package com.example.groupinterpreter.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Three-second offline speech windows. Capture continues while transcription runs.
 * Buffer budget: one frame in Whisper + one queued frame (about 375 KiB total).
 * If decoding is slower than recording, drop stale *queued* audio to stay responsive,
 * and make the loss visible to the user. No cloud fallback or fake transcripts.
 */
class VoiceCaptureEngine {
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null

    fun prepareStart() { stopRequested.set(false) }

    fun requestStop() {
        stopRequested.set(true)
        try { recorder?.stop() } catch (_: IllegalStateException) {}
    }

    @SuppressLint("MissingPermission")
    suspend fun listen(
        modelFile: File,
        language: () -> String,
        onReady: () -> Unit,
        onCapture: (seconds: Int, level: Int) -> Unit,
        onProcessing: (chunk: Int) -> Unit,
        onTranscript: (String) -> Unit,
        onDecoded: (Int, Boolean) -> Unit,
        onWarning: (String) -> Unit
    ) {
        if (stopRequested.get()) return
        val nativeContext = withContext(Dispatchers.Default) {
            WhisperBridge.load(modelFile.absolutePath)
        }
        require(nativeContext != 0L) { "โหลดโมเดล Whisper ไม่สำเร็จ" }
        try {
            if (stopRequested.get()) return
            coroutineScope {
                val pending = Channel<Pair<Int, FloatArray>>(capacity = 1)
                val decoder = launch(Dispatchers.Default) {
                    for ((number, samples) in pending) {
                        onProcessing(number)
                        val result = WhisperBridge.transcribe(
                            nativeContext, samples, language()
                        ).trim()
                        if (result.isNotEmpty()) onTranscript(result)
                        onDecoded(number, result.isNotEmpty())
                    }
                }
                try {
                    withContext(Dispatchers.IO) {
                        if (stopRequested.get()) return@withContext
                        val min = AudioRecord.getMinBufferSize(
                            RATE, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                        require(min > 0) { "โทรศัพท์ไม่รองรับการบันทึก 16 kHz" }
                        val readSize = max(2048, min / 2)
                        val audio = AudioRecord(
                            MediaRecorder.AudioSource.MIC, RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, readSize * 2
                        )
                        if (audio.state != AudioRecord.STATE_INITIALIZED) {
                            audio.release()
                            error("ไมโครโฟนไม่พร้อม กรุณาตรวจสอบสิทธิ์การใช้งาน")
                        }
                        recorder = audio
                        val buffer = ShortArray(readSize)
                        val window = FloatArray(SAMPLES_PER_WINDOW)
                        var filled = 0
                        var sumSquares = 0.0
                        var totalSamples = 0L
                        var lastSecond = 0
                        var part = 0
                        var dropped = 0
                        fun offerChunk(frame: FloatArray, rms: Double) {
                            // Ignore truly silent windows, but do not throw away soft speech.
                            if (rms < 0.0015) return
                            part++
                            val item = part to frame
                            if (pending.trySend(item).isFailure) {
                                // Retain the currently decoding frame and the newest input.
                                pending.tryReceive()
                                if (pending.trySend(item).isFailure) {
                                    onWarning("ระบบถอดเสียงไม่ทัน เสียงบางช่วงอาจหายไป")
                                }
                                dropped++
                                if (dropped == 1 || dropped % 3 == 0) {
                                    onWarning("เครื่องถอดเสียงไม่ทัน ข้ามเสียงที่ค้างแล้ว ${dropped} ช่วง")
                                }
                            }
                        }
                        try {
                            if (stopRequested.get()) return@withContext
                            audio.startRecording()
                            onReady()
                            while (currentCoroutineContext().isActive && !stopRequested.get()) {
                                val count = audio.read(buffer, 0, buffer.size)
                                if (count < 0) {
                                    if (stopRequested.get()) break
                                    error("ไมโครโฟนอ่านเสียงไม่สำเร็จ ($count)")
                                }
                                if (count == 0) continue
                                for (index in 0 until count) {
                                    val sample = buffer[index].toFloat() / 32768f
                                    window[filled++] = sample
                                    sumSquares += sample.toDouble() * sample
                                    totalSamples++
                                    val second = (totalSamples / RATE).toInt()
                                    if (second > lastSecond) {
                                        lastSecond = second
                                        val level = (sqrt(sumSquares / filled) * 1000).toInt().coerceIn(0, 100)
                                        onCapture(second, level)
                                    }
                                    if (filled == SAMPLES_PER_WINDOW) {
                                        offerChunk(window.copyOf(), sqrt(sumSquares / filled))
                                        filled = 0
                                        sumSquares = 0.0
                                    }
                                }
                            }
                            // Keep final partial speech; the decoder sees at most one queued frame.
                            if (filled >= RATE / 2) {
                                offerChunk(window.copyOf(filled), sqrt(sumSquares / filled))
                            }
                        } finally {
                            recorder = null
                            try { audio.stop() } catch (_: IllegalStateException) {}
                            audio.release()
                        }
                    }
                } finally {
                    pending.close()
                    // Native model must outlive the decoder. OnStop is visible in UI,
                    // and only at most one queued frame remains.
                    withContext(NonCancellable) { decoder.join() }
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Default) {
                WhisperBridge.unload(nativeContext)
            }
        }
    }

    companion object {
        const val RATE = 16_000
        const val WINDOW_SECONDS = 3
        const val SAMPLES_PER_WINDOW = RATE * WINDOW_SECONDS
    }
}
