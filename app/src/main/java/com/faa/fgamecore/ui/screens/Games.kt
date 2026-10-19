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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.faa.fgamecore.core.AppEntry
import com.faa.fgamecore.core.Device
import com.faa.fgamecore.core.Fgc
import com.faa.fgamecore.core.Game
import com.faa.fgamecore.core.LaunchResult
import com.faa.fgamecore.core.Launcher
import com.faa.fgamecore.core.Logs
import com.faa.fgamecore.core.Policy
import com.faa.fgamecore.core.UiSnapshot
import com.faa.fgamecore.ui.ActionButton
import com.faa.fgamecore.ui.AppIcon
import com.faa.fgamecore.ui.BgDeep
import com.faa.fgamecore.ui.BorderC
import com.faa.fgamecore.ui.CardBg
import com.faa.fgamecore.ui.Cyan
import com.faa.fgamecore.ui.Electric
import com.faa.fgamecore.ui.EmptyState
import com.faa.fgamecore.ui.FgcCard
import com.faa.fgamecore.ui.FgcTextField
import com.faa.fgamecore.ui.Green
import com.faa.fgamecore.ui.Muted
import com.faa.fgamecore.ui.Pill
import com.faa.fgamecore.ui.Red
import com.faa.fgamecore.ui.ScreenShell
import com.faa.fgamecore.ui.SearchField
import com.faa.fgamecore.ui.TabSwitch
import com.faa.fgamecore.ui.TextMain
import com.faa.fgamecore.ui.Yellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object AppCatalog {
    var cached: List<AppEntry>? = null
    fun get(ctx: android.content.Context): List<AppEntry> =
        cached ?: Device.installedApps(ctx).also { cached = it }
    fun invalidate() { cached = null }
}

@Composable
private fun rememberApps(): List<AppEntry> {
    val ctx = LocalContext.current
    val apps by produceState(initialValue = AppCatalog.cached ?: emptyList()) {
        if (AppCatalog.cached == null) {
            value = withContext(Dispatchers.IO) { AppCatalog.get(ctx) }
        }
    }
    return apps
}

@Composable
fun GameListScreen(
    state: UiSnapshot,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var launching by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var missing by remember { mutableStateOf<Game?>(null) }
    val games = state.games.filter {
        query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)
    }

    fun doLaunch(g: Game) {
        if (launching != null) return // double-tap guard: one launch at a time
        if (!Launcher.installed(ctx, g.pkg)) {
            missing = g
            return
        }
        launching = g.pkg
        feedback = "Menyiapkan ${g.label}…" to true
        scope.launch {
            // Profile work is optional; a Shizuku failure must not block the game.
            val prepError = runCatching { Fgc.prepareLaunch(g.pkg) }.getOrNull()
            val result = Launcher.start(ctx, g.pkg)
            val text = when (result) {
                is LaunchResult.Started -> {
                    val verified = Launcher.verifyForeground(g.pkg)
                    when (verified) {
                        true -> "${g.label} berjalan"
                        false -> "${g.label} diluncurkan, belum terlihat di foreground"
                        null -> "${g.label} diluncurkan"
                    }
                }
                LaunchResult.NotInstalled -> "${g.label} tidak terpasang"
                LaunchResult.NoLauncher -> "${g.label} tidak punya launcher activity"
                is LaunchResult.Failed -> "Gagal membuka ${g.label}: ${result.reason}"
            }
            if (result !is LaunchResult.Started) Fgc.abortLaunch(g.pkg)
            Logs.add(if (result is LaunchResult.Started) "LAUNCH" else "LAUNCH_FAILED", text)
            feedback = (if (prepError != null) "$text • profil: $prepError" else text) to (result is LaunchResult.Started)
            if (result is LaunchResult.Started && Fgc.store.overlayAutoShow &&
                Fgc.store.overlayEnabled && !Fgc.state.value.overlay && Fgc.overlayAllowed()
            ) Fgc.startOverlay()
            launching = null
            onChanged()
        }
    }

    ScreenShell(
        title = "Game List",
        subtitle = "${state.games.count { it.enabled }} aktif dari ${state.games.size} game",
        onBack = onBack,
        scroll = false,
        headerActions = {
            Icon(
                Icons.Rounded.AddCircleOutline, "Tambah game", tint = Green,
                modifier = Modifier.size(24.dp).clickable { onAdd() },
            )
        },
    ) {
        SearchField(query, { query = it })
        feedback?.let { (msg, ok) ->
            FgcCard(border = (if (ok) Green else Yellow).copy(alpha = 0.5f)) {
                Text(
                    msg, style = MaterialTheme.typography.bodyMedium,
                    color = if (ok) Green else Yellow,
                )
            }
        }
        if (state.games.isEmpty()) {
            EmptyState(
                Icons.Rounded.SportsEsports, "Belum ada game",
                "Tambahkan game yang ingin dipantau. Faa GameCore hanya aktif saat game berjalan.",
            )
            ActionButton("Tambah Game", onAdd, icon = Icons.Rounded.Add, filled = true)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(games, key = { it.pkg }) { g ->
                    GameRow(
                        game = g,
                        launching = launching == g.pkg,
                        onToggle = { enabled ->
                            Fgc.store.games = Fgc.store.games.map { if (it.pkg == g.pkg) it.copy(enabled = enabled) else it }
                            Fgc.refresh()
                            onChanged()
                        },
                        onLaunch = { doLaunch(g) },
                    )
                }
            }
            ActionButton("Tambah Game", onAdd, icon = Icons.Rounded.Add, accent = Green)
        }
    }

    missing?.let { g ->
        MissingGameDialog(
            game = g,
            onDismiss = { missing = null },
            onRemove = {
                Fgc.store.games = Fgc.store.games.filterNot { it.pkg == g.pkg }
                Fgc.refresh()
                feedback = "${g.label} dihapus dari Game List (aplikasi tidak di-uninstall)" to true
                missing = null
                onChanged()
            },
        )
    }
}

