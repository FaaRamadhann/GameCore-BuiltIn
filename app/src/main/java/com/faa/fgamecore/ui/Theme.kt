package com.faa.fgamecore.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF050B14)
val BgDeep = Color(0xFF03070D)
val CardBg = Color(0xFF0A1826)
val CardBg2 = Color(0xFF0C1E2E)
val BorderC = Color(0xFF16344D)
val Cyan = Color(0xFF00E5FF)
val Electric = Color(0xFF2D9CFF)
val Green = Color(0xFF2ECC71)
val Red = Color(0xFFFF4D5E)
val Yellow = Color(0xFFFFC93C)
val TextMain = Color(0xFFE6F1FF)
val Muted = Color(0xFF7E97AD)

private val scheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF001018),
    secondary = Electric,
    onSecondary = Color(0xFF001018),
    tertiary = Green,
    background = Bg,
    onBackground = TextMain,
    surface = CardBg,
    onSurface = TextMain,
    surfaceVariant = CardBg2,
    onSurfaceVariant = Muted,
    error = Red,
    outline = BorderC,
)

private val type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 1.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.5.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, letterSpacing = 0.4.sp),
)

@Composable
fun FgcTheme(content: @Composable () -> Unit) {
    // App is dark-only by design (Night Blue Sky); isSystemInDarkTheme is read so a
    // future light palette can branch here without touching screens.
    @Suppress("UNUSED_VARIABLE") val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
