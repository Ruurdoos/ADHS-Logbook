package com.adhs.logbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Titled card grouping related settings rows. Labels passed in are already localized. */
@Composable internal fun SettingsSection(title: String,footer: String? = null,content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,Modifier.padding(horizontal=4.dp).semantics { heading() },color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
        Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(vertical=4.dp),content=content)
        }
        footer?.let { Text(it,Modifier.padding(horizontal=4.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall) }
    }
}

@Composable internal fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha=if(enabled) 1f else 0.38f
    Row(modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        icon?.let { Icon(it,null,tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=alpha)) }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(label,color=MaterialTheme.colorScheme.onSurface.copy(alpha=alpha),style=MaterialTheme.typography.bodyLarge)
            supporting?.let { Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=alpha),style=MaterialTheme.typography.bodySmall) }
        }
        trailing?.invoke()
    }
}

@Composable internal fun SettingsNavRow(label: String,supporting: String? = null,icon: ImageVector? = null,enabled: Boolean = true,onClick: () -> Unit) {
    SettingsRow(label,Modifier.clickable(enabled=enabled,role=Role.Button,onClick=onClick),supporting,icon,enabled) {
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun SettingsSwitchRow(label: String,checked: Boolean,onChange: (Boolean) -> Unit,supporting: String? = null,icon: ImageVector? = null,enabled: Boolean = true) {
    SettingsRow(label,Modifier.toggleable(checked,enabled=enabled,role=Role.Switch,onValueChange=onChange),supporting,icon,enabled) {
        Switch(checked,null,enabled=enabled)
    }
}

@Composable internal fun SettingsDivider() { HorizontalDivider(Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.outlineVariant) }
