package com.faa.fgamecore.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import com.faa.fgamecore.core.Fgc
import com.faa.fgamecore.core.Logs
import com.faa.fgamecore.core.OverlayService
import com.faa.fgamecore.core.Policy
import com.faa.fgamecore.core.ShizukuStatus
import com.faa.fgamecore.core.UiSnapshot
import com.faa.fgamecore.ui.ActionButton
import com.faa.fgamecore.ui.BgDeep
import com.faa.fgamecore.ui.BorderC
import com.faa.fgamecore.ui.CardBg
import com.faa.fgamecore.ui.Cyan
import com.faa.fgamecore.ui.Electric
import com.faa.fgamecore.ui.FgcCard
import com.faa.fgamecore.ui.Green
import com.faa.fgamecore.ui.Muted
import com.faa.fgamecore.ui.Red
import com.faa.fgamecore.ui.ScreenShell
import com.faa.fgamecore.ui.SectionLabel
import com.faa.fgamecore.ui.SettingRow
import com.faa.fgamecore.ui.TextMain
import com.faa.fgamecore.ui.Chevron
import com.faa.fgamecore.ui.Divider
import com.faa.fgamecore.ui.Pill
import com.faa.fgamecore.ui.Yellow

@Composable
fun SettingsScreen(
    state: UiSnapshot,
    onDaemon: () -> Unit,
    onLogs: () -> Unit,
    onNotifications: () -> Unit,
    onProfiles: () -> Unit,
    onLists: () -> Unit,
    onGuide: () -> Unit,
    onAbout: () -> Unit,
    onChanged: () -> Unit,
) {
    ScreenShell(title = "Settings", subtitle = "Preferensi Faa GameCore") {
        FgcCard {
            SettingRow(
                Icons.Rounded.Security, "Shizuku",
                "Status : ${if (state.shizuku == ShizukuStatus.Granted) "Running" else "Stopped"}",
                onClick = onGuide,
            ) { Chevron() }
            Divider()
            SettingRow(
                Icons.Rounded.Bolt, "Daemon",
                "Auto Start : ${if (Fgc.store.autoStart) "On" else "Off"}",
                onClick = onDaemon,
            ) { Chevron() }
            Divider()
            SettingRow(
                Icons.Rounded.Notifications, "Notifikasi",
                "Game Detection : ${if (Fgc.store.notifyGame) "On" else "Off"}",
                onClick = onNotifications,
            ) { Chevron() }
            Divider()
            SettingRow(Icons.Rounded.Palette, "Tampilan", "Tema : Dark") { Pill("DARK", Cyan) }
            Divider()
            SettingRow(Icons.Rounded.Info, "Tentang", "Faa GameCore v1.3.1 (Standalone)", onClick = onAbout) { Chevron() }
        }

        FgcCard {
            SettingRow(Icons.Rounded.Tune, "Profiles", "Profil default: ${Policy.profileName(Fgc.store.defaultProfile)}", onClick = onProfiles) { Chevron() }
            Divider()
            SettingRow(Icons.Rounded.Shield, "Blacklist / Whitelist", "${state.blacklist.size} blacklist • ${state.whitelist.size} whitelist", onClick = onLists) { Chevron() }
            Divider()
            SettingRow(Icons.Rounded.Memory, "Runtime Logs", "Lihat aktivitas daemon", onClick = onLogs) { Chevron() }
        }

        GameLauncherSection(state)
        OverlaySection(state, onChanged)

        FgcCard {
            ToggleRow(
                Icons.Rounded.Settings, "Aktifkan Faa GameCore",
                "Jika off, daemon tidak membersihkan apa pun",
                Fgc.store.enabled,
            ) { v -> Fgc.store.enabled = v; Fgc.refresh(); onChanged() }
            Divider()
            ToggleRow(
                Icons.Rounded.Bolt, "Auto start saat boot",
                "Daemon dijalankan otomatis setelah reboot",
                Fgc.store.autoStart,
            ) { v -> Fgc.store.autoStart = v; Fgc.refresh() }
            Divider()
            ToggleRow(
                Icons.Rounded.Security, "Dry run",
                "Simulasikan tanpa force-stop / freeze",
                Fgc.store.dryRun,
            ) { v -> Fgc.store.dryRun = v; Fgc.refresh() }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("NON-ROOT", Green)
            Pill("SHIZUKU", Electric)
            Pill("v1.3.1", Cyan)
        }
        Text(
            "Faa GameCore tidak memakai root, Magisk, atau akses superuser.",
            style = MaterialTheme.typography.labelSmall, color = Muted,
        )
    }
}

