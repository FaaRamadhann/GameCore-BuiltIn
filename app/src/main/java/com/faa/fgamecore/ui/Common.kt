package com.faa.fgamecore.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScreenShell(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    headerActions: @Composable (RowScope.() -> Unit)? = null,
    scroll: Boolean = true,
    bottomPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth()
                .padding(start = if (onBack != null) 4.dp else 16.dp, top = 2.dp, end = 16.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                // 48dp touch target; 24dp icon would fail the accessibility minimum.
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .clickable(role = Role.Button) { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack, "Kembali",
                        tint = TextMain, modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            if (headerActions != null) Row(verticalAlignment = Alignment.CenterVertically, content = headerActions)
        }
        Column(
            Modifier
                .fillMaxSize()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun FgcCard(
    modifier: Modifier = Modifier,
    border: Color = BorderC,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, border),
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = Muted, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
fun Tile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FgcCard(modifier = modifier, onClick = onClick) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = Green,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth().then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = if (filled) accent.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.8f) else BorderC),
    ) {
        Row(
            Modifier.padding(vertical = 13.dp, horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = if (enabled) accent else Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text, color = if (enabled) accent else Muted,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun StatusDot(color: Color, label: String, value: String, valueColor: Color = color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Muted)
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
        Text(
            text, color = color, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
fun Chevron() {
    Text("›", color = Muted, fontSize = 22.sp)
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String = "Cari game…") {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = Muted, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted) },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan.copy(alpha = 0.7f),
            unfocusedBorderColor = BorderC,
            focusedContainerColor = CardBg,
            unfocusedContainerColor = CardBg,
            cursorColor = Cyan,
            focusedTextColor = TextMain,
            unfocusedTextColor = TextMain,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun FgcTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMain)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text(placeholder, color = Muted, style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan.copy(alpha = 0.7f),
                unfocusedBorderColor = BorderC,
                focusedContainerColor = BgDeep,
                unfocusedContainerColor = BgDeep,
                cursorColor = Cyan,
                focusedTextColor = TextMain,
                unfocusedTextColor = TextMain,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun TabSwitch(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) Cyan.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) Cyan else Muted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Muted, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
    }
}

private object IconCache {
    val map = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()
}

@Composable
fun AppIcon(pkg: String, size: Dp = 38.dp, label: String? = null) {
    val ctx = LocalContext.current
    val bmp by produceState(initialValue = IconCache.map[pkg], key1 = pkg) {
        if (IconCache.map.containsKey(pkg)) {
            value = IconCache.map[pkg]
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                ctx.packageManager.getApplicationIcon(pkg).toBitmap(108, 108).asImageBitmap()
            }.getOrNull()
        }
        IconCache.map[pkg] = loaded
        value = loaded
    }
    val shape = RoundedCornerShape(9.dp)
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp!!, contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(CardBg2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (label ?: pkg).take(1).uppercase(), color = Cyan,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(BorderC.copy(alpha = 0.6f)))
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
