package dev.oni.dreaminstall.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageTagTest {

    @Test
    fun anyPortugueseVariantUsesPortuguese() {
        assertEquals(LANG_PT, languageFor(listOf("pt-BR")))
        assertEquals(LANG_PT, languageFor(listOf("pt-PT")))
        assertEquals(LANG_PT, languageFor(listOf("pt")))
        assertEquals(LANG_PT, languageFor(listOf("en-US", "pt-BR")))
    }

    @Test
    fun everythingElseUsesEnglish() {
        assertEquals(LANG_EN, languageFor(listOf("en-US")))
        assertEquals(LANG_EN, languageFor(listOf("es-ES", "fr-FR")))
        assertEquals(LANG_EN, languageFor(emptyList()))
    }
}
