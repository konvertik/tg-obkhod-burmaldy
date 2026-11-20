package ru.mtproxymanager.util

/** Скрывает secret в тексте перед записью в журнал. */
object Redactor {
    private val SECRET = Regex("""(secret=)[0-9A-Za-z_\-=]+""", RegexOption.IGNORE_CASE)

    fun redact(text: String): String = text.replace(SECRET) { it.groupValues[1] + "***" }
}
