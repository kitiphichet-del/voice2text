package com.example.groupinterpreter.presentation

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.groupinterpreter.core.Direction
import com.example.groupinterpreter.core.MessageRules
import com.example.groupinterpreter.data.OnDeviceTranslationEngine
import com.example.groupinterpreter.voice.VoiceCaptureEngine
import com.example.groupinterpreter.voice.WhisperModelManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InputMode { TEXT, VOICE }

/** Only the most recent message is displayed; old async translations are discarded. */
data class InterpreterState(
    val source: String = "",
    val translation: String = "",
    val direction: Direction = Direction.AUTO,
    val inputMode: InputMode = InputMode.TEXT,
    val translating: Boolean = false,
    val downloading: Boolean = false,
    val prepared: Boolean = false,
    val speechReady: Boolean = false,
    val speechDownloading: Boolean = false,
    val speechProgress: Int = 0,
    val voicePreparing: Boolean = false,
    val listening: Boolean = false,
    val voiceProcessing: Boolean = false,
    val capturedSeconds: Int = 0,
    val processedSegments: Int = 0,
    val processingSegment: Int = 0,
    val audioLevel: Int = 0,
    val status: String = "พิมพ์ข้อความหรือเลือกโหมดเสียงเพื่อเริ่มแปล",
    val lastUsed: Direction? = null
)

