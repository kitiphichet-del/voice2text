package com.example.groupinterpreter.data

import com.example.groupinterpreter.core.Direction
import com.example.groupinterpreter.core.Language
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Translation is performed on device by ML Kit. Text is never submitted to a chat model. */
class OnDeviceTranslationEngine {
    private val idClient = LanguageIdentification.getClient()
    private val thaiToChinese = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.THAI)
            .setTargetLanguage(TranslateLanguage.CHINESE)
            .build()
    )
    private val chineseToThai = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.CHINESE)
            .setTargetLanguage(TranslateLanguage.THAI)
            .build()
    )

    /** Download both directions explicitly once, ideally while using Wi-Fi. */
    suspend fun prepareOnWifi() {
        val wifiOnly = DownloadConditions.Builder().requireWifi().build()
        thaiToChinese.downloadModelIfNeeded(wifiOnly).awaitTask()
        chineseToThai.downloadModelIfNeeded(wifiOnly).awaitTask()
    }

    suspend fun identifyLanguage(body: String): Language? {
        val code = idClient.identifyLanguage(body).awaitTask()
        return when {
            code == "th" || code.startsWith("th-") -> Language.THAI
            code == "zh" || code.startsWith("zh-") -> Language.CHINESE
            else -> null
        }
    }

    suspend fun translate(body: String, direction: Direction): String {
        val translator = when (direction) {
            Direction.TH_TO_ZH -> thaiToChinese
            Direction.ZH_TO_TH -> chineseToThai
            Direction.AUTO -> error("Resolve auto direction before translation")
        }
        // No network is needed once both language models have been downloaded.
        // For users who skipped predownload, try model download when translating.
        translator.downloadModelIfNeeded().awaitTask()
        return translator.translate(body).awaitTask()
    }

    fun close() {
        idClient.close()
        thaiToChinese.close()
        chineseToThai.close()
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(value)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener { continuation.cancel() }
}