/** Shown when the registered package is gone: the only honest options are remove or cancel. */
@Composable
private fun MissingGameDialog(game: Game, onDismiss: () -> Unit, onRemove: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp), color = BgDeep,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderC),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Aplikasi tidak terpasang", style = MaterialTheme.typography.titleLarge, color = TextMain)
                Text(
                    "${game.label} (${game.pkg}) tidak ada di perangkat ini. Entri bisa dihapus " +
                        "dari Game List; aplikasi tidak di-uninstall.",
                    style = MaterialTheme.typography.bodyMedium, color = Muted,
                )
                ActionButton("Hapus dari Game List", onRemove, accent = Red)
                ActionButton("Batal", onDismiss, accent = Muted)
            }
        }
    }
}

@Composable
private fun GameRow(
    game: Game,
    launching: Boolean,
    onToggle: (Boolean) -> Unit,
    onLaunch: () -> Unit,
) {
    var showOptions by remember { mutableStateOf(false) }
    FgcCard(onClick = { showOptions = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(game.pkg, 42.dp, game.label)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    game.label, style = MaterialTheme.typography.titleMedium, color = TextMain,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    game.pkg, style = MaterialTheme.typography.labelSmall, color = Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(if (game.profile > 0) Policy.profileName(game.profile) else "Default", Cyan)
                    if (!game.enabled) {
                        Spacer(Modifier.width(6.dp))
                        Pill("OFF", Muted)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = game.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Green,
                        uncheckedThumbColor = Muted,
                        uncheckedTrackColor = CardBg,
                        uncheckedBorderColor = BorderC,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                if (Fgc.store.launchEnabled) PlayButton(launching = launching, onClick = onLaunch)
            }
        }
    }
    if (showOptions) {
        GameOptionsDialog(
            game = game,
            onDismiss = { showOptions = false },
            onProfile = { p ->
                Fgc.store.games = Fgc.store.games.map { if (it.pkg == game.pkg) it.copy(profile = p) else it }
                Fgc.refresh(); showOptions = false
            },
            onToggle = { onToggle(it) },
            onRemove = {
                Fgc.store.games = Fgc.store.games.filterNot { it.pkg == game.pkg }
                Fgc.refresh(); showOptions = false
            },
            onLaunch = { showOptions = false; onLaunch() },
        )
    }
}

