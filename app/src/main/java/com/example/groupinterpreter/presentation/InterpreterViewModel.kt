package com.example.groupinterpreter.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.groupinterpreter.core.Direction
import com.example.groupinterpreter.core.MessageRules
import com.example.groupinterpreter.data.OnDeviceTranslationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** App rules are enforced in code, not just a prompt. */
data class InterpreterState(
    val source: String = "",
    val translation: String = "",
    val direction: Direction = Direction.AUTO,
    val translating: Boolean = false,
    val downloading: Boolean = false,
    val prepared: Boolean = false,
    val status: String = "พิมพ์ข้อความภาษาไทยหรือจีนเพื่อเริ่มแปล",
    val lastUsed: Direction? = null
)

class InterpreterViewModel : ViewModel() {
    private val engine = OnDeviceTranslationEngine()
    private val mutableState = MutableStateFlow(InterpreterState())
    val state = mutableState.asStateFlow()
    private var work: Job? = null
    private var downloadWork: Job? = null
    private var requestNumber: Long = 0L

    fun updateInput(input: String) {
        invalidateWork()
        mutableState.update {
            it.copy(
                source = input,
                translation = "", // NEVER show translation from a previous message.
                translating = false,
                status = if (input.isBlank()) "พิมพ์ข้อความภาษาไทยหรือจีนเพื่อเริ่มแปล"
                         else "รอข้อความล่าสุด…"
            )
        }
        if (input.isNotBlank()) scheduleTranslation(debounceMs = 850)
    }

    fun setDirection(direction: Direction) {
        invalidateWork()
        mutableState.update {
            it.copy(direction = direction, translation = "", translating = false)
        }
        if (mutableState.value.source.isNotBlank()) scheduleTranslation(debounceMs = 0)
    }

    fun translateNow() {
        invalidateWork()
        if (mutableState.value.source.isNotBlank()) scheduleTranslation(debounceMs = 0)
    }

    fun clear() {
        invalidateWork()
        mutableState.update {
            it.copy(source = "", translation = "", translating = false,
                status = "พิมพ์ข้อความภาษาไทยหรือจีนเพื่อเริ่มแปล")
        }
    }

    fun prepareModels() {
        if (downloadWork?.isActive == true) return
        downloadWork = viewModelScope.launch {
            mutableState.update { it.copy(downloading = true, status = "กำลังดาวน์โหลดโมเดลผ่าน Wi-Fi…") }
            try {
                engine.prepareOnWifi()
                mutableState.update {
                    it.copy(prepared = true, status = "ติดตั้งโมเดลแล้ว • พร้อมใช้งานโดยไม่ต่ออินเทอร์เน็ต")
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(status = "ดาวน์โหลดโมเดลไม่สำเร็จ: ตรวจสอบ Wi-Fi แล้วลองอีกครั้ง")
                }
            } finally {
                mutableState.update { it.copy(downloading = false) }
            }
        }
    }

    private fun invalidateWork() {
        requestNumber += 1
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
            mutableState.update { it.copy(translating = true, status = "กำลังแปล…") }
            try {
                val detected = if (snapshot.direction == Direction.AUTO &&
                    MessageRules.scriptLanguage(parsed.body) == null
                ) engine.identifyLanguage(parsed.body) else null
                val resolved = MessageRules.chooseDirection(snapshot.direction, parsed.body, detected)
                if (resolved == null) {
                    if (generation == requestNumber) {
                        mutableState.update {
                            it.copy(translating = false,
                                status = "ตรวจจับภาษาไม่ชัดเจน โปรดเลือก ไทย → 中文 หรือ 中文 → ไทย")
                        }
                    }
                    return@launch
                }
                val text = engine.translate(parsed.body, resolved)
                // ML Kit tasks may finish after coroutine cancellation. Old translations cannot win.
                if (generation != requestNumber || original != mutableState.value.source) return@launch
                mutableState.update {
                    it.copy(
                        translation = MessageRules.appendTranslation(parsed, text),
                        translating = false,
                        prepared = true,
                        lastUsed = resolved,
                        status = "แปลข้อความล่าสุดแล้ว • ${if (resolved == Direction.TH_TO_ZH) "ไทย → 中文" else "中文 → ไทย"}"
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                if (generation == requestNumber) {
                    mutableState.update {
                        it.copy(
                            translating = false,
                            translation = "",
                            status = "ไม่สามารถแปลได้: โปรดเชื่อมต่ออินเทอร์เน็ตเพื่อโหลดโมเดลครั้งแรก หรือกดเตรียมโมเดล"
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        invalidateWork()
        downloadWork?.cancel()
        engine.close()
        super.onCleared()
    }
}
