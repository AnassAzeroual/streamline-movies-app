package com.streamline.app.domain.model

import java.util.Locale

enum class LanguageRole(val label: String, val shortLabel: String) {
    ANY("Audio or subtitles", "Audio/Subs"),
    AUDIO("Audio", "Audio"),
    SUBTITLES("Subtitles", "Subs"),
}





enum class Language(
    val label: String,
    val audioAliases: List<String>,
    val subtitleAliases: List<String> = emptyList(),
) {
    ENGLISH(
        "English",
        listOf("ENGLISH", "ENG"),
        listOf("ENG SUB", "ENG SUBS", "ENGLISH SUB", "ENGLISH SUBS", "ESUB", "ESUBS"),
    ),
    FRENCH(
        "French",
        listOf("FRENCH", "TRUEFRENCH", "VFF", "VF", "VFQ"),
        listOf("VOSTFR", "SUBFRENCH", "SUBFRA", "FRENCH SUB", "FRENCH SUBS"),
    ),
    SPANISH(
        "Spanish",
        listOf("SPANISH", "ESPANOL", "CASTELLANO", "LATINO", "SPA"),
        listOf("SPANISH SUB", "ESP SUB", "SUB ESP"),
    ),
    GERMAN(
        "German",
        listOf("GERMAN", "DEUTSCH", "GER"),
        listOf("GERMAN SUB", "GER SUB", "SUB GER"),
    ),
    ITALIAN(
        "Italian",
        listOf("ITALIAN", "ITA"),
        listOf("ITALIAN SUB", "ITA SUB", "SUB ITA"),
    ),
    PORTUGUESE(
        "Portuguese",
        listOf("PORTUGUESE", "PORTUGUES", "PT BR", "DUBLADO"),
        listOf("PORTUGUESE SUB", "PT SUB", "SUB PT"),
    ),
    HINDI(
        "Hindi",
        listOf("HINDI", "HIN"),
        listOf("HINDI SUB", "HIN SUB"),
    ),
    TAMIL(
        "Tamil",
        listOf("TAMIL"),
        listOf("TAMIL SUB", "SUB TAMIL"),
    ),
    TELUGU(
        "Telugu",
        listOf("TELUGU"),
        listOf("TELUGU SUB", "SUB TELUGU"),
    ),
    KOREAN(
        "Korean",
        listOf("KOREAN", "KOR"),
        listOf("KOREAN SUB", "KOR SUB", "SUB KOR"),
    ),
    JAPANESE(
        "Japanese",
        listOf("JAPANESE", "JAP", "JPN"),
        listOf("JAPANESE SUB", "JAP SUB", "SUB JAP"),
    ),
    RUSSIAN(
        "Russian",
        listOf("RUSSIAN", "RUS"),
        listOf("RUSSIAN SUB", "RUS SUB", "SUB RUS"),
    ),
    ARABIC(
        "Arabic",
        listOf("ARABIC", "ARA"),
        listOf("ARABIC SUB", "ARA SUB", "SUB ARA"),
    ),
    TURKISH(
        "Turkish",
        listOf("TURKISH", "TUR"),
        listOf("TURKISH SUB", "TUR SUB", "SUB TUR"),
    ),
    CHINESE(
        "Chinese",
        listOf("CHINESE", "MANDARIN", "CANTONESE", "CHS", "CHT"),
        listOf("CHINESE SUB", "CHS SUB", "CHT SUB", "SUB CHS", "SUB CHT"),
    ),
    ;

    fun match(title: String): LanguageMatch {
        val normalized = normalize(title)
        if (normalized.isBlank()) return LanguageMatch()
        return LanguageMatch(
            audio = matchesAudio(normalized),
            subtitle = matchesSubtitle(normalized),
        )
    }

    fun matches(title: String, role: LanguageRole): Boolean {
        val match = match(title)
        return when (role) {
            LanguageRole.AUDIO -> match.audio
            LanguageRole.SUBTITLES -> match.subtitle
            LanguageRole.ANY -> match.audio || match.subtitle
        }
    }

    private fun matchesSubtitle(normalized: String): Boolean {
        if (subtitleAliases.any { normalized.hasToken(it) }) return true
        if (audioAliases.any { alias ->
                SUBTITLE_SUFFIXES.any { suffix -> normalized.hasToken(alias + " " + suffix) }
            }
        ) {
            return true
        }
        return GENERIC_SUBTITLE_ALIASES.any { normalized.hasToken(it) }
    }

    private fun matchesAudio(normalized: String): Boolean {
        val audioOnly = stripSubtitlePhrases(normalized)
        return audioAliases.any { audioOnly.hasToken(it) }
    }

    private fun stripSubtitlePhrases(normalized: String): String {
        val phrases = buildList {
            addAll(subtitleAliases)
            audioAliases.forEach { alias ->
                SUBTITLE_SUFFIXES.forEach { suffix -> add(alias + " " + suffix) }
            }
            addAll(GENERIC_SUBTITLE_ALIASES)
        }
        var text = " " + normalized + " "
        phrases.forEach { phrase ->
            val token = normalize(phrase)
            if (token.isNotBlank()) text = text.replace(" " + token + " ", " ")
        }
        return text.trim().replace(Regex("\\s+"), " ")
    }

    private companion object {
        val SUBTITLE_SUFFIXES = listOf("SUB", "SUBS", "SUBTITLE", "SUBTITLES", "SUBBED")
        val GENERIC_SUBTITLE_ALIASES = listOf(
            "MULTISUB",
            "MULTI SUB",
            "MULTI SUBS",
            "MULTI SUBTITLES",
        )
    }
}

data class LanguageMatch(
    val audio: Boolean = false,
    val subtitle: Boolean = false,
) {
    val any: Boolean
        get() = audio || subtitle
}

data class LanguageFilter(
    val language: Language? = null,
    val role: LanguageRole = LanguageRole.ANY,
) {
    val isAll: Boolean
        get() = language == null

    val shortLabel: String
        get() = if (language == null) {
            "All languages"
        } else {
            language.label + " · " + role.shortLabel
        }

    fun match(title: String): LanguageMatch =
        language?.match(title) ?: LanguageMatch()

    fun matches(title: String): Boolean =
        language?.matches(title, role) ?: true
}

private fun normalize(value: String): String =
    value.uppercase(Locale.US)
        .replace(Regex("[^A-Z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

private fun String.hasToken(token: String): Boolean {
    val normalized = normalize(token)
    if (normalized.isBlank()) return false
    return (" " + this + " ").contains(" " + normalized + " ")
}
