package com.example.groupinterpreter.presentation

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.groupinterpreter.core.Direction
import com.example.groupinterpreter.data.OnDeviceTranslationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SimpleUiState(
    val original: String = "",
    val preview: String = "",
    val translated: String = "",
    val status: String = "เลือกภาษา แล้วแตะปุ่มพูดหนึ่งครั้ง",
    val translationReady: Boolean = false,
    val preparing: Boolean = false,
    val translating: Boolean = false,
    val listening: Boolean = false,
    val activeDirection: Direction? = null,
    val speechAvailable: Boolean = false,
    val textOpen: Boolean = false,
    val typed: String = ""
)

/**
 * SpeechRecognizer.createOnDeviceSpeechRecognizer is the only speech engine.
 * Never uses the ordinary recognizer, cloud API, audio files, or a voice model loop.
 * One button press = one utterance = one translated result.
 */
class SimpleInterpreterViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = OnDeviceTranslationEngine()
    private val flags = application.getSharedPreferences("offline_models", 0)
    private val available = Build.VERSION.SDK_INT >= 31 &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(application)
    private val mutable = MutableStateFlow(SimpleUiState(
        translationReady = flags.getBoolean("translate_prepared", false),
        speechAvailable = available
    ))
    val state = mutable.asStateFlow()
    private var recognizer: SpeechRecognizer? = null
    private var voiceTimeout: Job? = null
    private var translationJob: Job? = null
    private var translationVersion: Long = 0

    fun prepareTranslations() {
        if (mutable.value.preparing) return
        viewModelScope.launch {
            mutable.update { it.copy(preparing = true, status = "กำลังเตรียมโมเดลแปลภาษาไทย–จีนผ่าน Wi-Fi…") }
            try {
                engine.prepareOnWifi()
                flags.edit().putBoolean("translate_prepared", true).apply()
                mutable.update { it.copy(translationReady = true, status = "พร้อมแปลแบบออฟไลน์") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update {
                    it.copy(status = "เตรียมโมเดลไม่สำเร็จ ตรวจสอบ Wi-Fi แล้วกดอีกครั้ง")
                }
            } finally {
                mutable.update { it.copy(preparing = false) }
            }
        }
    }

    fun microphoneDenied() {
        mutable.update { it.copy(status = "ต้องอนุญาตไมโครโฟนก่อนใช้การแปลเสียง") }
    }

    fun startSpeech(direction: Direction) {
        if (direction == Direction.AUTO) return
        if (!mutable.value.translationReady) {
            mutable.update { it.copy(status = "กดดาวน์โหลดโมเดลแปลภาษาก่อนเริ่มใช้") }
            return
        }
        if (Build.VERSION.SDK_INT < 31 || !available) {
            mutable.update {
                it.copy(status = "โทรศัพท์ไม่พบระบบรู้จำเสียงบนเครื่อง ใช้ช่องพิมพ์แทนได้")
            }
            return
        }
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            microphoneDenied()
            return
        }

        finishRecognizer()
        invalidateTranslation()
        mutable.update {
            it.copy(original = "", preview = "", translated = "", translating = false,
                listening = true, activeDirection = direction,
                status = if (direction == Direction.TH_TO_ZH)
                    "กำลังฟังภาษาไทย… พูดจบแล้วหยุดสักครู่"
                else "正在听中文… 说完请稍等")
        }
        try {
            val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(getApplication())
            recognizer = speech
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                    if (direction == Direction.TH_TO_ZH) "th-TH" else "zh-CN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    if (direction == Direction.TH_TO_ZH) "th-TH" else "zh-CN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1700L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            }
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (recognizer === speech) mutable.update {
                        it.copy(status = "🎙 กำลังฟัง…")
                    }
                }
                override fun onBeginningOfSpeech() {
                    if (recognizer === speech) mutable.update {
                        it.copy(status = "ได้ยินเสียงพูดแล้ว…")
                    }
                }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    if (recognizer === speech) mutable.update {
                        it.copy(status = "กำลังแปลงเสียงเป็นข้อความบนเครื่อง…")
                    }
                }
                override fun onError(error: Int) {
                    if (recognizer !== speech) return
                    val errorText = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "ยังฟังไม่ชัด ลองกดพูดอีกครั้ง"
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            "ยังไม่ได้ติดตั้งแพ็กเสียงภาษานี้บนโทรศัพท์ กรุณาติดตั้งภาษาสำหรับการรู้จำเสียงออฟไลน์"
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                            "โทรศัพท์ไม่รองรับการรู้จำเสียงภาษานี้แบบออฟไลน์"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "กรุณาอนุญาตใช้ไมโครโฟน"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "ระบบไมโครโฟนกำลังทำงานอยู่ กรุณาลองใหม่"
                        else ->
                            "ระบบรู้จำเสียงบนโทรศัพท์ไม่พร้อม (รหัส $error) ลองใหม่หรือใช้ช่องพิมพ์"
                    }
                    if (Build.VERSION.SDK_INT >= 33 &&
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                        try {
                            // Only requests an ON-DEVICE language pack from the OS.
                            speech.triggerModelDownload(request)
                        } catch (_: Exception) {
                            // Some vendors require language pack setup through system Settings.
                        }
                    }
                    finishRecognizer()
                    mutable.update { it.copy(status = errorText, preview = "") }
                }
                override fun onResults(results: Bundle?) {
                    if (recognizer !== speech) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    finishRecognizer()
                    if (text.isBlank()) {
                        mutable.update { it.copy(status = "ไม่ได้ยินข้อความที่ชัดเจน ลองใหม่") }
                    } else {
                        translate(text, direction)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (recognizer !== speech) return
                    val words = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    if (words.isNotEmpty()) mutable.update { it.copy(preview = words) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            speech.startListening(request)
            voiceTimeout?.cancel()
            voiceTimeout = viewModelScope.launch {
                delay(30_000)
                if (recognizer === speech) {
                    mutable.update { it.copy(status = "กำลังจบประโยคเพื่อแปล…") }
                    speech.stopListening()
                    delay(7_000)
                    if (recognizer === speech) {
                        finishRecognizer()
                        mutable.update { it.copy(status = "การรู้จำเสียงใช้เวลานานเกินไป กรุณาลองใหม่") }
                    }
                }
            }
        } catch (e: Exception) {
            finishRecognizer()
            mutable.update { it.copy(status = "เปิดระบบเสียงออฟไลน์ไม่สำเร็จ: ${e.message ?: "ลองใหม่"}") }
        }
    }

    fun stopSpeech() {
        recognizer?.let { speech ->
            mutable.update { it.copy(status = "กำลังจบประโยคและแปล…") }
            try { speech.stopListening() } catch (_: Exception) {}
            voiceTimeout?.cancel()
            voiceTimeout = viewModelScope.launch {
                delay(7_000)
                if (recognizer === speech) {
                    finishRecognizer()
                    mutable.update { it.copy(status = "ระบบเสียงไม่ตอบกลับ ลองพูดอีกครั้ง") }
                }
            }
        }
    }

    fun cancelSpeech() {
        finishRecognizer()
        mutable.update { it.copy(status = "หยุดฟังแล้ว") }
    }

    fun toggleText() {
        mutable.update { it.copy(textOpen = !it.textOpen) }
    }

    fun updateText(text: String) {
        mutable.update { it.copy(typed = text) }
    }

    fun translateTyped(direction: Direction) {
        if (mutable.value.typed.isBlank()) return
        finishRecognizer()
        translate(mutable.value.typed.trim(), direction)
    }

    private fun translate(text: String, direction: Direction) {
        invalidateTranslation()
        val current = translationVersion
        mutable.update {
            it.copy(original = text, preview = "", translated = "", translating = true,
                activeDirection = direction, status = "กำลังแปล…")
        }
        translationJob = viewModelScope.launch {
            try {
                // Never start a network download while translating a spoken sentence.
                val translated = engine.translate(text, direction, offlineOnly = true)
                if (current == translationVersion) mutable.update {
                    it.copy(translated = translated.trim(), translating = false,
                        status = "แปลเสร็จแล้ว")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (current == translationVersion) mutable.update {
                    it.copy(translating = false, translated = "",
                        status = "ยังแปลไม่ได้ กรุณาเตรียมโมเดลแปลภาษาเมื่อเชื่อม Wi-Fi")
                }
            }
        }
    }

    private fun invalidateTranslation() {
        translationVersion++
        translationJob?.cancel()
    }

    private fun finishRecognizer() {
        voiceTimeout?.cancel()
        voiceTimeout = null
        val previous = recognizer
        recognizer = null
        try { previous?.cancel() } catch (_: Exception) {}
        try { previous?.destroy() } catch (_: Exception) {}
        mutable.update { it.copy(listening = false) }
    }

    override fun onCleared() {
        finishRecognizer()
        invalidateTranslation()
        engine.close()
        super.onCleared()
    }
}
