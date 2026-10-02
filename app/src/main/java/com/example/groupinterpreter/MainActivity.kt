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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.example.groupinterpreter.presentation.InputMode
import com.example.groupinterpreter.presentation.InterpreterState
import com.example.groupinterpreter.presentation.InterpreterViewModel

private val Indigo = Color(0xFF234783)
private val DeepInk = Color(0xFF192C46)
private val Muted = Color(0xFF53647A)
private val Sky = Color(0xFFE7F1FD)
private val Canvas = Color(0xFFF7F9FC)

class MainActivity : ComponentActivity() {
    private val interpreter: InterpreterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Indigo, onPrimary = Color.White,
                    background = Canvas, surface = Color.White, onSurface = DeepInk
                )
            ) {
                val state by interpreter.state.collectAsState()
                InterpreterScreen(
                    state = state,
                    onText = interpreter::updateInput,
                    onMode = interpreter::setDirection,
                    onTab = interpreter::setInputMode,
                    onTranslate = interpreter::translateNow,
                    onClear = interpreter::clear,
                    onPrepareTranslation = interpreter::prepareModels,
                    onPrepareSpeech = interpreter::prepareSpeechModel,
                    onVoiceStart = interpreter::startVoice,
                    onVoiceStop = interpreter::stopVoice,
                    onPermissionDenied = interpreter::permissionDenied
                )
            }
        }
    }

    // Voice stays alive through rotation (ViewModel retained), but stops on backgrounding.
    // Background mic capture would require a foreground service and notification.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) interpreter.stopVoice()
    }
}