/** Compact PLAY target; shows progress and refuses a second tap while launching. */
@Composable
private fun PlayButton(launching: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (launching) CardBg else Green.copy(alpha = 0.16f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (launching) BorderC else Green.copy(alpha = 0.8f)),
        modifier = Modifier.clickable(enabled = !launching) { onClick() },
    ) {
        Text(
            if (launching) "…" else "PLAY",
            color = if (launching) Muted else Green,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GameOptionsDialog(
    game: Game,
    onDismiss: () -> Unit,
    onProfile: (Int) -> Unit,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onLaunch: () -> Unit,
) {
    val ctx = LocalContext.current
    val installed = remember(game.pkg) { Launcher.installed(ctx, game.pkg) }
    val canLaunch = remember(game.pkg) { Launcher.canLaunch(ctx, game.pkg) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp), color = BgDeep,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderC),
        ) {
            Column(
                Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(game.label, style = MaterialTheme.typography.titleLarge, color = TextMain)
                Text(game.pkg, style = MaterialTheme.typography.labelSmall, color = Muted)
                Text(
                    when {
                        !installed -> "Tidak terpasang di perangkat"
                        !canLaunch -> "Terpasang, tapi tidak punya launcher activity"
                        else -> "Terpasang • bisa diluncurkan"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (installed && canLaunch) Green else Yellow,
                )

                ActionButton(
                    if (canLaunch) "Buka Game" else "Tidak bisa dibuka",
                    onLaunch,
                    icon = Icons.Rounded.PlayArrow,
                    enabled = canLaunch,
                )

                Spacer(Modifier.height(4.dp))
                Text("Profil khusus game", style = MaterialTheme.typography.bodyMedium, color = Muted)
                listOf(0, 1, 2, 3, 4).forEach { p ->
                    val selected = game.profile == p
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Cyan.copy(alpha = 0.12f) else CardBg)
                            .clickable { onProfile(p) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (p == 0) "Ikuti profil default" else "${Policy.profileName(p)} ($p)",
                            color = if (selected) Cyan else TextMain,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) Text("✓", color = Cyan)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Daftar aplikasi", style = MaterialTheme.typography.bodyMedium, color = Muted)
                val inWhite = game.pkg in Fgc.store.whitelist
                val inBlack = game.pkg in Fgc.store.blacklist
                ListToggle("Whitelist (tidak pernah dibekukan)", inWhite, Cyan) { on ->
                    Fgc.store.whitelist = if (on) Fgc.store.whitelist + game.pkg else Fgc.store.whitelist - game.pkg
                    if (on) Fgc.store.blacklist = Fgc.store.blacklist - game.pkg
                    Fgc.refresh()
                }
                ListToggle("Blacklist (dibersihkan saat sesi)", inBlack, Yellow) { on ->
                    Fgc.store.blacklist = if (on) Fgc.store.blacklist + game.pkg else Fgc.store.blacklist - game.pkg
                    if (on) Fgc.store.whitelist = Fgc.store.whitelist - game.pkg
                    Fgc.refresh()
                }

                Spacer(Modifier.height(4.dp))
                ListToggle("Game aktif dipantau", game.enabled, Green) { onToggle(it) }

                ActionButton("Hapus dari Game List", onRemove, accent = Red)
                ActionButton("Tutup", onDismiss, accent = Muted)
            }
        }
    }
}

@Composable
private fun ListToggle(label: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, color = TextMain, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = CardBg,
                uncheckedBorderColor = BorderC,
            ),
        )
    }
}


