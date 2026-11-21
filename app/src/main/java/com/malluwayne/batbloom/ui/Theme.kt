package com.malluwayne.batbloom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The app's own colours for one theme (accents tuned so they read on that background). */
data class Palette(
    val lavender: Color,
    val teal: Color,
    val gold: Color,
    val night: Color,
    val panel: Color,
    val muted: Color,
    val isLight: Boolean,
)

val DarkPalette = Palette(Color(0xFFB9A3FF), Color(0xFF7FE3D0), Color(0xFFFFD27F), Color(0xFF0F0F13), Color(0xFF1A1A20), Color(0xFF9C9CA8), false)
val OledPalette = DarkPalette.copy(night = Color(0xFF000000), panel = Color(0xFF0D0D12), muted = Color(0xFF8C90A6))
val LightPalette = Palette(Color(0xFF6A4FD6), Color(0xFF0D8A79), Color(0xFFA86F00), Color(0xFFF5F4FA), Color(0xFFFFFFFF), Color(0xFF5E6276), true)

val LocalPalette = androidx.compose.runtime.staticCompositionLocalOf { DarkPalette }

val Lavender: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalPalette.current.lavender
val Teal: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalPalette.current.teal
val Gold: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalPalette.current.gold
val Night: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalPalette.current.night
val Panel: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalPalette.current.panel
val Muted: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalPalette.current.muted

enum class ThemeMode(val label: String) { System("System"), Dark("Dark"), Oled("OLED black"), Light("Light") }

private fun darkScheme(p: Palette) = darkColorScheme(
    primary = p.lavender,
    onPrimary = Color(0xFF1B1036),
    primaryContainer = Color(0xFF3A2C6B),
    onPrimaryContainer = Color(0xFFE9E0FF),
    secondary = p.teal,
    onSecondary = Color(0xFF00332C),
    secondaryContainer = Color(0xFF12443D),
    onSecondaryContainer = Color(0xFFCFF7EE),
    tertiary = p.gold,
    onTertiary = Color(0xFF2A1E00),
    tertiaryContainer = Color(0xFF4A3A10),
    background = p.night,
    onBackground = Color(0xFFE6E8F2),
    surface = p.night,
    onSurface = Color(0xFFE6E8F2),
    surfaceVariant = if (p.night == Color(0xFF000000)) Color(0xFF16161D) else Color(0xFF222229),
    onSurfaceVariant = Color(0xFFC4C4CE),
    surfaceContainer = p.panel,
    surfaceContainerHigh = if (p.night == Color(0xFF000000)) Color(0xFF16161D) else Color(0xFF222229),
    surfaceContainerHighest = if (p.night == Color(0xFF000000)) Color(0xFF1E1E26) else Color(0xFF2A2A32),
    surfaceContainerLow = if (p.night == Color(0xFF000000)) Color(0xFF07070A) else Color(0xFF141418),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF4A4A56),
    outlineVariant = Color(0xFF2C2C35),
)

private fun lightScheme(p: Palette) = androidx.compose.material3.lightColorScheme(
    primary = p.lavender,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = p.teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E1EE),
    onSecondaryContainer = Color(0xFF1A1B2E),
    tertiary = p.gold,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6B3),
    background = p.night,
    onBackground = Color(0xFF1A1B2E),
    surface = p.night,
    onSurface = Color(0xFF1A1B2E),
    surfaceVariant = Color(0xFFE7E5F0),
    onSurfaceVariant = Color(0xFF474A5C),
    surfaceContainer = p.panel,
    surfaceContainerHigh = Color(0xFFECEAF4),
    surfaceContainerHighest = Color(0xFFE3E0EE),
    surfaceContainerLow = Color(0xFFFAF9FD),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFB9B6C8),
    outlineVariant = Color(0xFFDCD9E6),
)

/** Resolves System to Dark or Light from the phone's setting. */
@Composable
fun resolvePalette(mode: ThemeMode): Palette = when (mode) {
    ThemeMode.Dark -> DarkPalette
    ThemeMode.Oled -> OledPalette
    ThemeMode.Light -> LightPalette
    ThemeMode.System -> if (androidx.compose.foundation.isSystemInDarkTheme()) DarkPalette else LightPalette
}

private val type = androidx.compose.material3.Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyMedium = t.bodyMedium.copy(lineHeight = 20.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
}

/** Inside Settings, titled cards fold up so the screen is a tidy list of sections. */
val LocalCollapsibleCards = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun BatbloomTheme(palette: Palette = DarkPalette, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = if (palette.isLight) lightScheme(palette) else darkScheme(palette), typography = type) {
            Surface(color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

/** Illustrated cards (night sky, milestones) stay dark in every theme. */
@Composable
fun DarkArt(content: @Composable () -> Unit) {
    val p = if (LocalPalette.current == OledPalette) OledPalette else DarkPalette
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = darkScheme(p), typography = MaterialTheme.typography, content = content)
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    val collapsible = title != null && LocalCollapsibleCards.current
    val open = if (collapsible) OpenSections.open[title] ?: false else true
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (LocalPalette.current.isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.05f)),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Row(
                Modifier.fillMaxWidth().then(if (collapsible) Modifier.clickable { OpenSections.open[title!!] = !open } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (collapsible) {
                    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "chev")
                    Text("⌄", color = Muted, fontSize = 20.sp, modifier = Modifier.rotate(turn))
                }
            }
            if (open) content()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, color: Color = Lavender) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Muted)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

/** Current time, refreshed every [periodMs]. */
@Composable
fun rememberNow(periodMs: Long = 1000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs - now % periodMs)
        }
    }
    return now
}

fun clock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun rupees(v: Double): String = "₹" + "%,.0f".format(v)

/** A small row of pill-shaped tabs. */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainer).padding(3.dp),
    ) {
        labels.forEachIndexed { i, l ->
            Text(
                l, fontSize = 13.sp,
                color = if (i == selected) MaterialTheme.colorScheme.onSurface else Muted,
                fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(if (i == selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** A titled part of a settings group: a thin line above, a small coloured heading. */
@Composable
fun SubSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 4.dp))
        Text(title, color = Lavender, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

/** A status row: label, then "On" in teal or a small fix button. */
@Composable
fun StatusRow(label: String, ok: Boolean, fixLabel: String, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (ok) Text("On", color = Teal, fontWeight = FontWeight.SemiBold)
        else androidx.compose.material3.FilledTonalButton(onFix) { Text(fixLabel, fontSize = 13.sp) }
    }
}

/** Behind small plant pictures: a dark night tile in every theme, so the glow and pale petals stay visible. */
@Composable
fun Modifier.plantTile(): Modifier = this.background(
    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF141A38), Color(0xFF221B3D))),
)

/** Which titled Settings and Quit sections are open, kept while the app runs. */
object OpenSections {
    val open = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
}
