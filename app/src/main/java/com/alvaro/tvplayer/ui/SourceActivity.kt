@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.alvaro.tvplayer.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.alvaro.tvplayer.data.*
import kotlinx.coroutines.launch

class SourceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvTheme { SourceScreen() } }
    }

    @Composable
    private fun SourceScreen() {
        val prefs = remember { Prefs(this) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var customUrl by remember { mutableStateOf("") }
        var recents by remember { mutableStateOf(prefs.recentPlaylists()) }

        // Xtream Codes
        var xHost by remember { mutableStateOf("") }
        var xUser by remember { mutableStateOf("") }
        var xPass by remember { mutableStateOf("") }

        // ---- Actualizacion automatica ----
        val activity = this
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        var updateMessage by remember { mutableStateOf<String?>(null) }
        var downloadProgress by remember { mutableIntStateOf(-1) }

        val currentVersionCode = remember {
            runCatching {
                val info = packageManager.getPackageInfo(packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
                else @Suppress("DEPRECATION") info.versionCode
            }.getOrDefault(1)
        }

        val currentVersionName = remember {
            runCatching {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
            }.getOrDefault("?")
        }
        var comprobando by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { update = UpdateChecker.check(currentVersionCode) }

        // Comprobacion a peticion del usuario: aqui SI se informa del resultado,
        // a diferencia de la del arranque, que es silenciosa.
        fun comprobarActualizaciones() {
            if (comprobando) return
            comprobando = true
            updateMessage = null
            lifecycleScope.launch {
                when (val r = UpdateChecker.comprobar(currentVersionCode)) {
                    is UpdateResult.Disponible -> {
                        update = r.info
                        updateMessage = null
                    }
                    is UpdateResult.AlDia ->
                        updateMessage = "Ya tienes la ultima version publicada ($currentVersionName)."
                    is UpdateResult.SinPublicaciones ->
                        updateMessage = "Todavia no hay ninguna version publicada en el repositorio.\n" +
                            "Subir cambios no basta: hay que lanzar el flujo \"Publicar version\" " +
                            "en la pestaña Actions para que se cree un Release."
                    is UpdateResult.Error ->
                        updateMessage = "No se pudo comprobar: ${r.mensaje}"
                }
                comprobando = false
            }
        }

        DisposableEffect(Unit) {
            val receiver = UpdateInstaller.registerStatusReceiver(activity) { msg ->
                updateMessage = msg
                downloadProgress = -1
            }
            onDispose { runCatching { activity.unregisterReceiver(receiver) } }
        }

        // Foco inicial: necesario para navegar con el control remoto en TV.
        val firstItemFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { firstItemFocus.requestFocus() } }

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
                        // Lista nueva: los resultados de verificacion anteriores ya no valen.
                        ChannelChecker.reset()
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

        fun startUpdate(info: UpdateInfo) {
            if (!UpdateInstaller.canInstall(activity)) {
                updateMessage = "Autoriza a esta app a instalar aplicaciones y vuelve a intentarlo."
                runCatching { startActivity(UpdateInstaller.permissionSettingsIntent(activity)) }
                return
            }
            downloadProgress = 0
            updateMessage = null
            lifecycleScope.launch {
                runCatching {
                    val apk = UpdateInstaller.download(activity, info) { downloadProgress = it }
                    UpdateInstaller.install(activity, apk)
                }.onFailure {
                    downloadProgress = -1
                    updateMessage = it.message ?: "Fallo la actualizacion."
                }
            }
        }

        Box(Modifier.fillMaxSize().background(Bg)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column {
                        Text(
                            "Mi Reproductor TV",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Elige un catalogo abierto o carga tu propia lista M3U",
                            fontSize = 14.sp,
                            color = TextMuted
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                update?.let { info ->
                    item {
                        UpdateBanner(
                            info = info,
                            progress = downloadProgress,
                            message = updateMessage,
                            onInstall = { startUpdate(info) },
                            onDismiss = { update = null }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                if (update == null && updateMessage != null) {
                    item {
                        InfoBox(updateMessage!!, TextMuted, Surface1)
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (loading) {
                    item {
                        InfoBox("Cargando lista, espera un momento...", Accent, Surface1)
                        Spacer(Modifier.height(8.dp))
                    }
                }

                error?.let { msg ->
                    item {
                        InfoBox(msg, Color(0xFFFF8A7E), Color(0xFF3A1714))
                        Spacer(Modifier.height(8.dp))
                    }
                }

                item { SectionTitle("Catalogos abiertos") }

                itemsIndexed(Catalogs.presets) { index, cat ->
                    RowCard(
                        title = cat.title,
                        subtitle = cat.subtitle,
                        onClick = { open(cat.url) },
                        modifier = if (index == 0) Modifier.focusRequester(firstItemFocus)
                                   else Modifier
                    )
                }

                item {
                    Spacer(Modifier.height(16.dp))
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
                        placeholder = "http://servidor/lista.m3u"
                    )
                    Spacer(Modifier.height(8.dp))
                    ActionButton("Cargar esta lista") {
                        if (customUrl.isNotBlank()) open(customUrl.trim())
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
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
                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Listas recientes")
                    }
                    items(recents) { url ->
                        RowCard(
                            title = url.substringAfterLast('/').take(60).ifBlank { url },
                            subtitle = url,
                            hint = "Manten pulsado para quitar",
                            onClick = { open(url) },
                            onLongClick = {
                                prefs.removeRecentPlaylist(url)
                                recents = prefs.recentPlaylists()
                            }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(20.dp))
                    SectionTitle("Version")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Instalada: $currentVersionName (codigo $currentVersionCode)",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                            Text(
                                "Las actualizaciones llegan desde los Releases del repositorio",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        FocusableCard(
                            onClick = { comprobarActualizaciones() },
                            containerColor = Surface2,
                            focusedContainerColor = Accent
                        ) {
                            Text(
                                if (comprobando) "Comprobando..." else "Buscar actualizaciones",
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Los catalogos precargados provienen del proyecto abierto iptv-org (CC0), " +
                        "que recopila señales publicadas de forma abierta por sus emisores. " +
                        "Esta app no aloja ni redistribuye contenido: solo reproduce la lista que elijas.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    @Composable
    private fun InfoBox(text: String, textColor: Color, bg: Color) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(bg, RoundedCorner12)
                .padding(14.dp)
        ) {
            Text(text, color = textColor, fontSize = 14.sp)
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(
            text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }

    @Composable
    private fun RowCard(
        title: String,
        subtitle: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        hint: String? = null,
        onLongClick: (() -> Unit)? = null
    ) {
        FocusableCard(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.fillMaxWidth()
        ) { active ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (active) Accent else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle,
                        fontSize = 12.5.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hint != null && active) {
                    Text(hint, fontSize = 11.sp, color = TextMuted)
                }
            }
        }
    }

    @Composable
    private fun ActionButton(label: String, onClick: () -> Unit) {
        FocusableCard(
            onClick = onClick,
            containerColor = Surface2,
            focusedContainerColor = Accent
        ) { active ->
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (active) Color.White else Accent
                )
                Spacer(Modifier.width(8.dp))
                Text(label, color = Color.White, fontSize = 15.sp)
            }
        }
    }

    @Composable
    private fun UrlField(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String
    ) {
        var focused by remember { mutableStateOf(false) }
        val fieldFocus = remember { FocusRequester() }

        Box(
            Modifier
                .fillMaxWidth()
                .background(if (focused) Surface2 else Surface1, RoundedCorner12)
                // Un toque en cualquier parte del recuadro lleva el cursor al campo
                .clickable { runCatching { fieldFocus.requestFocus() } }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (value.isEmpty()) {
                Text(placeholder, color = TextMuted, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onFocusChanged { focused = it.isFocused }
            )
        }
    }

    @Composable
    private fun UpdateBanner(
        info: UpdateInfo,
        progress: Int,
        message: String?,
        onInstall: () -> Unit,
        onDismiss: () -> Unit
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF10233F), RoundedCorner12)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Nueva version disponible: ${info.versionName}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(info.notes, color = TextMuted, fontSize = 13.sp, maxLines = 3)
            }

            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Color(0xFFFFCF5C), fontSize = 13.sp)
            }

            Spacer(Modifier.height(12.dp))

            when {
                progress in 0..99 -> {
                    Text("Descargando... $progress%", color = Accent, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(Surface2, RoundedCorner12)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress / 100f)
                                .height(6.dp)
                                .background(Accent, RoundedCorner12)
                        )
                    }
                }

                progress == 100 -> Text("Preparando la instalacion...", color = Accent, fontSize = 14.sp)

                else -> Row {
                    FocusableCard(
                        onClick = onInstall,
                        containerColor = Surface2,
                        focusedContainerColor = Accent
                    ) {
                        Text(
                            "Actualizar ahora",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    FocusableCard(
                        onClick = onDismiss,
                        containerColor = Color.Transparent,
                        focusedContainerColor = Surface2
                    ) { active ->
                        Text(
                            "Ahora no",
                            color = if (active) Color.White else TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp)
                        )
                    }
                }
            }
        }
    }
}

internal val RoundedCorner12 = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
