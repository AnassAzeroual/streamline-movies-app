package com.streamline.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageFilterTest {

    @Test
    fun `all languages matches every title`() {
        assertTrue(LanguageFilter().matches("Any Movie 2026 1080p"))
        assertTrue(LanguageFilter().matches(""))
    }

    @Test
    fun `french audio and subtitle tags are distinguished`() {
        val audio = LanguageFilter(Language.FRENCH, LanguageRole.AUDIO)
        val subs = LanguageFilter(Language.FRENCH, LanguageRole.SUBTITLES)
        val either = LanguageFilter(Language.FRENCH, LanguageRole.ANY)

        assertTrue(audio.matches("The Film 2026 FRENCH 1080p BluRay"))
        assertTrue(audio.matches("The Film 2026 TRUEFRENCH 1080p"))
        assertFalse(subs.matches("The Film 2026 FRENCH 1080p BluRay"))
        assertFalse(audio.matches("The Film 2026 VOSTFR 1080p"))
        assertTrue(subs.matches("The Film 2026 VOSTFR 1080p"))
        assertTrue(subs.matches("The Film 2026 SUBFRENCH 1080p"))
        assertTrue(either.matches("The Film 2026 FRENCH SUB 1080p"))
        assertFalse(audio.matches("The Film 2026 FRENCH SUB 1080p"))
    }

    @Test
    fun `language tags work for other audio tracks`() {
        assertTrue(LanguageFilter(Language.SPANISH, LanguageRole.AUDIO).matches("Film 2026 LATINO 720p"))
        assertTrue(LanguageFilter(Language.HINDI, LanguageRole.AUDIO).matches("Film 2026 HINDI 1080p"))
        assertTrue(LanguageFilter(Language.KOREAN, LanguageRole.AUDIO).matches("Film 2026 KOREAN 1080p"))
        assertTrue(LanguageFilter(Language.CHINESE, LanguageRole.AUDIO).matches("Film 2026 MANDARIN 1080p"))
    }

    @Test
    fun `english subtitle tags do not count as english audio`() {
        assertTrue(LanguageFilter(Language.ENGLISH, LanguageRole.SUBTITLES).matches("Film 2026 ENG SUB 1080p"))
        assertFalse(LanguageFilter(Language.ENGLISH, LanguageRole.AUDIO).matches("Film 2026 ENG SUB 1080p"))
        assertTrue(LanguageFilter(Language.ENGLISH, LanguageRole.AUDIO).matches("Film 2026 ENGLISH 1080p"))
    }

    @Test
    fun `match flags show audio, subtitles, or both`() {
        val french = Language.FRENCH

        val audio = french.match("Film 2026 FRENCH 1080p")
        assertTrue(audio.audio)
        assertFalse(audio.subtitle)

        val subtitles = french.match("Film 2026 VOSTFR 1080p")
        assertFalse(subtitles.audio)
        assertTrue(subtitles.subtitle)

        val both = french.match("Film 2026 FRENCH AUDIO SUBFRENCH 1080p")
        assertTrue(both.audio)
        assertTrue(both.subtitle)
        assertTrue(both.any)
    }

    @Test
    fun `untagged releases do not match a specific language`() {
        assertFalse(LanguageFilter(Language.FRENCH).matches("Film 2026 1080p BluRay"))
        assertTrue(LanguageFilter(Language.FRENCH, LanguageRole.SUBTITLES).matches("Film 2026 MULTISUB 1080p"))
    }
}
