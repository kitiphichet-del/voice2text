package com.example.groupinterpreter.core

/**
 * BCP-47 names returned by installed Android speech services vary.
 * Accept Mainland Mandarin and neutral zh/cmn forms, but do not substitute
 * Cantonese or Traditional Chinese locale as if it were zh-CN.
 */
object ChineseSpeechSupport {
    fun bestMainlandLanguage(tags: Collection<String>): String? {
        val normalized = tags.filter { it.isNotBlank() }
        val preferences = listOf(
            "zh-cn", "cmn-hans-cn", "zh-hans-cn", "cmn-cn",
            "zh-hans", "cmn-hans", "zh", "cmn"
        )
        for (wanted in preferences) {
            val found = normalized.firstOrNull {
                it.trim().replace('_', '-').lowercase() == wanted
            }
            if (found != null) return found
        }
        return null
    }

    fun isChineseCharacters(text: String): Boolean {
        return text.any { c ->
            c in '\u3400'..'\u9FFF' || c in '\uF900'..'\uFAFF'
        }
    }
}
