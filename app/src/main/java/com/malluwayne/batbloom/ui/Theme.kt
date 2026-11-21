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
import androidx.compose.ui.Alignment
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

@Composable
fun BatbloomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme) {
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
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