@Composable
private fun InterpreterScreen(
    state: InterpreterState,
    onText: (String) -> Unit,
    onMode: (Direction) -> Unit,
    onTab: (InputMode) -> Unit,
    onTranslate: () -> Unit,
    onClear: () -> Unit,
    onPrepareTranslation: () -> Unit,
    onPrepareSpeech: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onVoiceStart() else onPermissionDenied()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Indigo, shape = RoundedCornerShape(17.dp)) {
                    Text(
                        "文 ⇄ ก",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column {
                    Text("ล่ามไทย–จีน", fontSize = 25.sp, color = DeepInk, fontWeight = FontWeight.Bold)
                    Text("THAI  ↔  简体中文  •  ข้อความและเสียง", fontSize = 12.sp, color = Muted)
                }
            }

            Surface(color = Sky, shape = RoundedCornerShape(14.dp)) {
                Text(
                    "ทำงานบนเครื่องหลังดาวน์โหลดโมเดล • ไม่ส่งเสียงไปยังเซิร์ฟเวอร์ • ไม่ตอบคำถามแทนผู้พูด",
                    modifier = Modifier.padding(14.dp), fontSize = 13.sp,
                    lineHeight = 19.sp, color = Indigo
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.inputMode == InputMode.TEXT) {
                    Button(onClick = { onTab(InputMode.TEXT) }, modifier = Modifier.weight(1f)) {
                        Text("⌨  โหมดข้อความ")
                    }
                    OutlinedButton(onClick = { onTab(InputMode.VOICE) }, modifier = Modifier.weight(1f)) {
                        Text("🎤  โหมดเสียง")
                    }
                } else {
                    OutlinedButton(onClick = { onTab(InputMode.TEXT) }, modifier = Modifier.weight(1f)) {
                        Text("⌨  โหมดข้อความ")
                    }
                    Button(onClick = { onTab(InputMode.VOICE) }, modifier = Modifier.weight(1f)) {
                        Text("🎤  โหมดเสียง")
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ทิศทางการแปล", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DirectionChoice("อัตโนมัติ", Direction.AUTO, state.direction, onMode, Modifier.weight(1f))
                        DirectionChoice("ไทย → 中文", Direction.TH_TO_ZH, state.direction, onMode, Modifier.weight(1.2f))
                        DirectionChoice("中文 → ไทย", Direction.ZH_TO_TH, state.direction, onMode, Modifier.weight(1.2f))
                    }
                    if (state.inputMode == InputMode.VOICE) {
                        Text(
                            "อัตโนมัติอาจตรวจจับภาษาคลาดเคลื่อนเมื่อเสียงสั้นหรือพูดสลับภาษา " +
                                "เลือกทิศทางเองเพื่อบังคับภาษาเสียงต้นฉบับได้",
                            color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                        )
                    }
                }
            }

            if (state.inputMode == InputMode.TEXT) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("01  ข้อความต้นฉบับ", fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = onClear, enabled = state.source.isNotEmpty()) {
                                Text("ล้างข้อความ")
                            }
                        }
                        OutlinedTextField(
                            value = state.source, onValueChange = onText,
                            modifier = Modifier.fillMaxWidth().height(175.dp),
                            placeholder = { Text("พิมพ์หรือวางข้อความ…\nเช่น คุณหวัง: 下午两点出发。") },
                            singleLine = false, shape = RoundedCornerShape(12.dp)
                        )
                        Text("หยุดพิมพ์ประมาณ 1 วินาที ระบบจะแปลให้เอง", fontSize = 12.sp, color = Muted)
                        Button(
                            onClick = onTranslate,
                            enabled = !state.translating && state.source.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)
                        ) { Text("แปลข้อความบนเครื่อง", modifier = Modifier.padding(vertical = 5.dp)) }
                    }
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("01  ฟังเสียงสด", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f))
                            if (state.listening) {
                                Text("● REC", fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp, color = Color(0xFFD42D42))
                            }
                        }
                        Text(
                            "Whisper Tiny ประมวลผลเสียงเป็นช่วงประมาณ 3 วินาที " +
                                "เสียงและคำแปลไม่ถูกส่งไปยังบริการ Cloud",
                            color = Muted, fontSize = 13.sp, lineHeight = 19.sp
                        )
                        if (state.listening) {
                            Button(
                                onClick = onVoiceStop, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp)
                            ) { Text("■  หยุดฟังและถอดเสียงช่วงสุดท้าย") }
                        } else {
                            Button(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED) {
                                        onVoiceStart()
                                    } else {
                                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                enabled = state.speechReady && state.prepared &&
                                    !state.voicePreparing && !state.voiceProcessing,
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)
                            ) {
                                Text(
                                    if (state.voicePreparing) "กำลังโหลดโมเดลเสียง…"
                                    else if (state.voiceProcessing) "กำลังถอดเสียงช่วงสุดท้าย…"
                                    else "🎤  เริ่มฟังและแปลแบบออฟไลน์"
                                )
                            }
                        }

                        Text(
                            "รับเสียง ${state.capturedSeconds} วินาที • ถอดแล้ว ${state.processedSegments} ช่วง" +
                                if (state.processingSegment > state.processedSegments)
                                    " • กำลังถอดช่วงที่ ${state.processingSegment}"
                                else "",
                            color = Indigo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text("ข้อความเสียงล่าสุด", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        SelectionContainer {
                            Text(
                                state.source.ifBlank { "ข้อความที่ฟังได้จะปรากฏตรงนี้" },
                                modifier = Modifier.fillMaxWidth()
                                    .background(Canvas, RoundedCornerShape(11.dp))
                                    .padding(14.dp),
                                color = if (state.source.isBlank()) Muted else DeepInk,
                                fontSize = 17.sp, lineHeight = 25.sp
                            )
                        }
                        OutlinedButton(onClick = onClear, enabled = state.source.isNotBlank() &&
                            !state.listening && !state.voiceProcessing) { Text("ล้างข้อความล่าสุด") }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFD7E2F0)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(17.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("02  คำแปลล่าสุด", fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(state.translation)) },
                            enabled = state.translation.isNotBlank()
                        ) { Text("คัดลอก") }
                    }
                    HorizontalDivider(color = Color(0xFFEDF1F6))
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                            .background(Canvas, RoundedCornerShape(12.dp)).padding(16.dp),
                        contentAlignment = if (state.translation.isBlank()) Alignment.Center
                            else Alignment.TopStart
                    ) {
                        if (state.translating && state.translation.isBlank()) {
                            CircularProgressIndicator(color = Indigo)
                        } else if (state.translation.isNotBlank()) {
                            SelectionContainer {
                                Text(state.translation, color = DeepInk,
                                    fontSize = 20.sp, lineHeight = 31.sp)
                            }
                        } else {
                            Text(
                                "คำแปลล่าสุดจะปรากฏตรงนี้",
                                color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center
                            )
                        }
                    }
                    Text("Powered by Google Translate", fontSize = 11.sp, color = Muted)
                }
            }

            Surface(color = Color(0xFFEEF3F8), shape = RoundedCornerShape(13.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.translating || state.downloading || state.speechDownloading ||
                        state.voicePreparing || state.voiceProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(17.dp).height(17.dp), strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(9.dp))
                    }
                    Text(state.status, modifier = Modifier.weight(1f),
                        fontSize = 12.sp, color = DeepInk, lineHeight = 19.sp)
                }
            }

            OutlinedButton(
                onClick = onPrepareTranslation,
                enabled = !state.downloading && !state.listening,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (state.prepared) "✓  ตรวจสอบโมเดลแปลภาษา (Wi-Fi)"
                    else "1. ดาวน์โหลดโมเดลแปลภาษาไทย–จีน (Wi-Fi)")
            }
            if (state.inputMode == InputMode.VOICE) {
                OutlinedButton(
                    onClick = onPrepareSpeech,
                    enabled = !state.speechDownloading && !state.listening &&
                        !state.voicePreparing && !state.voiceProcessing,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (state.speechReady) "✓  โมเดลเสียง Whisper Tiny พร้อมใช้งาน"
                        else if (state.speechDownloading)
                            "ดาวน์โหลดโมเดลเสียง ${state.speechProgress}%"
                        else "2. ดาวน์โหลดโมเดลเสียงรุ่นเร็ว Whisper Tiny (~32 MB)")
                }
                Text(
                    "โมเดลเสียง Tiny หลายภาษา • ต้องดาวน์โหลดรุ่นเร็วครั้งแรกด้วยอินเทอร์เน็ต " +
                        "• หลังดาวน์โหลดครบ เปิดโหมดเครื่องบินได้ " +
                        "• ไม่บันทึกไฟล์เสียง " +
                        "• เมื่อออกจากแอปหรือปิดจอจะหยุดรับเสียง",
                    fontSize = 11.sp, lineHeight = 18.sp, color = Muted,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    "โมเดลที่ดาวน์โหลดแล้วแปลบนเครื่องได้ • ไม่มีการส่งข้อความไปยังแชตบอต",
                    fontSize = 11.sp, color = Muted,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "V 1.2.0  •  Android 8.0+  •  Offline live chunks",
                fontSize = 11.sp, color = Muted,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DirectionChoice(
    label: String,
    option: Direction,
    active: Direction,
    onClick: (Direction) -> Unit,
    modifier: Modifier
) {
    val selected = option == active
    Surface(
        modifier = modifier.selectable(selected = selected, onClick = { onClick(option) }),
        color = if (selected) Sky else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) Indigo else Color(0xFFDDE5EF))
    ) {
        Text(
            label, fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Indigo else DeepInk,
            textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 2.dp)
        )
    }
}
