package com.faa.fgamecore.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faa.fgamecore.core.Device
import com.faa.fgamecore.core.Fgc
import com.faa.fgamecore.core.Logs
import com.faa.fgamecore.core.Policy
import com.faa.fgamecore.core.ShizukuStatus
import com.faa.fgamecore.core.UiSnapshot
import com.faa.fgamecore.ui.ActionButton
import com.faa.fgamecore.ui.BorderC
import com.faa.fgamecore.ui.CardBg
import com.faa.fgamecore.ui.Cyan
import com.faa.fgamecore.ui.Electric
import com.faa.fgamecore.ui.FgcCard
import com.faa.fgamecore.ui.Green
import com.faa.fgamecore.ui.Muted
import com.faa.fgamecore.ui.Pill
import com.faa.fgamecore.ui.Red
import com.faa.fgamecore.ui.ScreenShell
import com.faa.fgamecore.ui.StatusDot
import com.faa.fgamecore.ui.TabSwitch
import com.faa.fgamecore.ui.TextMain
import com.faa.fgamecore.ui.Tile
import com.faa.fgamecore.ui.Yellow
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    state: UiSnapshot,
    onGames: () -> Unit,
    onProfiles: () -> Unit,
    onLists: () -> Unit,
    onSettings: () -> Unit,
    onDaemon: () -> Unit,
    onGuide: () -> Unit,
    onStartDaemon: () -> Unit,
) {
    ScreenShell(
        title = "Faa GameCore",
        subtitle = "Non-Root Support",
        headerActions = {
            Pill("v1.3.1", Cyan)
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Rounded.Settings, "Pengaturan", tint = Muted,
                modifier = Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable { onSettings() },
            )
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Rounded.SportsEsports, "Game List", "Manajemen Game", onGames, Modifier.weight(1f))
            Tile(Icons.Rounded.Tune, "Profiles", "Balance / Performance / Awaken / Auto", onProfiles, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Rounded.Shield, "Blacklist / Whitelist", "Aturan Aplikasi", onLists, Modifier.weight(1f))
            Tile(Icons.Rounded.Settings, "Settings", "Preferences", onSettings, Modifier.weight(1f))
        }

        FgcCard {
            StatusDot(
                if (state.shizuku == ShizukuStatus.Granted) Green else Red,
                "Shizuku :",
                if (state.shizuku == ShizukuStatus.Granted) "Running" else "Stopped",
            )
            Spacer(Modifier.height(8.dp))
            StatusDot(if (state.daemon) Green else Red, "Daemon :", if (state.daemon) "Running" else "Stopped")
            if (state.session != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("SESI AKTIF", Green)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${Device.label(com.faa.fgamecore.core.Fgc.app, state.session.game)} • ${Policy.profileName(state.session.profile)}",
                        style = MaterialTheme.typography.bodyMedium, color = TextMain, maxLines = 1,
                    )
                }
            }
            if (state.frozen.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${state.frozen.size} aplikasi ter-freeze (dipulihkan saat sesi selesai)",
                    style = MaterialTheme.typography.labelSmall, color = Yellow,
                )
            }
            Spacer(Modifier.height(14.dp))
            if (state.daemon) {
                ActionButton("Lihat Status Daemon", onDaemon, accent = Cyan, icon = Icons.Rounded.Bolt)
            } else {
                ActionButton(
                    "Start Daemon", onStartDaemon, filled = true,
                    icon = Icons.Rounded.PlayArrow,
                    accent = if (state.shizuku == ShizukuStatus.Granted) Green else Muted,
                    enabled = state.shizuku == ShizukuStatus.Granted,
                )
            }
        }

        if (state.shizuku != ShizukuStatus.Granted) {
            FgcCard(border = Yellow.copy(alpha = 0.5f), onClick = onGuide) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, null, tint = Yellow, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Shizuku belum aktif", style = MaterialTheme.typography.titleMedium, color = Yellow)
                        Text(
                            "Buka panduan untuk mengaktifkan akses non-root.",
                            style = MaterialTheme.typography.labelSmall, color = Muted,
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Baterai: ${if (state.battery >= 0) "${state.battery}%" else "—"}", style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(
                "Foreground: ${state.foreground?.let { Device.label(com.faa.fgamecore.core.Fgc.app, it) } ?: "—"}",
                style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1,
            )
        }
    }
}

