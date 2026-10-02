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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Continuously captures 16 kHz mono PCM and transcribes 7-second audio windows on-device.
 * This is near-live chunked translation, not instantaneous word-by-word translation.
 * Bounded queue prevents an unresponsive phone exhausting RAM.
 */
class VoiceCaptureEngine {
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null

    fun prepareStart() { stopRequested.set(false) }

    fun requestStop() {
        stopRequested.set(true)
        try {
            recorder?.stop() // Releases a blocking read so that remaining audio can be processed.
        } catch (_: IllegalStateException) {}
    }

    @SuppressLint("MissingPermission") // Runtime permission is verified before starting in ViewModel.
    suspend fun listen(
        modelFile: File,
        language: () -> String,
        onReady: () -> Unit,
        onTranscript: (String) -> Unit,
        onDecoded: (Int, Boolean) -> Unit,
        onWarning: (String) -> Unit
    ) {
        if (stopRequested.get()) return
        var nativeContext = 0L
        try {
            nativeContext = withContext(Dispatchers.Default) {
                WhisperBridge.load(modelFile.absolutePath)
            }
            require(nativeContext != 0L) { "เปิดโมเดลเสียงไม่ได้ กรุณาดาวน์โหลดอีกครั้ง" }
            if (stopRequested.get()) return
            val handle = nativeContext
            coroutineScope {
                val queue = Channel<FloatArray>(capacity = 3)
                val processor = launch(Dispatchers.Default) {
                    for (chunk in queue) {
                        val result = WhisperBridge.transcribe(handle, chunk, language()).trim()
                        if (result.isNotEmpty()) onTranscript(result)
                        onDecoded(number, result.isNotEmpty())
                    }
                }
                try {
                    withContext(Dispatchers.IO) {
                        if (stopRequested.get()) return@withContext
                        val min = AudioRecord.getMinBufferSize(
                            RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                        )
                        require(min > 0) { "อุปกรณ์ไม่รองรับการบันทึกเสียง 16 kHz" }
                        val readSize = max(4096, min / 2)
                        val audio = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            readSize * 2
                        )
                        if (audio.state != AudioRecord.STATE_INITIALIZED) {
                            audio.release()
                            error("เปิดไมโครโฟนไม่สำเร็จ")
                        }
                        recorder = audio
                        val readBuffer = ShortArray(readSize)
                        val window = FloatArray(RATE * SECONDS)
                        var filled = 0
                        var amplitude = 0.0
                        try {
                            if (stopRequested.get()) return@withContext
                            audio.startRecording()
                            onReady()
                            while (currentCoroutineContext().isActive && !stopRequested.get()) {
                                val read = audio.read(readBuffer, 0, readBuffer.size)
                                if (read < 0) {
                                    if (stopRequested.get()) break
                                    error("ไม่สามารถรับเสียงจากไมโครโฟนได้ ($read)")
                                }
                                if (read == 0) continue
                                for (n in 0 until read) {
                                    val sample = readBuffer[n].toFloat() / 32768f
                                    window[filled++] = sample
                                    amplitude += abs(sample.toDouble())
                                    if (filled == window.size) {
                                        emitIfSpeech(window.copyOf(), amplitude / filled, queue, onWarning)
                                        filled = 0
                                        amplitude = 0.0
                                    }
                                }
                            }
                            if (filled >= RATE) {
                                emitIfSpeech(window.copyOf(filled), amplitude / filled, queue, onWarning)
                            }
                        } finally {
                            recorder = null
                            try { audio.stop() } catch (_: IllegalStateException) {}
                            audio.release()
                        }
                    }
                } finally {
                    queue.close()
                    // Do not release the native model while a chunk is being transcribed.
                    withContext(NonCancellable) { processor.join() }
                }
            }
        } finally {
            if (nativeContext != 0L) {
                withContext(NonCancellable + Dispatchers.Default) {
                    WhisperBridge.unload(nativeContext)
                }
            }
        }
    }

    private fun emitIfSpeech(
        samples: FloatArray,
        meanAmplitude: Double,
        queue: Channel<FloatArray>,
        onWarning: (String) -> Unit
    ) {
        if (meanAmplitude < 0.004) return
        if (queue.trySend(samples).isFailure) {
            onWarning("เครื่องถอดเสียงไม่ทัน จึงข้ามเสียงบางช่วง ลองพูดช้าลงหรือใช้โทรศัพท์ที่เร็วขึ้น")
        }
    }

    companion object {
        const val RATE = 16_000
        const val SECONDS = 7
    }
}
