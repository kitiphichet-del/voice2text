package com.example.groupinterpreter.core

/** Pure Kotlin, platform independent and testable without the Android SDK. */
enum class Direction { AUTO, TH_TO_ZH, ZH_TO_TH }

enum class Language { THAI, CHINESE }

data class ParsedMessage(
    val prefix: String,
    val body: String
)

object MessageRules {
    // Keep explicitly written speaker labels intact. Do not guess speaker identity.
    // Avoid interpreting time values such as "14:00" as a speaker name.
    private val speakerPattern = Regex(
        pattern = "^([\\p{L}\\p{M} ._\\-]{2,32})([:：])([ \\t]*)([\\s\\S]+)$"
    )
    private val thai = Regex("[\\u0E01-\\u0E5B]")
    private val han = Regex("[\\u3400-\\u9FFF\\uF900-\\uFAFF]")

    fun parse(text: String): ParsedMessage {
        val match = speakerPattern.find(text)
        return if (match != null) {
            val speaker = match.groupValues[1]
            if (speaker.isNotBlank() && speaker.trim().length >= 2) {
                ParsedMessage(
                    match.groupValues[1] + match.groupValues[2] + match.groupValues[3],
                    match.groupValues[4]
                )
            } else ParsedMessage("", text)
        } else ParsedMessage("", text)
    }

    fun scriptLanguage(body: String): Language? {
        val thaiCount = thai.findAll(body).count()
        val hanCount = han.findAll(body).count()
        return when {
            thaiCount > hanCount -> Language.THAI
            hanCount > thaiCount -> Language.CHINESE
            else -> null // Tie or non Thai/Chinese text: let ML Kit try language ID.
        }
    }

    fun chooseDirection(mode: Direction, body: String, detected: Language?): Direction? {
        return when (mode) {
            Direction.TH_TO_ZH -> mode
            Direction.ZH_TO_TH -> mode
            Direction.AUTO -> when (scriptLanguage(body) ?: detected) {
                Language.THAI -> Direction.TH_TO_ZH
                Language.CHINESE -> Direction.ZH_TO_TH
                null -> null
            }
        }
    }

    fun appendTranslation(message: ParsedMessage, result: String): String {
        return message.prefix + result.trim()
    }
}
