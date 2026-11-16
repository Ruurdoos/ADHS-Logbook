package com.adhs.logbook

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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

@Composable
internal fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick,
        modifier.size(44.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("Back")) }
}

/** Full-width row with label and trailing switch; the whole row toggles and its contents stay vertically centred. */
@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, null, enabled = enabled)
    }
}

/** Full-width row with leading checkbox; the whole row toggles and its contents stay vertically centred. */
@Composable
internal fun CheckRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked, null, enabled = enabled)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}
