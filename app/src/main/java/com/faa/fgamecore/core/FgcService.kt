package com.faa.fgamecore.core

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FgcService : LifecycleService() {
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        Fgc.init(applicationContext)
        Notifier.ensureChannels(this)
        ServiceCompat.startForeground(
            this, NOTIF_ID, Notifier.daemon(this),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        Fgc.markDaemon(true, android.os.Process.myPid())
        Logs.add("DAEMON", "Daemon started (PID: ${android.os.Process.myPid()})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            Logs.add("DAEMON", "Stop diminta dari notifikasi")
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) {
            job = lifecycleScope.launch(Dispatchers.IO) {
                while (currentCoroutineContext().isActive) {
                    runCatching { Fgc.poll() }
                        .onFailure { Logs.add("CORE_ERROR", "Poll gagal: ${it.message}") }
                    runCatching {
                        ServiceCompat.startForeground(
                            this@FgcService, NOTIF_ID, Notifier.daemon(this@FgcService),
                            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
                        )
                    }
                    delay(Fgc.store.pollSec.coerceIn(1, 30) * 1000L)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        Fgc.markDaemon(false)
        Logs.add("DAEMON", "Daemon berhenti")
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.faa.fgamecore.STOP_DAEMON"
    }
}
