package com.faa.fgamecore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faa.fgamecore.core.Fgc
import com.faa.fgamecore.ui.screens.AboutScreen
import com.faa.fgamecore.ui.screens.AddGameScreen
import com.faa.fgamecore.ui.screens.DaemonScreen
import com.faa.fgamecore.ui.screens.DashboardScreen
import com.faa.fgamecore.ui.screens.GameListScreen
import com.faa.fgamecore.ui.screens.GuideScreen
import com.faa.fgamecore.ui.screens.ListsScreen
import com.faa.fgamecore.ui.screens.LogsScreen
import com.faa.fgamecore.ui.screens.NotificationScreen
import com.faa.fgamecore.ui.screens.PermissionScreen
import com.faa.fgamecore.ui.screens.ProfilesScreen
import com.faa.fgamecore.ui.screens.SettingsScreen
import com.faa.fgamecore.ui.screens.ShizukuWarnScreen
import com.faa.fgamecore.ui.screens.SplashScreen
import com.faa.fgamecore.ui.screens.SuccessScreen
import com.faa.fgamecore.ui.screens.WelcomeScreen
import kotlinx.coroutines.launch

sealed class Dest {
    data object Splash : Dest()
    data object Welcome : Dest()
    data object Permission : Dest()
    data object Dashboard : Dest()
    data object Games : Dest()
    data object AddGame : Dest()
    data object Profiles : Dest()
    data object Lists : Dest()
    data object Settings : Dest()
    data object Daemon : Dest()
    data object Logs : Dest()
    data object Guide : Dest()
    data object About : Dest()
    data object Notifications : Dest()
    data object Warn : Dest()
    data object Success : Dest()
}

private val tabs = listOf(Dest.Dashboard, Dest.Games, Dest.AddGame, Dest.Settings)

@Composable
fun FgcRoot(openLogsRequest: Int = 0) {
    val state by Fgc.state.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var stack by remember {
        // Opened from the overlay LOGS button: Back returns to the dashboard.
        mutableStateOf(
            if (openLogsRequest > 0) listOf(Dest.Dashboard, Dest.Logs) else listOf(Dest.Splash)
        )
    }
    // Same request while the activity is already running (singleTask → onNewIntent).
    LaunchedEffect(openLogsRequest) {
        if (openLogsRequest > 0) stack = listOf(Dest.Dashboard, Dest.Logs)
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val current = stack.last()

    fun openShizuku() {
        val launch = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        val intent = launch ?: android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("market://details?id=moe.shizuku.privileged.api"),
        )
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    fun go(d: Dest) { stack = stack + d }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
    fun reset(d: Dest) { stack = listOf(d) }
    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    BackHandler(enabled = stack.size > 1) { back() }

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (current in tabs) BottomBar(current) { d -> if (d == current) Unit else reset(d) }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (current) {
                Dest.Splash -> SplashScreen {
                    reset(if (Fgc.store.onboarded) Dest.Dashboard else Dest.Welcome)
                }
                Dest.Welcome -> WelcomeScreen(
                    state = state,
                    onOpenShizuku = { openShizuku() },
                    onGuide = { go(Dest.Guide) },
                    onContinue = { go(Dest.Permission) },
                )
                Dest.Permission -> PermissionScreen(
                    state = state,
                    onBack = { back() },
                    onContinue = {
                        Fgc.store.onboarded = true
                        reset(Dest.Dashboard)
                        toast("Izin diproses. Jalankan daemon dari dashboard.")
                    },
                )
                Dest.Dashboard -> DashboardScreen(
                    state = state,
                    onGames = { go(Dest.Games) },
                    onProfiles = { go(Dest.Profiles) },
                    onLists = { go(Dest.Lists) },
                    onSettings = { go(Dest.Settings) },
                    onDaemon = { go(Dest.Daemon) },
                    onGuide = { go(Dest.Guide) },
                    onStartDaemon = {
                        Fgc.startDaemon()?.let { go(Dest.Warn) } ?: go(Dest.Success)
                    },
                )
                Dest.Games -> GameListScreen(
                    state = state,
                    onAdd = { go(Dest.AddGame) },
                    onBack = { reset(Dest.Dashboard) },
                    onChanged = { Fgc.refresh() },
                )
                Dest.AddGame -> AddGameScreen(
                    onBack = { if (stack.size > 1) back() else reset(Dest.Dashboard) },
                    onSaved = { msg -> toast(msg); reset(Dest.Dashboard) },
                )
                Dest.Profiles -> ProfilesScreen(
                    state = state,
                    onBack = { back() },
                    onSaved = { toast("Profil default disimpan") },
                )
                Dest.Lists -> ListsScreen(
                    state = state,
                    onBack = { back() },
                    onSaved = { toast("Daftar diperbarui") },
                )
                Dest.Settings -> SettingsScreen(
                    state = state,
                    onDaemon = { go(Dest.Daemon) },
                    onLogs = { go(Dest.Logs) },
                    onNotifications = { go(Dest.Notifications) },
                    onProfiles = { go(Dest.Profiles) },
                    onLists = { go(Dest.Lists) },
                    onGuide = { go(Dest.Guide) },
                    onAbout = { go(Dest.About) },
                    onChanged = { Fgc.refresh() },
                )
                Dest.Daemon -> DaemonScreen(
                    state = state,
                    onBack = { back() },
                    onLogs = { go(Dest.Logs) },
                )
                Dest.Logs -> LogsScreen(onBack = { back() })
                Dest.Guide -> GuideScreen(state = state, onBack = { back() }, onOpenShizuku = { openShizuku() })
                Dest.About -> AboutScreen(onBack = { back() })
                Dest.Notifications -> NotificationScreen(
                    state = state,
                    onBack = { back() },
                    onChanged = { Fgc.refresh() },
                )
                Dest.Warn -> ShizukuWarnScreen(
                    onBack = { back() },
                    onOpenShizuku = { openShizuku() },
                )
                Dest.Success -> SuccessScreen(onDone = { back() })
            }
        }
    }
}

@Composable
private fun BottomBar(current: Dest, onSelect: (Dest) -> Unit) {
    val items = listOf(
        Triple(Dest.Dashboard, Icons.Rounded.SpaceDashboard, "Home"),
        Triple(Dest.Games, Icons.Rounded.SportsEsports, "Game List"),
        Triple(Dest.AddGame, Icons.Rounded.AddCircle, "Add"),
        Triple(Dest.Settings, Icons.Rounded.Menu, "Menu"),
    )
    Column(Modifier.background(BgDeep).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderC))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { (dest, icon, label) ->
                val active = dest == current
                BottomItem(icon, label, active) { onSelect(dest) }
            }
        }
    }
}

@Composable
private fun BottomItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable { onClick() }.padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (active) Green else Muted, modifier = Modifier.size(22.dp))
        Text(label, color = if (active) Green else Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

