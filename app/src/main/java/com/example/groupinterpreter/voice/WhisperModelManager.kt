package com.example.groupinterpreter.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Multilingual Whisper Tiny Q5_1, SHA-256 pinned to the official Hugging Face revision.
 * Tiny is substantially faster than Base on CPUs; actual latency remains device-dependent.
 */
class WhisperModelManager(private val context: Context) {
    private val preferences = context.getSharedPreferences("offline_models", Context.MODE_PRIVATE)
    val file: File get() = File(context.filesDir, "ggml-tiny-q5_1.bin")
    private val partial: File get() = File(context.filesDir, "ggml-tiny-q5_1.part")

    fun isReady(): Boolean = file.isFile &&
        file.length() in 30_000_000L..35_000_000L &&
        preferences.getBoolean("speech_fast_verified", false)

    suspend fun download(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        if (isReady()) {
            onProgress(100)
            return@withContext
        }
        preferences.edit().putBoolean("speech_fast_verified", false).apply()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 25_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ThaiChineseOfflineInterpreter/1.2")
        }
        try {
            require(connection.responseCode in 200..299) {
                "ดาวน์โหลดโมเดลไม่ได้ (HTTP ${connection.responseCode})"
            }
            val total = connection.contentLengthLong
            var count = 0L
            var lastProgress = -1
            val sha256 = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { source ->
                partial.outputStream().buffered().use { target ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = source.read(buffer)
                        if (n < 0) break
                        sha256.update(buffer, 0, n)
                        target.write(buffer, 0, n)
                        count += n
                        if (total > 0) {
                            val progress = (count * 100 / total).toInt().coerceIn(0, 99)
                            if (progress != lastProgress) {
                                onProgress(progress)
                                lastProgress = progress
                            }
                        }
                    }
                }
            }
            val downloaded = sha256.digest().joinToString("") { "%02x".format(it) }
            require(downloaded.equals(MODEL_SHA256, ignoreCase = true)) {
                "ไฟล์โมเดลไม่ผ่านการตรวจสอบ SHA-256"
            }
            if (file.exists()) file.delete()
            require(partial.renameTo(file)) { "ย้ายโมเดลไปยังพื้นที่แอปไม่สำเร็จ" }
            preferences.edit().putBoolean("speech_fast_verified", true).apply()
            // The previous Base model is retained for rollback; it is not used in fast mode.
            onProgress(100)
        } finally {
            connection.disconnect()
            if (partial.exists()) partial.delete()
        }
    }

    companion object {
        const val MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-tiny-q5_1.bin"
        const val MODEL_SHA256 =
            "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7"
    }
}
