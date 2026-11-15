package dev.oni.dreaminstall.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.oni.dreaminstall.AppViewModel
import dev.oni.dreaminstall.KeyKind
import dev.oni.dreaminstall.R
import dev.oni.dreaminstall.core.InstallState
import dev.oni.dreaminstall.core.QueueItem
import dev.oni.dreaminstall.core.SdVolume
import dev.oni.dreaminstall.core.Storage
import dev.oni.dreaminstall.core.TitleKind
import dev.oni.dreaminstall.core.TitleState
import dev.oni.dreaminstall.core.UpdateInfo
import dev.oni.dreaminstall.core.formatSize
import java.io.File
import java.util.Locale
import kotlin.math.max

@Composable
fun InstallScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keys by vm.keys.collectAsStateWithLifecycle()
    val keyStatus by vm.keyStatus.collectAsStateWithLifecycle()
    val volumes by vm.volumes.collectAsStateWithLifecycle()
    val selectedRoot by vm.selectedRoot.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val layoutError by vm.layoutError.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val install by vm.install.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()

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

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = if (queue.isNotEmpty() && !install.running) 92.dp else 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!granted) {
                item { GrantCard(onOpenSettings = vm::openAllFilesSettings) }
            }
            if (install.running) {
                item { ProgressCard(vm = vm, state = install) }
            }
            if (!install.running && install.result != null) {
                item { ResultCard(vm = vm, state = install) }
            }
            if (!install.running && install.error != null) {
                item { ErrorCard(message = install.error ?: "", onDismiss = vm::dismissResult) }
            }

            update?.let { info ->
                item {
                    UpdateBanner(
                        info = info,
                        onDownload = {
                            openUrl(info.apkUrl ?: info.pageUrl)
                            vm.dismissUpdate()
                        },
                        onGithub = {
                            openUrl(info.pageUrl)
                            vm.dismissUpdate()
                        },
                        onLater = vm::dismissUpdate,
                    )
                }
            }

            item {
                KeysCard(
                    status = keyStatus,
                    hasCard = selectedRoot != null,
                    onBoot9 = { boot9Launcher.launch(arrayOf("*/*")) },
                    onMovable = { movableLauncher.launch(arrayOf("*/*")) },
                    onSeeddb = { seeddbLauncher.launch(arrayOf("*/*")) },
                    onScan = { vm.scanCardForKeys(overwrite = true) },
                )
            }

            item {
                SdCard(
                    volume = volumes.firstOrNull { it.root == selectedRoot },
                    root = selectedRoot,
                    layoutReady = layout != null,
                    layoutError = layoutError,
                    onRefresh = vm::refreshVolumes,
                    onChange = { showVolumes = true },
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.queue_title) + if (queue.isNotEmpty()) " · ${queue.size}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (queue.isNotEmpty() && !install.running) {
                        TextButton(onClick = vm::clearQueue) {
                            Text(stringResource(R.string.clear_queue), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (queue.isEmpty()) {
                item {
                    EmptyQueueCard(
                        enabled = !busy && keys != null,
                        onAdd = { ciasLauncher.launch(arrayOf("*/*")) },
                    )
                }
            } else {
                item {
                    QueueCard(queue = queue, running = install.running, onRemove = vm::removeItem)
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
            }

            item {
                FinalizeCard(state = install)
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                if (showLog) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (showLog) {
                            Column(
                                Modifier
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                install.log.takeLast(120).forEach { line ->
                                    Text(
                                        line,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (queue.isNotEmpty() && !install.running) {
            val total = queue.sumOf { it.installSize }
            val enabled = keys != null && layout != null && !busy
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 3.dp,
            ) {
                Button(
                    onClick = { requestInstall() },
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(50.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (queue.size == 1) {
                            stringResource(R.string.install_one, formatSize(total))
                        } else {
                            stringResource(R.string.install_many, queue.size, formatSize(total))
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showVolumes) {
        AlertDialog(
            onDismissRequest = { showVolumes = false },
            title = { Text(stringResource(R.string.sd_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (volumes.isEmpty()) {
                        Text(stringResource(R.string.sd_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                TextButton(onClick = vm::refreshVolumes) { Text(stringResource(R.string.sd_refresh)) }
            },
            dismissButton = {
                TextButton(onClick = { showVolumes = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun GrantCard(onOpenSettings: () -> Unit) {
    DreamCard {
        Text(
            stringResource(R.string.grant_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.grant_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.grant_button)) }
    }
}

@Composable
private fun KeysCard(
    status: dev.oni.dreaminstall.KeyStatus,
    hasCard: Boolean,
    onBoot9: () -> Unit,
    onMovable: () -> Unit,
    onSeeddb: () -> Unit,
    onScan: () -> Unit,
) {
    DreamCard {
        SectionLabel(stringResource(R.string.keys_title)) {
            if (hasCard) {
                TextButton(onClick = onScan) {
                    Text(stringResource(R.string.keys_scan), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeyChip("boot9", status.boot9, optional = false, onClick = onBoot9)
            KeyChip("movable", status.movable, optional = false, onClick = onMovable)
            KeyChip("seeddb", status.seeddb, optional = true, onClick = onSeeddb)
        }
        if (status.error != null) {
            Spacer(Modifier.height(8.dp))
            LineNote(status.error!!, ChipTone.Bad)
        } else if (status.boot9 && status.movable && !status.seeddb) {
            Spacer(Modifier.height(8.dp))
            LineNote(stringResource(R.string.error_seeddb), ChipTone.Warn, Icons.Filled.Warning)
        } else if (!status.boot9 || !status.movable) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.keys_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeyChip(label: String, present: Boolean, optional: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        Chip(
            text = label,
            tone = when {
                present -> ChipTone.Ok
                optional -> ChipTone.Dim
                else -> ChipTone.Warn
            },
            check = present,
        )
    }
}

@Composable
private fun SdCard(
    volume: SdVolume?,
    root: File?,
    layoutReady: Boolean,
    layoutError: String?,
    onRefresh: () -> Unit,
    onChange: () -> Unit,
) {
    DreamCard {
        SectionLabel(stringResource(R.string.sd_title)) {
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.sd_refresh), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (root == null) {
            Text(
                stringResource(R.string.sd_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onChange) { Text(stringResource(R.string.sd_change)) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        volume?.label ?: root.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val free = volume?.freeBytes ?: Storage.freeBytes(root)
                    val freeText = if (free > 0) {
                        stringResource(R.string.sd_free, formatSize(free))
                    } else {
                        stringResource(R.string.sd_free_unknown)
                    }
                    Text(
                        freeText + " · " + stringResource(
                            if (layoutReady) R.string.sd_id0_ok else R.string.sd_id0_missing,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onChange) {
                    Text(stringResource(R.string.sd_change), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (!layoutReady && layoutError != null) {
                Spacer(Modifier.height(8.dp))
                LineNote(layoutError, ChipTone.Warn, Icons.Filled.Warning)
            }
        }
    }
}

@Composable
private fun EmptyQueueCard(enabled: Boolean, onAdd: () -> Unit) {
    DreamCard {
        Text(
            stringResource(R.string.queue_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onAdd, enabled = enabled) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.add_cias))
        }
    }
}

@Composable
private fun QueueCard(queue: List<QueueItem>, running: Boolean, onRemove: (QueueItem) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
            queue.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
                QueueRow(item = item, running = running, onRemove = { onRemove(item) })
            }
        }
    }
}

@Composable
private fun QueueRow(item: QueueItem, running: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Thumb(item.icon)
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${kindLabel(item.kind)} · ${formatSize(item.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Chip(stringResource(R.string.state_waiting), ChipTone.Dim)
            }
            TitleState.Writing -> Chip(stringResource(R.string.state_writing), ChipTone.Accent)
            TitleState.Finishing -> Chip(stringResource(R.string.state_finishing), ChipTone.Accent)
            TitleState.Done -> Chip(stringResource(R.string.state_done), ChipTone.Ok, check = true)
            TitleState.Failed -> Chip(stringResource(R.string.state_failed), ChipTone.Bad)
            TitleState.Skipped -> Chip(stringResource(R.string.state_skipped), ChipTone.Dim)
        }
    }
}

@Composable
private fun ProgressCard(vm: AppViewModel, state: InstallState) {
    val currentTitle = state.titles.getOrNull(state.current)?.name ?: ""
    val overall = (state.overall * 100).toInt()
    val titlePercent = ((state.titles.getOrNull(state.current)?.fraction ?: 0f) * 100).toInt()
    val eta = etaSeconds(state)
    DreamCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.install_running, (state.current + 1).coerceAtLeast(1), state.titles.size),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.speed > 0) {
                Text(
                    stringResource(R.string.install_speed, formatSize(state.speed)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (currentTitle.isNotEmpty()) {
            Text(
                currentTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (eta != null) {
                Text(
                    stringResource(R.string.install_eta, formatDuration(eta)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (state.cancelling) {
            LineNote(stringResource(R.string.cancelling), ChipTone.Warn)
        } else {
            OutlinedButton(onClick = vm::cancelInstall) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.cancel_hint))
            }
        }
    }
}

@Composable
private fun ResultCard(vm: AppViewModel, state: InstallState) {
    val result = state.result ?: return
    DreamCard {
        SectionLabel(stringResource(R.string.done))
        if (result.done.isNotEmpty()) {
            Text(
                stringResource(R.string.result_done, result.done.size),
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF8FE3B0),
            )
        }
        if (result.failed.isNotEmpty()) {
            Text(
                stringResource(R.string.result_failed, result.failed.size),
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFF08A80),
            )
            result.failed.entries.take(6).forEach { (name, reason) ->
                Text(
                    "$name — $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = vm::dismissResult) { Text(stringResource(R.string.done)) }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    DreamCard {
        Text(
            stringResource(R.string.crash_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(6.dp))
        LineNote(message, ChipTone.Bad, Icons.Filled.Warning)
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
    }
}

@Composable
private fun UpdateBanner(
    info: UpdateInfo,
    onDownload: () -> Unit,
    onGithub: () -> Unit,
    onLater: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.update_tag).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.update_available, info.version),
                style = MaterialTheme.typography.titleSmall,
            )
            val notes = info.notes.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            if (!notes.isNullOrEmpty()) {
                Text(
                    notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onDownload) {
                    Text(stringResource(R.string.update_download), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onLater) {
                    Text(stringResource(R.string.update_later), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onGithub) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.update_github),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FinalizeCard(state: InstallState) {
    DreamCard {
        Text(
            stringResource(R.string.finalize_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.finalize_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.result?.let { it.finalizer3dsxCopied || it.finalizerCiaCopied } == true) {
            Spacer(Modifier.height(8.dp))
            LineNote(stringResource(R.string.finalize_copied), ChipTone.Ok, Icons.Filled.Check)
        }
        if (state.result?.finalizerTitleInstalled == true) {
            Spacer(Modifier.height(6.dp))
            LineNote(stringResource(R.string.finalizer_title_installed), ChipTone.Ok, Icons.Filled.Check)
        }
    }
}

@Composable
private fun VolumeRow(volume: SdVolume, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(volume.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    volume.root.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.sd_free, formatSize(volume.freeBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (volume.has3ds) Chip(stringResource(R.string.key_ok), ChipTone.Ok, check = true)
        }
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

private fun etaSeconds(state: InstallState): Long? {
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
