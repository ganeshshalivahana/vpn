package com.example.myvpn

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val Bg = Color(0xFF0B1020)
private val Card = Color(0xFF151C30)
private val Accent = Color(0xFF3DDC97)
private val Warn = Color(0xFFFFB454)
private val Bad = Color(0xFFFF6B6B)
private val Dim = Color(0xFF8C97B2)

class MainActivity : ComponentActivity() {

    private lateinit var store: ServerStore
    private var servers by mutableStateOf<List<Server>>(emptyList())
    private var pendingConnect: Server? = null
    private var free by mutableStateOf<List<FreeServer>>(emptyList())
    private var freeLoading by mutableStateOf(false)
    private var freeError by mutableStateOf<String?>(null)
    private val uiScope = MainScope()

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val s = pendingConnect
        pendingConnect = null
        if (result.resultCode == Activity.RESULT_OK && s != null) VpnManager.connect(this, s)
    }

    private val importFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> importUris(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ServerStore(this)
        servers = store.load()
        VpnManager.sync(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Card)) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    Screen()
                }
            }
        }
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    // ---------- actions ----------

    private fun loadFree() {
        freeLoading = true
        freeError = null
        uiScope.launch {
            try {
                free = VpnGate.fetch()
                if (free.isEmpty()) freeError = "The server list was empty"
            } catch (e: Exception) {
                freeError = e.localizedMessage ?: "Network error"
            }
            freeLoading = false
        }
    }

    /** Hands the .ovpn profile to the OpenVPN for Android app, which runs the actual tunnel. */
    private fun openInOpenVpn(s: FreeServer) {
        try {
            val dir = File(cacheDir, "ovpn").apply { mkdirs() }
            val f = File(dir, "vpngate-${s.cc}-${s.ip}.ovpn")
            f.writeText(s.ovpn)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/x-openvpn-profile")
                    .setPackage("de.blinkt.openvpn")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Install \"OpenVPN for Android\" first", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=de.blinkt.openvpn")))
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open profile: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun connect(server: Server) {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            pendingConnect = server
            vpnPermission.launch(prepare)
        } else {
            VpnManager.connect(this, server)
        }
    }

    private fun addServer(s: Server) {
        servers = servers + s
        store.save(servers)
    }

    private fun removeServer(s: Server) {
        servers = servers.filter { it.id != s.id }
        store.save(servers)
    }

    private fun importUris(uris: List<Uri>) {
        var ok = 0
        var failed = 0
        for (uri in uris) {
            try {
                val fileName = displayName(uri)
                val text = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                val base = fileName.substringBeforeLast('.')
                addServer(ServerStore.create(base, Countries.fromFileName(fileName), text))
                ok++
            } catch (e: Exception) {
                failed++
            }
        }
        Toast.makeText(this, "Imported $ok server(s)" + if (failed > 0) ", $failed invalid" else "", Toast.LENGTH_LONG).show()
    }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        } ?: "server.conf"

    // ---------- UI ----------

    @Composable
    private fun Screen() {
        val status by VpnManager.status.collectAsState()
        var selectedId by remember { mutableStateOf<String?>(null) }
        var showPaste by remember { mutableStateOf(false) }
        var tab by remember { mutableStateOf(0) }
        LaunchedEffect(tab) { if (tab == 1 && free.isEmpty() && !freeLoading) loadFree() }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

        val selected = servers.firstOrNull { it.id == selectedId }
        val connected = status.state == VpnManager.State.CONNECTED
        val busy = status.state == VpnManager.State.CONNECTING

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(40.dp))
            Text("MyVPN", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(20.dp))

            // Connect button + status
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val ring = when (status.state) {
                    VpnManager.State.CONNECTED -> Accent
                    VpnManager.State.CONNECTING -> Warn
                    VpnManager.State.ERROR -> Bad
                    else -> Dim
                }
                Box(
                    Modifier.size(120.dp).clip(CircleShape).background(Card)
                        .clickable(enabled = !busy) {
                            if (connected) VpnManager.disconnect(this@MainActivity)
                            else if (selected == null)
                                Toast.makeText(this@MainActivity, "Pick or import a server first", Toast.LENGTH_SHORT).show()
                            else connect(selected)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(104.dp).clip(CircleShape).background(ring.copy(alpha = 0.25f)), Alignment.Center) {
                        Text(if (connected) "ON" else if (busy) "…" else "OFF",
                            color = ring, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    when (status.state) {
                        VpnManager.State.CONNECTED -> "Connected to ${status.serverName}"
                        VpnManager.State.CONNECTING -> "Connecting to ${status.serverName}…"
                        VpnManager.State.ERROR -> "Error: ${status.error}"
                        else -> selected?.let { "Ready: ${it.name}" } ?: "Not connected"
                    },
                    color = Color.White, textAlign = TextAlign.Center
                )
                if (connected) {
                    Text(
                        "${formatDuration((now - status.since) / 1000)}  •  ↓ ${formatBytes(status.rx)}  ↑ ${formatBytes(status.tx)}",
                        color = Dim, fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { tab = 0 }, modifier = Modifier.weight(1f)) {
                    Text("My servers (WireGuard)", color = if (tab == 0) Accent else Dim)
                }
                TextButton(onClick = { tab = 1 }, modifier = Modifier.weight(1f)) {
                    Text("Free servers", color = if (tab == 1) Accent else Dim)
                }
            }
            if (tab == 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { importFiles.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                        Text("Import .conf files")
                    }
                    OutlinedButton(onClick = { showPaste = true }, modifier = Modifier.weight(1f)) {
                        Text("Paste config")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (tab == 1) {
                FreeList()
            } else if (servers.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        "No servers yet.\n\nImport WireGuard .conf files from any provider (or your own server). " +
                            "Name files like us-nyc-01.conf or de-berlin.conf and they are grouped by country.",
                        color = Dim, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                val grouped = servers.groupBy { it.country }
                    .toSortedMap(compareBy<String> { Countries.name(it) })
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    grouped.forEach { (cc, list) ->
                        item(key = "h_$cc") {
                            Text("${Countries.flag(cc)}  ${Countries.name(cc)} (${list.size})",
                                color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                        }
                        items(list, key = { it.id }) { s ->
                            val isSel = s.id == selectedId
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) Accent.copy(alpha = 0.18f) else Card)
                                    .clickable {
                                        selectedId = s.id
                                        // Switch live if already connected.
                                        if (connected) connect(s)
                                    }
                                    .padding(start = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.name, color = Color.White, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    if (selectedId == s.id) selectedId = null
                                    removeServer(s)
                                }) { Text("✕", color = Dim) }
                            }
                        }
                    }
                }
            }
        }

        if (showPaste) PasteDialog(onDismiss = { showPaste = false })
    }

    @Composable
    private fun FreeList() {
        Text(
            "Free volunteer servers from VPN Gate. Run by strangers: don't use for banking or logins. " +
                "Tapping a server opens it in OpenVPN for Android, which must be installed; save the profile there, then tap it to connect.",
            color = Warn, fontSize = 12.sp
        )
        when {
            freeLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Loading servers…", color = Dim) }
            freeError != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't load: $freeError", color = Bad, textAlign = TextAlign.Center)
                TextButton(onClick = { loadFree() }) { Text("Retry") }
            }
            else -> {
                val grouped = free.groupBy { it.cc }
                    .mapValues { (_, l) -> l.sortedByDescending { it.score }.take(5) }
                    .toSortedMap(compareBy<String> { Countries.name(it) })
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    grouped.forEach { (cc, list) ->
                        item(key = "fh_$cc") {
                            Text("${Countries.flag(cc)}  ${Countries.name(cc)}",
                                color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                        }
                        items(list, key = { "${it.cc}_${it.ip}" }) { s ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card)
                                    .clickable { openInOpenVpn(s) }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.host, color = Color.White, modifier = Modifier.weight(1f))
                                Text(String.format(Locale.US, "%.0f Mbps • %d ms", s.speedMbps, s.ping),
                                    color = Dim, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PasteDialog(onDismiss: () -> Unit) {
        var name by remember { mutableStateOf("") }
        var cc by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(cc, { cc = it.take(2) }, label = { Text("Country code (e.g. US)") }, singleLine = true)
                    OutlinedTextField(text, { text = it }, label = { Text("WireGuard config") }, minLines = 5, maxLines = 8)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        addServer(ServerStore.create(name, cc.ifBlank { "ZZ" }, text))
                        onDismiss()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Invalid config: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    private fun formatBytes(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", b / 1024.0)
        b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
        else -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
    }

    private fun formatDuration(sec: Long): String {
        val s = sec.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }
}
