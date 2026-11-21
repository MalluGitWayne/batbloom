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

val Lavender = Color(0xFFB9A3FF)
val Teal = Color(0xFF7FE3D0)
val Gold = Color(0xFFFFD27F)
val Night = Color(0xFF0B0E1A)
val Panel = Color(0xFF151B30)
val Muted = Color(0xFF9AA0BC)

private val scheme = darkColorScheme(
    primary = Lavender,
    onPrimary = Color(0xFF1B1036),
    primaryContainer = Color(0xFF3A2C6B),
    onPrimaryContainer = Color(0xFFE9E0FF),
    secondary = Teal,
    onSecondary = Color(0xFF00332C),
    secondaryContainer = Color(0xFF12443D),
    tertiary = Gold,
    onTertiary = Color(0xFF2A1E00),
    tertiaryContainer = Color(0xFF4A3A10),
    background = Night,
    onBackground = Color(0xFFE6E8F2),
    surface = Night,
    onSurface = Color(0xFFE6E8F2),
    surfaceVariant = Color(0xFF1E2540),
    onSurfaceVariant = Color(0xFFC0C5DC),
    surfaceContainer = Panel,
    surfaceContainerHigh = Color(0xFF1B2240),
    surfaceContainerHighest = Color(0xFF222A4A),
    surfaceContainerLow = Color(0xFF111628),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF4A5275),
    outlineVariant = Color(0xFF2C3352),
)

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
fun BatbloomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
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
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { androidx.compose.runtime.mutableStateOf(!collapsible) }
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Row(
                Modifier.fillMaxWidth().then(if (collapsible) Modifier.clickable { open = !open } else Modifier),
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
        androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))
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
