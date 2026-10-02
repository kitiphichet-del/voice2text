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
 * Official multilingual (not English-only) ggml-base-q5_1.bin model. About 60 MB.
 * The checksum is pinned to the file, not to an unverified mutable latest URL.
 */
class WhisperModelManager(private val context: Context) {
    private val preferences = context.getSharedPreferences("offline_models", Context.MODE_PRIVATE)
    val file: File get() = File(context.filesDir, "ggml-base-q5_1.bin")
    private val partial: File get() = File(context.filesDir, "ggml-base-q5_1.part")

    fun isReady(): Boolean = file.isFile &&
        file.length() > 50_000_000L && preferences.getBoolean("speech_verified", false)

    suspend fun download(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        if (isReady()) {
            onProgress(100)
            return@withContext
        }
        preferences.edit().putBoolean("speech_verified", false).apply()
        val url = URL(MODEL_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 25_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ThaiChineseOfflineInterpreter/1.1")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("ดาวน์โหลดโมเดลเสียงไม่ได้ (HTTP ${connection.responseCode})")
            }
            val total = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            connection.inputStream.use { source ->
                partial.outputStream().buffered().use { target ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = source.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        target.write(buffer, 0, n)
                        count += n
                        if (total > 0) onProgress(((count * 100) / total).toInt().coerceIn(0, 99))
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            require(sha == MODEL_SHA256) { "ไฟล์โมเดลเสียงไม่ผ่านการตรวจสอบความถูกต้อง" }
            if (file.exists()) file.delete()
            require(partial.renameTo(file)) { "บันทึกโมเดลลงโทรศัพท์ไม่สำเร็จ" }
            preferences.edit().putBoolean("speech_verified", true).apply()
            onProgress(100)
        } finally {
            connection.disconnect()
            if (partial.exists()) partial.delete()
        }
    }

    companion object {
        const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-base-q5_1.bin"
        const val MODEL_SHA256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
    }
}
