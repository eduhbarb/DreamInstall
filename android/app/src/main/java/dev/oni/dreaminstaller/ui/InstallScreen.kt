package dev.oni.dreaminstaller.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.oni.dreaminstaller.AppViewModel
import dev.oni.dreaminstaller.KeyKind
import dev.oni.dreaminstaller.R
import dev.oni.dreaminstaller.core.QueueItem
import dev.oni.dreaminstaller.core.SdVolume
import dev.oni.dreaminstaller.core.Storage
import dev.oni.dreaminstaller.core.TitleKind
import dev.oni.dreaminstaller.core.TitleState
import dev.oni.dreaminstaller.core.formatSize
import dev.oni.dreaminstaller.ui.theme.LocalPalette
import java.io.File
import java.util.Locale
import kotlin.math.max

@Composable
fun InstallScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val keys by vm.keys.collectAsStateWithLifecycle()
    val keyStatus by vm.keyStatus.collectAsStateWithLifecycle()
    val volumes by vm.volumes.collectAsStateWithLifecycle()
    val selectedRoot by vm.selectedRoot.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val layoutError by vm.layoutError.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val install by vm.install.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var granted by remember { mutableStateOf(Storage.isAllFilesGranted(context)) }
    var showVolumes by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = Storage.isAllFilesGranted(context)
                vm.refreshVolumes()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val boot9Launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setKey(KeyKind.Boot9, it) }
    }
    val movableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setKey(KeyKind.Movable, it) }
    }
    val seeddbLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setKey(KeyKind.SeedDb, it) }
    }
    val ciasLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addCias(uris)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.startInstall()
    }

    fun requestInstall() {
        val needsNotification = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotification) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startInstall()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Crescent(40.dp)
                Column {
                    Text(
                        stringResource(R.string.app_name),
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
        }

        if (!granted) {
            item {
                DreamCard {
                    SectionTitle(stringResource(R.string.grant_title))
                    Text(
                        stringResource(R.string.grant_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(palette.soft),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.openAllFilesSettings() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(palette.accent), contentColor = Color(palette.onAccent)),
                    ) { Text(stringResource(R.string.grant_button)) }
                }
            }
        }

        if (install.running) {
            item { ProgressCard(vm = vm, state = install) }
        }
        if (!install.running && install.result != null) {
            item { ResultCard(vm = vm, state = install) }
        }
        if (!install.running && install.error != null) {
            item {
                DreamCard {
                    SectionTitle(stringResource(R.string.crash_title))
                    LineNote(install.error ?: "", ChipTone.Bad, Icons.Filled.Warning)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { vm.dismissResult() }) { Text(stringResource(R.string.ok)) }
                }
            }
        }

        item {
            DreamCard {
                SectionTitle(
                    stringResource(R.string.sd_title),
                    trailing = {
                        TextButton(onClick = { vm.refreshVolumes() }) {
                            Text(stringResource(R.string.sd_refresh), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                )
                if (selectedRoot == null) {
                    Text(
                        stringResource(R.string.sd_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(palette.soft),
                    )
                } else {
                    SdRow(volume = volumes.firstOrNull { it.root == selectedRoot }, root = selectedRoot!!, layoutReady = layout != null)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { showVolumes = true }) {
                            Text(stringResource(R.string.sd_change))
                        }
                    }
                    if (layout == null && layoutError != null) {
                        Spacer(Modifier.height(10.dp))
                        LineNote(layoutError!!, ChipTone.Warn, Icons.Filled.Warning)
                    }
                }
            }
        }

        item {
            DreamCard {
                SectionTitle(
                    stringResource(R.string.keys_title),
                    trailing = {
                        if (selectedRoot != null) {
                            TextButton(onClick = { vm.scanCardForKeys(overwrite = true) }) {
                                Text(stringResource(R.string.keys_scan), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                )
                Text(
                    stringResource(R.string.keys_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(palette.dim),
                )
                Spacer(Modifier.height(10.dp))
                KeyRow(
                    name = "boot9.bin",
                    subtitle = if (keyStatus.error != null) keyStatus.error!! else null,
                    present = keyStatus.boot9,
                    onClick = { boot9Launcher.launch(arrayOf("*/*")) },
                )
                KeyRow(
                    name = "movable.sed",
                    present = keyStatus.movable,
                    onClick = { movableLauncher.launch(arrayOf("*/*")) },
                )
                KeyRow(
                    name = "seeddb.bin",
                    present = keyStatus.seeddb,
                    optional = true,
                    onClick = { seeddbLauncher.launch(arrayOf("*/*")) },
                )
                if (keyStatus.boot9 && keyStatus.movable && !keyStatus.seeddb) {
                    Spacer(Modifier.height(8.dp))
                    LineNote(stringResource(R.string.error_seeddb), ChipTone.Warn, Icons.Filled.Warning)
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (stringResource(R.string.queue_title) + if (queue.isNotEmpty()) " · ${queue.size}" else "").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(palette.dim),
                )
                if (queue.isNotEmpty() && !install.running) {
                    TextButton(onClick = { vm.clearQueue() }) {
                        Text(stringResource(R.string.clear_queue), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (queue.isEmpty()) {
            item {
                DreamCard {
                    Text(
                        stringResource(R.string.queue_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(palette.soft),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { ciasLauncher.launch(arrayOf("*/*")) },
                        enabled = !busy && keys != null,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.add_cias))
                    }
                }
            }
        } else {
            itemsIndexed(queue, key = { _, item -> item.uri }) { index, item ->
                QueueRow(
                    item = item,
                    running = install.running,
                    onRemove = { vm.removeItem(item) },
                )
            }
            item {
                OutlinedButton(
                    onClick = { ciasLauncher.launch(arrayOf("*/*")) },
                    enabled = !busy && keys != null && !install.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    } else {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(stringResource(R.string.add_cias))
                }
            }
            item {
                val total = queue.sumOf { it.installSize }
                val enabled = keys != null && layout != null && !install.running && !busy
                Button(
                    onClick = { requestInstall() },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(palette.accent), contentColor = Color(palette.onAccent)),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        if (queue.size == 1) {
                            stringResource(R.string.install_one, formatSize(total))
                        } else {
                            stringResource(R.string.install_many, queue.size, formatSize(total))
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        item {
            DreamCard {
                SectionTitle(stringResource(R.string.finalize_title))
                Text(
                    stringResource(R.string.finalize_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(palette.soft),
                )
                if (install.result?.finalizeCopied == true) {
                    Spacer(Modifier.height(10.dp))
                    LineNote(stringResource(R.string.finalize_copied), ChipTone.Ok, Icons.Filled.Check)
                }
            }
        }

        if (install.log.isNotEmpty()) {
            item {
                DreamCard {
                    Row(
                        Modifier.fillMaxWidth().clickable { showLog = !showLog },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.log).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(palette.dim),
                        )
                        Icon(
                            if (showLog) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(palette.dim),
                        )
                    }
                    if (showLog) {
                        Column(
                            Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            install.log.takeLast(120).forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color(palette.dim),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showVolumes) {
        AlertDialog(
            onDismissRequest = { showVolumes = false },
            containerColor = Color(palette.card),
            title = { Text(stringResource(R.string.sd_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (volumes.isEmpty()) {
                        Text(stringResource(R.string.sd_none), color = Color(palette.dim))
                    }
                    volumes.forEach { volume ->
                        VolumeRow(
                            volume = volume,
                            selected = volume.root == selectedRoot,
                            onClick = {
                                vm.selectRoot(volume.root)
                                showVolumes = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.refreshVolumes() }) { Text(stringResource(R.string.sd_refresh)) }
            },
            dismissButton = {
                TextButton(onClick = { showVolumes = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun KeyRow(name: String, present: Boolean, optional: Boolean = false, subtitle: String? = null, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE44C3E),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Chip(
            text = when {
                present -> stringResource(R.string.key_ok)
                optional -> stringResource(R.string.key_optional)
                else -> stringResource(R.string.key_missing)
            },
            tone = when {
                present -> ChipTone.Ok
                optional -> ChipTone.Dim
                else -> ChipTone.Warn
            },
        )
        TextButton(onClick = onClick) {
            Text(stringResource(if (present) R.string.replace else R.string.add), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SdRow(volume: SdVolume?, root: File, layoutReady: Boolean) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(palette.track)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.List, contentDescription = null, tint = Color(palette.highlight), modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                volume?.label ?: root.name,
                style = MaterialTheme.typography.titleSmall,
                color = Color(palette.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                root.absolutePath,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(palette.dim),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val free = volume?.freeBytes ?: Storage.freeBytes(root)
            Text(
                if (free > 0) stringResource(R.string.sd_free, formatSize(free)) else stringResource(R.string.sd_free_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = Color(palette.dim),
            )
        }
        Chip(
            text = if (layoutReady) "id1" else "id0",
            tone = if (layoutReady) ChipTone.Ok else ChipTone.Warn,
        )
    }
}

@Composable
private fun VolumeRow(volume: SdVolume, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(if (selected) palette.selected else palette.track))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(volume.label, style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
            Text(volume.root.absolutePath, style = MaterialTheme.typography.bodySmall, color = Color(palette.dim))
            Text(
                stringResource(R.string.sd_free, formatSize(volume.freeBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = Color(palette.dim),
            )
        }
        if (volume.has3ds) Chip(stringResource(R.string.key_ok), ChipTone.Ok)
    }
}

@Composable
private fun QueueRow(item: QueueItem, running: Boolean, onRemove: () -> Unit) {
    val palette = LocalPalette.current
    DreamCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumb(item.icon)
            Column(Modifier.weight(1f)) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(palette.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${kindLabel(item.kind)} · ${item.titleId} · ${formatSize(item.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(palette.dim),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.message != null) {
                    Text(
                        item.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE7B45C),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (item.needsSeed) {
                    Text(
                        stringResource(R.string.error_seed_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE7B45C),
                    )
                }
            }
            when (item.state) {
                TitleState.Waiting -> if (!running) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove), tint = Color(palette.dim))
                    }
                } else {
                    Chip(stringResource(R.string.state_waiting), ChipTone.Dim)
                }
                TitleState.Writing -> Chip(stringResource(R.string.state_writing), ChipTone.Accent)
                TitleState.Finishing -> Chip(stringResource(R.string.state_finishing), ChipTone.Accent)
                TitleState.Done -> Chip(stringResource(R.string.state_done), ChipTone.Ok)
                TitleState.Failed -> Chip(stringResource(R.string.state_failed), ChipTone.Bad)
                TitleState.Skipped -> Chip(stringResource(R.string.state_skipped), ChipTone.Dim)
            }
        }
    }
}

@Composable
private fun ProgressCard(vm: AppViewModel, state: dev.oni.dreaminstaller.core.InstallState) {
    val palette = LocalPalette.current
    val currentTitle = state.titles.getOrNull(state.current)?.name ?: ""
    val overall = (state.overall * 100).toInt()
    val titlePercent = ((state.titles.getOrNull(state.current)?.fraction ?: 0f) * 100).toInt()
    val eta = etaSeconds(state)
    DreamCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.install_running, (state.current + 1).coerceAtLeast(1), state.titles.size),
                style = MaterialTheme.typography.titleMedium,
                color = Color(palette.text),
            )
            if (state.speed > 0) {
                Text(
                    stringResource(R.string.install_speed, formatSize(state.speed)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(palette.dim),
                )
            }
        }
        if (currentTitle.isNotEmpty()) {
            Text(
                currentTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color(palette.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
        }
        ProgressLine(state.overall)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.install_overall, overall) + " · " +
                    stringResource(R.string.install_title_progress, titlePercent),
                style = MaterialTheme.typography.bodySmall,
                color = Color(palette.dim),
            )
            if (eta != null) {
                Text(
                    stringResource(R.string.install_eta, formatDuration(eta)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(palette.dim),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.cancelling) {
            LineNote(stringResource(R.string.cancelling), ChipTone.Warn)
        } else {
            OutlinedButton(onClick = { vm.cancelInstall() }) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.cancel_hint))
            }
        }
    }
}

@Composable
private fun ResultCard(vm: AppViewModel, state: dev.oni.dreaminstaller.core.InstallState) {
    val palette = LocalPalette.current
    val result = state.result ?: return
    DreamCard {
        SectionTitle(stringResource(R.string.done))
        if (result.done.isNotEmpty()) {
            Text(
                stringResource(R.string.result_done, result.done.size),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF54D08A),
            )
        }
        if (result.failed.isNotEmpty()) {
            Text(
                stringResource(R.string.result_failed, result.failed.size),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFE44C3E),
            )
            result.failed.entries.take(6).forEach { (name, reason) ->
                Text(
                    "$name — $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(palette.dim),
                )
            }
        }
        if (result.usedTitleCount >= 300) {
            Spacer(Modifier.height(8.dp))
            LineNote(stringResource(R.string.result_icons, result.usedTitleCount), ChipTone.Warn, Icons.Filled.Warning)
        }
        result.backupDir?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.backup_done, it.absolutePath),
                style = MaterialTheme.typography.bodySmall,
                color = Color(palette.dim),
            )
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { vm.dismissResult() }) { Text(stringResource(R.string.done)) }
    }
}

@Composable
private fun kindLabel(kind: TitleKind): String = stringResource(
    when (kind) {
        TitleKind.Game -> R.string.kind_game
        TitleKind.Update -> R.string.kind_update
        TitleKind.Dlc -> R.string.kind_dlc
        TitleKind.Other -> R.string.kind_other
    },
)

private fun etaSeconds(state: dev.oni.dreaminstaller.core.InstallState): Long? {
    if (!state.running || state.startedAt <= 0L) return null
    val elapsed = (System.currentTimeMillis() - state.startedAt) / 1000f
    val overall = state.overall
    if (overall <= 0.02f) return null
    val total = elapsed / overall
    return max(0L, (total - elapsed).toLong())
}

private fun formatDuration(seconds: Long): String {
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val rest = seconds % 60
    if (minutes < 60) return if (rest > 0) "${minutes}m ${rest}s" else "${minutes}m"
    return String.format(Locale.US, "%dh %02dm", minutes / 60, minutes % 60)
}
