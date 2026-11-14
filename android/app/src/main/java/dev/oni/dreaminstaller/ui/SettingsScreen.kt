package dev.oni.dreaminstaller.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.oni.dreaminstaller.AppViewModel
import dev.oni.dreaminstaller.BuildConfig
import dev.oni.dreaminstaller.R
import dev.oni.dreaminstaller.core.formatSize
import dev.oni.dreaminstaller.ui.theme.ACCENTS
import dev.oni.dreaminstaller.ui.theme.LocalPalette

@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val accent by vm.accent.collectAsStateWithLifecycle()
    val verify by vm.verifyHashes.collectAsStateWithLifecycle()
    val overwrite by vm.overwriteSaves.collectAsStateWithLifecycle()
    val finalize by vm.copyFinalize.collectAsStateWithLifecycle()
    val installFinalizer by vm.installFinalizerTitle.collectAsStateWithLifecycle()
    val status by vm.keyStatus.collectAsStateWithLifecycle()
    var showLicenses by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color(palette.text),
                )
                Text(
                    stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(palette.dim),
                )
            }
        }

        item {
            DreamCard {
                SectionTitle(stringResource(R.string.settings_look))
                Text(
                    stringResource(R.string.settings_accent),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(palette.text),
                )
                Spacer(Modifier.height(12.dp))
                ACCENTS.chunked(4).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        row.forEach { (name, color) ->
                            AccentSwatch(
                                color = color,
                                name = name,
                                selected = color == accent,
                                onClick = { vm.setAccent(color) },
                            )
                        }
                    }
                }
            }
        }

        item {
            DreamCard {
                SectionTitle(stringResource(R.string.settings_behaviour))
                SwitchRow(
                    title = stringResource(R.string.settings_verify),
                    description = stringResource(R.string.settings_verify_desc),
                    checked = verify,
                    onChange = { vm.setVerify(it) },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_overwrite),
                    description = stringResource(R.string.settings_overwrite_desc),
                    checked = overwrite,
                    onChange = { vm.setOverwriteSaves(it) },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_finalize),
                    description = stringResource(R.string.settings_finalize_desc),
                    checked = finalize,
                    onChange = { vm.setCopyFinalize(it) },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_finalizer_title),
                    description = stringResource(R.string.settings_finalizer_title_desc),
                    checked = installFinalizer,
                    onChange = { vm.setInstallFinalizerTitle(it) },
                )
            }
        }

        item {
            DreamCard {
                SectionTitle(stringResource(R.string.settings_keys))
                Text(
                    stringResource(
                        R.string.keys_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(palette.dim),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("boot9.bin", if (status.boot9) ChipTone.Ok else ChipTone.Warn)
                    Chip("movable.sed", if (status.movable) ChipTone.Ok else ChipTone.Warn)
                    Chip("seeddb.bin", if (status.seeddb) ChipTone.Ok else ChipTone.Dim)
                }
                if (status.error != null) {
                    Spacer(Modifier.height(8.dp))
                    LineNote(status.error!!, ChipTone.Bad)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { confirmForget = true },
                    enabled = status.boot9 || status.movable || status.seeddb,
                ) {
                    Text(stringResource(R.string.settings_clear_keys))
                }
            }
        }

        item {
            DreamCard {
                SectionTitle(stringResource(R.string.settings_about))
                Text(
                    stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(palette.soft),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(palette.dim),
                    )
                    TextButton(onClick = { showLicenses = true }) {
                        Text(stringResource(R.string.settings_licenses))
                    }
                }
            }
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            containerColor = Color(palette.card),
            title = { Text(stringResource(R.string.settings_licenses), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.licenses_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(palette.soft),
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
            containerColor = Color(palette.card),
            title = { Text(stringResource(R.string.settings_clear_keys)) },
            text = { Text(stringResource(R.string.keys_hint), color = Color(palette.soft)) },
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
private fun AccentSwatch(color: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(if (selected) 3.dp else 1.dp, if (selected) Color(palette.text) else Color(palette.border), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(palette.onAccent), modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color(palette.text) else Color(palette.dim),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color(palette.dim))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(palette.onAccent),
                checkedTrackColor = Color(palette.accent),
                uncheckedThumbColor = Color(palette.dim),
                uncheckedTrackColor = Color(palette.track),
                uncheckedBorderColor = Color(palette.border),
            ),
        )
    }
}
