package com.faa.fgamecore.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faa.fgamecore.core.Fgc
import com.faa.fgamecore.core.ShizukuBridge
import com.faa.fgamecore.core.ShizukuStatus
import com.faa.fgamecore.core.UiSnapshot
import com.faa.fgamecore.ui.ActionButton
import com.faa.fgamecore.ui.AppIcon
import com.faa.fgamecore.ui.Bg
import com.faa.fgamecore.ui.CardBg
import com.faa.fgamecore.ui.Cyan
import com.faa.fgamecore.ui.Electric
import com.faa.fgamecore.ui.FgcCard
import com.faa.fgamecore.ui.Green
import com.faa.fgamecore.ui.Muted
import com.faa.fgamecore.ui.Red
import com.faa.fgamecore.ui.ScreenShell
import com.faa.fgamecore.ui.StatusDot
import com.faa.fgamecore.ui.TextMain
import com.faa.fgamecore.ui.Yellow
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onDone()
    }
    Column(
        Modifier.fillMaxSize().background(Bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Cyan.copy(alpha = 0.06f))
                .padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            Text(
                "Faa GameCore", color = Cyan, fontSize = 34.sp, fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("Standalone Version", style = MaterialTheme.typography.bodyLarge, color = Green)
        Spacer(Modifier.height(34.dp))
        Icon(Icons.Rounded.SportsEsports, null, tint = Electric, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(26.dp))
        Text("Play Better  •  Stay Focused", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Spacer(Modifier.height(26.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.72f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Cyan,
            trackColor = CardBg,
        )
        Spacer(Modifier.height(14.dp))
        Text("Initializing…", style = MaterialTheme.typography.bodyMedium, color = TextMain)
        Text("v1.3.1 (Standalone)", style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
fun WelcomeScreen(
    state: UiSnapshot,
    onOpenShizuku: () -> Unit,
    onGuide: () -> Unit,
    onContinue: () -> Unit,
) {
    ScreenShell(
        title = "Faa GameCore",
        subtitle = "Non-Root Support",
    ) {
        Spacer(Modifier.height(8.dp))
        FgcCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon("moe.shizuku.privileged.api", 44.dp, "S")
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Shizuku", style = MaterialTheme.typography.titleLarge, color = TextMain)
                    StatusDot(
                        if (state.shizuku == ShizukuStatus.Granted) Green else Red,
                        "Status :",
                        when (state.shizuku) {
                            ShizukuStatus.Granted -> "Running"
                            ShizukuStatus.NotInstalled -> "Not Installed"
                            ShizukuStatus.PermissionDenied -> "No Permission"
                            ShizukuStatus.NotRunning -> "Not Running"
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Faa GameCore memerlukan Shizuku untuk mengakses fitur sistem. " +
                    "Silakan aktifkan Shizuku terlebih dahulu.",
                style = MaterialTheme.typography.bodyMedium, color = Muted,
            )
            Spacer(Modifier.height(16.dp))
            ActionButton(
                text = when (state.shizuku) {
                    ShizukuStatus.NotInstalled -> "Install Shizuku"
                    ShizukuStatus.NotRunning -> "Buka Shizuku"
                    ShizukuStatus.PermissionDenied -> "Minta Izin Shizuku"
                    ShizukuStatus.Granted -> "Shizuku Siap"
                },
                icon = if (state.shizuku == ShizukuStatus.Granted) Icons.Rounded.Check else Icons.Rounded.OpenInNew,
                accent = if (state.shizuku == ShizukuStatus.Granted) Green else Green,
                onClick = {
                    if (state.shizuku == ShizukuStatus.PermissionDenied) ShizukuBridge.requestPermission()
                    else onOpenShizuku()
                },
            )
            Spacer(Modifier.height(10.dp))
            ActionButton("Cara Menggunakan", onGuide, accent = Electric, icon = Icons.Rounded.Security)
        }
        if (state.shizuku == ShizukuStatus.Granted) {
            ActionButton("Lanjutkan", onContinue, filled = true, icon = Icons.Rounded.CheckCircle)
        } else {
            ActionButton(
                "Lanjutkan tanpa Shizuku", onContinue,
                accent = Yellow, icon = Icons.Rounded.WarningAmber,
            )
            Text(
                "Aktifkan Shizuku lalu kembali ke aplikasi ini. Status akan diperbarui otomatis.",
                style = MaterialTheme.typography.labelSmall, color = Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Faa GameCore • v1.3.1\nTanpa root, tanpa Magisk.",
            style = MaterialTheme.typography.labelSmall, color = Muted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun PermissionScreen(state: UiSnapshot, onBack: () -> Unit, onContinue: () -> Unit) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    ScreenShell(title = "Izin Akses", subtitle = "Diperlukan sebelum memulai", onBack = onBack) {
        Text(
            "Faa GameCore membutuhkan izin berikut:",
            style = MaterialTheme.typography.bodyLarge, color = TextMain,
        )
        PermissionRow(
            Icons.Rounded.Security, "Akses aplikasi (Shizuku)",
            if (state.shizuku == ShizukuStatus.Granted) "Diberikan" else "Belum diberikan",
            state.shizuku == ShizukuStatus.Granted,
        )
        PermissionRow(
            Icons.Rounded.Lock, "Modifikasi pengaturan sistem",
            if (state.shizuku == ShizukuStatus.Granted) "Diberikan lewat Shizuku" else "Butuh Shizuku aktif",
            state.shizuku == ShizukuStatus.Granted,
        )
        PermissionRow(
            Icons.Rounded.Memory, "Akses memori (opsional)",
            "Dipakai untuk daftar aplikasi & ikon",
            true,
        )
        Text(
            "Pastikan Shizuku sudah aktif sebelum melanjutkan.",
            style = MaterialTheme.typography.bodyMedium, color = Muted,
        )
        Spacer(Modifier.height(4.dp))
        ActionButton(
            "Lanjutkan",
            onClick = {
                if (state.shizuku == ShizukuStatus.PermissionDenied) ShizukuBridge.requestPermission()
                if (Build.VERSION.SDK_INT >= 33 &&
                    ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onContinue()
            },
            filled = true,
        )
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
) {
    FgcCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (granted) Green else Cyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Icon(
                if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                null, tint = if (granted) Green else Yellow, modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun GuideScreen(state: UiSnapshot, onBack: () -> Unit, onOpenShizuku: () -> Unit) {
    ScreenShell(title = "Panduan Shizuku", subtitle = "Tiga langkah, sekali saja", onBack = onBack) {
        GuideStep(1, "Install Shizuku", "Download dan install aplikasi Shizuku dari Play Store.")
        GuideStep(2, "Jalankan Shizuku", "Buka aplikasi dan ikuti instruksi untuk menjalankan mode ADB.")
        GuideStep(
            3, "Aktifkan di Faa GameCore",
            "Pastikan status Shizuku di Faa GameCore berubah menjadi Running.",
        )
        FgcCard(border = Yellow.copy(alpha = 0.55f)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.WarningAmber, null, tint = Yellow, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Catatan", style = MaterialTheme.typography.titleMedium, color = Yellow)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Faa GameCore memakai Shizuku untuk mengakses fitur sistem (force-stop, freeze, " +
                            "baca foreground). Tanpa Shizuku, fitur tersebut tidak berfungsi.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMain,
                    )
                }
            }
        }
        Text(
            "Status saat ini: ${if (state.shizuku == ShizukuStatus.Granted) "Shizuku aktif" else "Shizuku belum aktif"}",
            style = MaterialTheme.typography.bodyMedium, color = Muted,
        )
        ActionButton(
            if (state.shizuku == ShizukuStatus.PermissionDenied) "Minta Izin" else "Buka Shizuku",
            onClick = {
                if (state.shizuku == ShizukuStatus.PermissionDenied) ShizukuBridge.requestPermission()
                else onOpenShizuku()
            },
            accent = Electric,
            icon = Icons.Rounded.OpenInNew,
        )
    }
}

@Composable
private fun GuideStep(n: Int, title: String, body: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Green.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", color = Green, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Green)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}

@Composable
fun ShizukuWarnScreen(onBack: () -> Unit, onOpenShizuku: () -> Unit) {
    ScreenShell(title = "Peringatan", onBack = onBack) {
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = Yellow, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(14.dp))
            Text(
                "Shizuku Tidak Aktif",
                style = MaterialTheme.typography.headlineSmall, color = TextMain,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Fitur Faa GameCore tidak akan berfungsi dengan optimal tanpa Shizuku.",
                style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            ActionButton("Buka Shizuku", onOpenShizuku, accent = Yellow, filled = true)
            Spacer(Modifier.height(10.dp))
            ActionButton("Nanti Saja", onBack, accent = Muted)
        }
    }
}

@Composable
fun SuccessScreen(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = Green, modifier = Modifier.size(76.dp))
        Spacer(Modifier.height(16.dp))
        Text("Daemon Berhasil Dijalankan", style = MaterialTheme.typography.headlineSmall, color = TextMain, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Faa GameCore siap digunakan. Nikmati pengalaman bermain yang lebih baik!",
            style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        ActionButton("Oke", onDone, filled = true, icon = Icons.Rounded.Check)
    }
}
