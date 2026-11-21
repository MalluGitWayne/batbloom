@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.malluwayne.batbloom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.focus.FocusEngine
import com.malluwayne.batbloom.sound.AmbientPlayer
import com.malluwayne.batbloom.sound.Sound

/**
 * Pick a soundscape. Before a session there's a Preview button; during one ([live]) a change is heard straight away.
 */
@Composable
fun SoundPicker(live: Boolean) {
    val prefs = BatbloomApp.instance.prefs
    val sound by prefs.sound.collectAsState()
    val volume by prefs.soundVolume.collectAsState()
    var previewing by remember { mutableStateOf(false) }

    fun apply(s: Sound, v: Float) {
        if (live || previewing) AmbientPlayer.play(s, v)
        if (s == Sound.Off) previewing = false
    }

    // Leaving the setup screen ends a preview (a running session keeps its sound).
    if (!live) DisposableEffect(Unit) {
        onDispose { if (previewing && !FocusEngine.isActive) AmbientPlayer.stop() }
    }

    SectionCard("Sound") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Sound.entries.forEach { s ->
                FilterChip(sound == s, { prefs.setSound(s); apply(s, volume) }, { Text(s.label, fontSize = 13.sp) })
            }
        }
        if (sound != Sound.Off) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Volume", color = Muted, fontSize = 13.sp)
                Slider(
                    value = volume,
                    onValueChange = { prefs.setSoundVolume(it); AmbientPlayer.volume = it },
                    valueRange = 0.05f..1f,
                    modifier = Modifier.weight(1f),
                )
                if (!live) TextButton({
                    previewing = !previewing
                    if (previewing) AmbientPlayer.play(sound, volume) else AmbientPlayer.stop()
                }) { Text(if (previewing) "Stop" else "Preview") }
            }
            Text(
                if (live) "Plays while you focus; breaks are quiet." else "Plays during focus rounds. Made on your phone, no downloads.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
