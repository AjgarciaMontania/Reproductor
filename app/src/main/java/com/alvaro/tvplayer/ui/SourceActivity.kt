package com.alvaro.tvplayer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.*
import com.alvaro.tvplayer.data.*
import kotlinx.coroutines.launch

class SourceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvTheme { SourceScreen() } }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun SourceScreen() {
        val prefs = remember { Prefs(this) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var customUrl by remember { mutableStateOf("") }
        var recents by remember { mutableStateOf(prefs.recentPlaylists()) }

        // Campos Xtream Codes
        var xHost by remember { mutableStateOf("") }
        var xUser by remember { mutableStateOf("") }
        var xPass by remember { mutableStateOf("") }

        fun open(url: String) {
            if (loading) return
            loading = true
            error = null
            lifecycleScope.launch {
                runCatching { PlaylistRepository.load(url) }
                    .onSuccess { playlist ->
                        loading = false
                        prefs.addRecentPlaylist(url)
                        recents = prefs.recentPlaylists()
                        PlaylistHolder.current = playlist
                        PlaylistHolder.sourceUrl = url
                        startActivity(Intent(this@SourceActivity, BrowseActivity::class.java))
                    }
                    .onFailure {
                        loading = false
                        error = it.message ?: "No se pudo cargar la lista."
                    }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Bg)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column {
                        Text(
                            "Mi Reproductor TV",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Elige un catalogo abierto o carga tu propia lista M3U",
                            fontSize = 15.sp,
                            color = TextMuted
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }

                error?.let { msg ->
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF3A1714), RoundedCorner12)
                                .padding(14.dp)
                        ) {
                            Text(msg, color = Color(0xFFFF8A7E), fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                if (loading) {
                    item {
                        Text("Cargando lista...", color = Accent, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                }

                item { SectionTitle("Catalogos abiertos") }

                items(Catalogs.presets) { cat ->
                    RowCard(
                        title = cat.title,
                        subtitle = cat.subtitle,
                        onClick = { open(cat.url) }
                    )
                }

                item {
                    Spacer(Modifier.height(18.dp))
                    SectionTitle("Tu propia lista")
                    Text(
                        "Pega la URL de tu lista M3U / M3U8",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                item {
                    UrlField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        placeholder = "http://servidor/lista.m3u",
                        onDone = { if (customUrl.isNotBlank()) open(customUrl.trim()) }
                    )
                    Spacer(Modifier.height(8.dp))
                    ActionButton("Cargar esta lista") {
                        if (customUrl.isNotBlank()) open(customUrl.trim())
                    }
                }

                item {
                    Spacer(Modifier.height(18.dp))
                    SectionTitle("Xtream Codes")
                    Text(
                        "Si tu proveedor te dio servidor, usuario y clave",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                item {
                    UrlField(xHost, { xHost = it }, "http://servidor:8080")
                    Spacer(Modifier.height(8.dp))
                    UrlField(xUser, { xUser = it }, "usuario")
                    Spacer(Modifier.height(8.dp))
                    UrlField(xPass, { xPass = it }, "clave")
                    Spacer(Modifier.height(8.dp))
                    ActionButton("Conectar Xtream") {
                        if (xHost.isNotBlank() && xUser.isNotBlank() && xPass.isNotBlank()) {
                            open(M3uParser.xtreamToM3u(xHost, xUser, xPass))
                        }
                    }
                }

                if (recents.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(18.dp))
                        SectionTitle("Listas recientes")
                    }
                    items(recents) { url ->
                        RowCard(
                            title = url.substringAfterLast('/').take(60).ifBlank { url },
                            subtitle = url,
                            trailingLabel = "Quitar",
                            onClick = { open(url) },
                            onLongClick = {
                                prefs.removeRecentPlaylist(url)
                                recents = prefs.recentPlaylists()
                            }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = TextMuted, fontSize = 12.sp)) {
                                append("Los catalogos precargados provienen del proyecto abierto iptv-org (CC0), ")
                                append("que recopila señales publicadas de forma abierta por sus emisores. ")
                                append("Esta app no aloja ni redistribuye contenido: solo reproduce la lista que tu elijas.")
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(
            text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun RowCard(
        title: String,
        subtitle: String,
        trailingLabel: String? = null,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null
    ) {
        var focused by remember { mutableStateOf(false) }
        Card(
            onClick = onClick,
            onLongClick = onLongClick ?: {},
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = Surface1,
                focusedContainerColor = Surface2
            )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (focused) Accent else Color.White,
                        maxLines = 1
                    )
                    Text(subtitle, fontSize = 12.5.sp, color = TextMuted, maxLines = 1)
                }
                if (trailingLabel != null && focused) {
                    Text("Mantener OK: $trailingLabel", fontSize = 11.sp, color = TextMuted)
                }
            }
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun ActionButton(label: String, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.colors(
                containerColor = Surface2,
                focusedContainerColor = Accent,
                contentColor = Color.White,
                focusedContentColor = Color.White
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }

    @Composable
    private fun UrlField(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        onDone: (() -> Unit)? = null
    ) {
        var focused by remember { mutableStateOf(false) }
        Box(
            Modifier
                .fillMaxWidth()
                .background(if (focused) Surface2 else Surface1, RoundedCorner12)
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            if (value.isEmpty()) {
                Text(placeholder, color = TextMuted, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
            )
        }
    }
}

internal val RoundedCorner12 = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