class InterpreterViewModel(application: Application) : AndroidViewModel(application) {
    private val translator = OnDeviceTranslationEngine()
    private val model = WhisperModelManager(application)
    private val voice = VoiceCaptureEngine()
    private val modelFlags = application.getSharedPreferences("offline_models", android.content.Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(
        InterpreterState(
            speechReady = model.isReady(),
            prepared = modelFlags.getBoolean("translate_prepared", false)
        )
    )
    val state = mutableState.asStateFlow()
    private var work: Job? = null
    private var downloadWork: Job? = null
    private var speechDownloadWork: Job? = null
    private var voiceJob: Job? = null
    private var requestNumber = 0L
    private var voiceSession = 0L
    private var finishingMonitor: Job? = null

    fun setInputMode(mode: InputMode) {
        if (mutableState.value.inputMode == mode) return
        if (mode == InputMode.TEXT) {
            voiceSession++ // Pending voice transcripts must not appear on the text tab.
            stopVoice()
        }
        invalidateWork()
        mutableState.update {
            it.copy(inputMode = mode, source = "", translation = "", translating = false,
                voicePreparing = false, listening = false, voiceProcessing = false,
                status = if (mode == InputMode.VOICE)
                    "โหลดโมเดลเสียงและโมเดลแปลภาษาก่อนเริ่มใช้ไมโครโฟน"
                else "พิมพ์ข้อความภาษาไทยหรือจีนเพื่อเริ่มแปล")
        }
    }

    fun updateInput(input: String) {
        invalidateWork()
        mutableState.update {
            it.copy(
                source = input,
                translation = "",
                translating = false,
                status = if (input.isBlank()) "รอข้อความล่าสุด…" else "กำลังรับข้อความล่าสุด…"
            )
        }
        if (input.isNotBlank()) scheduleTranslation(if (mutableState.value.inputMode == InputMode.VOICE) 0 else 850)
    }

    fun setDirection(direction: Direction) {
        invalidateWork()
        mutableState.update {
            it.copy(direction = direction, translation = "", translating = false)
        }
        if (mutableState.value.source.isNotBlank()) scheduleTranslation(0)
    }

    fun translateNow() {
        invalidateWork()
        if (mutableState.value.source.isNotBlank()) scheduleTranslation(0)
    }

    fun clear() {
        voiceSession++
        stopVoice()
        invalidateWork()
        mutableState.update {
            it.copy(source = "", translation = "", translating = false,
                status = "ล้างคำแปลล่าสุดแล้ว")
        }
    }

    fun prepareModels() {
        if (downloadWork?.isActive == true) return
        downloadWork = viewModelScope.launch {
            mutableState.update { it.copy(downloading = true, status = "กำลังตรวจสอบโมเดลแปลภาษา (Wi-Fi)…") }
            try {
                translator.prepareOnWifi()
                modelFlags.edit().putBoolean("translate_prepared", true).apply()
                mutableState.update { it.copy(prepared = true, status = "โมเดลแปลภาษาไทย–จีนพร้อมทำงานออฟไลน์") }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                mutableState.update { it.copy(status = "ติดตั้งโมเดลแปลภาษาไม่สำเร็จ กรุณาเชื่อม Wi-Fi แล้วลองอีกครั้ง") }
            } finally {
                mutableState.update { it.copy(downloading = false) }
            }
        }
    }

    fun prepareSpeechModel() {
        if (speechDownloadWork?.isActive == true || model.isReady()) {
            if (model.isReady()) mutableState.update {
                it.copy(speechReady = true, status = "โมเดลรู้จำเสียงอยู่บนโทรศัพท์แล้ว")
            }
            return
        }
        speechDownloadWork = viewModelScope.launch {
            mutableState.update {
                it.copy(speechDownloading = true, speechProgress = 0,
                    status = "กำลังดาวน์โหลดโมเดลเสียงรุ่นเร็ว Tiny (~32 MB) ครั้งแรก…")
            }
            try {
                model.download { progress ->
                    mutableState.update { it.copy(speechProgress = progress) }
                }
                mutableState.update {
                    it.copy(speechReady = true, speechProgress = 100,
                        status = "ติดตั้งโมเดลเสียงสำเร็จ • ใช้รู้จำเสียงบนเครื่องได้")
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(speechReady = false,
                        status = "ดาวน์โหลดโมเดลเสียงไม่ได้: ${error.message ?: "ลองอีกครั้ง"}")
                }
            } finally {
                mutableState.update { it.copy(speechDownloading = false) }
            }
        }
    }

    fun permissionDenied() {
        mutableState.update { it.copy(status = "ต้องอนุญาตการใช้ไมโครโฟนจึงจะรับเสียงได้") }
    }

    fun startVoice() {
        val snapshot = mutableState.value
        if (snapshot.inputMode != InputMode.VOICE || voiceJob?.isActive == true ||
            snapshot.voicePreparing || snapshot.listening || snapshot.voiceProcessing) return
        if (!model.isReady()) {
            mutableState.update { it.copy(status = "กรุณาดาวน์โหลดโมเดลเสียงให้ครบก่อน (ครั้งเดียว)") }
            return
        }
        if (!snapshot.prepared) {
            mutableState.update { it.copy(status = "กรุณากดเตรียมโมเดลแปลภาษาไทย–จีน (Wi-Fi) ก่อน") }
            return
        }
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissionDenied()
            return
        }
        voiceSession++
        val session = voiceSession
        invalidateWork()
        mutableState.update {
            it.copy(voicePreparing = true, listening = false, voiceProcessing = false,
                source = "", translation = "", translating = false,
                capturedSeconds = 0, processedSegments = 0,
                processingSegment = 0, audioLevel = 0,
                status = "กำลังเปิดโมเดล Whisper Tiny บนเครื่อง…")
        }
        voice.prepareStart()
        voiceJob = viewModelScope.launch {
            try {
                voice.listen(
                    modelFile = model.file,
                    language = {
                        when (mutableState.value.direction) {
                            Direction.TH_TO_ZH -> "th"
                            Direction.ZH_TO_TH -> "zh"
                            Direction.AUTO -> "auto"
                        }
                    },
                    onReady = {
                        if (session == voiceSession) mutableState.update {
                            it.copy(voicePreparing = false, listening = true, voiceProcessing = false,
                                status = "กำลังฟังและถอดเสียงเป็นช่วง 3 วินาที…")
                        }
                    },
                    onCapture = { seconds, level ->
                        if (session == voiceSession) mutableState.update {
                            it.copy(capturedSeconds = seconds, audioLevel = level)
                        }
                    },
                    onProcessing = { number ->
                        if (session == voiceSession) mutableState.update {
                            it.copy(processingSegment = number,
                                status = "กำลังถอดเสียงช่วงที่ $number บนเครื่อง…")
                        }
                    },
                    onTranscript = { recognized ->
                        if (session == voiceSession && mutableState.value.inputMode == InputMode.VOICE) {
                            updateInput(recognized)
                        }
                    },
                    onDecoded = { number, recognized ->
                        if (session == voiceSession) mutableState.update {
                            it.copy(
                                processedSegments = it.processedSegments + 1,
                                status = if (recognized)
                                    "ถอดเสียงช่วงที่ $number ได้แล้ว กำลังแปล…"
                                else "ยังจับคำพูดไม่ได้ในช่วงที่ $number ลองเข้าใกล้ไมโครโฟน"
                            )
                        }
                    },
                    onWarning = { message ->
                        if (session == voiceSession) mutableState.update { it.copy(status = message) }
                    }
                )
                if (session == voiceSession) mutableState.update {
                    it.copy(status = if (it.source.isBlank())
                        "หยุดฟังแล้ว แต่ยังถอดข้อความไม่ได้ กรุณาลองพูดให้ดังขึ้นหรือบังคับภาษาต้นฉบับ"
                    else "หยุดฟังแล้ว ประมวลผลเสียงทั้งหมดเสร็จแล้ว")
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                if (session == voiceSession) mutableState.update {
                    it.copy(status = "โหมดเสียงขัดข้อง: ${error.message ?: "โปรดตรวจสอบสิทธิ์ไมโครโฟน"}")
                }
            } finally {
                finishingMonitor?.cancel()
                if (session == voiceSession) mutableState.update {
                    it.copy(voicePreparing = false, listening = false, voiceProcessing = false)
                }
            }
        }
    }

    fun stopVoice() {
        if (voiceJob?.isActive != true) return
        voice.requestStop()
        mutableState.update {
            it.copy(listening = false, voicePreparing = false, voiceProcessing = true,
                status = "หยุดรับเสียงแล้ว กำลังจบช่วงที่ถอดเสียงอยู่…")
        }
        finishingMonitor?.cancel()
        finishingMonitor = viewModelScope.launch {
            delay(8_000)
            if (voiceJob?.isActive == true && mutableState.value.voiceProcessing) {
                mutableState.update {
                    it.copy(status = "เครื่องกำลังถอดเสียงช้ากว่าปกติ (รอได้หรือเปลี่ยนไปโหมดข้อความ) " +
                        "ระบบไม่ส่งเสียงขึ้น Cloud")
                }
            }
        }
    }

    private fun invalidateWork() {
        requestNumber++
        work?.cancel()
    }

    private fun scheduleTranslation(debounceMs: Long) {
        val generation = requestNumber
        work = viewModelScope.launch {
            delay(debounceMs)
            val snapshot = mutableState.value
            val original = snapshot.source
            if (original.isBlank()) return@launch
            val parsed = MessageRules.parse(original)
            if (parsed.body.isBlank()) return@launch
            mutableState.update { it.copy(translating = true, status = "กำลังแปลบนโทรศัพท์…") }
            try {
                val detected = if (snapshot.direction == Direction.AUTO &&
                    MessageRules.scriptLanguage(parsed.body) == null
                ) translator.identifyLanguage(parsed.body) else null
                val resolved = MessageRules.chooseDirection(snapshot.direction, parsed.body, detected)
                if (resolved == null) {
                    if (generation == requestNumber) mutableState.update {
                        it.copy(translating = false,
                            status = "ตรวจจับภาษาไม่ชัดเจน โปรดเลือก ไทย → 中文 หรือ 中文 → ไทย")
                    }
                    return@launch
                }
                // Voice mode NEVER attempts a translation-model download during recognition.
                val text = translator.translate(
                    parsed.body, resolved, offlineOnly = snapshot.inputMode == InputMode.VOICE
                )
                if (generation != requestNumber || original != mutableState.value.source) return@launch
                mutableState.update {
                    it.copy(
                        translation = MessageRules.appendTranslation(parsed, text),
                        translating = false, lastUsed = resolved, prepared = true,
                        status = "แปลข้อความล่าสุดแล้ว • ${if (resolved == Direction.TH_TO_ZH) "ไทย → 中文" else "中文 → ไทย"}"
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                if (generation == requestNumber) mutableState.update {
                    it.copy(translating = false, translation = "",
                        status = "แปลไม่สำเร็จ: กรุณาเตรียมโมเดลภาษาให้ครบก่อนใช้แบบออฟไลน์")
                }
            }
        }
    }

    override fun onCleared() {
        voiceSession++
        voice.requestStop()
        voiceJob?.cancel()
        finishingMonitor?.cancel()
        invalidateWork()
        downloadWork?.cancel()
        speechDownloadWork?.cancel()
        translator.close()
        super.onCleared()
    }
}
