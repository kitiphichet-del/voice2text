package com.example.groupinterpreter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.groupinterpreter.core.Direction
import com.example.groupinterpreter.presentation.SimpleInterpreterViewModel
import com.example.groupinterpreter.presentation.SimpleUiState

private val Navy = Color(0xFF234783)
private val Ink = Color(0xFF192C46)
private val Muted = Color(0xFF52647C)
private val Soft = Color(0xFFF3F6FB)

class MainActivity : ComponentActivity() {
    private val model: SimpleInterpreterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Navy, background = Soft, surface = Color.White
            )) {
                val state by model.state.collectAsState()
                SimpleScreen(
                    state = state,
                    onVoice = model::startSpeech,
                    onStop = model::stopSpeech,
                    onDenied = model::microphoneDenied,
                    onPrepare = model::prepareTranslations,
                    onTextToggle = model::toggleText,
                    onTyped = model::updateText,
                    onTranslateTyped = model::translateTyped
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) model.cancelSpeech()
    }
}

@Composable
private fun SimpleScreen(
    state: SimpleUiState,
    onVoice: (Direction) -> Unit,
    onStop: () -> Unit,
    onDenied: () -> Unit,
    onPrepare: () -> Unit,
    onTextToggle: () -> Unit,
    onTyped: (String) -> Unit,
    onTranslateTyped: (Direction) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var pendingDirection by remember { mutableStateOf(Direction.TH_TO_ZH) }
    val microphone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onVoice(pendingDirection) else onDenied()
    }
    fun start(direction: Direction) {
        pendingDirection = direction
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) onVoice(direction) else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Soft) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            Text("ล่ามไทย ⇄ 中文", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("กดปุ่ม • พูดหนึ่งประโยค • ดูคำแปล", fontSize = 15.sp, color = Muted)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { start(Direction.TH_TO_ZH) },
                    enabled = state.translationReady && state.speechAvailable &&
                        (!state.listening || state.activeDirection == Direction.TH_TO_ZH) &&
                        !state.translating,
                    modifier = Modifier.weight(1f).height(112.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("🎙\nพูดไทย\n→ 中文", textAlign = TextAlign.Center,
                        fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { start(Direction.ZH_TO_TH) },
                    enabled = state.translationReady && state.speechAvailable &&
                        (!state.listening || state.activeDirection == Direction.ZH_TO_TH) &&
                        !state.translating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF167A72)),
                    modifier = Modifier.weight(1f).height(112.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("🎙\n说中文\n→ ไทย", textAlign = TextAlign.Center,
                        fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (state.listening) {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text("■  หยุดพูดแล้วแปล", fontSize = 16.sp)
                }
                if (state.preview.isNotBlank()) {
                    Text("ได้ยิน: ${state.preview}", color = Muted, fontSize = 13.sp)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDDE5F0))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("คำแปลล่าสุด", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = Ink,
                            modifier = Modifier.weight(1f))
                        TextButton(
                            enabled = state.translated.isNotBlank(),
                            onClick = { clipboard.setText(AnnotatedString(state.translated)) }
                        ) { Text("คัดลอก") }
                    }
                    SelectionContainer {
                        Text(
                            if (state.translated.isNotBlank()) state.translated
                            else if (state.translating) "กำลังแปลข้อความ…"
                            else "กดปุ่มพูดเพื่อเริ่มแปล",
                            modifier = Modifier.fillMaxWidth()
                                .background(Soft, RoundedCornerShape(12.dp))
                                .padding(vertical = 28.dp, horizontal = 14.dp),
                            color = if (state.translated.isBlank()) Muted else Ink,
                            fontSize = if (state.translated.isBlank()) 16.sp else 23.sp,
                            lineHeight = 33.sp,
                            textAlign = if (state.translated.isBlank()) TextAlign.Center
                                else TextAlign.Start
                        )
                    }
                    if (state.original.isNotBlank()) {
                        Text("ได้ยิน: ${state.original}", fontSize = 12.sp, color = Muted)
                    }
                    Text("Powered by Google Translate", fontSize = 11.sp, color = Muted)
                }
            }

            Surface(
                color = Color(0xFFE8EEF8),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.preparing || state.translating) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp),
                            strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(state.status, color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }

            if (!state.translationReady) {
                Button(
                    onClick = onPrepare,
                    enabled = !state.preparing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("ดาวน์โหลดโมเดลแปลภาษา (Wi-Fi) — ครั้งแรก") }
            } else {
                TextButton(onClick = onPrepare, enabled = !state.preparing) {
                    Text("✓ โมเดลแปลภาษาพร้อมใช้ • ตรวจสอบโมเดล")
                }
            }

            if (!state.speechAvailable) {
                Text(
                    "โทรศัพท์ไม่มีระบบรู้จำเสียงออฟไลน์ของ Android หรือใช้ Android ต่ำกว่า 12 " +
                        "จึงไม่เปิดการแปลเสียงออนไลน์ให้โดยอัตโนมัติ",
                    fontSize = 13.sp, color = Muted, lineHeight = 19.sp
                )
            } else {
                Text(
                    "ใช้ระบบรู้จำเสียงบนเครื่องของ Android เท่านั้น " +
                        "หากไม่มีแพ็กภาษาไทยหรือจีนในเครื่อง จะมีข้อความแจ้งให้ติดตั้ง " +
                        "ไม่มีการอัปโหลดเสียงโดยแอป",
                    fontSize = 12.sp, color = Muted, lineHeight = 19.sp
                )
            }

            TextButton(onClick = onTextToggle, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.textOpen) "ซ่อนช่องพิมพ์" else "พิมพ์ข้อความแทนการพูด")
            }
            if (state.textOpen) {
                OutlinedTextField(
                    value = state.typed,
                    onValueChange = onTyped,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("พิมพ์ข้อความภาษาไทยหรือจีน…") },
                    minLines = 2, maxLines = 5
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = state.translationReady && state.typed.isNotBlank(),
                        onClick = { onTranslateTyped(Direction.TH_TO_ZH) }
                    ) { Text("แปลเป็นจีน") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = state.translationReady && state.typed.isNotBlank(),
                        onClick = { onTranslateTyped(Direction.ZH_TO_TH) }
                    ) { Text("แปลเป็นไทย") }
                }
            }

            Text(
                "V 2.0.0  •  แปลข้อความอย่างเดียว ไม่มีเสียงพูดกลับ",
                color = Muted, fontSize = 11.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
