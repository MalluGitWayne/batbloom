package com.malluwayne.batbloom.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.update.GITHUB_URL
import com.malluwayne.batbloom.update.Release
import com.malluwayne.batbloom.update.SelfUpdate
import com.malluwayne.batbloom.update.Updates
import kotlinx.coroutines.launch

/** Opens Android's "Update Batbloom?" screen as soon as the installer asks for it. */
@Composable
fun InstallConfirmLauncher() {
    val ctx = LocalContext.current
    val confirm = SelfUpdate.confirm
    LaunchedEffect(confirm) {
        if (confirm != null) {
            runCatching { ctx.startActivity(confirm) }
            SelfUpdate.confirm = null
        }
    }
}

@Composable
fun UpdateBanner(onOpen: () -> Unit) {
    val v = Updates.known ?: return
    SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Batbloom $v is ready", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TextButton(onOpen) { Text("Update") }
        }
    }
}

@Composable
fun UpdateSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { Updates.appVersion(ctx) }
    var checking by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<Release?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val state = SelfUpdate.state

    fun check() {
        checking = true
        message = null
        scope.launch {
            val r = Updates.latest()
            checking = false
            if (r == null) message = "Couldn't reach GitHub. Check your connection."
            else {
                Updates.remember(ctx, r)
                release = r
                if (!Updates.isNewer(r.version, current)) message = "You have the latest version."
            }
        }
    }
    LaunchedEffect(Updates.known) { if (Updates.known != null && release == null) check() }

    SectionCard("Updates") {
        LabeledRow("This version", current)
        val r = release
        when {
            state is SelfUpdate.State.Downloading -> {
                Text("Downloading…", fontSize = 13.sp)
                val p = if (state.total > 0) state.done.toFloat() / state.total else 0f
                LinearProgressIndicator({ p }, Modifier.fillMaxWidth())
                TextButton({ SelfUpdate.cancel() }) { Text("Cancel") }
            }
            state is SelfUpdate.State.Installing -> Text("Installing… Android may ask you to confirm.", fontSize = 13.sp)
            r != null && Updates.isNewer(r.version, current) -> {
                Text("Batbloom ${r.version} is available.", fontWeight = FontWeight.SemiBold, color = Teal)
                if (r.notes.isNotBlank()) Text(r.notes.take(600), fontSize = 13.sp, color = Muted)
                if (!SelfUpdate.canInstall(ctx)) {
                    Text("First allow Batbloom to install updates (a one-time Android switch).", fontSize = 13.sp)
                    OutlinedButton({ SelfUpdate.openInstallPermission(ctx) }) { Text("Allow installing updates") }
                } else if (r.apkUrl != null && SelfUpdate.trusted(r.apkUrl)) {
                    Button({ SelfUpdate.start(ctx, r.apkUrl, r.version) }) { Text("Update to ${r.version}") }
                } else {
                    OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl))) }) { Text("Open on GitHub") }
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ check() }, enabled = !checking) { Text(if (checking) "Checking…" else "Check for updates") }
                TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$GITHUB_URL/releases"))) }) { Text("Releases") }
            }
        }
        (state as? SelfUpdate.State.Failed)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        message?.let { Text(it, color = Muted, fontSize = 13.sp) }
        Text("Updates come only from Batbloom's GitHub releases. Checking is the app's only use of the internet.", color = Muted, fontSize = 11.sp)
    }
}
