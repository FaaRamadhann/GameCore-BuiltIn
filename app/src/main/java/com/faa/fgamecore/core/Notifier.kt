package com.faa.fgamecore.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.faa.fgamecore.MainActivity
import com.faa.fgamecore.R

object Notifier {
    const val CH_DAEMON = "fgc-daemon"
    const val CH_ALERTS = "fgc-alerts"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_DAEMON, "Faa GameCore Daemon", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Status daemon Faa GameCore yang berjalan"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Faa GameCore Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Deteksi game, freeze, dan status daemon"
            }
        )
    }

    fun daemon(ctx: Context): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            ctx, 1, Intent(ctx, FgcService::class.java).setAction(FgcService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val session = Fgc.store.sessionGame
        val text = if (session != null) "Sesi: ${Device.label(ctx, session)} • ${Policy.profileName(Fgc.store.sessionProfile)}"
        else "Memantau game… ${Fgc.state.value.games.count { it.enabled }} game terdaftar"
        return NotificationCompat.Builder(ctx, CH_DAEMON)
            .setSmallIcon(R.drawable.ic_stat_fgc)
            .setContentTitle("Faa GameCore Daemon aktif")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stop)
            .build()
    }

    /** Ongoing notification for the floating overlay; closing it stops the overlay. */
    fun overlay(ctx: Context): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val close = PendingIntent.getService(
            ctx, 2, Intent(ctx, OverlayService::class.java).setAction(OverlayService.ACTION_CLOSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(ctx, CH_DAEMON)
            .setSmallIcon(R.drawable.ic_stat_fgc)
            .setContentTitle("Faa GameCore Overlay aktif")
            .setContentText("Panel status melayang di atas game")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Tutup", close)
            .build()
    }

    fun alert(ctx: Context, id: Int, title: String, text: String) {
        if (!ctx.let { NotificationManagerCompatCompat.canPost(it) }) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_fgc)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java)?.notify(id, n) }
    }
}

/** Tiny wrapper so a missing POST_NOTIFICATIONS grant never crashes a notify(). */
object NotificationManagerCompatCompat {
    fun canPost(ctx: Context): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }.getOrDefault(false)
}
