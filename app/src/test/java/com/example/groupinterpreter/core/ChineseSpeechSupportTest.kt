package com.example.groupinterpreter.core

import org.junit.Assert.*
import org.junit.Test

class ChineseSpeechSupportTest {
    @Test fun checksMainlandMandarinVariants() {
        assertEquals("zh-CN", ChineseSpeechSupport.bestMainlandLanguage(listOf("fr-FR", "zh-CN")))
        assertEquals("cmn-Hans-CN", ChineseSpeechSupport.bestMainlandLanguage(listOf("cmn-Hans-CN")))
        assertEquals("zh", ChineseSpeechSupport.bestMainlandLanguage(listOf("zh")))
    }

    @Test fun doesNotMistakeCantoneseOrTaiwanForMandarinMainland() {
        assertNull(ChineseSpeechSupport.bestMainlandLanguage(listOf("yue-HK", "zh-HK", "zh-TW")))
    }

    @Test fun requiresActualChineseCharactersForChineseSpeech() {
        assertTrue(ChineseSpeechSupport.isChineseCharacters("你好"))
        assertTrue(ChineseSpeechSupport.isChineseCharacters("你好 hello"))
        assertFalse(ChineseSpeechSupport.isChineseCharacters("ni hao"))
        assertFalse(ChineseSpeechSupport.isChineseCharacters("nǐ hǎo"))
    }
}
