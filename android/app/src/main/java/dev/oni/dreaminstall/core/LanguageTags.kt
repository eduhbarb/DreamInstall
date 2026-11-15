package dev.oni.dreaminstall.core

const val LANG_PT = "pt"
const val LANG_EN = "en"

/** Any Portuguese variant counts as Portuguese; every other language becomes English. */
fun languageFor(tags: Iterable<String>): String {
    val portuguese = tags.any { tag ->
        val base = tag.substringBefore('-').substringBefore('_')
        base.equals(LANG_PT, ignoreCase = true)
    }
    return if (portuguese) LANG_PT else LANG_EN
}