@Composable
fun DaemonScreen(state: UiSnapshot, onBack: () -> Unit, onLogs: () -> Unit) {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.daemon) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(1000)
        }
    }
    val uptime = if (state.daemon && state.daemonSince > 0) tick - state.daemonSince else 0L

    ScreenShell(
        title = "Statun Daemon",
        onBack = onBack,
        headerActions = {
            Icon(
                Icons.Rounded.Bolt, "Log", tint = Muted,
                modifier = Modifier.size(22.dp).clickable { onLogs() },
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (state.daemon) Green.copy(alpha = 0.12f) else Red.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Settings, null,
                    tint = if (state.daemon) Green else Red, modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            InfoRow("Daemon", if (state.daemon) "Running" else "Stopped", if (state.daemon) Green else Red)
            InfoRow("PID", if (state.daemon) state.daemonPid.toString() else "—", TextMain)
            InfoRow("Uptime", Device.fmtDuration(uptime), TextMain)
            if (state.session != null) {
                InfoRow("Game", Device.label(Fgc.app, state.session.game), Cyan)
                InfoRow("Profil", Policy.profileName(state.session.profile), Cyan)
            }
            if (state.frozen.isNotEmpty()) InfoRow("Frozen", "${state.frozen.size} paket", Yellow)
        }
        Spacer(Modifier.height(10.dp))
        ActionButton(
            "Stop Daemon",
            onClick = { Fgc.stopDaemon() },
            accent = Red,
            icon = Icons.Rounded.Stop,
            enabled = state.daemon,
        )
        ActionButton(
            "Restart Daemon",
            onClick = { Fgc.restartDaemon() },
            accent = Electric,
            icon = Icons.Rounded.RestartAlt,
            enabled = state.shizuku == ShizukuStatus.Granted,
        )
        if (state.shizuku != ShizukuStatus.Granted) {
            Text(
                "Restart butuh Shizuku aktif. Status: ${state.shizuku.name}",
                style = MaterialTheme.typography.labelSmall, color = Yellow,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.Center) {
        Text("$label : ", style = MaterialTheme.typography.bodyLarge, color = Muted)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val lines by Logs.lines.collectAsStateWithLifecycle()
    var tab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val filtered = if (tab == 0) lines else lines.filter { it.isDaemon }
    val listState = rememberLazyListState()

    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.animateScrollToItem(filtered.lastIndex)
    }

    ScreenShell(title = "Log Faa GameCore", onBack = onBack, scroll = false) {
        TabSwitch(listOf("Runtime", "Daemon"), tab) { tab = it }
        Box(Modifier.weight(1f).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(CardBg)) {
            if (filtered.isEmpty()) {
                Text(
                    "Belum ada log. Jalankan daemon untuk mulai mencatat.",
                    style = MaterialTheme.typography.bodyMedium, color = Muted,
                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    items(filtered) { line ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(
                                "[${line.clock}]", color = Cyan,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                line.text, color = colorFor(line.level),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("Hapus Log", onClick = { Logs.clear() }, accent = Red, icon = Icons.Rounded.Delete, modifier = Modifier.weight(1f))
            ActionButton("Refresh", onClick = { Fgc.refresh() }, accent = Electric, icon = Icons.Rounded.Refresh, modifier = Modifier.weight(1f))
        }
    }
}

private fun colorFor(level: String): Color = when {
    level.contains("FAIL") || level.contains("ERROR") -> Red
    level.contains("SKIP") || level.contains("DEFER") -> Yellow
    level.contains("SUCCESS") || level.contains("ENDED") -> Green
    else -> TextMain
}