@Composable
private fun GameLauncherSection(state: UiSnapshot) {
    SectionLabel("Game Launcher")
    FgcCard {
        ToggleRow(
            Icons.Rounded.PlayArrow, "Launch game langsung",
            "Tombol PLAY di Game List membuka game lewat launcher intent",
            Fgc.store.launchEnabled,
        ) { v -> Fgc.store.launchEnabled = v; Fgc.refresh() }
        Divider()
        Text(
            if (state.shizuku == ShizukuStatus.Granted)
                "Profil diterapkan sebelum game dibuka. Jika profil gagal, game tetap dibuka."
            else
                "Shizuku belum aktif: game tetap dibuka, profil tidak diterapkan.",
            style = MaterialTheme.typography.labelSmall,
            color = if (state.shizuku == ShizukuStatus.Granted) Muted else Yellow,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun OverlaySection(state: UiSnapshot, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(OverlayService.allowed(ctx)) }
    var askPermission by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle) {
        // Permission can be revoked in system settings while Faa GameCore is in the background.
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                allowed = OverlayService.allowed(ctx)
                if (!allowed && Fgc.store.overlayEnabled) {
                    Fgc.store.overlayEnabled = false
                    Fgc.stopOverlay()
                    Logs.add("OVERLAY_ERROR", "Izin overlay dicabut, overlay dimatikan")
                }
            }
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }

    fun openPermissionPage() {
        runCatching {
            ctx.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Enables the overlay only with a real permission; otherwise explains first. */
    fun setOverlay(on: Boolean) {
        if (!on) {
            Fgc.store.overlayEnabled = false
            Fgc.stopOverlay()
            onChanged()
            return
        }
        allowed = OverlayService.allowed(ctx)
        if (!allowed) { askPermission = true; return }
        Fgc.store.overlayEnabled = true
        Fgc.startOverlay()?.let { msg -> Fgc.store.overlayEnabled = false; Logs.add("OVERLAY_ERROR", msg) }
        onChanged()
    }

    SectionLabel("Floating Overlay")
    FgcCard {
        ToggleRow(
            Icons.Rounded.Layers, "Enable overlay",
            when {
                !allowed -> "Butuh izin 'Tampil di atas aplikasi lain'"
                state.overlay -> "Panel aktif • ${if (Fgc.store.overlayMinimized) "bubble" else "panel"}"
                Fgc.store.overlayEnabled -> "Aktif, panel belum tampil"
                else -> "Panel status Faa GameCore di atas game"
            },
            Fgc.store.overlayEnabled,
        ) { v -> setOverlay(v) }
        Divider()
        ToggleRow(
            Icons.Rounded.Minimize, "Start minimized",
            "Mulai sebagai bubble kecil, tap untuk membuka panel",
            Fgc.store.overlayMinimized,
        ) { v -> Fgc.store.overlayMinimized = v; Fgc.refresh() }
        Divider()
        ToggleRow(
            Icons.Rounded.PlayArrow, "Auto-show on game launch",
            "Tampilkan overlay saat game dibuka dari Faa GameCore (butuh izin)",
            Fgc.store.overlayAutoShow,
        ) { v ->
            if (v && !allowed) askPermission = true
            else { Fgc.store.overlayAutoShow = v; Fgc.refresh() }
        }
        Divider()
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                if (state.overlay) "Sembunyikan" else "Tampilkan",
                onClick = {
                    if (state.overlay) { Fgc.stopOverlay(); Fgc.store.overlayEnabled = false }
                    else setOverlay(true)
                    onChanged()
                },
                accent = if (state.overlay) Red else Cyan,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                "Izin overlay",
                onClick = {
                    // Re-check at click time: the user may have just granted it.
                    allowed = OverlayService.allowed(ctx)
                    if (allowed) setOverlay(true) else askPermission = true
                },
                accent = if (allowed) Green else Yellow,
                modifier = Modifier.weight(1f),
            )
        }
        if (!allowed) {
            Text(
                "Overlay butuh izin sistem agar bisa tampil di atas game.",
                style = MaterialTheme.typography.labelSmall, color = Yellow,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }

    SectionLabel("Overlay behavior")
    FgcCard {
        ToggleRow(Icons.Rounded.Tune, "Show active profile", "Baris profil di panel overlay", Fgc.store.overlayShowProfile) { v ->
            Fgc.store.overlayShowProfile = v; Fgc.refresh()
        }
        Divider()
        ToggleRow(Icons.Rounded.Security, "Show Shizuku status", "Baris status Shizuku di panel overlay", Fgc.store.overlayShowShizuku) { v ->
            Fgc.store.overlayShowShizuku = v; Fgc.refresh()
        }
        Divider()
        ToggleRow(Icons.Rounded.Bolt, "Show Faa GameCore runtime status", "Baris status daemon di panel overlay", Fgc.store.overlayShowStatus) { v ->
            Fgc.store.overlayShowStatus = v; Fgc.refresh()
        }
    }

    if (askPermission) {
        AlertDialog(
            onDismissRequest = { askPermission = false },
            containerColor = BgDeep,
            title = { Text("Izin overlay", color = TextMain) },
            text = {
                Text(
                    "Faa GameCore perlu izin 'Tampil di atas aplikasi lain' supaya panel status bisa " +
                        "terlihat saat game berjalan. Izin ini hanya dipakai untuk panel kecil itu " +
                        "dan bisa dicabut kapan saja di pengaturan Android.",
                    color = Muted, style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { askPermission = false; openPermissionPage() }) {
                    Text("Buka Pengaturan", color = Cyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { askPermission = false }) { Text("Batal", color = Muted) }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Green,
                uncheckedThumbColor = Muted, uncheckedTrackColor = CardBg, uncheckedBorderColor = BorderC,
            ),
        )
    }
}

private data class ProfileDef(
    val id: Int, val name: String, val desc: String, val icon: ImageVector, val color: Color,
)

@Composable
fun ProfilesScreen(state: UiSnapshot, onBack: () -> Unit, onSaved: () -> Unit) {
    var selected by remember { mutableIntStateOf(Fgc.store.defaultProfile) }
    val profiles = listOf(
        ProfileDef(1, "Balance", "Seimbang antara performa dan daya tahan baterai", Icons.Rounded.Balance, Green),
        ProfileDef(2, "Performance", "Performance maksimal (lebih boros daya)", Icons.Rounded.Speed, Cyan),
        ProfileDef(3, "Awaken", "Respons lebih cepat (untuk game tertentu)", Icons.Rounded.Bolt, Red),
        ProfileDef(4, "Auto", "Otomatis menyesuaikan berdasarkan game", Icons.Rounded.AutoAwesome, Yellow),
    )

    ScreenShell(title = "Profiles", subtitle = "Profil default untuk sesi game", onBack = onBack) {
        Text(
            "Profil menentukan tindakan Faa GameCore saat game terdeteksi.",
            style = MaterialTheme.typography.bodyMedium, color = Muted,
        )
        profiles.forEach { p ->
            val active = selected == p.id
            FgcCard(border = if (active) p.color.copy(alpha = 0.75f) else BorderC, onClick = { selected = p.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(p.color.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(p.icon, null, tint = p.color, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, color = TextMain)
                        Text(p.desc, style = MaterialTheme.typography.bodyMedium, color = Muted)
                        Spacer(Modifier.height(4.dp))
                        Text(actionText(p.id), style = MaterialTheme.typography.labelSmall, color = p.color)
                    }
                    Box(
                        Modifier.size(20.dp).clip(CircleShape)
                            .background(if (active) p.color else Color.Transparent)
                            .then(Modifier.padding(2.dp)),
                    ) {
                        if (active) Text("✓", color = Color(0xFF04121A), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
        if (Fgc.store.defaultProfile == 4) {
            Text(
                "Auto: baterai ${state.battery.coerceAtLeast(0)}% → ${Policy.profileName(Policy.resolveProfile(4, state.battery))}",
                style = MaterialTheme.typography.labelSmall, color = Yellow,
            )
        }
        ActionButton(
            "Simpan",
            onClick = { Fgc.store.defaultProfile = selected; Fgc.refresh(); onSaved() },
            filled = true,
            icon = Icons.Rounded.Settings,
        )
    }
}

private fun actionText(id: Int) = when (id) {
    1 -> "Sesi dipantau, tanpa force-stop / freeze"
    2 -> "Force-stop semua target blacklist"
    3 -> "Force-stop + freeze, dipulihkan saat sesi selesai"
    else -> "1–20% Balance • 21–50% Performance • 51%+ Awaken"
}

@Composable
fun NotificationScreen(state: UiSnapshot, onBack: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    ScreenShell(title = "Notifikasi", onBack = onBack) {
        FgcCard {
            ToggleRow(Icons.Rounded.Notifications, "Game Detection", "Notifikasi saat game terdeteksi", Fgc.store.notifyGame) {
                Fgc.store.notifyGame = it; onChanged()
            }
            Divider()
            ToggleRow(Icons.Rounded.Bolt, "Daemon Status", "Notifikasi saat daemon berubah", Fgc.store.notifyDaemon) {
                Fgc.store.notifyDaemon = it; onChanged()
            }
            Divider()
            ToggleRow(Icons.Rounded.Shield, "App Freeze/Unfreeze", "Notifikasi saat aplikasi di-freeze/restore", Fgc.store.notifyFreeze) {
                Fgc.store.notifyFreeze = it; onChanged()
            }
            Divider()
            ToggleRow(Icons.Rounded.Info, "Update", "Notifikasi versi terbaru", Fgc.store.notifyUpdate) {
                Fgc.store.notifyUpdate = it; onChanged()
            }
        }
        val granted = android.os.Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            FgcCard(border = Yellow.copy(alpha = 0.55f)) {
                Text("Izin notifikasi belum diberikan", style = MaterialTheme.typography.titleMedium, color = Yellow)
                Text(
                    "Aktifkan izin notifikasi di pengaturan sistem agar notifikasi Faa GameCore tampil.",
                    style = MaterialTheme.typography.bodyMedium, color = Muted,
                )
            }
        }
        Text(
            "Notifikasi daemon berjalan sebagai foreground service agar monitor tetap hidup.",
            style = MaterialTheme.typography.labelSmall, color = Muted,
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    ScreenShell(title = "Tentang Faa GameCore", onBack = onBack) {
        Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Faa GameCore", color = Cyan, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("Standalone Version", style = MaterialTheme.typography.bodyLarge, color = Green)
        }
        Spacer(Modifier.height(6.dp))
        FgcCard {
            AboutRow("Versi", "1.3.1")
            AboutRow("Package", "com.faa.fgamecore")
            AboutRow("Source", "FaaRamadhann")
            AboutRow("Mode", "Non-root • Shizuku")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LinkText("GitHub") { openUrl(ctx, "https://github.com/FaaRamadhann/Faa-GameCore") }
            LinkText("Telegram") { openUrl(ctx, "https://t.me/faaramadhann") }
            LinkText("Support") { openUrl(ctx, "https://github.com/FaaRamadhann/Faa-GameCore/issues") }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Play Better  •  Stay Focused",
            style = MaterialTheme.typography.bodyMedium, color = Muted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Dibangun dari logika inti Faa GameCore (GPL-3.0).",
            style = MaterialTheme.typography.labelSmall, color = Muted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.width(92.dp))
        Text(":", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextMain, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text, color = Electric, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.clickable { onClick() }.padding(8.dp),
    )
}

private fun openUrl(ctx: android.content.Context, url: String) {
    runCatching {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
