package dev.oni.dreaminstall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.oni.dreaminstall.AppViewModel
import dev.oni.dreaminstall.BuildConfig
import dev.oni.dreaminstall.Language
import dev.oni.dreaminstall.R
import dev.oni.dreaminstall.core.isNewer
import dev.oni.dreaminstall.ui.theme.ACCENTS

@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val accent by vm.accent.collectAsStateWithLifecycle()
    val verify by vm.verifyHashes.collectAsStateWithLifecycle()
    val overwrite by vm.overwriteSaves.collectAsStateWithLifecycle()
    val finalize by vm.copyFinalize.collectAsStateWithLifecycle()
    val installFinalizer by vm.installFinalizerTitle.collectAsStateWithLifecycle()
    val status by vm.keyStatus.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val autoUpdate by vm.autoUpdate.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val latest by vm.lastRelease.collectAsStateWithLifecycle()
    val checking by vm.checkingUpdate.collectAsStateWithLifecycle()
    val checked by vm.updateChecked.collectAsStateWithLifecycle()
    var showLicenses by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            DreamCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
                    LanguageToggle(current = language, onPick = vm::setLanguage)
                }
            }
        }

        item {
            DreamCard {
                SectionLabel(stringResource(R.string.settings_look))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCENTS.forEach { (name, color) ->
                        AccentDot(
                            color = color,
                            name = name,
                            selected = color == accent,
                            onClick = { vm.setAccent(color) },
                        )
                    }
                }
            }
        }

        item {
            DreamCard {
                SectionLabel(stringResource(R.string.settings_behaviour))
                SettingSwitch(
                    title = stringResource(R.string.settings_verify),
                    description = stringResource(R.string.settings_verify_desc),
                    checked = verify,
                    onChange = vm::setVerify,
                )
                SettingSwitch(
                    title = stringResource(R.string.settings_overwrite),
                    description = stringResource(R.string.settings_overwrite_desc),
                    checked = overwrite,
                    onChange = vm::setOverwriteSaves,
                )
                SettingSwitch(
                    title = stringResource(R.string.settings_finalize),
                    description = stringResource(R.string.settings_finalize_desc),
                    checked = finalize,
                    onChange = vm::setCopyFinalize,
                )
                SettingSwitch(
                    title = stringResource(R.string.settings_finalizer_title),
                    description = stringResource(R.string.settings_finalizer_title_desc),
                    checked = installFinalizer,
                    onChange = vm::setInstallFinalizerTitle,
                )
            }
        }

        item {
            DreamCard {
                SectionLabel(stringResource(R.string.settings_updates))
                SettingSwitch(
                    title = stringResource(R.string.settings_update_auto),
                    description = stringResource(R.string.settings_update_auto_desc),
                    checked = autoUpdate,
                    onChange = vm::setAutoUpdate,
                    divider = false,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val available = update?.version
                        ?: latest?.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }?.version
                    val statusText = when {
                        checking -> stringResource(R.string.update_checking)
                        available != null -> stringResource(R.string.update_available, available)
                        checked -> stringResource(R.string.update_latest_short, BuildConfig.VERSION_NAME)
                        else -> stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (checking) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.checkUpdates(manual = true) }, enabled = !checking) {
                        Text(stringResource(R.string.update_check_now), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            DreamCard {
                SectionLabel(stringResource(R.string.settings_keys))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("boot9", if (status.boot9) ChipTone.Ok else ChipTone.Warn, check = status.boot9)
                    Chip("movable", if (status.movable) ChipTone.Ok else ChipTone.Warn, check = status.movable)
                    Chip("seeddb", if (status.seeddb) ChipTone.Ok else ChipTone.Dim, check = status.seeddb)
                }
                if (status.error != null) {
                    Spacer(Modifier.height(8.dp))
                    LineNote(status.error!!, ChipTone.Bad)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = { confirmForget = true },
                        enabled = status.boot9 || status.movable || status.seeddb,
                    ) {
                        Text(stringResource(R.string.settings_clear_keys))
                    }
                }
            }
        }

        item {
            DreamCard {
                SectionLabel(stringResource(R.string.settings_about))
                Text(
                    stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { showLicenses = true }) {
                        Text(stringResource(R.string.settings_licenses), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Column(Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.licenses_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.settings_clear_keys)) },
            text = { Text(stringResource(R.string.keys_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearKeys()
                    confirmForget = false
                }) { Text(stringResource(R.string.settings_clear_keys)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun LanguageToggle(current: String, onPick: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
    ) {
        Row(Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(Language.PT to "PT", Language.EN to "EN").forEach { (code, label) ->
                val selected = current == code
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onPick(code) },
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentDot(color: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(
        when (name) {
            "Purple" -> R.string.accent_purple
            "Indigo" -> R.string.accent_indigo
            "Blue" -> R.string.accent_blue
            "Teal" -> R.string.accent_teal
            "Green" -> R.string.accent_green
            "Amber" -> R.string.accent_amber
            "Orange" -> R.string.accent_orange
            "Pink" -> R.string.accent_pink
            else -> R.string.accent_purple
        },
    )
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = label,
                tint = Color(0xFF14061F),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    divider: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
    if (divider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
    }
}
