package com.adhs.logbook

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** App-wide date format: DD.MM.YYYY. */
internal val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

internal fun LocalDate.display(): String = format(dateFormat)

/** Weekday followed by DD.MM.YYYY, e.g. "Montag, 16.11.2026". */
internal fun LocalDate.displayWithWeekday(): String =
    format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.getDefault()))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

internal fun dateTimeText(
    millis: Long,
    clock: DateTimeFormatter,
    zone: ZoneId = ZoneId.systemDefault()
): String = Instant.ofEpochMilli(millis).atZone(zone).let { "${it.format(dateFormat)} ${it.format(clock)}" }

internal fun dateTimeText(millis: Long, context: Context, zone: ZoneId = ZoneId.systemDefault()) =
    dateTimeText(millis, timeFormat(context), zone)