@Composable
fun AddGameScreen(onBack: () -> Unit, onSaved: (String) -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val apps = rememberApps()

    ScreenShell(title = "Tambah Game", onBack = onBack) {
        TabSwitch(listOf("Manual", "Auto Detect"), tab) { tab = it }

        if (tab == 0) {
            FgcTextField(name, { name = it }, "Nama Aplikasi", "Contoh: Mobile Legends")
            FgcTextField(pkg, { pkg = it.trim() }, "Package Name", "Contoh: com.mobile.legends")

            Text("Pilih Icon (Opsional)", style = MaterialTheme.typography.bodyMedium, color = TextMain)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .clickable { showPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (pkg.isNotBlank()) AppIcon(pkg, 64.dp, name.ifBlank { pkg })
                    else Icon(Icons.Rounded.Image, null, tint = Muted, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Klik untuk pilih icon\natau ambil dari APK",
                    style = MaterialTheme.typography.labelSmall, color = Muted,
                )
            }

            error?.let { Text(it, color = Red, style = MaterialTheme.typography.bodyMedium) }

            ActionButton(
                "Simpan",
                onClick = {
                    val p = pkg.trim()
                    error = when {
                        !Policy.validPkg(p) -> "Package name tidak valid"
                        runCatching { ctx.packageManager.getApplicationInfo(p, 0) }.isFailure ->
                            "Aplikasi $p tidak terpasang di perangkat"
                        Fgc.store.games.any { it.pkg == p } -> "Game sudah ada di daftar"
                        else -> null
                    }
                    if (error == null) {
                        val label = name.trim().ifBlank { Device.label(ctx, p) }
                        Fgc.store.games = Fgc.store.games + Game(p, label)
                        Fgc.refresh()
                        onSaved("$label ditambahkan")
                    }
                },
                filled = true,
                icon = Icons.Rounded.Add,
            )
            Text(
                "Game manual juga harus terpasang agar ikon dan labelnya akurat.",
                style = MaterialTheme.typography.labelSmall, color = Muted,
            )
        } else {
            SearchField(query, { query = it }, "Cari aplikasi terpasang…")
            val list = apps.filter {
                it.pkg !in Fgc.store.games.map { g -> g.pkg } &&
                    (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true))
            }
            if (list.isEmpty()) {
                EmptyState(Icons.Rounded.Search, "Tidak ditemukan", "Coba kata kunci lain.")
            } else {
                LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list.take(80), key = { it.pkg }) { app ->
                        AppPickRow(app) {
                            Fgc.store.games = Fgc.store.games + Game(app.pkg, app.label)
                            Fgc.refresh()
                            onSaved("${app.label} ditambahkan")
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(title = "Pilih aplikasi", onDismiss = { showPicker = false }) { app ->
            pkg = app.pkg
            if (name.isBlank()) name = app.label
            showPicker = false
        }
    }
}

@Composable
fun ListsScreen(state: UiSnapshot, onBack: () -> Unit, onSaved: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }
    val isWhite = tab == 0
    val entries = (if (isWhite) state.whitelist else state.blacklist).sorted()

    ScreenShell(title = "Blacklist / Whitelist", onBack = onBack, scroll = false) {
        TabSwitch(listOf("Whitelist", "Blacklist"), tab) { tab = it }
        Text(
            if (isWhite) "Aplikasi di whitelist selalu dijalankan meskipun tidak terdeteksi sebagai game."
            else "Aplikasi di blacklist dihentikan (dan dibekukan pada profil Awaken) saat sesi game berjalan.",
            style = MaterialTheme.typography.bodyMedium, color = Muted,
        )
        if (entries.isEmpty()) {
            EmptyState(
                if (isWhite) Icons.Rounded.SportsEsports else Icons.Rounded.Close,
                "Daftar kosong",
                "Tambahkan aplikasi untuk mengatur perilakunya saat bermain game.",
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it }) { p ->
                    FgcCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(p, 38.dp, p)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    Device.label(Fgc.app, p),
                                    style = MaterialTheme.typography.titleMedium, color = TextMain,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    p, style = MaterialTheme.typography.labelSmall, color = Muted,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Switch(
                                checked = true,
                                onCheckedChange = {
                                    if (isWhite) Fgc.store.whitelist = Fgc.store.whitelist - p
                                    else Fgc.store.blacklist = Fgc.store.blacklist - p
                                    Fgc.refresh(); onSaved()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Green,
                                    uncheckedThumbColor = Muted,
                                    uncheckedTrackColor = CardBg,
                                ),
                            )
                        }
                    }
                }
            }
        }
        ActionButton(
            "Tambah Aplikasi",
            onClick = { showPicker = true },
            accent = Green,
            icon = Icons.Rounded.Add,
        )
        Text(
            "Whitelist selalu menang atas blacklist. Aplikasi sistem tidak pernah dibekukan.",
            style = MaterialTheme.typography.labelSmall, color = Muted,
        )
    }

    if (showPicker) {
        AppPickerDialog(title = if (isWhite) "Tambah ke Whitelist" else "Tambah ke Blacklist", onDismiss = { showPicker = false }) { app ->
            if (isWhite) Fgc.store.whitelist = Fgc.store.whitelist + app.pkg
            else Fgc.store.blacklist = Fgc.store.blacklist + app.pkg
            Fgc.refresh(); onSaved(); showPicker = false
        }
    }
}

@Composable
private fun AppPickRow(app: AppEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.pkg, 36.dp, app.label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label, style = MaterialTheme.typography.bodyLarge, color = TextMain,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.pkg, style = MaterialTheme.typography.labelSmall, color = Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (app.system) Pill("SYS", Electric)
    }
}

@Composable
fun AppPickerDialog(title: String, onDismiss: () -> Unit, onPick: (AppEntry) -> Unit) {
    val apps = rememberApps()
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp), color = BgDeep,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderC),
            modifier = Modifier.heightIn(max = 560.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = TextMain, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Rounded.Close, "Tutup", tint = Muted,
                        modifier = Modifier.size(22.dp).clickable { onDismiss() },
                    )
                }
                Spacer(Modifier.height(10.dp))
                SearchField(query, { query = it }, "Cari aplikasi…")
                Spacer(Modifier.height(10.dp))
                val list = apps.filter {
                    query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)
                }
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list.take(120), key = { it.pkg }) { app ->
                        AppPickRow(app) { onPick(app) }
                    }
                }
            }
        }
    }
}
