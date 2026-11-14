package dev.oni.dreaminstaller.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.oni.dreaminstaller.MainActivity
import dev.oni.dreaminstaller.R
import dev.oni.dreaminstaller.core.ByteSource
import dev.oni.dreaminstaller.core.Cia
import dev.oni.dreaminstaller.core.InstallBus
import dev.oni.dreaminstaller.core.InstallCancelled
import dev.oni.dreaminstaller.core.Installer
import dev.oni.dreaminstaller.core.TitleState
import dev.oni.dreaminstaller.core.formatSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InstallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotify = 0L
    private var lastSpeedAt = 0L
    private var lastSpeedBytes = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                if (InstallBus.state.value.running) {
                    InstallBus.cancel.set(true)
                    InstallBus.state.update { it.copy(cancelling = true) }
                } else {
                    stopSelf()
                }
            }
            else -> start()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0f, getString(R.string.app_name), true))
        if (job?.isActive == true) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DreamInstaller:install").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
        job = scope.launch { runInstall() }
    }

    private fun runInstall() {
        val request = InstallBus.request
        if (request == null) {
            finishWithError("pedido de instalação ausente")
            return
        }
        val opened = ArrayList<Cia>()
        try {
            val installer = Installer(
                context = this,
                keys = request.keys,
                keyFiles = request.keyFiles,
                layout = request.layout,
                verifyHashes = request.verifyHashes,
                overwriteSaves = request.overwriteSaves,
                copyFinalizeProgram = request.copyFinalize,
                installFinalizerTitle = request.installFinalizerTitle,
                log = { appendLog(it) },
                onTitleState = { index, state, message -> updateTitle(index, state, message) },
                onBytes = { index, written, total ->
                    val now = System.currentTimeMillis()
                    if (lastSpeedAt == 0L) {
                        lastSpeedAt = now
                        lastSpeedBytes = 0
                    }
                    val titles = InstallBus.state.value.titles.toMutableList()
                    if (index in titles.indices) {
                        titles[index] = titles[index].copy(written = written, total = total)
                    }
                    var speed = 0L
                    val dt = now - lastSpeedAt
                    if (dt >= 900) {
                        val cumulative = titles.sumOf { it.written }
                        speed = ((cumulative - lastSpeedBytes) * 1000 / dt).coerceAtLeast(0)
                        lastSpeedAt = now
                        lastSpeedBytes = cumulative
                    }
                    InstallBus.state.update { it.copy(titles = titles, speed = if (speed > 0) speed else it.speed) }
                    notifyProgress()
                },
                isCancelled = { InstallBus.cancel.get() },
            )

            request.titles.forEach { item ->
                val source = ByteSource.fromUri(this, Uri.parse(item.uri))
                opened.add(Cia.open(source, request.keys))
            }

            val result = installer.run(opened)
            InstallBus.state.update {
                it.copy(
                    running = false,
                    cancelling = false,
                    result = result,
                    endedAt = System.currentTimeMillis(),
                    speed = 0,
                )
            }
            notifyDone(result.done.isNotEmpty())
        } catch (cancelled: InstallCancelled) {
            InstallBus.state.update {
                it.copy(running = false, cancelling = false, endedAt = System.currentTimeMillis(), speed = 0)
            }
            appendLog("cancelado")
        } catch (e: Exception) {
            finishWithError(e.message ?: e.javaClass.simpleName)
        } finally {
            opened.forEach { runCatching { it.close() } }
            InstallBus.request = null
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateTitle(index: Int, state: TitleState, message: String?) {
        val titles = InstallBus.state.value.titles.toMutableList()
        if (index !in titles.indices) return
        val old = titles[index]
        titles[index] = old.copy(
            state = state,
            message = message,
            written = if (state == TitleState.Done) old.total else old.written,
        )
        val current = when (state) {
            TitleState.Writing, TitleState.Finishing -> index
            else -> InstallBus.state.value.current
        }
        InstallBus.state.update { it.copy(titles = titles, current = current) }
        notifyProgress()
    }

    private fun appendLog(line: String) {
        InstallBus.state.update { state ->
            val log = (state.log + line).takeLast(400)
            state.copy(log = log)
        }
    }

    private fun finishWithError(message: String) {
        InstallBus.state.update {
            it.copy(running = false, cancelling = false, error = message, endedAt = System.currentTimeMillis(), speed = 0)
        }
        appendLog("erro: $message")
    }

    private fun notificationText(): String {
        val state = InstallBus.state.value
        val total = state.titles.size
        val index = (state.current + 1).coerceAtLeast(1)
        return getString(R.string.notification_text, index, total) + " · " + formatSize(state.speed) + "/s"
    }

    private fun notifyProgress() {
        val now = System.currentTimeMillis()
        if (now - lastNotify < 700) return
        lastNotify = now
        val state = InstallBus.state.value
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(state.overall, notificationText(), false))
    }

    private fun notifyDone(anyDone: Boolean) {
        val text = if (anyDone) getString(R.string.notification_done) else getString(R.string.app_name)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(1f, text, false))
    }

    private fun buildNotification(progress: Float, text: String, indeterminate: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this, 1, Intent(this, InstallService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setColor(0xFFA855F7.toInt())
            .setContentIntent(pending)
            .setOngoing(InstallBus.state.value.running)
            .setOnlyAlertOnce(true)
            .setProgress(1000, (progress * 1000).toInt(), indeterminate)
            .addAction(0, getString(R.string.cancel), cancelIntent)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "install"
        private const val NOTIFICATION_ID = 41
        const val ACTION_START = "dev.oni.dreaminstaller.INSTALL"
        const val ACTION_CANCEL = "dev.oni.dreaminstaller.CANCEL"

        fun start(context: Context) {
            val intent = Intent(context, InstallService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, InstallService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
