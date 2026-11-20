package ru.mtproxymanager.ui

import ru.mtproxymanager.data.MANUAL_SOURCE
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru")).format(Date(millis))

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("ru")).format(Date(millis))

fun sourceLabel(source: String): String =
    if (source == MANUAL_SOURCE) source else runCatching { URI(source).host }.getOrNull() ?: source
